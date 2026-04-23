import { Undo2 } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { Button } from '@/components/ui/Button';
import { useBatchStore } from '@/stores/batchStore';

/**
 * "Recent batch commands" block on SettingsScreen. Every committed batch
 * sits here for the 24h undo window. Shows per-batch totals and lets the
 * user reverse a batch after the 30-second toast has dismissed.
 *
 * Expired records are purged by the store (`hydrate` on sign-in and
 * `commit` capping at MAX_HISTORY_ENTRIES), so this component renders
 * `history` directly without re-filtering.
 */
export function BatchHistorySection() {
  const items = useBatchStore((s) => s.history);
  const undo = useBatchStore((s) => s.undo);

  if (items.length === 0) {
    return (
      <p className="text-sm text-[var(--color-text-secondary)]">
        Batch commands you run from the quick-add bar appear here for 24 hours.
      </p>
    );
  }

  return (
    <ul className="space-y-2">
      {items.map((record) => {
        const alreadyUndone = record.undone_at != null;
        const applied = record.applied_count;
        const skipped = record.skipped_count;
        return (
          <li
            key={record.batch_id}
            className="flex items-start gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-[var(--color-text-primary)]">
                "{record.command_text}"
              </p>
              <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
                {formatDistanceToNow(record.created_at, { addSuffix: true })}
                {' · '}
                {applied} applied{skipped > 0 ? `, ${skipped} skipped` : ''}
                {alreadyUndone ? ' · undone' : ''}
              </p>
            </div>
            <Button
              variant="ghost"
              size="sm"
              disabled={alreadyUndone || applied === 0}
              onClick={() => undo(record.batch_id)}
            >
              <Undo2 className="mr-1 h-3.5 w-3.5" />
              Undo
            </Button>
          </li>
        );
      })}
    </ul>
  );
}
