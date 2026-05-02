package com.averycorp.prismtask.ui.theme

import androidx.compose.ui.graphics.Color
import com.averycorp.prismtask.domain.model.TaskMode

/**
 * Palette for the Work / Play / Relax mode dimension (orthogonal to
 * [LifeCategoryColor]). Distinct hues from the LifeCategory palette so
 * the two balance bars don't visually conflict on the Today screen.
 *
 * See `docs/WORK_PLAY_RELAX.md` for the philosophy.
 */
object TaskModeColor {
    val WORK = Color(0xFF1A6FB5) // deeper blue than LifeCategory.WORK
    val PLAY = Color(0xFFE89A2C) // amber
    val RELAX = Color(0xFF4FAE9E) // teal
    val UNCATEGORIZED = Color(0xFF9E9E9E) // neutral gray

    fun forMode(mode: TaskMode): Color = when (mode) {
        TaskMode.WORK -> WORK
        TaskMode.PLAY -> PLAY
        TaskMode.RELAX -> RELAX
        TaskMode.UNCATEGORIZED -> UNCATEGORIZED
    }
}
