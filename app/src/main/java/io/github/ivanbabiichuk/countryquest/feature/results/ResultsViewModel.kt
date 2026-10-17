package io.github.ivanbabiichuk.countryquest.feature.results

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import io.github.ivanbabiichuk.countryquest.navigation.ResultsRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameHistoryRepository: GameHistoryRepository
) : ViewModel() {

    private companion object {
        const val TAG = "ResultsViewModel"
    }

    private val gameId = savedStateHandle
        .toRoute<ResultsRoute>()
        .gameId

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ResultsUiState> = retryTrigger
        .flatMapLatest {
            flow<ResultsUiState> {
                emit(ResultsUiState.Loading)

                val result = gameHistoryRepository.getResult(gameId)

                emit(
                    if (result == null) {
                        ResultsUiState.NotFound
                    } else {
                        ResultsUiState.Content(result)
                    }
                )
            }.catch { exception ->
                if (exception is CancellationException) {
                    throw exception
                }

                Log.e(TAG, "Failed to load game result", exception)
                emit(ResultsUiState.LoadError)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ResultsUiState.Loading
        )

    fun retry() {
        if (uiState.value != ResultsUiState.LoadError) return
        retryTrigger.update { it + 1 }
    }

}