"""Google Calendar integration — OAuth flow + token storage.

Modeled on `gmail_integration.py`. Holds the user's refresh token
encrypted in `integration_configs.config_json`; the token is decrypted
only inside `calendar_service.py` when a Calendar API call needs to be
made.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any

try:
    from google_auth_oauthlib.flow import Flow
    from google.oauth2.credentials import Credentials as GoogleCredentials
    from google.auth.transport.requests import Request as GoogleAuthRequest
except ImportError:  # pragma: no cover
    Flow = None  # type: ignore
    GoogleCredentials = None  # type: ignore
    GoogleAuthRequest = None  # type: ignore

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models import IntegrationConfig, IntegrationSource
from app.services.integrations.token_crypto import decrypt_json, encrypt_json

logger = logging.getLogger(__name__)

CALENDAR_SCOPES = [
    "https://www.googleapis.com/auth/calendar",
    "https://www.googleapis.com/auth/calendar.events",
]


def _client_config() -> dict[str, Any]:
    return {
        "web": {
            "client_id": settings.GOOGLE_CLIENT_ID,
            "client_secret": settings.GOOGLE_CLIENT_SECRET,
            "redirect_uris": [settings.GOOGLE_CALENDAR_REDIRECT_URI],
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
        }
    }


def build_authorization_url(user_id: int) -> tuple[str, str]:
    """Build the Google consent URL. Returns (url, state); persist state
    keyed by user_id so we can verify it in the callback.
    """
    if Flow is None:
        raise RuntimeError("google-auth-oauthlib is not installed")
    if not settings.GOOGLE_CLIENT_ID or not settings.GOOGLE_CLIENT_SECRET:
        raise RuntimeError("Google OAuth client is not configured")
    flow = Flow.from_client_config(
        _client_config(),
        scopes=CALENDAR_SCOPES,
        redirect_uri=settings.GOOGLE_CALENDAR_REDIRECT_URI,
    )
    state = f"{user_id}:{int(datetime.now(timezone.utc).timestamp())}"
    auth_url, _ = flow.authorization_url(
        access_type="offline",
        include_granted_scopes="true",
        prompt="consent",
        state=state,
    )
    return auth_url, state


async def handle_oauth_callback(
    db: AsyncSession, user_id: int, code: str, state: str
) -> IntegrationConfig:
    """Exchange the authorization *code* for tokens and persist them as
    a `source=CALENDAR` `IntegrationConfig` row for *user_id*.
    """
    if Flow is None:
        raise RuntimeError("google-auth-oauthlib is not installed")
    expected_prefix = f"{user_id}:"
    if not state.startswith(expected_prefix):
        raise ValueError("OAuth state does not match authenticated user")
    flow = Flow.from_client_config(
        _client_config(),
        scopes=CALENDAR_SCOPES,
        redirect_uri=settings.GOOGLE_CALENDAR_REDIRECT_URI,
        state=state,
    )
    flow.fetch_token(code=code)
    creds = flow.credentials
    payload = {
        "access_token": creds.token,
        "refresh_token": creds.refresh_token,
        "token_uri": creds.token_uri,
        "client_id": creds.client_id,
        "client_secret": creds.client_secret,
        "scopes": list(creds.scopes or CALENDAR_SCOPES),
        "expiry": creds.expiry.isoformat() if creds.expiry else None,
    }
    encrypted = encrypt_json(payload)
    existing = await db.execute(
        select(IntegrationConfig).where(
            IntegrationConfig.user_id == user_id,
            IntegrationConfig.source == IntegrationSource.CALENDAR.value,
        )
    )
    row = existing.scalar_one_or_none()
    if row is None:
        row = IntegrationConfig(
            user_id=user_id,
            source=IntegrationSource.CALENDAR.value,
            is_enabled=True,
            config_json=encrypted,
        )
        db.add(row)
    else:
        row.config_json = encrypted
        row.is_enabled = True
    await db.commit()
    await db.refresh(row)
    return row


async def disconnect(db: AsyncSession, user_id: int) -> None:
    """Revoke tokens and delete the stored config for the user."""
    existing = await db.execute(
        select(IntegrationConfig).where(
            IntegrationConfig.user_id == user_id,
            IntegrationConfig.source == IntegrationSource.CALENDAR.value,
        )
    )
    row = existing.scalar_one_or_none()
    if row is None:
        return
    try:
        creds = _hydrate_credentials(row.config_json or "")
        if creds and GoogleAuthRequest is not None:
            # Best-effort revoke — ignore failures.
            import httpx  # local import to keep startup cost down

            httpx.post(
                "https://oauth2.googleapis.com/revoke",
                params={"token": creds.refresh_token or creds.token},
                timeout=5.0,
            )
    except Exception as exc:  # noqa: BLE001
        logger.warning("calendar disconnect revoke failed: %s", exc)
    await db.delete(row)
    await db.commit()


async def load_credentials(
    db: AsyncSession, user_id: int
) -> GoogleCredentials | None:
    """Load and refresh Google credentials for *user_id*. Returns None if
    the user hasn't connected Calendar."""
    result = await db.execute(
        select(IntegrationConfig).where(
            IntegrationConfig.user_id == user_id,
            IntegrationConfig.source == IntegrationSource.CALENDAR.value,
        )
    )
    row = result.scalar_one_or_none()
    if row is None or not row.config_json:
        return None
    creds = _hydrate_credentials(row.config_json)
    if creds is None:
        return None
    if creds.expired and creds.refresh_token and GoogleAuthRequest is not None:
        creds.refresh(GoogleAuthRequest())
        row.config_json = encrypt_json(_serialize_credentials(creds))
        await db.commit()
    return creds


def _hydrate_credentials(encoded: str) -> GoogleCredentials | None:
    if GoogleCredentials is None:
        raise RuntimeError("google-auth is not installed")
    if not encoded:
        return None
    try:
        data = decrypt_json(encoded)
    except ValueError:
        # If we tried to read a legacy JSON blob, fall back to raw parse.
        try:
            data = json.loads(encoded)
        except json.JSONDecodeError:
            return None
    if not data.get("refresh_token"):
        return None
    expiry = data.get("expiry")
    try:
        expiry_dt = datetime.fromisoformat(expiry) if expiry else None
    except ValueError:
        expiry_dt = None
    return GoogleCredentials(
        token=data.get("access_token"),
        refresh_token=data.get("refresh_token"),
        token_uri=data.get("token_uri", "https://oauth2.googleapis.com/token"),
        client_id=data.get("client_id") or settings.GOOGLE_CLIENT_ID,
        client_secret=data.get("client_secret") or settings.GOOGLE_CLIENT_SECRET,
        scopes=data.get("scopes", CALENDAR_SCOPES),
        expiry=expiry_dt,
    )


def _serialize_credentials(creds: GoogleCredentials) -> dict[str, Any]:
    return {
        "access_token": creds.token,
        "refresh_token": creds.refresh_token,
        "token_uri": creds.token_uri,
        "client_id": creds.client_id,
        "client_secret": creds.client_secret,
        "scopes": list(creds.scopes or CALENDAR_SCOPES),
        "expiry": creds.expiry.isoformat() if creds.expiry else None,
    }
