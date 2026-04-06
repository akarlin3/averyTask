import json
from datetime import date, timedelta


def calculate_next_date(recurrence_json: str, current_due: date) -> date | None:
    """Calculate the next occurrence date based on a recurrence rule JSON string."""
    if not recurrence_json:
        return None

    try:
        rule = json.loads(recurrence_json)
    except json.JSONDecodeError:
        return None

    rec_type = rule.get("type", "").lower()
    interval = rule.get("interval", 1)

    if rec_type == "daily":
        return current_due + timedelta(days=interval)

    elif rec_type == "weekly":
        days_of_week = rule.get("daysOfWeek", [])
        if not days_of_week:
            return current_due + timedelta(weeks=interval)
        # Find next matching day
        for i in range(1, 8 * interval):
            candidate = current_due + timedelta(days=i)
            if candidate.weekday() in days_of_week:
                return candidate
        return current_due + timedelta(weeks=interval)

    elif rec_type == "monthly":
        day_of_month = rule.get("dayOfMonth", current_due.day)
        month = current_due.month + interval
        year = current_due.year
        while month > 12:
            month -= 12
            year += 1
        # Clamp day to valid range
        import calendar
        max_day = calendar.monthrange(year, month)[1]
        day = min(day_of_month, max_day)
        return date(year, month, day)

    elif rec_type == "yearly":
        return date(current_due.year + interval, current_due.month, current_due.day)

    return None
