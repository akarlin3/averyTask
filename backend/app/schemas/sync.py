from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel


class SyncOperation(BaseModel):
    entity_type: str  # "task", "project", "tag", "habit", etc.
    operation: str  # "create", "update", "delete"
    entity_id: Optional[int] = None
    data: Optional[dict[str, Any]] = None
    client_timestamp: datetime


class SyncPushRequest(BaseModel):
    operations: list[SyncOperation]
    last_sync: Optional[datetime] = None


class SyncPushResponse(BaseModel):
    processed: int
    errors: list[str] = []
    server_timestamp: datetime


class SyncPullRequest(BaseModel):
    since: Optional[datetime] = None


class SyncChange(BaseModel):
    entity_type: str
    operation: str
    entity_id: int
    data: Optional[dict[str, Any]] = None
    timestamp: datetime


class SyncPullResponse(BaseModel):
    changes: list[SyncChange]
    server_timestamp: datetime
