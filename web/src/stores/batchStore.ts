import { create } from 'zustand';
import { toast } from 'sonner';
import * as firestoreTasks from '@/api/firestore/tasks';
import * as firestoreHabits from '@/api/firestore/habits';
import * as firestoreProjects from '@/api/firestore/projects';
import { nlpBatchApi } from '@/api/nlpBatch';
import { webToAndroidPriority } from '@/api/firestore/converters';
import { getFirebaseUid } from '@/stores/firebaseUid';
import type {
  AmbiguousEntityHint,
  BatchHistoryRecord,
  BatchParseResponse,
  BatchUserContext,
  ForcedAmbiguousPhrase,
  ProposedMutation,
} from '@/types/batch';
import { applyMutation, undoEntry } from '@/features/batch/batchApplier';
import {
  matchMedicationsInCommand,
  type MatchResult,
} from '@/features/batch/medicationNameMatcher';

/** Audit failure mode #2 firewall: MEDICATION mutations from Haiku get
 *  auto-stripped below this confidence floor unless the deterministic
 *  matcher already committed the entity_id. TASK / HABIT / PROJECT mutations
 *  stay regardless — wrong-day scheduling is recoverable, wrong-medication
 *  is not. Mirrors `BatchPreviewViewModel.MEDICATION_CONFIDENCE_FLOOR`. */
const MEDICATION_CONFIDENCE_FLOOR = 0.85;

/** 24h to match Android's `UNDO_WINDOW_MILLIS` — the quick Snackbar is
 *  the primary surface, but Settings → Batch History stays usable within
 *  the same window. */
const UNDO_WINDOW_MS = 24 * 60 * 60 * 1000;

/** How many past batches to keep in localStorage. Older batches fall off
 *  the list even if still inside UNDO_WINDOW_MS — matches the "recent"
 *  framing on Android's BatchHistoryScreen. */
const MAX_HISTORY_ENTRIES = 25;

function storageKey(uid: string): string {
  return `prismtask_batch_history_${uid}`;
}

function loadHistory(uid: string): BatchHistoryRecord[] {
  try {
    const raw = localStorage.getItem(storageKey(uid));
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed as BatchHistoryRecord[];
  } catch {
    return [];
  }
}

function saveHistory(uid: string, records: BatchHistoryRecord[]): void {
  try {
    localStorage.setItem(storageKey(uid), JSON.stringify(records));
  } catch {
    // Quota / private-mode — non-fatal.
  }
}

function randomBatchId(): string {
  // crypto.randomUUID isn't universally available; fall back to a
  // simple random when missing. The ID is local-only.
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `batch_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function expandMatchResult(result: MatchResult): {
  committed: Record<string, string>;
  forced: ForcedAmbiguousPhrase[];
} {
  switch (result.kind) {
    case 'no_match':
      return { committed: {}, forced: [] };
    case 'unambiguous':
      return { committed: { ...result.matches }, forced: [] };
    case 'ambiguous':
      return {
        committed: {},
        forced: result.phrases.map((p) => ({
          phrase: p.phrase,
          candidate_entity_type: 'MEDICATION',
          candidate_entity_ids: p.candidate_entity_ids,
        })),
      };
    case 'mixed':
      return {
        committed: { ...result.unambiguous },
        forced: result.ambiguous.map((p) => ({
          phrase: p.phrase,
          candidate_entity_type: 'MEDICATION',
          candidate_entity_ids: p.candidate_entity_ids,
        })),
      };
  }
}

/** Apply the auto-strip + low-confidence safeguards on top of a Haiku
 *  response. Mirrors `BatchPreviewViewModel.loadPreview` on Android: any
 *  mutation whose entity_id appears in `committedIds` is exempt from both
 *  guards because the deterministic matcher has already proven its
 *  correctness. */
function applyClientSafeguards(
  response: BatchParseResponse,
  committedIds: Set<string>,
): BatchParseResponse {
  const ambiguousIds = new Set(
    response.ambiguous_entities.flatMap((h) => h.candidate_entity_ids),
  );
  const autoStripped: ProposedMutation[] = [];
  const afterStrip: ProposedMutation[] = [];
  for (const m of response.mutations) {
    if (ambiguousIds.has(m.entity_id) && !committedIds.has(m.entity_id)) {
      autoStripped.push(m);
    } else {
      afterStrip.push(m);
    }
  }
  const lowConfStripped: ProposedMutation[] = [];
  const keptMutations: ProposedMutation[] = [];
  for (const m of afterStrip) {
    if (
      m.entity_type === 'MEDICATION' &&
      response.confidence < MEDICATION_CONFIDENCE_FLOOR &&
      !committedIds.has(m.entity_id)
    ) {
      lowConfStripped.push(m);
    } else {
      keptMutations.push(m);
    }
  }
  const stripped = [...autoStripped, ...lowConfStripped];
  const augmented: AmbiguousEntityHint[] = [...response.ambiguous_entities];
  const seen = new Set(
    augmented.map(
      (h) => `${h.phrase}::${[...h.candidate_entity_ids].sort().join(',')}`,
    ),
  );
  for (const m of lowConfStripped) {
    const phrase = m.human_readable_description || m.entity_id;
    const ids = [m.entity_id];
    const key = `${phrase}::${ids.join(',')}`;
    if (seen.has(key)) continue;
    seen.add(key);
    augmented.push({
      phrase,
      candidate_entity_type: 'MEDICATION',
      candidate_entity_ids: ids,
      note:
        "Couldn't confirm the medication for this command — pick below or rephrase.",
    });
  }
  return {
    ...response,
    mutations: keptMutations,
    ambiguous_entities: augmented,
    stripped_ambiguous_count: stripped.length,
  };
}

async function buildUserContext(uid: string): Promise<BatchUserContext> {
  const [tasks, habits, projects] = await Promise.all([
    firestoreTasks.getAllTasks(uid),
    firestoreHabits.getHabits(uid),
    firestoreProjects.getProjects(uid),
  ]);

  const projectNameById = new Map(projects.map((p) => [p.id, p.title]));
  const today = new Date().toISOString().slice(0, 10);
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

  return {
    today,
    timezone,
    tasks: tasks
      .filter((t) => !t.id || t.id.length > 0) // belt-and-suspenders
      .map((t) => ({
        id: t.id,
        title: t.title,
        due_date: t.due_date ?? null,
        scheduled_start_time: null,
        priority: t.priority != null ? webToAndroidPriority(t.priority) : 0,
        project_id: t.project_id || null,
        project_name: t.project_id ? projectNameById.get(t.project_id) || null : null,
        tags: [],
        life_category: null,
        is_completed: t.status === 'done',
      })),
    habits: habits.map((h) => ({
      id: h.id,
      name: h.name,
      is_archived: !h.is_active,
    })),
    projects: projects.map((p) => ({
      id: p.id,
      name: p.title,
      status: p.status,
    })),
    medications: [],
  };
}

interface BatchStoreState {
  /** Current preview — set by QuickAddBar on batch detection, read by
   *  BatchPreviewScreen, cleared on commit or dismiss. */
  pendingCommand: string | null;
  pendingResponse: BatchParseResponse | null;
  isParsing: boolean;
  parseError: string | null;

  /** Persisted batch history for the current uid. Hydrated on
   *  `hydrate(uid)` — stays empty until then. */
  history: BatchHistoryRecord[];

  setPendingCommand: (commandText: string | null) => void;
  parsePendingCommand: () => Promise<void>;
  clearPending: () => void;

  hydrate: (uid: string) => void;
  commit: (
    commandText: string,
    mutations: ProposedMutation[],
  ) => Promise<BatchHistoryRecord>;
  undo: (batchId: string) => Promise<number>;
  purgeExpired: () => void;
}

export const useBatchStore = create<BatchStoreState>((set, get) => ({
  pendingCommand: null,
  pendingResponse: null,
  isParsing: false,
  parseError: null,
  history: [],

  setPendingCommand: (commandText) =>
    set({
      pendingCommand: commandText,
      pendingResponse: null,
      parseError: null,
    }),

  parsePendingCommand: async () => {
    const commandText = get().pendingCommand;
    if (!commandText) return;
    set({ isParsing: true, parseError: null, pendingResponse: null });
    try {
      const uid = getFirebaseUid();
      const userContext = await buildUserContext(uid);

      // Pre-resolver: run the deterministic local matcher and forward its
      // result to the backend as authoritative hints. NoMatch / empty
      // medication list reduces to a no-op so the wire-up stays safe even
      // before the web has a medications UI.
      const matchResult = matchMedicationsInCommand(
        commandText,
        userContext.medications.map((m) => ({
          id: m.id,
          name: m.name,
          display_label: m.display_label ?? null,
        })),
      );
      const { committed, forced } = expandMatchResult(matchResult);

      const enrichedContext: BatchUserContext = {
        ...userContext,
        committed_medication_matches: committed,
        forced_ambiguous_phrases: forced,
      };
      const response = await nlpBatchApi.parse({
        command_text: commandText,
        user_context: enrichedContext,
      });

      const safeguardedResponse = applyClientSafeguards(
        response,
        new Set(Object.values(committed)),
      );
      set({ pendingResponse: safeguardedResponse, isParsing: false });
    } catch (e) {
      set({
        isParsing: false,
        parseError: (e as Error).message || 'Failed to parse batch command',
      });
    }
  },

  clearPending: () =>
    set({ pendingCommand: null, pendingResponse: null, parseError: null }),

  hydrate: (uid) => {
    const history = loadHistory(uid).filter(
      (r) => r.expires_at > Date.now(),
    );
    set({ history });
    saveHistory(uid, history);
  },

  commit: async (commandText, mutations) => {
    const uid = getFirebaseUid();
    const batchId = randomBatchId();
    const createdAt = Date.now();
    const expiresAt = createdAt + UNDO_WINDOW_MS;
    const entries = [];
    let applied = 0;
    let skipped = 0;

    for (const mutation of mutations) {
      const outcome = await applyMutation(uid, mutation);
      if (outcome.applied && outcome.entry) {
        entries.push(outcome.entry);
        applied += 1;
      } else {
        entries.push({
          entity_type: mutation.entity_type,
          entity_id: mutation.entity_id,
          mutation_type: mutation.mutation_type,
          pre_state: {},
          applied: false,
          skip_reason: outcome.reason,
        });
        skipped += 1;
      }
    }

    const record: BatchHistoryRecord = {
      batch_id: batchId,
      command_text: commandText,
      created_at: createdAt,
      expires_at: expiresAt,
      undone_at: null,
      entries,
      applied_count: applied,
      skipped_count: skipped,
    };

    const nextHistory = [record, ...get().history].slice(0, MAX_HISTORY_ENTRIES);
    set({ history: nextHistory });
    saveHistory(uid, nextHistory);
    return record;
  },

  undo: async (batchId) => {
    const uid = getFirebaseUid();
    const record = get().history.find((r) => r.batch_id === batchId);
    if (!record || record.undone_at != null) return 0;

    let restored = 0;
    // Reverse order so dependent mutations on the same entity roll back
    // in the reverse order they were applied — matches Android.
    for (let i = record.entries.length - 1; i >= 0; i -= 1) {
      const entry = record.entries[i];
      if (!entry.applied) continue;
      const ok = await undoEntry(uid, entry);
      if (ok) restored += 1;
    }

    const now = Date.now();
    const nextHistory = get().history.map((r) =>
      r.batch_id === batchId ? { ...r, undone_at: now } : r,
    );
    set({ history: nextHistory });
    saveHistory(uid, nextHistory);

    if (restored > 0) {
      toast.success(`Undo complete — restored ${restored} change${restored === 1 ? '' : 's'}`);
    } else {
      toast.error('Nothing could be restored');
    }
    return restored;
  },

  purgeExpired: () => {
    const now = Date.now();
    const nextHistory = get().history.filter((r) => r.expires_at > now);
    if (nextHistory.length !== get().history.length) {
      try {
        const uid = getFirebaseUid();
        saveHistory(uid, nextHistory);
        set({ history: nextHistory });
      } catch {
        // Not authed — leave history in memory.
      }
    }
  },
}));
