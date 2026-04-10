from datetime import date, datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.middleware.rate_limit import RateLimiter
from app.models import Task, TaskStatus, User
from app.schemas.ai import (
    EisenhowerRequest,
    EisenhowerResponse,
    EisenhowerSummary,
    PomodoroRequest,
    PomodoroResponse,
)

router = APIRouter(prefix="/ai", tags=["ai"])

# Rate limiter: max 1 call per 5 minutes (300 seconds) per IP
ai_rate_limiter = RateLimiter(max_requests=1, window_seconds=300)


async def _fetch_incomplete_tasks(
    user: User, db: AsyncSession, task_ids: list[int] | None = None
) -> list[Task]:
    query = select(Task).where(
        Task.user_id == user.id,
        Task.status != TaskStatus.DONE,
        Task.status != TaskStatus.CANCELLED,
    )
    if task_ids:
        query = query.where(Task.id.in_(task_ids))
    result = await db.execute(query.order_by(Task.sort_order, Task.created_at))
    return list(result.scalars().all())


def _task_to_ai_dict(task: Task) -> dict:
    return {
        "task_id": task.id,
        "title": task.title,
        "description": task.description,
        "due_date": task.due_date.isoformat() if task.due_date else None,
        "priority": task.priority,
        "project_id": task.project_id,
        "eisenhower_quadrant": task.eisenhower_quadrant,
    }


@router.post("/eisenhower", response_model=EisenhowerResponse)
async def categorize_eisenhower(
    data: EisenhowerRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    ai_rate_limiter.check(request)

    tasks = await _fetch_incomplete_tasks(current_user, db, data.task_ids)
    if not tasks:
        return EisenhowerResponse(
            categorizations=[],
            summary=EisenhowerSummary(),
        )

    task_dicts = [_task_to_ai_dict(t) for t in tasks]

    try:
        from app.services.ai_productivity import categorize_eisenhower as ai_categorize

        categorizations = ai_categorize(task_dicts, date.today())
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    # Build task lookup for updating
    task_map = {t.id: t for t in tasks}
    now = datetime.now(timezone.utc)

    valid_quadrants = {"Q1", "Q2", "Q3", "Q4"}
    cleaned = []
    for cat in categorizations:
        tid = cat.get("task_id")
        quadrant = cat.get("quadrant", "")
        reason = cat.get("reason", "")
        if tid in task_map and quadrant in valid_quadrants:
            task_map[tid].eisenhower_quadrant = quadrant
            task_map[tid].eisenhower_updated_at = now
            cleaned.append({"task_id": tid, "quadrant": quadrant, "reason": reason})

    await db.flush()

    summary = EisenhowerSummary()
    for cat in cleaned:
        current = getattr(summary, cat["quadrant"])
        setattr(summary, cat["quadrant"], current + 1)

    return EisenhowerResponse(
        categorizations=[
            {"task_id": c["task_id"], "quadrant": c["quadrant"], "reason": c["reason"]}
            for c in cleaned
        ],
        summary=summary,
    )


@router.post("/pomodoro-plan", response_model=PomodoroResponse)
async def plan_pomodoro(
    data: PomodoroRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    ai_rate_limiter.check(request)

    tasks = await _fetch_incomplete_tasks(current_user, db)
    if not tasks:
        return PomodoroResponse(
            sessions=[],
            total_sessions=0,
            total_work_minutes=0,
            total_break_minutes=0,
            skipped_tasks=[],
        )

    task_dicts = [_task_to_ai_dict(t) for t in tasks]

    try:
        from app.services.ai_productivity import plan_pomodoro as ai_plan

        plan = ai_plan(
            tasks=task_dicts,
            available_minutes=data.available_minutes,
            session_length=data.session_length,
            break_length=data.break_length,
            long_break_length=data.long_break_length,
            focus_preference=data.focus_preference,
            today=date.today(),
        )
    except RuntimeError:
        raise HTTPException(status_code=503, detail="AI service temporarily unavailable")
    except ValueError:
        raise HTTPException(status_code=500, detail="AI returned an invalid response")

    return PomodoroResponse(**plan)
