"""Add calendar_sync_settings table

Revision ID: 013
Revises: 012
Create Date: 2026-04-16
"""

from alembic import op
import sqlalchemy as sa

revision = "013"
down_revision = "012"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "calendar_sync_settings",
        sa.Column(
            "user_id",
            sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("enabled", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column(
            "direction",
            sa.String(16),
            nullable=False,
            server_default="both",
        ),
        sa.Column(
            "frequency",
            sa.String(16),
            nullable=False,
            server_default="15min",
        ),
        sa.Column(
            "target_calendar_id",
            sa.String(255),
            nullable=False,
            server_default="primary",
        ),
        sa.Column(
            "display_calendar_ids_json",
            sa.Text,
            nullable=False,
            server_default="[]",
        ),
        sa.Column(
            "show_events",
            sa.Boolean,
            nullable=False,
            server_default=sa.true(),
        ),
        sa.Column(
            "sync_completed_tasks",
            sa.Boolean,
            nullable=False,
            server_default=sa.false(),
        ),
        sa.Column("last_sync_at", sa.DateTime, nullable=True),
        sa.Column(
            "last_sync_token_per_calendar_json",
            sa.Text,
            nullable=False,
            server_default="{}",
        ),
        sa.Column(
            "timezone",
            sa.String(64),
            nullable=False,
            server_default="UTC",
        ),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now()),
    )


def downgrade() -> None:
    op.drop_table("calendar_sync_settings")
