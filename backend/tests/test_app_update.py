"""Tests for the app_update router — version check + release creation."""

from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_version_endpoint_returns_404_when_no_releases(client: AsyncClient):
    resp = await client.get("/api/v1/app/version")
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_version_endpoint_is_public(client: AsyncClient):
    # Should not require auth headers — 404 (no releases) is fine, the point
    # is that it doesn't 401.
    resp = await client.get("/api/v1/app/version")
    assert resp.status_code != 401
    assert resp.status_code != 403


@pytest.mark.asyncio
async def test_releases_endpoint_rejects_missing_deploy_key(client: AsyncClient):
    resp = await client.post(
        "/api/v1/app/releases",
        json={
            "version_code": 1,
            "version_name": "1.0.0",
            "release_notes": "first release",
            "apk_url": "https://example.com/x.apk",
            "apk_size_bytes": 1024,
            "sha256": "a" * 64,
            "is_mandatory": False,
        },
    )
    # Expect auth rejection (either 401 or 403 depending on middleware).
    assert resp.status_code in (401, 403, 503)
