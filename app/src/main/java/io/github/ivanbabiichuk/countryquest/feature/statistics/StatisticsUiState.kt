package io.github.ivanbabiichuk.countryquest.feature.statistics

import io.github.ivanbabiichuk.countryquest.domain.model.QuizStatistics

sealed interface StatisticsUiState {

    data object Loading : StatisticsUiState

    data object Empty : StatisticsUiState

    data object LoadError : StatisticsUiState

    data class Content(
        val statistics: QuizStatistics
    ) : StatisticsUiState
}