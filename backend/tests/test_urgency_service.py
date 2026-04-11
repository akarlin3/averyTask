"""Unit tests for the urgency scoring service."""

from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from app.services.urgency import compute_urgency


def _now() -> datetime:
    return datetime.now(timezone.utc)


def test_completed_task_returns_zero():
    assert (
        compute_urgency(
            due_date=date.today(),
            priority=3,
            created_at=_now() - timedelta(days=1),
            completed_at=_now(),
        )
        == 0.0
    )


def test_overdue_task_has_maximum_due_factor():
    score = compute_urgency(
        due_date=date.today() - timedelta(days=1),
        priority=3,
        created_at=_now() - timedelta(days=2),
    )
    # Overdue + high priority should be near the top.
    assert score > 0.6


def test_due_today_outranks_due_next_week():
    today_score = compute_urgency(
        due_date=date.today(),
        priority=2,
        created_at=_now() - timedelta(days=1),
    )
    next_week_score = compute_urgency(
        due_date=date.today() + timedelta(days=7),
        priority=2,
        created_at=_now() - timedelta(days=1),
    )
    assert today_score > next_week_score


def test_higher_priority_boosts_score():
    low = compute_urgency(
        due_date=date.today() + timedelta(days=3),
        priority=1,
        created_at=_now() - timedelta(days=1),
    )
    high = compute_urgency(
        due_date=date.today() + timedelta(days=3),
        priority=4,
        created_at=_now() - timedelta(days=1),
    )
    assert high > low


def test_task_with_no_due_date_still_scored():
    score = compute_urgency(
        due_date=None,
        priority=2,
        created_at=_now() - timedelta(days=30),
    )
    # Should be non-zero and clamped to [0, 1]
    assert 0.0 <= score <= 1.0


def test_score_is_clamped_to_one():
    # Maximum-priority, extremely overdue task.
    score = compute_urgency(
        due_date=date.today() - timedelta(days=60),
        priority=4,
        created_at=_now() - timedelta(days=120),
    )
    assert score <= 1.0
