"""Backend-mediated Google Calendar sync endpoints.

All endpoints require a valid Firebase-authenticated user (see
`middleware.auth.get_current_user`). The backend owns the user's Google
OAuth refresh token; Android calls these endpoints and never touches the
Calendar API directly.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import (
    CalendarSyncSettings,
    IntegrationConfig,
    IntegrationSource,
    User,
)
from app.services import calendar_service
from app.services.integrations import calendar_integration

logger = logging.getLogger(__name__)

router = APIRouter(tags=["calendar"])


# ============================================================================
# Pydantic models
# ============================================================================


class CalendarAuthorizeResponse(BaseModel):
    url: str


class CalendarStatusResponse(BaseModel):
    connected: bool
    email: str | None = None


class CalendarListItem(BaseModel):
    id: str
    name: str
    color: str | None = None
    primary: bool = False
    writable: bool = True


class CalendarListResponse(BaseModel):
    calendars: list[CalendarListItem]


class CalendarPushRequest(BaseModel):
    taskId: int
    title: str
    description: str | None = None
    notes: str | None = None
    dueDateMillis: int | None = None
    dueTimeMillis: int | None = None
    scheduledStartMillis: int | None = None
    estimatedDurationMinutes: int | None = None
    priority: int = 0
    isCompleted: bool = False
    knownEventId: str | None = None
    knownCalendarId: str | None = None


class CalendarPushResponse(BaseModel):
    eventId: str | None = None
    calendarId: str | None = None
    etag: str | None = None


class EventsPullRequest(BaseModel):
    calendarIds: list[str]


class EventsPullResponse(BaseModel):
    created: int = 0
    updated: int = 0
    deleted: int = 0


class CalendarEventDto(BaseModel):
    id: str
    title: str
    startMillis: int
    endMillis: int
    allDay: bool = False
    calendarId: str | None = None


class EventsListResponse(BaseModel):
    events: list[CalendarEventDto]


class EventSearchItem(BaseModel):
    id: str
    summary: str
    startMillis: int
    allDay: bool = False


class EventSearchResponse(BaseModel):
    events: list[EventSearchItem]


class CalendarSettingsPayload(BaseModel):
    enabled: bool = False
    direction: str = "both"
    frequency: str = "15min"
    targetCalendarId: str = "primary"
    displayCalendarIds: list[str] = Field(default_factory=list)
    showEvents: bool = True
    syncCompletedTasks: bool = False


# ============================================================================
# Helpers
# ============================================================================


async def _get_or_create_settings(
    db: AsyncSession, user_id: int
) -> CalendarSyncSettings:
    result = await db.execute(
        select(CalendarSyncSettings).where(CalendarSyncSettings.user_id == user_id)
    )
    row = result.scalar_one_or_none()
    if row is None:
        row = CalendarSyncSettings(user_id=user_id)
        db.add(row)
        await db.commit()
        await db.refresh(row)
    return row


def _settings_to_payload(row: CalendarSyncSettings) -> CalendarSettingsPayload:
    try:
        display = json.loads(row.display_calendar_ids_json or "[]")
    except json.JSONDecodeError:
        display = []
    return CalendarSettingsPayload(
        enabled=row.enabled,
        direction=row.direction,
        frequency=row.frequency,
        targetCalendarId=row.target_calendar_id,
        displayCalendarIds=display,
        showEvents=row.show_events,
        syncCompletedTasks=row.sync_completed_tasks,
    )


def _task_payload_to_dict(req: CalendarPushRequest) -> dict:
    return {
        "title": req.title,
        "description": req.description,
        "notes": req.notes,
        "due_date": req.dueDateMillis,
        "due_time": req.dueTimeMillis,
        "scheduled_start_time": req.scheduledStartMillis,
        "estimated_duration": req.estimatedDurationMinutes,
        "priority": req.priority,
    }


async def _user_timezone(db: AsyncSession, user_id: int) -> ZoneInfo:
    row = await _get_or_create_settings(db, user_id)
    try:
        return ZoneInfo(row.timezone or "UTC")
    except Exception:  # noqa: BLE001
        return ZoneInfo("UTC")


# ============================================================================
# OAuth lifecycle
# ============================================================================


@router.get(
    "/integrations/calendar/authorize",
    response_model=CalendarAuthorizeResponse,
)
async def get_authorization_url(
    current_user: User = Depends(get_current_user),
):
    """Return the Google consent URL that Android will open in a browser."""
    try:
        url, _state = calendar_integration.build_authorization_url(current_user.id)
    except RuntimeError as e:
        raise HTTPException(status_code=501, detail=str(e)) from e
    return CalendarAuthorizeResponse(url=url)


@router.get("/integrations/calendar/callback")
async def oauth_callback(
    code: str = Query(...),
    state: str = Query(...),
    db: AsyncSession = Depends(get_db),
):
    """Google redirects the user here with an authorization *code*. We
    exchange it for tokens and persist an `IntegrationConfig` row keyed
    off the state prefix (`{user_id}:{unix_ts}`).
    """
    try:
        user_id = int(state.split(":", 1)[0])
    except (ValueError, IndexError) as e:
        raise HTTPException(status_code=400, detail="Invalid state") from e
    try:
        await calendar_integration.handle_oauth_callback(db, user_id, code, state)
    except Exception as e:  # noqa: BLE001
        logger.exception("Calendar OAuth exchange failed for user=%s", user_id)
        raise HTTPException(status_code=400, detail=str(e)) from e
    return {"status": "connected"}


@router.delete("/integrations/calendar/disconnect")
async def disconnect_calendar(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await calendar_integration.disconnect(db, current_user.id)
    # Also clear sync settings so the next connect starts clean.
    result = await db.execute(
        select(CalendarSyncSettings).where(
            CalendarSyncSettings.user_id == current_user.id
        )
    )
    row = result.scalar_one_or_none()
    if row is not None:
        row.enabled = False
        await db.commit()
    return {"status": "disconnected"}


@router.get(
    "/integrations/calendar/status",
    response_model=CalendarStatusResponse,
)
async def calendar_status(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(IntegrationConfig).where(
            IntegrationConfig.user_id == current_user.id,
            IntegrationConfig.source == IntegrationSource.CALENDAR.value,
        )
    )
    row = result.scalar_one_or_none()
    return CalendarStatusResponse(connected=bool(row and row.is_enabled))


# ============================================================================
# Calendar list + sync endpoints
# ============================================================================


@router.get(
    "/calendar/calendars",
    response_model=CalendarListResponse,
)
async def list_calendars(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    try:
        calendars = await calendar_service.list_calendars(db, current_user.id)
    except RuntimeError as e:
        raise HTTPException(status_code=501, detail=str(e)) from e
    return CalendarListResponse(
        calendars=[
            CalendarListItem(
                id=c.id,
                name=c.name,
                color=c.color,
                primary=c.primary,
                writable=c.writable,
            )
            for c in calendars
        ]
    )


@router.post(
    "/calendar/sync/push",
    response_model=CalendarPushResponse,
)
async def push_task(
    request: CalendarPushRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    settings_row = await _get_or_create_settings(db, current_user.id)
    if not settings_row.enabled:
        return CalendarPushResponse()
    # Pull-only mode: never write to the user's calendar on push calls.
    if settings_row.direction == "pull":
        return CalendarPushResponse()
    if request.dueDateMillis is None:
        if request.knownEventId and request.knownCalendarId:
            try:
                await calendar_service.delete_event(
                    db,
                    current_user.id,
                    request.knownCalendarId,
                    request.knownEventId,
                )
            except Exception as exc:  # noqa: BLE001
                logger.warning("Soft delete on no-due-date task failed: %s", exc)
        return CalendarPushResponse()
    calendar_id = request.knownCalendarId or settings_row.target_calendar_id
    tz = await _user_timezone(db, current_user.id)
    task_data = _task_payload_to_dict(request)
    try:
        if request.knownEventId:
            result = await calendar_service.update_event(
                db,
                current_user.id,
                calendar_id,
                request.knownEventId,
                task_data,
                tz,
            )
        else:
            result = await calendar_service.create_event(
                db, current_user.id, calendar_id, task_data, tz
            )
    except PermissionError as e:
        raise HTTPException(status_code=401, detail=str(e)) from e
    return CalendarPushResponse(
        eventId=result.event_id,
        calendarId=result.calendar_id,
        etag=result.etag,
    )


@router.delete("/calendar/sync/push/{task_id}")
async def delete_task_event(
    task_id: int,
    calendar_id: str = Query(..., alias="calendar_id"),
    event_id: str = Query(..., alias="event_id"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    settings_row = await _get_or_create_settings(db, current_user.id)
    if settings_row.direction == "pull":
        # Pull-only: don't touch the user's calendar.
        return {"status": "skipped", "task_id": task_id}
    try:
        await calendar_service.delete_event(db, current_user.id, calendar_id, event_id)
    except PermissionError as e:
        raise HTTPException(status_code=401, detail=str(e)) from e
    return {"status": "deleted", "task_id": task_id}


@router.post(
    "/calendar/sync/pull",
    response_model=EventsPullResponse,
)
async def pull_events(
    request: EventsPullRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    settings_row = await _get_or_create_settings(db, current_user.id)
    if not settings_row.enabled:
        return EventsPullResponse()
    # Push-only mode: skip pulls so the user's tasks don't get overwritten
    # by remote calendar edits they didn't opt into syncing down.
    if settings_row.direction == "push":
        return EventsPullResponse()
    try:
        tokens = json.loads(settings_row.last_sync_token_per_calendar_json or "{}")
    except json.JSONDecodeError:
        tokens = {}
    total_created = total_updated = total_deleted = 0
    updated_tokens: dict[str, str] = dict(tokens)
    for calendar_id in request.calendarIds:
        try:
            result = await calendar_service.list_events(
                db,
                current_user.id,
                calendar_id,
                sync_token=tokens.get(calendar_id),
            )
        except PermissionError as e:
            raise HTTPException(status_code=401, detail=str(e)) from e
        total_created += result.created
        total_updated += result.updated
        total_deleted += len(result.deleted)
        if result.next_sync_token:
            updated_tokens[calendar_id] = result.next_sync_token
    settings_row.last_sync_token_per_calendar_json = json.dumps(updated_tokens)
    settings_row.last_sync_at = datetime.now(timezone.utc)
    await db.commit()
    return EventsPullResponse(
        created=total_created, updated=total_updated, deleted=total_deleted
    )


@router.post("/calendar/sync/full")
async def full_sync(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    settings_row = await _get_or_create_settings(db, current_user.id)
    # Clear cached sync tokens so the next pull does a full range scan.
    settings_row.last_sync_token_per_calendar_json = "{}"
    await db.commit()
    try:
        display = json.loads(settings_row.display_calendar_ids_json or "[]")
    except json.JSONDecodeError:
        display = []
    calendar_ids = list({*display, settings_row.target_calendar_id})
    for calendar_id in calendar_ids:
        try:
            await calendar_service.list_events(db, current_user.id, calendar_id)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Full sync calendar=%s failed: %s", calendar_id, exc)
    settings_row.last_sync_at = datetime.now(timezone.utc)
    await db.commit()
    return {"status": "ok"}


@router.post("/calendar/webhook")
async def calendar_webhook(request: Request):
    """Placeholder for Google `events.watch` push notifications. Real
    per-calendar subscriptions are deferred — see
    docs/FUTURE-CALENDAR-WORK.md. Returning 204 keeps the platform happy
    if a registration leaks through to staging.
    """
    channel_id = request.headers.get("X-Goog-Channel-Id")
    resource_id = request.headers.get("X-Goog-Resource-Id")
    state = request.headers.get("X-Goog-Resource-State")
    logger.info(
        "calendar webhook received channel=%s resource=%s state=%s",
        channel_id,
        resource_id,
        state,
    )
    return {"status": "accepted"}


@router.get(
    "/calendar/events/today",
    response_model=EventsListResponse,
)
async def list_today_events(
    start: int = Query(..., description="Start of window in epoch millis"),
    end: int = Query(..., description="End of window in epoch millis"),
    limit: int = Query(3, ge=1, le=50),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    settings_row = await _get_or_create_settings(db, current_user.id)
    if not settings_row.enabled or not settings_row.show_events:
        return EventsListResponse(events=[])
    try:
        display = json.loads(settings_row.display_calendar_ids_json or "[]")
    except json.JSONDecodeError:
        display = []
    calendar_ids = display or [settings_row.target_calendar_id]
    time_min = datetime.fromtimestamp(start / 1000, tz=timezone.utc)
    time_max = datetime.fromtimestamp(end / 1000, tz=timezone.utc)
    events = await calendar_service.list_events_in_window(
        db,
        current_user.id,
        calendar_ids,
        time_min=time_min,
        time_max=time_max,
        limit=limit,
    )
    return EventsListResponse(
        events=[
            CalendarEventDto(
                id=e["id"],
                title=e["title"],
                startMillis=e["start_millis"],
                endMillis=e["end_millis"],
                allDay=e["all_day"],
                calendarId=e["calendar_id"],
            )
            for e in events
        ]
    )


# ============================================================================
# Sync settings endpoints (Step 8)
# ============================================================================


@router.get(
    "/calendar/events/search",
    response_model=EventSearchResponse,
)
async def search_events(
    pattern: str = Query(..., min_length=1, max_length=200),
    calendar_id: str = Query("primary"),
    time_min: int | None = Query(None, description="Start (epoch millis)"),
    time_max: int | None = Query(None, description="End (epoch millis)"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Fuzzy search for events whose summary contains *pattern*. Used by
    the Android medication-reminder scheduler so it can find habit-linked
    calendar events without holding the Calendar OAuth scope on the
    device."""
    time_min_dt = (
        datetime.fromtimestamp(time_min / 1000, tz=timezone.utc)
        if time_min is not None
        else None
    )
    time_max_dt = (
        datetime.fromtimestamp(time_max / 1000, tz=timezone.utc)
        if time_max is not None
        else None
    )
    events = await calendar_service.search_events_by_summary(
        db,
        current_user.id,
        pattern=pattern,
        calendar_id=calendar_id,
        time_min=time_min_dt,
        time_max=time_max_dt,
    )
    return EventSearchResponse(
        events=[
            EventSearchItem(
                id=e["id"],
                summary=e["summary"],
                startMillis=e["start_millis"],
                allDay=e["all_day"],
            )
            for e in events
        ]
    )


@router.get(
    "/calendar/settings",
    response_model=CalendarSettingsPayload,
)
async def get_settings(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    row = await _get_or_create_settings(db, current_user.id)
    return _settings_to_payload(row)


@router.put(
    "/calendar/settings",
    response_model=CalendarSettingsPayload,
)
async def update_settings(
    payload: CalendarSettingsPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if payload.direction not in {"push", "pull", "both"}:
        raise HTTPException(status_code=422, detail="Invalid direction")
    if payload.frequency not in {"realtime", "15min", "hourly", "manual"}:
        raise HTTPException(status_code=422, detail="Invalid frequency")
    row = await _get_or_create_settings(db, current_user.id)
    row.enabled = payload.enabled
    row.direction = payload.direction
    row.frequency = payload.frequency
    row.target_calendar_id = payload.targetCalendarId
    row.display_calendar_ids_json = json.dumps(payload.displayCalendarIds)
    row.show_events = payload.showEvents
    row.sync_completed_tasks = payload.syncCompletedTasks
    await db.commit()
    await db.refresh(row)
    return _settings_to_payload(row)


__all__ = ["router"]
