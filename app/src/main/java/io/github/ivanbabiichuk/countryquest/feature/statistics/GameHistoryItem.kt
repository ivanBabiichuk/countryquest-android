package io.github.ivanbabiichuk.countryquest.feature.statistics

import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode

data class GameHistoryItem(
    val gameId: String,
    val mode: QuizMode,
    val completedAtEpochMillis: Long,
    val score: Int,
    val totalQuestions: Int
)