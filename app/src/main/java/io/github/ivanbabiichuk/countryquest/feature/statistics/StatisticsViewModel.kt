package io.github.ivanbabiichuk.countryquest.feature.statistics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import io.github.ivanbabiichuk.countryquest.domain.statistics.QuizStatisticsCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val gameHistoryRepository: GameHistoryRepository,
    private val statisticsCalculator: QuizStatisticsCalculator
) : ViewModel() {

    private companion object {
        const val TAG = "StatisticsViewModel"
    }

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<StatisticsUiState> = retryTrigger
        .flatMapLatest {
            gameHistoryRepository.observeResults()
                .map { results ->
                    val state: StatisticsUiState =
                        if (results.isEmpty()) {
                            StatisticsUiState.Empty
                        } else {
                            StatisticsUiState.Content(statisticsCalculator.calculate(results))
                        }

                    state
                }
                .onStart {
                    emit(StatisticsUiState.Loading)
                }
                .catch { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }

                    Log.e(TAG, "Failed to load statistics", exception)
                    emit(StatisticsUiState.LoadError)
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatisticsUiState.Loading
        )

    fun retry() {
        if (uiState.value != StatisticsUiState.LoadError) return
        retryTrigger.update { it + 1 }
    }

}