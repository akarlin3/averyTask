import json
import logging
import os
from datetime import date

from app.config import settings

try:
    import anthropic
except ImportError:
    anthropic = None  # type: ignore

logger = logging.getLogger(__name__)

MODEL = "claude-haiku-4-5-20251001"


def _get_client():
    api_key = os.environ.get("ANTHROPIC_API_KEY") or settings.ANTHROPIC_API_KEY
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY environment variable is not set")
    if anthropic is None:
        raise RuntimeError("anthropic package is not installed")
    return anthropic.Anthropic(api_key=api_key)


def _parse_ai_json(content: str) -> dict | list:
    """Strip markdown fences and parse JSON from AI response."""
    content = content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content[3:]
    if content.endswith("```"):
        content = content[:-3]
    content = content.strip()
    return json.loads(content)


def categorize_eisenhower(tasks: list[dict], today: date) -> list[dict]:
    """Call Claude Haiku to categorize tasks into Eisenhower quadrants."""
    client = _get_client()
    tasks_json = json.dumps(tasks, default=str, indent=2)

    prompt = f"""You are a productivity assistant. Categorize each task into an Eisenhower Matrix quadrant.

Quadrants:
- Q1 (Urgent + Important): Deadlines within 48 hours, high-priority blockers, critical issues
- Q2 (Not Urgent + Important): Long-term goals, planning, skill building, health, relationships
- Q3 (Urgent + Not Important): Most emails, some meetings, minor deadlines, others' priorities
- Q4 (Not Urgent + Not Important): Time-wasters, excessive social media, busywork

Consider: due date proximity, priority level, project context, task description.
A task with no due date but high priority is likely Q2.
A task due today with low priority is likely Q3.

Tasks:
{tasks_json}

Today's date: {today.isoformat()}

Respond ONLY with valid JSON — no markdown, no preamble:
[
  {{"task_id": 1, "quadrant": "Q1", "reason": "Due tomorrow, high priority"}},
  {{"task_id": 2, "quadrant": "Q2", "reason": "No deadline but important for career growth"}}
]"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=2048,
                messages=[{"role": "user", "content": prompt}],
            )
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, list):
                raise ValueError("Expected a JSON array")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse Eisenhower response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Eisenhower AI error: {type(e).__name__}: {e}")
            raise

    raise ValueError(f"Failed to parse AI response: {last_error}")


def plan_pomodoro(
    tasks: list[dict],
    available_minutes: int,
    session_length: int,
    break_length: int,
    long_break_length: int,
    focus_preference: str,
    today: date,
) -> dict:
    """Call Claude Haiku to generate a Pomodoro focus session plan."""
    client = _get_client()
    tasks_json = json.dumps(tasks, default=str, indent=2)

    prompt = f"""You are a productivity coach planning focus sessions (Pomodoro technique).

Available time: {available_minutes} minutes
Session length: {session_length} min work + {break_length} min break
Long break: {long_break_length} min after every 4 sessions
Focus preference: {focus_preference}

Focus preference meanings:
- "deep_work": prioritize complex, high-concentration tasks; batch similar work
- "quick_wins": start with short, easy tasks to build momentum
- "balanced": mix of quick wins and deep work
- "deadline_driven": prioritize by due date urgency

Today's date: {today.isoformat()}

User's tasks:
{tasks_json}

Create an ordered plan of which tasks to work on in each session.
Tasks longer than one session can span multiple sessions.
Tasks shorter than one session can be batched together.

Respond ONLY with valid JSON — no markdown, no preamble:
{{
  "sessions": [
    {{
      "session_number": 1,
      "tasks": [
        {{"task_id": 1, "title": "Write report draft", "allocated_minutes": 25}}
      ],
      "rationale": "Starting with the most urgent deadline"
    }}
  ],
  "total_sessions": 4,
  "total_work_minutes": 100,
  "total_break_minutes": 20,
  "skipped_tasks": [
    {{"task_id": 7, "reason": "No estimated duration and low priority"}}
  ]
}}"""

    last_error = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model=MODEL,
                max_tokens=4096,
                messages=[{"role": "user", "content": prompt}],
            )
            content = message.content[0].text
            result = _parse_ai_json(content)
            if not isinstance(result, dict):
                raise ValueError("Expected a JSON object")
            return result
        except (json.JSONDecodeError, KeyError, TypeError, IndexError, ValueError) as e:
            last_error = e
            logger.error(f"Failed to parse Pomodoro response (attempt {attempt + 1}): {e}")
            if attempt == 0:
                continue
            raise ValueError(f"Failed to parse AI response after retry: {e}") from e
        except Exception as e:
            logger.error(f"Pomodoro AI error: {type(e).__name__}: {e}")
            raise

    raise ValueError(f"Failed to parse AI response: {last_error}")
