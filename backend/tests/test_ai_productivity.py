import json
import sys
import types
from datetime import date
from unittest.mock import MagicMock, patch

import pytest
import pytest_asyncio
from httpx import AsyncClient


def _make_mock_response(data) -> MagicMock:
    content_block = MagicMock()
    content_block.text = json.dumps(data)
    message = MagicMock()
    message.content = [content_block]
    return message


@pytest.fixture(autouse=True)
def mock_anthropic_module():
    mock_mod = types.ModuleType("anthropic")
    mock_mod.Anthropic = MagicMock  # type: ignore
    mock_mod.APIError = Exception  # type: ignore
    sys.modules["anthropic"] = mock_mod

    import importlib
    import app.services.ai_productivity
    importlib.reload(app.services.ai_productivity)

    yield mock_mod

    if "anthropic" in sys.modules and sys.modules["anthropic"] is mock_mod:
        del sys.modules["anthropic"]
    importlib.reload(app.services.ai_productivity)


class TestEisenhowerService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_categorize_eisenhower_success(self):
        from app.services.ai_productivity import categorize_eisenhower

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response([
                {"task_id": 1, "quadrant": "Q1", "reason": "Due tomorrow"},
                {"task_id": 2, "quadrant": "Q2", "reason": "Long-term goal"},
            ])

            tasks = [
                {"task_id": 1, "title": "Fix bug", "due_date": "2026-04-11", "priority": 1},
                {"task_id": 2, "title": "Learn Kotlin", "due_date": None, "priority": 2},
            ]
            result = categorize_eisenhower(tasks, date(2026, 4, 10))

            assert len(result) == 2
            assert result[0]["quadrant"] == "Q1"
            assert result[1]["quadrant"] == "Q2"

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_categorize_eisenhower_malformed_retry(self):
        from app.services.ai_productivity import categorize_eisenhower

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "not valid json"
            bad_response.content = [bad_content]

            good_response = _make_mock_response([
                {"task_id": 1, "quadrant": "Q1", "reason": "Urgent"},
            ])

            mock_client.messages.create.side_effect = [bad_response, good_response]

            result = categorize_eisenhower(
                [{"task_id": 1, "title": "Test"}], date(2026, 4, 10)
            )
            assert result[0]["quadrant"] == "Q1"
            assert mock_client.messages.create.call_count == 2


class TestPomodoroService:
    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_plan_pomodoro_success(self):
        from app.services.ai_productivity import plan_pomodoro

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client
            mock_client.messages.create.return_value = _make_mock_response({
                "sessions": [
                    {
                        "session_number": 1,
                        "tasks": [{"task_id": 1, "title": "Write report", "allocated_minutes": 25}],
                        "rationale": "Most urgent task",
                    }
                ],
                "total_sessions": 1,
                "total_work_minutes": 25,
                "total_break_minutes": 0,
                "skipped_tasks": [],
            })

            tasks = [{"task_id": 1, "title": "Write report", "due_date": "2026-04-11"}]
            result = plan_pomodoro(
                tasks=tasks,
                available_minutes=60,
                session_length=25,
                break_length=5,
                long_break_length=15,
                focus_preference="balanced",
                today=date(2026, 4, 10),
            )

            assert result["total_sessions"] == 1
            assert len(result["sessions"]) == 1
            assert result["sessions"][0]["tasks"][0]["task_id"] == 1

    @patch.dict("os.environ", {"ANTHROPIC_API_KEY": "sk-test-key"})
    def test_plan_pomodoro_malformed_both_fail(self):
        from app.services.ai_productivity import plan_pomodoro

        with patch("app.services.ai_productivity.anthropic") as mock_anthropic:
            mock_client = MagicMock()
            mock_anthropic.Anthropic.return_value = mock_client

            bad_response = MagicMock()
            bad_content = MagicMock()
            bad_content.text = "{{invalid}}"
            bad_response.content = [bad_content]
            mock_client.messages.create.return_value = bad_response

            with pytest.raises(ValueError, match="Failed to parse AI response"):
                plan_pomodoro(
                    tasks=[{"task_id": 1, "title": "Test"}],
                    available_minutes=60,
                    session_length=25,
                    break_length=5,
                    long_break_length=15,
                    focus_preference="balanced",
                    today=date(2026, 4, 10),
                )


class TestAIEndpoints:
    @pytest.mark.asyncio
    async def test_eisenhower_endpoint(self, client: AsyncClient, auth_headers: dict):
        # Create a goal and project first
        goal_resp = await client.post(
            "/api/v1/goals",
            json={"title": "Test Goal"},
            headers=auth_headers,
        )
        goal_id = goal_resp.json()["id"]
        proj_resp = await client.post(
            f"/api/v1/goals/{goal_id}/projects",
            json={"title": "Test Project"},
            headers=auth_headers,
        )
        project_id = proj_resp.json()["id"]
        await client.post(
            f"/api/v1/projects/{project_id}/tasks",
            json={"title": "Test task"},
            headers=auth_headers,
        )

        with patch("app.routers.ai.ai_rate_limiter"):
            with patch("app.services.ai_productivity.categorize_eisenhower") as mock_cat:
                mock_cat.return_value = [
                    {"task_id": 1, "quadrant": "Q1", "reason": "Urgent task"},
                ]
                resp = await client.post(
                    "/api/v1/ai/eisenhower",
                    json={},
                    headers=auth_headers,
                )
                assert resp.status_code == 200
                data = resp.json()
                assert "categorizations" in data
                assert "summary" in data

    @pytest.mark.asyncio
    async def test_pomodoro_endpoint(self, client: AsyncClient, auth_headers: dict):
        # Create a goal and project first
        goal_resp = await client.post(
            "/api/v1/goals",
            json={"title": "Test Goal 2"},
            headers=auth_headers,
        )
        goal_id = goal_resp.json()["id"]
        proj_resp = await client.post(
            f"/api/v1/goals/{goal_id}/projects",
            json={"title": "Test Project 2"},
            headers=auth_headers,
        )
        project_id = proj_resp.json()["id"]
        await client.post(
            f"/api/v1/projects/{project_id}/tasks",
            json={"title": "Focus task"},
            headers=auth_headers,
        )

        with patch("app.routers.ai.ai_rate_limiter"):
            with patch("app.services.ai_productivity.plan_pomodoro") as mock_plan:
                mock_plan.return_value = {
                    "sessions": [
                        {
                            "session_number": 1,
                            "tasks": [{"task_id": 1, "title": "Focus task", "allocated_minutes": 25}],
                            "rationale": "Only task available",
                        }
                    ],
                    "total_sessions": 1,
                    "total_work_minutes": 25,
                    "total_break_minutes": 0,
                    "skipped_tasks": [],
                }
                resp = await client.post(
                    "/api/v1/ai/pomodoro-plan",
                    json={"available_minutes": 60},
                    headers=auth_headers,
                )
                assert resp.status_code == 200
                data = resp.json()
                assert data["total_sessions"] == 1

    @pytest.mark.asyncio
    async def test_rate_limiting(self, client: AsyncClient, auth_headers: dict):
        from app.routers.ai import ai_rate_limiter
        ai_rate_limiter._requests.clear()

        # First call should work (even with no tasks)
        resp = await client.post(
            "/api/v1/ai/eisenhower",
            json={},
            headers=auth_headers,
        )
        assert resp.status_code == 200

        # Second call within window should be rate limited
        resp2 = await client.post(
            "/api/v1/ai/eisenhower",
            json={},
            headers=auth_headers,
        )
        assert resp2.status_code == 429
