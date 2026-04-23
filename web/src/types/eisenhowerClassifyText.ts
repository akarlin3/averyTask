/**
 * Types matching `backend/app/schemas/ai.py`:
 *   EisenhowerClassifyTextRequest / EisenhowerClassifyTextResponse.
 *
 * Unlike `/ai/eisenhower` (which takes a list of persisted task IDs),
 * this endpoint classifies an ad-hoc task that may not yet exist in
 * Firestore. Useful for "preview" quadrants in quick-add bars and
 * for classifying draft tasks before saving.
 *
 * `priority` is the Android 0–4 scale (0=None, 4=Urgent); callers
 * should convert from the web 1–4 scale via `webToAndroidPriority`
 * before sending.
 */

export type EisenhowerQuadrant = 'Q1' | 'Q2' | 'Q3' | 'Q4';

export interface EisenhowerClassifyTextRequest {
  title: string;
  description?: string | null;
  /** ISO `YYYY-MM-DD`. `null` means no due date. */
  due_date?: string | null;
  /** Android 0–4 scale. */
  priority?: number;
}

export interface EisenhowerClassifyTextResponse {
  quadrant: EisenhowerQuadrant;
  reason: string;
}
