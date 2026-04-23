import { useState } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import type {
  HabitTemplate,
  ProjectTemplate,
  ProjectTaskTemplate,
} from '@/features/templates/starterLibrary';

/**
 * Habit template editor modal. Used for both create ({ mode: 'create' })
 * and edit ({ mode: 'edit' }); the parent persists the result via
 * `userTemplates` Firestore helpers. Purely a form — no side effects
 * here beyond onSave.
 */
export function HabitTemplateEditor({
  initial,
  onClose,
  onSave,
}: {
  initial?: Partial<HabitTemplate>;
  onClose: () => void;
  onSave: (data: Omit<HabitTemplate, 'id'>) => Promise<void>;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [icon, setIcon] = useState(initial?.icon ?? '⭐');
  const [color, setColor] = useState(initial?.color ?? '#6366f1');
  const [frequency, setFrequency] = useState<'daily' | 'weekly'>(
    initial?.frequency ?? 'daily',
  );
  const [targetCount, setTargetCount] = useState<number>(
    initial?.target_count ?? 1,
  );
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    const cleanName = name.trim();
    if (!cleanName) return;
    setSaving(true);
    try {
      await onSave({
        name: cleanName,
        description: description.trim(),
        icon,
        color,
        frequency,
        target_count: Math.max(1, Math.floor(targetCount || 1)),
      });
      onClose();
    } catch (e) {
      toast.error((e as Error).message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={initial?.name ? 'Edit Habit Template' : 'New Habit Template'}
      size="sm"
    >
      <div className="flex flex-col gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Name
          </span>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Drink water"
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Description
          </span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <div className="grid grid-cols-3 gap-3">
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Icon
            </span>
            <input
              type="text"
              value={icon}
              onChange={(e) => setIcon(e.target.value)}
              maxLength={4}
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-lg text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Color
            </span>
            <input
              type="color"
              value={color}
              onChange={(e) => setColor(e.target.value)}
              className="h-10 w-full cursor-pointer rounded-md border border-[var(--color-border)]"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Target
            </span>
            <input
              type="number"
              min={1}
              value={targetCount}
              onChange={(e) =>
                setTargetCount(Math.max(1, Number(e.target.value) || 1))
              }
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
        </div>

        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Frequency
          </span>
          <select
            value={frequency}
            onChange={(e) => setFrequency(e.target.value as 'daily' | 'weekly')}
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          >
            <option value="daily">Daily</option>
            <option value="weekly">Weekly</option>
          </select>
        </label>

        <div className="mt-3 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!name.trim() || saving}>
            {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Save
          </Button>
        </div>
      </div>
    </Modal>
  );
}

/**
 * Project template editor modal. Lets users define a project name +
 * ordered list of starter tasks. Applied via the existing starter
 * panel flow on Use.
 */
export function ProjectTemplateEditor({
  initial,
  onClose,
  onSave,
}: {
  initial?: Partial<ProjectTemplate>;
  onClose: () => void;
  onSave: (data: Omit<ProjectTemplate, 'id'>) => Promise<void>;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [icon, setIcon] = useState(initial?.icon ?? '📁');
  const [color, setColor] = useState(initial?.color ?? '#6366f1');
  const [tasks, setTasks] = useState<ProjectTaskTemplate[]>(
    initial?.tasks ?? [],
  );
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [saving, setSaving] = useState(false);

  const addTask = () => {
    const title = newTaskTitle.trim();
    if (!title) return;
    setTasks((prev) => [...prev, { title }]);
    setNewTaskTitle('');
  };

  const handleSave = async () => {
    const cleanName = name.trim();
    if (!cleanName) return;
    setSaving(true);
    try {
      await onSave({
        name: cleanName,
        description: description.trim(),
        icon,
        color,
        tasks,
      });
      onClose();
    } catch (e) {
      toast.error((e as Error).message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={initial?.name ? 'Edit Project Template' : 'New Project Template'}
      size="md"
    >
      <div className="flex flex-col gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Name
          </span>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Ship a feature"
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Description
          </span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Icon
            </span>
            <input
              type="text"
              value={icon}
              onChange={(e) => setIcon(e.target.value)}
              maxLength={4}
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-lg text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Color
            </span>
            <input
              type="color"
              value={color}
              onChange={(e) => setColor(e.target.value)}
              className="h-10 w-full cursor-pointer rounded-md border border-[var(--color-border)]"
            />
          </label>
        </div>

        <div>
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Tasks
          </span>
          {tasks.length === 0 ? (
            <p className="mb-2 text-xs italic text-[var(--color-text-secondary)]">
              No tasks yet.
            </p>
          ) : (
            <ul className="mb-2 flex flex-col gap-1">
              {tasks.map((t, i) => (
                <li
                  key={i}
                  className="flex items-center gap-2 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-2 py-1.5"
                >
                  <span className="flex-1 text-sm text-[var(--color-text-primary)]">
                    {t.title}
                  </span>
                  <button
                    onClick={() =>
                      setTasks((prev) => prev.filter((_, j) => j !== i))
                    }
                    className="text-[var(--color-text-secondary)] hover:text-red-500"
                    aria-label={`Remove ${t.title}`}
                  >
                    <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="flex gap-1.5">
            <input
              type="text"
              value={newTaskTitle}
              onChange={(e) => setNewTaskTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addTask();
                }
              }}
              placeholder="Add a task…"
              className="flex-1 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
            <Button
              variant="secondary"
              size="sm"
              onClick={addTask}
              disabled={!newTaskTitle.trim()}
            >
              <Plus className="mr-1 h-3.5 w-3.5" />
              Add
            </Button>
          </div>
        </div>

        <div className="mt-3 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!name.trim() || saving}>
            {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Save
          </Button>
        </div>
      </div>
    </Modal>
  );
}
