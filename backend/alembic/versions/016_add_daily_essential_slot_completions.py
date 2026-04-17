"""Add daily_essential_slot_completions table

Revision ID: 016
Revises: 015
Create Date: 2026-04-17
"""

from alembic import op
import sqlalchemy as sa

revision = "016"
down_revision = "015"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "daily_essential_slot_completions",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "user_id",
            sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("date", sa.Date, nullable=False),
        sa.Column("slot_key", sa.String(16), nullable=False),
        sa.Column("med_ids_json", sa.Text, nullable=True),
        sa.Column("taken_at", sa.DateTime, nullable=True),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column(
            "updated_at",
            sa.DateTime,
            server_default=sa.func.now(),
            onupdate=sa.func.now(),
        ),
        sa.UniqueConstraint(
            "user_id",
            "date",
            "slot_key",
            name="uq_user_date_slot",
        ),
    )


def downgrade() -> None:
    op.drop_table("daily_essential_slot_completions")
