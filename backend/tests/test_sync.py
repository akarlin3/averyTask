import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_sync_push_create_tag(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "synced-tag", "color": "#123456"},
                    "client_timestamp": "2026-04-06T12:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["processed"] == 1
    assert len(data["errors"]) == 0


@pytest.mark.asyncio
async def test_sync_push_unknown_entity(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "nonexistent",
                    "operation": "create",
                    "data": {"name": "test"},
                    "client_timestamp": "2026-04-06T12:00:00Z",
                }
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["processed"] == 0
    assert len(data["errors"]) == 1


@pytest.mark.asyncio
async def test_sync_pull(client: AsyncClient, auth_headers: dict):
    # Create some data first
    await client.post(
        "/api/v1/tags", json={"name": "pull-test"}, headers=auth_headers
    )

    resp = await client.get("/api/v1/sync/pull", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert "changes" in data
    assert "server_timestamp" in data


@pytest.mark.asyncio
async def test_sync_pull_with_since(client: AsyncClient, auth_headers: dict):
    resp = await client.get(
        "/api/v1/sync/pull",
        params={"since": "2020-01-01T00:00:00Z"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert "changes" in resp.json()


@pytest.mark.asyncio
async def test_sync_push_multiple_operations(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/sync/push",
        json={
            "operations": [
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "tag-a"},
                    "client_timestamp": "2026-04-06T12:00:00Z",
                },
                {
                    "entity_type": "tag",
                    "operation": "create",
                    "data": {"name": "tag-b"},
                    "client_timestamp": "2026-04-06T12:00:01Z",
                },
            ]
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["processed"] == 2
