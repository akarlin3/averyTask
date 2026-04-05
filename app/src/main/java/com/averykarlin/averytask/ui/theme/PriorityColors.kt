package com.averykarlin.averytask.ui.theme

import androidx.compose.ui.graphics.Color

object PriorityColors {
    val None = Color(0xFF9E9E9E)
    val Low = Color(0xFF4A90D9)
    val Medium = Color(0xFFF59E0B)
    val High = Color(0xFFE86F3C)
    val Urgent = Color(0xFFD4534A)

    fun forLevel(priority: Int): Color = when (priority) {
        1 -> Low; 2 -> Medium; 3 -> High; 4 -> Urgent; else -> None
    }
}
