import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_tag(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/tags",
        json={"name": "work", "color": "#FF0000"},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "work"
    assert data["color"] == "#FF0000"
    assert "id" in data


@pytest.mark.asyncio
async def test_list_tags(client: AsyncClient, auth_headers: dict):
    await client.post("/api/v1/tags", json={"name": "alpha"}, headers=auth_headers)
    await client.post("/api/v1/tags", json={"name": "beta"}, headers=auth_headers)

    resp = await client.get("/api/v1/tags", headers=auth_headers)
    assert resp.status_code == 200
    tags = resp.json()
    assert len(tags) >= 2
    names = [t["name"] for t in tags]
    assert "alpha" in names
    assert "beta" in names


@pytest.mark.asyncio
async def test_update_tag(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/tags", json={"name": "old"}, headers=auth_headers
    )
    tag_id = create_resp.json()["id"]

    resp = await client.patch(
        f"/api/v1/tags/{tag_id}",
        json={"name": "new", "color": "#00FF00"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["name"] == "new"
    assert resp.json()["color"] == "#00FF00"


@pytest.mark.asyncio
async def test_delete_tag(client: AsyncClient, auth_headers: dict):
    create_resp = await client.post(
        "/api/v1/tags", json={"name": "temp"}, headers=auth_headers
    )
    tag_id = create_resp.json()["id"]

    resp = await client.delete(f"/api/v1/tags/{tag_id}", headers=auth_headers)
    assert resp.status_code == 204


@pytest.mark.asyncio
async def test_delete_nonexistent_tag(client: AsyncClient, auth_headers: dict):
    resp = await client.delete("/api/v1/tags/9999", headers=auth_headers)
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_set_task_tags(client: AsyncClient, auth_headers: dict):
    # Create a goal -> project -> task chain
    goal_resp = await client.post(
        "/api/v1/goals", json={"title": "Test Goal"}, headers=auth_headers
    )
    goal_id = goal_resp.json()["id"]

    proj_resp = await client.post(
        f"/api/v1/goals/{goal_id}/projects",
        json={"title": "Test Project"},
        headers=auth_headers,
    )
    project_id = proj_resp.json()["id"]

    task_resp = await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Test Task"},
        headers=auth_headers,
    )
    task_id = task_resp.json()["id"]

    # Create tags
    tag1 = await client.post("/api/v1/tags", json={"name": "tag1"}, headers=auth_headers)
    tag2 = await client.post("/api/v1/tags", json={"name": "tag2"}, headers=auth_headers)
    tag1_id = tag1.json()["id"]
    tag2_id = tag2.json()["id"]

    # Set tags on task
    resp = await client.put(
        f"/api/v1/tags/tasks/{task_id}/tags",
        json=[tag1_id, tag2_id],
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert len(resp.json()) == 2
