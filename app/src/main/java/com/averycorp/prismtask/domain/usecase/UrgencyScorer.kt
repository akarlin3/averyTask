package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.UrgencyBands
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.averycorp.prismtask.data.preferences.UrgencyWindows

enum class UrgencyLevel { LOW, MEDIUM, HIGH, CRITICAL }

object UrgencyScorer {
    fun calculateScore(
        task: TaskEntity,
        subtaskCount: Int = 0,
        subtaskCompleted: Int = 0,
        weights: UrgencyWeights = UrgencyWeights(),
        windows: UrgencyWindows = UrgencyWindows()
    ): Float {
        // Fall back to the defaults if every weight is zero, so the scorer never
        // returns a flat 0 for every task just because the user slid every
        // slider to the bottom.
        val effective = if (weights.dueDate == 0f &&
            weights.priority == 0f &&
            weights.age == 0f &&
            weights.subtasks == 0f
        ) {
            UrgencyWeights()
        } else {
            weights
        }

        val now = System.currentTimeMillis()

        // 1. Due date proximity (weight 0.40)
        val overdueCeil = windows.overdueCeilingDays.toFloat()
        val imminent = windows.imminentWindowDays.toFloat()
        val dueDateScore = when {
            task.dueDate == null -> 0f
            else -> {
                val daysUntilDue = (task.dueDate - now).toFloat() / (24 * 60 * 60 * 1000f)
                when {
                    daysUntilDue < -overdueCeil -> 1.0f
                    daysUntilDue < 0 -> 0.7f + (-daysUntilDue / overdueCeil * 0.3f).coerceAtMost(0.3f)
                    daysUntilDue < 1 -> 0.6f
                    daysUntilDue < 2 -> 0.4f
                    daysUntilDue < imminent -> 0.1f + (1f - daysUntilDue / imminent) * 0.3f
                    else -> (0.1f - (daysUntilDue - imminent) / 30f * 0.1f).coerceIn(0f, 0.1f)
                }
            }
        }

        // 2. Explicit priority (weight 0.30)
        val priorityScore = when (task.priority) {
            0 -> 0.0f
            1 -> 0.2f
            2 -> 0.5f
            3 -> 0.8f
            4 -> 1.0f
            else -> 0.0f
        }

        // 3. Task age (weight 0.15)
        val daysOld = (now - task.createdAt).toFloat() / (24 * 60 * 60 * 1000f)
        val ageScore = when {
            daysOld < 1 -> 0.0f
            daysOld < 3 -> 0.1f + (daysOld - 1f) / 2f * 0.2f
            daysOld < 7 -> 0.3f + (daysOld - 3f) / 4f * 0.3f
            daysOld < 14 -> 0.6f + (daysOld - 7f) / 7f * 0.2f
            else -> (0.8f + (daysOld - 14f) / 30f * 0.2f).coerceAtMost(1.0f)
        }

        // 4. Subtask progress (weight 0.15)
        val subtaskScore = when {
            subtaskCount == 0 -> 0.0f
            subtaskCompleted == 0 -> 0.8f
            subtaskCompleted < subtaskCount -> 0.4f
            else -> 0.0f
        }

        return (
            dueDateScore * effective.dueDate + priorityScore * effective.priority + ageScore * effective.age +
                subtaskScore * effective.subtasks
            ).coerceIn(0f, 1f)
    }

    fun getUrgencyLevel(score: Float, bands: UrgencyBands = UrgencyBands()): UrgencyLevel = when {
        score >= bands.critical -> UrgencyLevel.CRITICAL
        score >= bands.high -> UrgencyLevel.HIGH
        score >= bands.medium -> UrgencyLevel.MEDIUM
        else -> UrgencyLevel.LOW
    }
}
