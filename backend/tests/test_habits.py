import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_habit(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/habits",
        json={
            "name": "Exercise",
            "description": "Daily workout",
            "icon": "💪",
            "color": "#FF5733",
            "frequency": "daily",
        },
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "Exercise"
    assert data["frequency"] == "daily"
    assert data["is_active"] is True


@pytest.mark.asyncio
async def test_list_habits(client: AsyncClient, auth_headers: dict):
    await client.post(
        "/api/v1/habits",
        json={"name": "Read"},
        headers=auth_headers,
    )
    await client.post(
        "/api/v1/habits",
        json={"name": "Meditate"},
        headers=auth_headers,
    )

    resp = await client.get("/api/v1/habits", headers=auth_headers)
    assert resp.status_code == 200
    habits = resp.json()
    assert len(habits) >= 2


@pytest.mark.asyncio
async def test_get_habit_with_completions(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/habits",
        json={"name": "Journal"},
        headers=auth_headers,
    )
    habit_id = create_resp.json()["id"]

    resp = await client.get(f"/api/v1/habits/{habit_id}", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["name"] == "Journal"
    assert "completions" in data


@pytest.mark.asyncio
async def test_update_habit(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/habits",
        json={"name": "Old Habit"},
        headers=auth_headers,
    )
    habit_id = create_resp.json()["id"]

    resp = await client.patch(
        f"/api/v1/habits/{habit_id}",
        json={"name": "New Habit", "color": "#00FF00"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["name"] == "New Habit"


@pytest.mark.asyncio
async def test_delete_habit(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/habits",
        json={"name": "Temp Habit"},
        headers=auth_headers,
    )
    habit_id = create_resp.json()["id"]

    resp = await client.delete(f"/api/v1/habits/{habit_id}", headers=auth_headers)
    assert resp.status_code == 204


@pytest.mark.asyncio
async def test_complete_habit(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/habits",
        json={"name": "Water"},
        headers=auth_headers,
    )
    habit_id = create_resp.json()["id"]

    resp = await client.post(
        f"/api/v1/habits/{habit_id}/complete",
        json={"date": "2026-04-06", "count": 1},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    assert resp.json()["date"] == "2026-04-06"
    assert resp.json()["count"] == 1


@pytest.mark.asyncio
async def test_toggle_habit_completion(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/habits",
        json={"name": "Toggle Test"},
        headers=auth_headers,
    )
    habit_id = create_resp.json()["id"]

    # Complete
    resp1 = await client.post(
        f"/api/v1/habits/{habit_id}/complete",
        json={"date": "2026-04-06"},
        headers=auth_headers,
    )
    assert resp1.status_code == 201
    assert resp1.json()["count"] == 1

    # Toggle off (complete again same date)
    resp2 = await client.post(
        f"/api/v1/habits/{habit_id}/complete",
        json={"date": "2026-04-06"},
        headers=auth_headers,
    )
    assert resp2.status_code == 201
    assert resp2.json()["count"] == 0


@pytest.mark.asyncio
async def test_habit_stats(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/habits",
        json={"name": "Stats Habit"},
        headers=auth_headers,
    )
    habit_id = create_resp.json()["id"]

    # Add some completions
    for day in range(1, 6):
        await client.post(
            f"/api/v1/habits/{habit_id}/complete",
            json={"date": f"2026-04-0{day}"},
            headers=auth_headers,
        )

    resp = await client.get(f"/api/v1/habits/{habit_id}/stats", headers=auth_headers)
    assert resp.status_code == 200
    stats = resp.json()
    assert stats["total_completions"] == 5
    assert stats["habit_id"] == habit_id


@pytest.mark.asyncio
async def test_get_nonexistent_habit(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/habits/9999", headers=auth_headers)
    assert resp.status_code == 404
