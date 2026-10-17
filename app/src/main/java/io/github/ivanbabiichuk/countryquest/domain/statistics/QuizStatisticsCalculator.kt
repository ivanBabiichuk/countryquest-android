package io.github.ivanbabiichuk.countryquest.domain.statistics

import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.model.QuizStatistics
import io.github.ivanbabiichuk.countryquest.domain.model.StatisticsSummary

class QuizStatisticsCalculator {

    fun calculate(results: List<GameResult>): QuizStatistics {
        val resultsByMode = results.groupBy { it.mode }

        return QuizStatistics(
            overall = summarize(results),
            byMode = QuizMode.entries.associateWith { mode ->
                summarize(resultsByMode[mode].orEmpty())
            }
        )
    }

    private fun summarize(results: List<GameResult>): StatisticsSummary =
        StatisticsSummary(
            gamesPlayed = results.size,
            correctAnswers = results.sumOf { it.score.toLong() },
            totalAnswers = results.sumOf {
                it.totalQuestions.toLong()
            },
            bestScoreOutOfTen = results
                .asSequence()
                .filter { it.totalQuestions == 10 }
                .maxOfOrNull { it.score }
        )
}