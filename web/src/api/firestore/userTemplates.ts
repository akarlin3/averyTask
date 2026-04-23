import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDocs,
  orderBy,
  query,
  updateDoc,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import type {
  HabitTemplate,
  ProjectTaskTemplate,
  ProjectTemplate,
} from '@/features/templates/starterLibrary';

/**
 * User-authored habit + project templates, persisted in Firestore.
 *
 * The starter library (starterLibrary.ts) ships a curated list that
 * lives in web source; this module layers user-authored templates on
 * top at `users/{uid}/habit_templates` and `users/{uid}/project_templates`.
 * Shape is compatible with the starter types so the existing
 * "apply template → create live habit/project" flow works identically
 * for both.
 *
 * The backend has no habit/project template endpoints; this is
 * web-scoped until that changes.
 */

// ── Habit templates ──────────────────────────────────────────────

function habitTemplatesCol(uid: string) {
  return collection(firestore, 'users', uid, 'habit_templates');
}

function habitTemplateDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'habit_templates', id);
}

function docToHabitTemplate(id: string, data: DocumentData): HabitTemplate {
  return {
    id,
    name: data.name ?? '',
    description: data.description ?? '',
    icon: data.icon ?? '⭐',
    color: data.color ?? '#6366f1',
    frequency:
      data.frequency === 'weekly' || data.frequency === 'daily'
        ? data.frequency
        : 'daily',
    target_count:
      typeof data.target_count === 'number' && data.target_count > 0
        ? data.target_count
        : 1,
  };
}

export async function getHabitTemplates(uid: string): Promise<HabitTemplate[]> {
  const snap = await getDocs(
    query(habitTemplatesCol(uid), orderBy('createdAt', 'asc')),
  );
  return snap.docs.map((d) => docToHabitTemplate(d.id, d.data()));
}

export async function createHabitTemplate(
  uid: string,
  data: Omit<HabitTemplate, 'id'>,
): Promise<HabitTemplate> {
  const payload = {
    ...data,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  const ref = await addDoc(habitTemplatesCol(uid), payload);
  return docToHabitTemplate(ref.id, payload);
}

export async function updateHabitTemplate(
  uid: string,
  id: string,
  data: Partial<Omit<HabitTemplate, 'id'>>,
): Promise<void> {
  await updateDoc(habitTemplateDoc(uid, id), {
    ...data,
    updatedAt: Date.now(),
  });
}

export async function deleteHabitTemplate(
  uid: string,
  id: string,
): Promise<void> {
  await deleteDoc(habitTemplateDoc(uid, id));
}

// ── Project templates ────────────────────────────────────────────

function projectTemplatesCol(uid: string) {
  return collection(firestore, 'users', uid, 'project_templates');
}

function projectTemplateDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'project_templates', id);
}

function docToProjectTemplate(id: string, data: DocumentData): ProjectTemplate {
  const tasks: ProjectTaskTemplate[] = Array.isArray(data.tasks)
    ? data.tasks
        .filter(
          (t: unknown): t is { title: string; description?: string } =>
            !!t &&
            typeof (t as { title?: unknown }).title === 'string' &&
            (t as { title: string }).title.length > 0,
        )
        .map((t) => ({
          title: t.title,
          description:
            typeof t.description === 'string' ? t.description : undefined,
        }))
    : [];
  return {
    id,
    name: data.name ?? '',
    description: data.description ?? '',
    color: data.color ?? '#6366f1',
    icon: data.icon ?? '📁',
    tasks,
  };
}

export async function getProjectTemplates(
  uid: string,
): Promise<ProjectTemplate[]> {
  const snap = await getDocs(
    query(projectTemplatesCol(uid), orderBy('createdAt', 'asc')),
  );
  return snap.docs.map((d) => docToProjectTemplate(d.id, d.data()));
}

export async function createProjectTemplate(
  uid: string,
  data: Omit<ProjectTemplate, 'id'>,
): Promise<ProjectTemplate> {
  const payload = {
    ...data,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  const ref = await addDoc(projectTemplatesCol(uid), payload);
  return docToProjectTemplate(ref.id, payload);
}

export async function updateProjectTemplate(
  uid: string,
  id: string,
  data: Partial<Omit<ProjectTemplate, 'id'>>,
): Promise<void> {
  await updateDoc(projectTemplateDoc(uid, id), {
    ...data,
    updatedAt: Date.now(),
  });
}

export async function deleteProjectTemplate(
  uid: string,
  id: string,
): Promise<void> {
  await deleteDoc(projectTemplateDoc(uid, id));
}
