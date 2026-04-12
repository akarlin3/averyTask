import { useState, useRef, useEffect, useCallback } from 'react';
import { Sparkles, X, Loader2 } from 'lucide-react';
import { parseApi } from '@/api/parse';
import { Button } from '@/components/ui/Button';
import type { NLPParseResult } from '@/types/api';

interface NLPInputProps {
  onTaskCreate?: (data: { title: string; due_date?: string; priority?: number; project_suggestion?: string }) => void;
  className?: string;
}

export function NLPInput({ onTaskCreate, className = '' }: NLPInputProps) {
  const [value, setValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [parseResult, setParseResult] = useState<NLPParseResult | null>(null);
  const [editedResult, setEditedResult] = useState<Partial<NLPParseResult>>({});
  const inputRef = useRef<HTMLInputElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  // Global `/` shortcut
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (
        e.key === '/' &&
        !e.ctrlKey &&
        !e.metaKey &&
        !e.altKey &&
        document.activeElement?.tagName !== 'INPUT' &&
        document.activeElement?.tagName !== 'TEXTAREA' &&
        !document.activeElement?.getAttribute('contenteditable')
      ) {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  // Close popover on outside click
  useEffect(() => {
    if (!parseResult) return;
    const handler = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setParseResult(null);
        setEditedResult({});
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [parseResult]);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    const text = value.trim();
    if (!text) return;

    setLoading(true);
    try {
      const result = await parseApi.parse({ text });
      setParseResult(result);
      setEditedResult({
        title: result.title,
        due_date: result.due_date,
        priority: result.priority,
        project_suggestion: result.project_suggestion,
      });
    } catch {
      // On parse error, fall back to raw text
      setParseResult({
        title: text,
        project_suggestion: null,
        due_date: null,
        priority: null,
        parent_task_suggestion: null,
        confidence: 0,
        suggestions: [],
        needs_confirmation: false,
      });
      setEditedResult({ title: text });
    } finally {
      setLoading(false);
    }
  }, [value]);

  const handleConfirm = () => {
    onTaskCreate?.({
      title: (editedResult.title || parseResult?.title) ?? '',
      due_date: editedResult.due_date ?? parseResult?.due_date ?? undefined,
      priority: editedResult.priority ?? parseResult?.priority ?? undefined,
      project_suggestion: editedResult.project_suggestion ?? parseResult?.project_suggestion ?? undefined,
    });
    setValue('');
    setParseResult(null);
    setEditedResult({});
  };

  const handleCancel = () => {
    setParseResult(null);
    setEditedResult({});
  };

  const priorityLabels: Record<number, string> = { 1: 'Urgent', 2: 'High', 3: 'Medium', 4: 'Low' };

  return (
    <div className={`relative flex-1 max-w-2xl ${className}`}>
      <form onSubmit={handleSubmit}>
        <div className="relative">
          <Sparkles className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-secondary)]" />
          <input
            ref={inputRef}
            type="text"
            placeholder="Add task... (e.g. 'Buy milk tomorrow !high #shopping')  Press /"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] py-2 pl-10 pr-4 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-secondary)] outline-none transition-colors focus:border-[var(--color-accent)] focus:ring-1 focus:ring-[var(--color-accent)]"
            aria-label="Quick add task"
          />
          {loading && (
            <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-[var(--color-accent)]" />
          )}
        </div>
      </form>

      {/* Parse result popover */}
      {parseResult && (
        <div
          ref={popoverRef}
          className="absolute left-0 top-full z-50 mt-2 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4 shadow-lg"
        >
          <div className="mb-3 flex items-center justify-between">
            <h4 className="text-sm font-semibold text-[var(--color-text-primary)]">
              Parsed Task
            </h4>
            <button
              onClick={handleCancel}
              className="text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="flex flex-col gap-3">
            {/* Title */}
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                Title
              </label>
              <input
                type="text"
                value={editedResult.title || ''}
                onChange={(e) => setEditedResult({ ...editedResult, title: e.target.value })}
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              {/* Due date */}
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                  Due Date
                </label>
                <input
                  type="date"
                  value={editedResult.due_date || ''}
                  onChange={(e) => setEditedResult({ ...editedResult, due_date: e.target.value || null })}
                  className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                />
              </div>

              {/* Priority */}
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                  Priority
                </label>
                <select
                  value={editedResult.priority ?? ''}
                  onChange={(e) =>
                    setEditedResult({
                      ...editedResult,
                      priority: e.target.value ? Number(e.target.value) : null,
                    })
                  }
                  className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                >
                  <option value="">None</option>
                  {[1, 2, 3, 4].map((p) => (
                    <option key={p} value={p}>
                      {priorityLabels[p]}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Project suggestion */}
            {editedResult.project_suggestion && (
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                  Project Suggestion
                </label>
                <input
                  type="text"
                  value={editedResult.project_suggestion || ''}
                  onChange={(e) =>
                    setEditedResult({ ...editedResult, project_suggestion: e.target.value || null })
                  }
                  className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                />
              </div>
            )}
          </div>

          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={handleCancel}>
              Cancel
            </Button>
            <Button size="sm" onClick={handleConfirm}>
              Add Task
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
