from typing import Optional

from pydantic import BaseModel, Field


# --- Eisenhower ---


class EisenhowerRequest(BaseModel):
    task_ids: Optional[list[int]] = None


class EisenhowerCategorization(BaseModel):
    task_id: int
    quadrant: str
    reason: str


class EisenhowerSummary(BaseModel):
    Q1: int = 0
    Q2: int = 0
    Q3: int = 0
    Q4: int = 0


class EisenhowerResponse(BaseModel):
    categorizations: list[EisenhowerCategorization]
    summary: EisenhowerSummary


# --- Pomodoro ---


class PomodoroRequest(BaseModel):
    available_minutes: int = Field(default=120, ge=15, le=480)
    session_length: int = Field(default=25, ge=5, le=60)
    break_length: int = Field(default=5, ge=1, le=30)
    long_break_length: int = Field(default=15, ge=5, le=60)
    focus_preference: str = Field(default="balanced")


class SessionTask(BaseModel):
    task_id: int
    title: str
    allocated_minutes: int


class PomodoroSession(BaseModel):
    session_number: int
    tasks: list[SessionTask]
    rationale: str


class SkippedTask(BaseModel):
    task_id: int
    reason: str


class PomodoroResponse(BaseModel):
    sessions: list[PomodoroSession]
    total_sessions: int
    total_work_minutes: int
    total_break_minutes: int
    skipped_tasks: list[SkippedTask] = []
