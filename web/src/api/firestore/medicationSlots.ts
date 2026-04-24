import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  orderBy,
  query,
  setDoc,
  updateDoc,
  where,
  type DocumentData,
  type Unsubscribe,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';

/**
 * Web-native medication slot definitions + daily tier states. These
 * live in Firestore at `users/{uid}/medication_slot_defs` and
 * `users/{uid}/medication_tier_states` because the backend's
 * `/daily-essentials/*` surface only handles completion toggles —
 * it has no CRUD for slot configuration and no tier concept.
 *
 * Keeping this Firestore-native means the web is fully self-sufficient
 * for slot + tier management. When Android adds a matching Firestore
 * schema (or the backend gains those endpoints), the web can keep
 * reading/writing here and either sync or migrate.
 *
 * Slot-completion state (per-day med_ids + taken_at) still flows
 * through the existing backend `/daily-essentials/slots` path — this
 * file does NOT touch completions.
 */

export type MedicationTier = 'SKIPPED' | 'PARTIAL' | 'COMPLETE';

export interface MedicationSlotDef {
  id: string;
  slot_key: string;
  display_name: string;
  sort_order: number;
  created_at: number;
  updated_at: number;
}

export interface MedicationTierState {
  id: string;
  slot_key: string;
  date_iso: string;
  tier: MedicationTier;
  /** Who set it: `auto` (derived from doses) or `user_set` (manual override). */
  source: 'auto' | 'user_set';
  /**
   * User-claimed wall-clock epoch millis for when the dose was actually
   * taken. NULL when the user hasn't backdated — UI treats `logged_at`
   * as the de-facto intended time in that case. Parity with the
   * Android `medication_tier_states.intended_time` column.
   */
  intended_time: number | null;
  /**
   * Database-write epoch millis. Distinct from `intended_time` for
   * backlogged entries. Always populated; falls back to `updated_at`
   * for legacy docs written before this column existed.
   */
  logged_at: number;
  updated_at: number;
}

// ── Slot definitions ────────────────────────────────────────────

function slotDefsCol(uid: string) {
  return collection(firestore, 'users', uid, 'medication_slot_defs');
}

function slotDefDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'medication_slot_defs', id);
}

function docToSlotDef(id: string, data: DocumentData): MedicationSlotDef {
  return {
    id,
    slot_key: data.slotKey ?? '',
    display_name: data.displayName ?? data.slotKey ?? '',
    sort_order: typeof data.sortOrder === 'number' ? data.sortOrder : 0,
    created_at: typeof data.createdAt === 'number' ? data.createdAt : Date.now(),
    updated_at: typeof data.updatedAt === 'number' ? data.updatedAt : Date.now(),
  };
}

export async function getSlotDefs(uid: string): Promise<MedicationSlotDef[]> {
  const snap = await getDocs(query(slotDefsCol(uid), orderBy('sortOrder', 'asc')));
  return snap.docs.map((d) => docToSlotDef(d.id, d.data()));
}

export async function createSlotDef(
  uid: string,
  data: { slot_key: string; display_name: string; sort_order?: number },
): Promise<MedicationSlotDef> {
  const now = Date.now();
  const payload = {
    slotKey: data.slot_key,
    displayName: data.display_name,
    sortOrder: data.sort_order ?? 0,
    createdAt: now,
    updatedAt: now,
  };
  const ref = await addDoc(slotDefsCol(uid), payload);
  return docToSlotDef(ref.id, payload);
}

export async function updateSlotDef(
  uid: string,
  id: string,
  updates: { slot_key?: string; display_name?: string; sort_order?: number },
): Promise<void> {
  const payload: Record<string, unknown> = { updatedAt: Date.now() };
  if (updates.slot_key !== undefined) payload.slotKey = updates.slot_key;
  if (updates.display_name !== undefined) payload.displayName = updates.display_name;
  if (updates.sort_order !== undefined) payload.sortOrder = updates.sort_order;
  await updateDoc(slotDefDoc(uid, id), payload);
}

export async function deleteSlotDef(uid: string, id: string): Promise<void> {
  await deleteDoc(slotDefDoc(uid, id));
}

export function subscribeToSlotDefs(
  uid: string,
  cb: (defs: MedicationSlotDef[]) => void,
): Unsubscribe {
  return onSnapshot(
    query(slotDefsCol(uid), orderBy('sortOrder', 'asc')),
    (snap) => cb(snap.docs.map((d) => docToSlotDef(d.id, d.data()))),
  );
}

// ── Tier states ─────────────────────────────────────────────────

function tierStatesCol(uid: string) {
  return collection(firestore, 'users', uid, 'medication_tier_states');
}

/** Deterministic doc id: `${dateIso}__${slotKey}` — lets us setDoc()
 *  without hitting Firestore to discover an existing row. */
function tierStateId(dateIso: string, slotKey: string): string {
  return `${dateIso}__${slotKey}`;
}

function tierStateDoc(uid: string, dateIso: string, slotKey: string) {
  return doc(
    firestore,
    'users',
    uid,
    'medication_tier_states',
    tierStateId(dateIso, slotKey),
  );
}

function docToTierState(id: string, data: DocumentData): MedicationTierState {
  const updatedAt =
    typeof data.updatedAt === 'number' ? data.updatedAt : Date.now();
  // intended_time stays null for legacy docs (honest "we don't know").
  // logged_at falls back to updated_at so every row has a non-zero
  // stamp — matches the Android MIGRATION_60_61 backfill convention.
  const intendedTime =
    typeof data.intendedTime === 'number' ? data.intendedTime : null;
  const loggedAt =
    typeof data.loggedAt === 'number' ? data.loggedAt : updatedAt;
  return {
    id,
    slot_key: data.slotKey ?? '',
    date_iso: data.dateIso ?? '',
    tier: (data.tier as MedicationTier) ?? 'SKIPPED',
    source: (data.source as 'auto' | 'user_set') ?? 'user_set',
    intended_time: intendedTime,
    logged_at: loggedAt,
    updated_at: updatedAt,
  };
}

export async function getTierStatesForDate(
  uid: string,
  dateIso: string,
): Promise<MedicationTierState[]> {
  const snap = await getDocs(
    query(tierStatesCol(uid), where('dateIso', '==', dateIso)),
  );
  return snap.docs.map((d) => docToTierState(d.id, d.data()));
}

export async function getTierState(
  uid: string,
  dateIso: string,
  slotKey: string,
): Promise<MedicationTierState | null> {
  const snap = await getDoc(tierStateDoc(uid, dateIso, slotKey));
  if (!snap.exists()) return null;
  return docToTierState(snap.id, snap.data()!);
}

export async function setTierState(
  uid: string,
  dateIso: string,
  slotKey: string,
  tier: MedicationTier,
  source: 'auto' | 'user_set' = 'user_set',
): Promise<MedicationTierState> {
  const ref = tierStateDoc(uid, dateIso, slotKey);
  const now = Date.now();
  // intended_time is intentionally omitted from the merge payload —
  // setTierStateIntendedTime is the dedicated path. logged_at always
  // stamps the moment of write (matches Android's loggedAt = now()
  // default in MedicationTierStateEntity).
  const payload = {
    slotKey,
    dateIso,
    tier,
    source,
    loggedAt: now,
    updatedAt: now,
  };
  await setDoc(ref, payload, { merge: true });
  // Re-fetch so we surface the merged state (existing intended_time
  // stays put rather than getting nulled out by docToTierState).
  const snap = await getDoc(ref);
  return docToTierState(ref.id, snap.data() ?? payload);
}

/**
 * Stamp a user-claimed `intended_time` on the existing tier-state doc.
 * Distinct from {@link setTierState} because intended_time is a user
 * intention about wall-clock — it must not be clobbered by every
 * tier-change write. Parity with the Android
 * `MedicationSlotRepository.setTierStateIntendedTime` write path.
 *
 * Caps `intendedTime` to `Date.now()` — no forward-dating supported.
 */
export async function setTierStateIntendedTime(
  uid: string,
  dateIso: string,
  slotKey: string,
  intendedTime: number,
): Promise<MedicationTierState> {
  const ref = tierStateDoc(uid, dateIso, slotKey);
  const capped = Math.min(intendedTime, Date.now());
  const payload = {
    slotKey,
    dateIso,
    intendedTime: capped,
    updatedAt: Date.now(),
  };
  await setDoc(ref, payload, { merge: true });
  const snap = await getDoc(ref);
  return docToTierState(ref.id, snap.data() ?? payload);
}

export async function clearTierState(
  uid: string,
  dateIso: string,
  slotKey: string,
): Promise<void> {
  await deleteDoc(tierStateDoc(uid, dateIso, slotKey));
}
