from datetime import datetime, timezone
from typing import Any, Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import (
    Attachment,
    Goal,
    GoalStatus,
    Habit,
    HabitCompletion,
    HabitFrequency,
    Project,
    ProjectStatus,
    Tag,
    Task,
    TaskStatus,
    User,
)
from app.schemas.sync import (
    SyncChange,
    SyncOperation,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
)

router = APIRouter(prefix="/sync", tags=["sync"])

ENTITY_MAP = {
    "goal": Goal,
    "project": Project,
    "task": Task,
    "tag": Tag,
    "habit": Habit,
    "habit_completion": HabitCompletion,
    "attachment": Attachment,
}

STATUS_ENUM_MAP = {
    "goal": GoalStatus,
    "project": ProjectStatus,
    "task": TaskStatus,
}

# Known columns on each model are derived from the SQLAlchemy table, so the
# sync router can silently drop unknown fields from clients (e.g. Android
# entities may carry extra local-only columns that don't exist server-side).


def _model_columns(model) -> set[str]:
    return {col.name for col in model.__table__.columns}


def _coerce_enum(enum_cls, value):
    """Coerce an incoming enum value (string name, value, or enum member)
    to the lowercase string value stored in Postgres."""
    if value is None:
        return None
    if isinstance(value, enum_cls):
        return value.value
    if isinstance(value, str):
        # Accept either "active" or "ACTIVE"
        try:
            return enum_cls(value).value
        except ValueError:
            try:
                return enum_cls[value.upper()].value
            except KeyError as e:
                raise ValueError(
                    f"Invalid {enum_cls.__name__} value: {value!r}"
                ) from e
    raise ValueError(f"Invalid {enum_cls.__name__} value: {value!r}")


async def _get_or_create_inbox_goal(user: User, db: AsyncSession) -> Goal:
    """Return (or create) a default 'Inbox' goal for the user so that sync
    operations that omit goal_id still satisfy the NOT NULL FK constraint."""
    result = await db.execute(
        select(Goal).where(Goal.user_id == user.id, Goal.title == "Inbox")
    )
    goal = result.scalar_one_or_none()
    if goal:
        return goal
    goal = Goal(user_id=user.id, title="Inbox", status=GoalStatus.ACTIVE.value)
    db.add(goal)
    await db.flush()
    return goal


async def _get_or_create_inbox_project(user: User, db: AsyncSession) -> Project:
    """Return (or create) a default 'Inbox' project (under the Inbox goal)."""
    result = await db.execute(
        select(Project).where(Project.user_id == user.id, Project.title == "Inbox")
    )
    project = result.scalar_one_or_none()
    if project:
        return project
    goal = await _get_or_create_inbox_goal(user, db)
    project = Project(
        user_id=user.id,
        goal_id=goal.id,
        title="Inbox",
        status=ProjectStatus.ACTIVE.value,
    )
    db.add(project)
    await db.flush()
    return project


async def _prepare_entity_data(
    entity_type: str,
    raw: dict[str, Any],
    user: User,
    db: AsyncSession,
    is_create: bool,
) -> tuple[Optional[dict[str, Any]], Optional[str]]:
    """Normalize client payload for an entity: map aliases, coerce enums,
    fill defaults for required fields (on create), and drop unknown columns.

    Returns (data_dict, error_message). Only one is non-None.
    """
    model = ENTITY_MAP[entity_type]
    data = dict(raw)

    # --- Aliases from Android-side field names ---
    if entity_type in ("project", "goal"):
        if "title" not in data and "name" in data:
            data["title"] = data.pop("name")

    # --- Drop columns the server model doesn't know about ---
    allowed = _model_columns(model)
    # Never let the client set these - always derived server-side
    for server_managed in ("id", "user_id", "created_at", "updated_at"):
        data.pop(server_managed, None)
    unknown = [k for k in list(data.keys()) if k not in allowed]
    for k in unknown:
        data.pop(k)

    # --- Enum coercion ---
    if "status" in data and entity_type in STATUS_ENUM_MAP:
        try:
            data["status"] = _coerce_enum(STATUS_ENUM_MAP[entity_type], data["status"])
        except ValueError as e:
            return None, str(e)
    if "frequency" in data and entity_type == "habit":
        try:
            data["frequency"] = _coerce_enum(HabitFrequency, data["frequency"])
        except ValueError as e:
            return None, str(e)

    # --- user_id (for entities that have it, only on create) ---
    if is_create and "user_id" in allowed:
        data["user_id"] = user.id

    if not is_create:
        # Partial updates: no required-field checks, no default backfill
        return data, None

    # --- Required-field defaults / FK fallbacks (create only) ---
    if entity_type == "goal":
        if not data.get("title"):
            return None, "goal requires a 'title'"
        data.setdefault("status", GoalStatus.ACTIVE.value)

    elif entity_type == "project":
        if not data.get("title"):
            return None, "project requires a 'title'"
        data.setdefault("status", ProjectStatus.ACTIVE.value)
        if not data.get("goal_id"):
            inbox_goal = await _get_or_create_inbox_goal(user, db)
            data["goal_id"] = inbox_goal.id

    elif entity_type == "task":
        if not data.get("title"):
            return None, "task requires a 'title'"
        data.setdefault("status", TaskStatus.TODO.value)
        if not data.get("project_id"):
            inbox_project = await _get_or_create_inbox_project(user, db)
            data["project_id"] = inbox_project.id

    elif entity_type == "tag":
        if not data.get("name"):
            return None, "tag requires a 'name'"

    elif entity_type == "habit":
        if not data.get("name"):
            return None, "habit requires a 'name'"
        data.setdefault("frequency", HabitFrequency.DAILY.value)

    elif entity_type == "habit_completion":
        if not data.get("habit_id"):
            return None, "habit_completion requires 'habit_id'"
        if not data.get("date"):
            return None, "habit_completion requires 'date'"

    elif entity_type == "attachment":
        if not data.get("task_id"):
            return None, "attachment requires 'task_id'"
        if not data.get("name") or not data.get("uri") or not data.get("type"):
            return None, "attachment requires 'name', 'uri', and 'type'"

    return data, None


class _SyncOpError(Exception):
    """Internal marker exception that carries a human-readable sync error."""


async def _do_operation(
    op: SyncOperation, user: User, db: AsyncSession
) -> str | None:
    model = ENTITY_MAP.get(op.entity_type)
    if not model:
        return f"Unknown entity type: {op.entity_type}"

    if op.operation == "create":
        if not op.data:
            return f"{op.entity_type} create operation requires 'data'"
        data, err = await _prepare_entity_data(
            op.entity_type, op.data, user, db, is_create=True
        )
        if err:
            return err
        entity = model(**data)
        db.add(entity)
        await db.flush()

    elif op.operation == "update":
        if not op.entity_id or not op.data:
            return f"{op.entity_type} update requires 'entity_id' and 'data'"
        query = select(model).where(model.id == op.entity_id)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == user.id)
        result = await db.execute(query)
        entity = result.scalar_one_or_none()
        if not entity:
            return f"{op.entity_type} {op.entity_id} not found"
        data, err = await _prepare_entity_data(
            op.entity_type, op.data, user, db, is_create=False
        )
        if err:
            return err
        for key, value in data.items():
            setattr(entity, key, value)
        await db.flush()

    elif op.operation == "delete":
        if not op.entity_id:
            return f"{op.entity_type} delete requires 'entity_id'"
        query = select(model).where(model.id == op.entity_id)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == user.id)
        result = await db.execute(query)
        entity = result.scalar_one_or_none()
        if not entity:
            return f"{op.entity_type} {op.entity_id} not found"
        await db.delete(entity)
        await db.flush()

    else:
        return f"Unknown operation: {op.operation}"

    return None


async def _process_operation(
    op: SyncOperation, user: User, db: AsyncSession
) -> str | None:
    """Run a single operation inside a SAVEPOINT so that failures don't
    abort the whole transaction and prevent subsequent ops from running."""
    try:
        async with db.begin_nested():
            err = await _do_operation(op, user, db)
            if err:
                # Roll the savepoint back so the outer transaction stays clean.
                raise _SyncOpError(err)
        return None
    except _SyncOpError as e:
        return str(e)
    except Exception as e:
        return f"{op.entity_type} {op.operation} failed: {type(e).__name__}: {e}"


@router.post("/push", response_model=SyncPushResponse)
async def sync_push(
    data: SyncPushRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    errors = []
    processed = 0

    for op in data.operations:
        error = await _process_operation(op, current_user, db)
        if error:
            errors.append(error)
        else:
            processed += 1

    await db.flush()

    return SyncPushResponse(
        processed=processed,
        errors=errors,
        server_timestamp=datetime.now(timezone.utc),
    )


@router.get("/pull", response_model=SyncPullResponse)
async def sync_pull(
    since: datetime | None = Query(default=None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    changes: list[SyncChange] = []

    for entity_type, model in ENTITY_MAP.items():
        if entity_type in ("habit_completion", "attachment"):
            # These don't have user_id directly; handled below.
            continue

        query = select(model)
        if hasattr(model, "user_id"):
            query = query.where(model.user_id == current_user.id)

        if since and hasattr(model, "updated_at"):
            query = query.where(model.updated_at > since)
        elif since and hasattr(model, "created_at"):
            query = query.where(model.created_at > since)

        result = await db.execute(query)
        for entity in result.scalars().all():
            data = {}
            for col in entity.__table__.columns:
                val = getattr(entity, col.name)
                if hasattr(val, "value"):
                    val = val.value
                if hasattr(val, "isoformat"):
                    val = val.isoformat()
                data[col.name] = val

            timestamp = getattr(entity, "updated_at", None) or getattr(entity, "created_at", None)
            changes.append(
                SyncChange(
                    entity_type=entity_type,
                    operation="upsert",
                    entity_id=entity.id,
                    data=data,
                    timestamp=timestamp or datetime.now(timezone.utc),
                )
            )

    # Pull habit completions via the user's habits
    habit_result = await db.execute(
        select(Habit.id).where(Habit.user_id == current_user.id)
    )
    habit_ids = [r[0] for r in habit_result.all()]
    if habit_ids:
        comp_query = select(HabitCompletion).where(HabitCompletion.habit_id.in_(habit_ids))
        if since:
            comp_query = comp_query.where(HabitCompletion.created_at > since)
        comp_result = await db.execute(comp_query)
        for c in comp_result.scalars().all():
            changes.append(
                SyncChange(
                    entity_type="habit_completion",
                    operation="upsert",
                    entity_id=c.id,
                    data={
                        "id": c.id,
                        "habit_id": c.habit_id,
                        "date": c.date.isoformat(),
                        "count": c.count,
                    },
                    timestamp=c.created_at,
                )
            )

    return SyncPullResponse(
        changes=changes,
        server_timestamp=datetime.now(timezone.utc),
    )
