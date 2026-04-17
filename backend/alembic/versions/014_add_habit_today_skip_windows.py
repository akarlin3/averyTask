"""Add per-habit Today-screen skip-window columns

Revision ID: 014
Revises: 013
Create Date: 2026-04-16
"""

from alembic import op
import sqlalchemy as sa

revision = "014"
down_revision = "013"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "habits",
        sa.Column(
            "today_skip_after_complete_days",
            sa.Integer,
            nullable=False,
            server_default="-1",
        ),
    )
    op.add_column(
        "habits",
        sa.Column(
            "today_skip_before_schedule_days",
            sa.Integer,
            nullable=False,
            server_default="-1",
        ),
    )


def downgrade() -> None:
    op.drop_column("habits", "today_skip_before_schedule_days")
    op.drop_column("habits", "today_skip_after_complete_days")
