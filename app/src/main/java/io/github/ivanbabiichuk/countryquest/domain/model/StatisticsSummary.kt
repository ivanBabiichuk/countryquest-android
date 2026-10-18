package io.github.ivanbabiichuk.countryquest.domain.model

data class StatisticsSummary(
    val gamesPlayed: Int,
    val correctAnswers: Long,
    val totalAnswers: Long,
    val bestScoreOutOfTen: Int?
) {
    val accuracyPercent: Double?
        get() = if (totalAnswers == 0L) {
            null
        } else {
            correctAnswers.toDouble() / totalAnswers * 100.0
        }
}