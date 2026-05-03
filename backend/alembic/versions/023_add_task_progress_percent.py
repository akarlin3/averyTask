"""Add ``tasks.progress_percent`` for the PrismTask-Timeline-Class scope.

Per audit ``docs/audits/PRISMTASK_TIMELINE_CLASS_AUDIT.md`` § P9 option
(a), only tasks under a project use the fractional column; everywhere
else continues reading binary ``status == DONE``. The backend's
``compute_project_burndown`` reads ``progress_percent / 100`` when
non-NULL and falls back to ``1.0 if status == DONE else 0.0``.

NULL on every legacy row preserves binary semantics — no backfill
required and no behavior change for tasks not under a project.

Revision ID: 023
Revises: 022
Create Date: 2026-05-03
"""

from alembic import op
import sqlalchemy as sa


revision = "023"
down_revision = "022"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("progress_percent", sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "progress_percent")
