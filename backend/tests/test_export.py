"""Tests for the export router — JSON/CSV export and import."""

from __future__ import annotations

import json

import pytest
from httpx import AsyncClient


@pytest.fixture
async def seeded_project(client: AsyncClient, auth_headers: dict):
    goal = await client.post(
        "/api/v1/goals", json={"title": "Export Goal"}, headers=auth_headers
    )
    proj = await client.post(
        f"/api/v1/goals/{goal.json()['id']}/projects",
        json={"title": "Export Project"},
        headers=auth_headers,
    )
    project_id = proj.json()["id"]
    await client.post(
        f"/api/v1/projects/{project_id}/tasks",
        json={"title": "Exported task"},
        headers=auth_headers,
    )
    return project_id


@pytest.mark.asyncio
async def test_export_json_requires_auth(client: AsyncClient):
    resp = await client.get("/api/v1/export/json")
    assert resp.status_code in (401, 403)


@pytest.mark.asyncio
async def test_export_json_returns_valid_structure(
    client: AsyncClient, auth_headers: dict, seeded_project: int
):
    resp = await client.get("/api/v1/export/json", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    # Should be a dict (or stream-parsed object) with recognizable top-level keys
    assert isinstance(data, dict)
    # Tasks should be included
    assert any("task" in key.lower() for key in data.keys())


@pytest.mark.asyncio
async def test_export_json_contains_seeded_task(
    client: AsyncClient, auth_headers: dict, seeded_project: int
):
    resp = await client.get("/api/v1/export/json", headers=auth_headers)
    assert resp.status_code == 200
    body = json.dumps(resp.json())
    assert "Exported task" in body


@pytest.mark.asyncio
async def test_export_csv_returns_text_response(
    client: AsyncClient, auth_headers: dict, seeded_project: int
):
    resp = await client.get("/api/v1/export/csv", headers=auth_headers)
    assert resp.status_code == 200
    # CSV should be a non-empty string body
    assert len(resp.text) > 0
