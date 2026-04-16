"""APScheduler job that runs every 15 minutes and pulls changes for every
user with calendar sync enabled.

The job intentionally keeps per-run cost low: it selects only the users
with `CalendarSyncSettings.enabled = True`, walks their selected display
calendars, and calls `calendar_service.list_events` which uses
incremental sync tokens stored in `CalendarSyncSettings`.

Push reconciliation (retrying any `PENDING_PUSH` / `PENDING_DELETE`
states stored Android-side) is not done from the backend — Android's
WorkManager handles push retries locally because it owns the local task
state. The periodic job is pull-only.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone

try:
    from apscheduler.schedulers.asyncio import AsyncIOScheduler
except ImportError:  # pragma: no cover
    AsyncIOScheduler = None  # type: ignore

from sqlalchemy import select

from app.database import async_session_factory
from app.models import CalendarSyncSettings
from app.services import calendar_service

logger = logging.getLogger(__name__)

_scheduler: AsyncIOScheduler | None = None


async def run_calendar_sync_all_users() -> None:
    """Pull new/changed/deleted events for every user with sync enabled."""
    if async_session_factory is None:
        return
    async with async_session_factory() as db:
        result = await db.execute(
            select(CalendarSyncSettings).where(CalendarSyncSettings.enabled.is_(True))
        )
        rows = result.scalars().all()
        for row in rows:
            # Push-only users never want their tasks touched by remote
            # edits, so skip the periodic pull for them.
            if row.direction == "push":
                continue
            try:
                tokens = json.loads(row.last_sync_token_per_calendar_json or "{}")
            except json.JSONDecodeError:
                tokens = {}
            try:
                display = json.loads(row.display_calendar_ids_json or "[]")
            except json.JSONDecodeError:
                display = []
            calendar_ids = list({*display, row.target_calendar_id})
            updated_tokens = dict(tokens)
            for calendar_id in calendar_ids:
                try:
                    outcome = await calendar_service.list_events(
                        db,
                        row.user_id,
                        calendar_id,
                        sync_token=tokens.get(calendar_id),
                    )
                except Exception as exc:  # noqa: BLE001
                    logger.warning(
                        "periodic sync user=%s calendar=%s failed: %s",
                        row.user_id,
                        calendar_id,
                        exc,
                    )
                    continue
                if outcome.next_sync_token:
                    updated_tokens[calendar_id] = outcome.next_sync_token
            row.last_sync_token_per_calendar_json = json.dumps(updated_tokens)
            row.last_sync_at = datetime.now(timezone.utc)
            await db.commit()


def start_scheduler() -> None:
    """Start the periodic job. Idempotent — safe to call multiple times
    from the FastAPI startup hook."""
    global _scheduler
    if AsyncIOScheduler is None:
        logger.warning("APScheduler not installed; calendar periodic sync disabled")
        return
    if _scheduler is not None and _scheduler.running:
        return
    _scheduler = AsyncIOScheduler(timezone="UTC")
    _scheduler.add_job(
        run_calendar_sync_all_users,
        "interval",
        minutes=15,
        id="calendar_sync_all_users",
        replace_existing=True,
        max_instances=1,
    )
    _scheduler.start()
    logger.info("Calendar periodic sync started (every 15 minutes)")


def stop_scheduler() -> None:
    global _scheduler
    if _scheduler is not None and _scheduler.running:
        _scheduler.shutdown(wait=False)
    _scheduler = None
