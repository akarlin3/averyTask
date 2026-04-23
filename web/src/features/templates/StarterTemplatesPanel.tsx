import { useEffect, useState } from 'react';
import { Edit3, Loader2, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import * as firestoreHabits from '@/api/firestore/habits';
import * as firestoreProjects from '@/api/firestore/projects';
import * as firestoreTasks from '@/api/firestore/tasks';
import * as userTemplates from '@/api/firestore/userTemplates';
import { getFirebaseUid } from '@/stores/firebaseUid';
import { useHabitStore } from '@/stores/habitStore';
import { useProjectStore } from '@/stores/projectStore';
import {
  STARTER_HABITS,
  STARTER_PROJECTS,
  type HabitTemplate,
  type ProjectTemplate,
} from './starterLibrary';
import {
  HabitTemplateEditor,
  ProjectTemplateEditor,
} from './UserTemplateEditors';

function currentUid(): string | null {
  try {
    return getFirebaseUid();
  } catch {
    return null;
  }
}

async function applyHabitTemplate(template: HabitTemplate): Promise<void> {
  const uid = currentUid();
  if (!uid) throw new Error('Not signed in');
  await firestoreHabits.createHabit(uid, {
    name: template.name,
    description: template.description,
    icon: template.icon,
    color: template.color,
    frequency: template.frequency,
    target_count: template.target_count,
  });
}

async function applyProjectTemplate(template: ProjectTemplate): Promise<void> {
  const uid = currentUid();
  if (!uid) throw new Error('Not signed in');
  const project = await firestoreProjects.createProject(uid, {
    title: template.name,
    description: template.description,
    color: template.color,
    icon: template.icon,
  });
  for (const t of template.tasks) {
    await firestoreTasks.createTask(uid, {
      title: t.title,
      description: t.description ?? null,
      project_id: project.id,
    });
  }
}

export function HabitStarterList() {
  const fetchHabits = useHabitStore((s) => s.fetchHabits);
  const [applying, setApplying] = useState<string | null>(null);

  const [customTemplates, setCustomTemplates] = useState<HabitTemplate[]>([]);
  const [editing, setEditing] = useState<HabitTemplate | 'new' | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<HabitTemplate | null>(null);

  useEffect(() => {
    const uid = currentUid();
    if (!uid) return;
    userTemplates.getHabitTemplates(uid).then(setCustomTemplates).catch(() => {
      /* non-fatal — starters still work */
    });
  }, []);

  const handleApply = async (template: HabitTemplate) => {
    setApplying(template.id);
    try {
      await applyHabitTemplate(template);
      await fetchHabits();
      toast.success(`Created habit "${template.name}"`);
    } catch (e) {
      toast.error((e as Error).message || 'Failed to create habit');
    } finally {
      setApplying(null);
    }
  };

  const handleSave = async (data: Omit<HabitTemplate, 'id'>) => {
    const uid = currentUid();
    if (!uid) throw new Error('Not signed in');
    if (editing && editing !== 'new') {
      await userTemplates.updateHabitTemplate(uid, editing.id, data);
      setCustomTemplates((prev) =>
        prev.map((t) => (t.id === editing.id ? { ...t, ...data } : t)),
      );
      toast.success(`Updated "${data.name}"`);
    } else {
      const created = await userTemplates.createHabitTemplate(uid, data);
      setCustomTemplates((prev) => [...prev, created]);
      toast.success(`Created template "${data.name}"`);
    }
  };

  const handleDelete = async () => {
    const uid = currentUid();
    if (!uid || !deleteTarget) return;
    try {
      await userTemplates.deleteHabitTemplate(uid, deleteTarget.id);
      setCustomTemplates((prev) =>
        prev.filter((t) => t.id !== deleteTarget.id),
      );
      toast.success('Template deleted');
    } catch (e) {
      toast.error((e as Error).message || 'Delete failed');
    } finally {
      setDeleteTarget(null);
    }
  };

  const renderCard = (
    template: HabitTemplate,
    kind: 'starter' | 'custom',
  ) => (
    <li
      key={`${kind}-${template.id}`}
      className="flex items-start gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
    >
      <span
        aria-hidden="true"
        className="text-2xl"
        style={{ filter: 'saturate(1.1)' }}
      >
        {template.icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-[var(--color-text-primary)]">
          {template.name}
        </p>
        <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
          {template.description}
        </p>
        <p className="mt-1 text-[11px] font-medium uppercase tracking-wide text-[var(--color-text-secondary)]">
          {template.frequency === 'daily' ? 'Daily' : 'Weekly'} ·{' '}
          {template.target_count}×
        </p>
      </div>
      <div className="flex flex-col gap-1">
        <Button
          size="sm"
          onClick={() => handleApply(template)}
          disabled={applying === template.id}
        >
          {applying === template.id ? (
            <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
          ) : (
            <Plus className="mr-1 h-3.5 w-3.5" />
          )}
          Use
        </Button>
        {kind === 'custom' && (
          <div className="flex gap-1">
            <button
              onClick={() => setEditing(template)}
              className="rounded-md p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
              title="Edit"
              aria-label={`Edit ${template.name}`}
            >
              <Edit3 className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
            <button
              onClick={() => setDeleteTarget(template)}
              className="rounded-md p-1 text-[var(--color-text-secondary)] hover:text-red-500"
              title="Delete"
              aria-label={`Delete ${template.name}`}
            >
              <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    </li>
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <Button size="sm" variant="secondary" onClick={() => setEditing('new')}>
          <Plus className="mr-1 h-3.5 w-3.5" />
          New habit template
        </Button>
      </div>
      {customTemplates.length > 0 && (
        <>
          <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Your templates
          </p>
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {customTemplates.map((t) => renderCard(t, 'custom'))}
          </ul>
        </>
      )}
      <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
        Starter library
      </p>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {STARTER_HABITS.map((t) => renderCard(t, 'starter'))}
      </ul>

      {editing && (
        <HabitTemplateEditor
          initial={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSave={handleSave}
        />
      )}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title="Delete template?"
        message={`"${deleteTarget?.name}" will be removed from your templates.`}
        confirmLabel="Delete"
        variant="danger"
      />
    </div>
  );
}

export function ProjectStarterList() {
  const fetchAllProjects = useProjectStore((s) => s.fetchAllProjects);
  const [applying, setApplying] = useState<string | null>(null);

  const [customTemplates, setCustomTemplates] = useState<ProjectTemplate[]>([]);
  const [editing, setEditing] = useState<ProjectTemplate | 'new' | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProjectTemplate | null>(
    null,
  );

  useEffect(() => {
    const uid = currentUid();
    if (!uid) return;
    userTemplates
      .getProjectTemplates(uid)
      .then(setCustomTemplates)
      .catch(() => {
        /* non-fatal */
      });
  }, []);

  const handleApply = async (template: ProjectTemplate) => {
    setApplying(template.id);
    try {
      await applyProjectTemplate(template);
      await fetchAllProjects();
      toast.success(
        `Created project "${template.name}" with ${template.tasks.length} tasks`,
      );
    } catch (e) {
      toast.error((e as Error).message || 'Failed to create project');
    } finally {
      setApplying(null);
    }
  };

  const handleSave = async (data: Omit<ProjectTemplate, 'id'>) => {
    const uid = currentUid();
    if (!uid) throw new Error('Not signed in');
    if (editing && editing !== 'new') {
      await userTemplates.updateProjectTemplate(uid, editing.id, data);
      setCustomTemplates((prev) =>
        prev.map((t) => (t.id === editing.id ? { ...t, ...data } : t)),
      );
      toast.success(`Updated "${data.name}"`);
    } else {
      const created = await userTemplates.createProjectTemplate(uid, data);
      setCustomTemplates((prev) => [...prev, created]);
      toast.success(`Created template "${data.name}"`);
    }
  };

  const handleDelete = async () => {
    const uid = currentUid();
    if (!uid || !deleteTarget) return;
    try {
      await userTemplates.deleteProjectTemplate(uid, deleteTarget.id);
      setCustomTemplates((prev) =>
        prev.filter((t) => t.id !== deleteTarget.id),
      );
      toast.success('Template deleted');
    } catch (e) {
      toast.error((e as Error).message || 'Delete failed');
    } finally {
      setDeleteTarget(null);
    }
  };

  const renderCard = (
    template: ProjectTemplate,
    kind: 'starter' | 'custom',
  ) => (
    <li
      key={`${kind}-${template.id}`}
      className="flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
    >
      <div className="flex items-start gap-3">
        <span aria-hidden="true" className="text-2xl">
          {template.icon}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-[var(--color-text-primary)]">
            {template.name}
          </p>
          <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
            {template.description}
          </p>
        </div>
        <div className="flex flex-col gap-1">
          <Button
            size="sm"
            onClick={() => handleApply(template)}
            disabled={applying === template.id}
          >
            {applying === template.id ? (
              <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
            ) : (
              <Plus className="mr-1 h-3.5 w-3.5" />
            )}
            Use
          </Button>
          {kind === 'custom' && (
            <div className="flex gap-1">
              <button
                onClick={() => setEditing(template)}
                className="rounded-md p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
                title="Edit"
                aria-label={`Edit ${template.name}`}
              >
                <Edit3 className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
              <button
                onClick={() => setDeleteTarget(template)}
                className="rounded-md p-1 text-[var(--color-text-secondary)] hover:text-red-500"
                title="Delete"
                aria-label={`Delete ${template.name}`}
              >
                <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </div>
          )}
        </div>
      </div>
      <ul className="ml-10 flex flex-col gap-0.5 text-xs text-[var(--color-text-secondary)]">
        {template.tasks.map((t, i) => (
          <li key={i} className="flex gap-1.5">
            <span className="text-[var(--color-text-secondary)]/50">•</span>
            <span>{t.title}</span>
          </li>
        ))}
      </ul>
    </li>
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <Button size="sm" variant="secondary" onClick={() => setEditing('new')}>
          <Plus className="mr-1 h-3.5 w-3.5" />
          New project template
        </Button>
      </div>
      {customTemplates.length > 0 && (
        <>
          <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Your templates
          </p>
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {customTemplates.map((t) => renderCard(t, 'custom'))}
          </ul>
        </>
      )}
      <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
        Starter library
      </p>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {STARTER_PROJECTS.map((t) => renderCard(t, 'starter'))}
      </ul>

      {editing && (
        <ProjectTemplateEditor
          initial={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSave={handleSave}
        />
      )}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title="Delete template?"
        message={`"${deleteTarget?.name}" will be removed from your templates.`}
        confirmLabel="Delete"
        variant="danger"
      />
    </div>
  );
}
