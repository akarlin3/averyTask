import { useState } from 'react';
import { Loader2, Sparkles } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { aiApi } from '@/api/ai';
import { webToAndroidPriority } from '@/api/firestore/converters';
import { ProUpgradeModal } from '@/components/shared/ProUpgradeModal';
import { useProFeature } from '@/hooks/useProFeature';
import type {
  EisenhowerClassifyTextResponse,
  EisenhowerQuadrant,
} from '@/types/eisenhowerClassifyText';

const QUADRANT_META: Record<
  EisenhowerQuadrant,
  { label: string; className: string }
> = {
  Q1: {
    label: 'Q1 — Urgent & Important',
    className: 'bg-red-500/10 text-red-500 border-red-500/40',
  },
  Q2: {
    label: 'Q2 — Important, Not Urgent',
    className: 'bg-blue-500/10 text-blue-500 border-blue-500/40',
  },
  Q3: {
    label: 'Q3 — Urgent, Not Important',
    className: 'bg-amber-500/10 text-amber-500 border-amber-500/40',
  },
  Q4: {
    label: 'Q4 — Neither',
    className:
      'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)] border-[var(--color-border)]',
  },
};

/**
 * Ad-hoc text classifier — wraps `POST /ai/eisenhower/classify_text`.
 * Useful for "what quadrant would this be?" previews before a task is
 * persisted. Does not write to Firestore itself.
 */
export function ClassifyTextModal({
  isOpen,
  onClose,
}: {
  isOpen: boolean;
  onClose: () => void;
}) {
  const { isPro, showUpgrade, setShowUpgrade } = useProFeature();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [priority, setPriority] = useState<number>(3);
  const [result, setResult] = useState<EisenhowerClassifyTextResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const handleClassify = async () => {
    if (!isPro) {
      setShowUpgrade(true);
      return;
    }
    const cleanTitle = title.trim();
    if (!cleanTitle) return;
    setLoading(true);
    try {
      const res = await aiApi.eisenhowerClassifyText({
        title: cleanTitle,
        description: description.trim() || null,
        due_date: dueDate || null,
        priority: webToAndroidPriority(priority),
      });
      setResult(res);
    } catch (e) {
      toast.error((e as Error).message || 'Classification failed');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setResult(null);
    onClose();
  };

  return (
    <>
      <Modal isOpen={isOpen} onClose={handleClose} title="Classify Text" size="sm">
        <div className="flex flex-col gap-3">
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Title
            </span>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Prepare Q3 board deck"
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Description (optional)
            </span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              maxLength={4000}
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="text-sm">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
                Due date
              </span>
              <input
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
                Priority
              </span>
              <select
                value={priority}
                onChange={(e) => setPriority(Number(e.target.value))}
                className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              >
                <option value={0}>None</option>
                <option value={1}>Urgent</option>
                <option value={2}>High</option>
                <option value={3}>Medium</option>
                <option value={4}>Low</option>
              </select>
            </label>
          </div>

          {result && (
            <div
              className={`rounded-md border p-3 ${QUADRANT_META[result.quadrant].className}`}
              role="status"
            >
              <p className="text-sm font-semibold">
                {QUADRANT_META[result.quadrant].label}
              </p>
              <p className="mt-1 text-xs">{result.reason}</p>
            </div>
          )}

          <div className="mt-2 flex justify-end gap-2">
            <Button variant="ghost" onClick={handleClose}>
              Close
            </Button>
            <Button onClick={handleClassify} disabled={!title.trim() || loading}>
              {loading ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Sparkles className="mr-2 h-4 w-4" />
              )}
              Classify
            </Button>
          </div>
        </div>
      </Modal>

      <ProUpgradeModal
        isOpen={showUpgrade}
        onClose={() => setShowUpgrade(false)}
        featureName="Text-based Eisenhower"
        featureDescription="Classify any draft task against the 2×2 before you commit it."
      />
    </>
  );
}
