"""Add estimated_duration and actual_duration to tasks

Revision ID: 007
Revises: 006
Create Date: 2026-04-10
"""

from alembic import op
import sqlalchemy as sa

revision = "007"
down_revision = "006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("estimated_duration", sa.Integer(), nullable=True))
    op.add_column("tasks", sa.Column("actual_duration", sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "actual_duration")
    op.drop_column("tasks", "estimated_duration")
