package com.averycorp.prismtask.ui.screens.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.BalanceConfig
import com.averycorp.prismtask.domain.usecase.BalanceState
import com.averycorp.prismtask.domain.usecase.BalanceTracker
import com.averycorp.prismtask.domain.usecase.BurnoutScorer
import com.averycorp.prismtask.domain.usecase.WeeklyReviewAggregator
import com.averycorp.prismtask.domain.usecase.WeeklyReviewStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Weekly Balance Report (v1.4.0 V3 phase 2).
 *
 * Aggregates the current week's stats via [WeeklyReviewAggregator] and
 * exposes them alongside the current [BalanceState] and burnout score.
 * The "Previous / Next Week" navigation lets the user scroll through
 * past weeks; each re-aggregation hits the same pure function so the
 * screen is functionally deterministic.
 */
@HiltViewModel
class WeeklyBalanceReportViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val aggregator = WeeklyReviewAggregator()
    private val balanceTracker = BalanceTracker()
    private val burnoutScorer = BurnoutScorer()

    private val _state = MutableStateFlow(WeeklyBalanceReportState())
    val state: StateFlow<WeeklyBalanceReportState> = _state.asStateFlow()

    init {
        loadWeek(System.currentTimeMillis())
    }

    fun loadWeek(reference: Long) {
        viewModelScope.launch {
            val prefs = userPreferencesDataStore.workLifeBalanceFlow.first()
            val tasks = taskRepository.getAllTasksOnce()
            val stats = aggregator.aggregate(tasks, reference)
            val config = BalanceConfig(
                workTarget = prefs.workTarget / 100f,
                personalTarget = prefs.personalTarget / 100f,
                selfCareTarget = prefs.selfCareTarget / 100f,
                healthTarget = prefs.healthTarget / 100f,
                overloadThreshold = prefs.overloadThresholdPct / 100f
            )
            val balance = balanceTracker.compute(tasks, config, now = reference)
            val workRatio = balance.currentRatios[com.averycorp.prismtask.domain.model.LifeCategory.WORK] ?: 0f
            val burnout = burnoutScorer.computeFromTasks(tasks, workRatio, prefs.workTarget / 100f, now = reference)
            _state.value = WeeklyBalanceReportState(
                stats = stats,
                balance = balance,
                burnoutScore = burnout.score,
                reference = reference
            )
        }
    }

    fun previousWeek() {
        loadWeek(_state.value.reference - 7L * 24 * 60 * 60 * 1000)
    }

    fun nextWeek() {
        loadWeek(_state.value.reference + 7L * 24 * 60 * 60 * 1000)
    }
}

data class WeeklyBalanceReportState(
    val stats: WeeklyReviewStats? = null,
    val balance: BalanceState = BalanceState.EMPTY,
    val burnoutScore: Int = 0,
    val reference: Long = System.currentTimeMillis()
)
