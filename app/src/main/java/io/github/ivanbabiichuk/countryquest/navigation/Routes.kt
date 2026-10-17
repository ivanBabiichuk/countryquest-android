package io.github.ivanbabiichuk.countryquest.navigation

import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class QuizRoute(
    val mode: QuizMode
)

@Serializable
data class ResultsRoute(
    val gameId: String
)

@Serializable
data object StatisticsRoute