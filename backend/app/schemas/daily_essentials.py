"""Pydantic schemas for Daily Essentials medication slot completions.

The slot completion layer persists only *materialized* rows. Clients derive
slots virtually from the user's medication schedule and write a row through
these endpoints the first time the user interacts with a slot on a given day.
"""

from datetime import date as date_cls, datetime
from typing import Optional

from pydantic import BaseModel, Field


class SlotCompletionBase(BaseModel):
    date: date_cls
    slot_key: str = Field(..., max_length=16)
    med_ids: list[str] = Field(default_factory=list)


class SlotCompletionResponse(SlotCompletionBase):
    id: int
    taken_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class SlotToggleRequest(BaseModel):
    """Materialize a slot and set its taken state in one call."""

    date: date_cls
    slot_key: str = Field(..., max_length=16)
    med_ids: list[str] = Field(default_factory=list)
    taken: bool


class SlotBatchEntry(BaseModel):
    slot_key: str = Field(..., max_length=16)
    med_ids: list[str] = Field(default_factory=list)
    taken: bool


class SlotBatchRequest(BaseModel):
    date: date_cls
    entries: list[SlotBatchEntry]


class SlotBatchResponse(BaseModel):
    updated: int
    slots: list[SlotCompletionResponse]
