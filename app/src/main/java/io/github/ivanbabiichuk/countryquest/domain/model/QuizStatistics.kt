package io.github.ivanbabiichuk.countryquest.domain.model

data class QuizStatistics(
    val overall: StatisticsSummary,
    val byMode: Map<QuizMode, StatisticsSummary>
)