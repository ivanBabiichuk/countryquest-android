package io.github.ivanbabiichuk.countryquest.feature.results

import io.github.ivanbabiichuk.countryquest.domain.model.GameResult

sealed interface ResultsUiState {

    data object Loading : ResultsUiState

    data object LoadError : ResultsUiState

    data object NotFound : ResultsUiState

    data class Content(
        val result: GameResult
    ) : ResultsUiState
}