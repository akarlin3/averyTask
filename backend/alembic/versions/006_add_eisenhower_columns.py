"""Add Eisenhower quadrant columns to tasks table

Revision ID: 006
Revises: 005
Create Date: 2026-04-10

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "006"
down_revision: Union[str, None] = "005"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("eisenhower_quadrant", sa.String(2), nullable=True))
    op.add_column("tasks", sa.Column("eisenhower_updated_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "eisenhower_updated_at")
    op.drop_column("tasks", "eisenhower_quadrant")
