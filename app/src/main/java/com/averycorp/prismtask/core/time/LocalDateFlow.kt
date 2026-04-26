package com.averycorp.prismtask.core.time

import com.averycorp.prismtask.data.preferences.StartOfDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive Flow over the user's current logical date.
 *
 * The previous medication call sites snapshotted the logical date once per
 * upstream Start-of-Day emission and had no mechanism to advance when the
 * wall-clock crossed the next logical-day boundary. This helper centralises
 * the correct shape: combine the SoD source with a wall-clock ticker that
 * re-emits at every logical-day boundary, so any caller that needs "what
 * day are we on" gets a Flow that actually represents the answer over time.
 *
 * Backed by [TimeProvider] so tests can drive virtual time without
 * monkey-patching `Instant.now()`. See `LocalDateFlowTest` and the bug
 * audit at `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md`.
 *
 * STUB COMMIT — body intentionally empty so the gating
 * `MedicationTodayDateRefreshTest` fails until the real implementation
 * lands in the follow-up commit.
 */
@Singleton
class LocalDateFlow @Inject constructor(
    @Suppress("unused") private val timeProvider: TimeProvider
) {
    /**
     * Emit the current logical [LocalDate] for the active SoD, then re-emit
     * at every logical-day boundary crossing. Re-keys when [sodSource]
     * emits a new Start-of-Day. Deduped — consecutive equal emissions are
     * suppressed.
     */
    fun observe(@Suppress("UNUSED_PARAMETER") sodSource: Flow<StartOfDay>): Flow<LocalDate> =
        flowOf()

    /** ISO `yyyy-MM-dd` form of [observe]. */
    fun observeIsoString(@Suppress("UNUSED_PARAMETER") sodSource: Flow<StartOfDay>): Flow<String> =
        flowOf()
}
