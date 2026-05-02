package com.averycorp.prismtask.domain.model

/**
 * Reward / output type for a task — orthogonal to [LifeCategory].
 *
 * Mode answers *what does the user want this task to produce?*
 *  - [WORK] produces output (deliverables, completed obligations).
 *  - [PLAY] produces enjoyment.
 *  - [RELAX] produces restored energy.
 *
 * A task carries both a [LifeCategory] (e.g. HEALTH) and a [TaskMode]
 * (e.g. PLAY). They are independent: a HEALTH task can be Work
 * (mandatory PT exercise), Play (pickup basketball), or Relax (slow walk).
 *
 * See `docs/WORK_PLAY_RELAX.md` for the full philosophy doc, including
 * descriptive-not-prescriptive copy rules and the mode-aware streak
 * defaults.
 *
 * Tasks that have not been classified default to [UNCATEGORIZED] and are
 * excluded from mode-balance ratio computations.
 */
enum class TaskMode {
    WORK,
    PLAY,
    RELAX,
    UNCATEGORIZED;

    companion object {
        /** Modes that participate in balance ratio computation. */
        val TRACKED: List<TaskMode> = listOf(WORK, PLAY, RELAX)

        /**
         * Parse a stored Room string back into an enum value, tolerating
         * unknown/legacy values by returning [UNCATEGORIZED].
         */
        fun fromStorage(value: String?): TaskMode {
            if (value.isNullOrBlank()) return UNCATEGORIZED
            return try {
                valueOf(value)
            } catch (_: IllegalArgumentException) {
                UNCATEGORIZED
            }
        }

        /** Display label for UI. */
        fun label(mode: TaskMode): String = when (mode) {
            WORK -> "Work"
            PLAY -> "Play"
            RELAX -> "Relax"
            UNCATEGORIZED -> "Uncategorized"
        }
    }
}
