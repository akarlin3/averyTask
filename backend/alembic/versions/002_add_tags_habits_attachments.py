"""Add tags, habits, attachments, and extend tasks

Revision ID: 002
Revises: 001
Create Date: 2026-04-06

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "002"
down_revision: Union[str, None] = "001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add firebase_uid to users
    op.add_column("users", sa.Column("firebase_uid", sa.String(255), unique=True, nullable=True))

    # Extend tasks table with new columns
    op.add_column("tasks", sa.Column("notes", sa.Text(), nullable=True))
    op.add_column("tasks", sa.Column("due_time", sa.Time(), nullable=True))
    op.add_column("tasks", sa.Column("planned_date", sa.Date(), nullable=True))
    op.add_column("tasks", sa.Column("urgency_score", sa.Float(), server_default="0.0"))
    op.add_column("tasks", sa.Column("recurrence_json", sa.Text(), nullable=True))

    # Tags table
    op.create_table(
        "tags",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("color", sa.String(7), nullable=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
    )

    # Task-tag junction table
    op.create_table(
        "task_tags",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("task_id", sa.Integer(), sa.ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("tag_id", sa.Integer(), sa.ForeignKey("tags.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.UniqueConstraint("task_id", "tag_id", name="uq_task_tag"),
    )

    # Attachments table
    op.create_table(
        "attachments",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("task_id", sa.Integer(), sa.ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("name", sa.String(255), nullable=False),
        sa.Column("uri", sa.Text(), nullable=False),
        sa.Column("type", sa.String(50), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
    )

    # Habits table
    op.create_table(
        "habits",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("name", sa.String(255), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("icon", sa.String(10), nullable=True),
        sa.Column("color", sa.String(7), nullable=True),
        sa.Column("category", sa.String(100), nullable=True),
        sa.Column("frequency", sa.Enum("daily", "weekly", name="habitfrequency"), default="daily", nullable=False),
        sa.Column("target_count", sa.Integer(), default=1),
        sa.Column("active_days_json", sa.Text(), nullable=True),
        sa.Column("is_active", sa.Boolean(), default=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now()),
    )

    # Habit completions table
    op.create_table(
        "habit_completions",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("habit_id", sa.Integer(), sa.ForeignKey("habits.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("count", sa.Integer(), default=1),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
        sa.UniqueConstraint("habit_id", "date", name="uq_habit_date"),
    )


def downgrade() -> None:
    op.drop_table("habit_completions")
    op.drop_table("habits")
    op.drop_table("attachments")
    op.drop_table("task_tags")
    op.drop_table("tags")
    op.drop_column("tasks", "recurrence_json")
    op.drop_column("tasks", "urgency_score")
    op.drop_column("tasks", "planned_date")
    op.drop_column("tasks", "due_time")
    op.drop_column("tasks", "notes")
    op.drop_column("users", "firebase_uid")
    op.execute("DROP TYPE IF EXISTS habitfrequency")
