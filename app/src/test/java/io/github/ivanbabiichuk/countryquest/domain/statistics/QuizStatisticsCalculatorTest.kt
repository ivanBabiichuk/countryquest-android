package io.github.ivanbabiichuk.countryquest.domain.statistics

import io.github.ivanbabiichuk.countryquest.domain.model.GameAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.model.StatisticsSummary
import org.junit.Assert.*
import org.junit.Test

class QuizStatisticsCalculatorTest {
    private val calculator = QuizStatisticsCalculator()

    @Test fun emptyHistoryHasNoAccuracyOrRecordAndIncludesEveryMode() {
        val statistics = calculator.calculate(emptyList())
        val empty = StatisticsSummary(0, 0, 0, null)
        assertEquals(empty, statistics.overall)
        assertNull(statistics.overall.accuracyPercent)
        assertEquals(QuizMode.entries.toSet(), statistics.byMode.keys)
        statistics.byMode.values.forEach {
            assertEquals(empty, it)
            assertNull(it.accuracyPercent)
        }
    }

    @Test fun mixedRoundsProduceOverallTotalsAndBestScore() {
        val summary = calculator.calculate(listOf(
            game("first", correct = 7), game("second", correct = 10)
        )).overall
        assertEquals(StatisticsSummary(2, 17, 20, 10), summary)
        assertEquals(85.0, requireNotNull(summary.accuracyPercent), 0.000001)
    }

    @Test fun allWrongAnswersAreZeroRatherThanMissingValues() {
        val summary = calculator.calculate(listOf(game(correct = 0))).overall
        assertEquals(StatisticsSummary(1, 0, 10, 0), summary)
        assertEquals(0.0, requireNotNull(summary.accuracyPercent), 0.000001)
    }

    @Test fun perfectRoundHasOneHundredPercentAccuracy() {
        val summary = calculator.calculate(listOf(game(correct = 10))).overall
        assertEquals(StatisticsSummary(1, 10, 10, 10), summary)
        assertEquals(100.0, requireNotNull(summary.accuracyPercent), 0.000001)
    }

    @Test fun eachModeHasIndependentTotalsAndRecord() {
        val statistics = calculator.calculate(listOf(
            game("flag-1", QuizMode.FLAG, correct = 2),
            game("capital", QuizMode.CAPITAL, correct = 9),
            game("flag-2", QuizMode.FLAG, correct = 6),
            game("country", QuizMode.COUNTRY, correct = 4)
        ))
        assertEquals(StatisticsSummary(4, 21, 40, 9), statistics.overall)
        assertEquals(StatisticsSummary(2, 8, 20, 6), statistics.byMode[QuizMode.FLAG])
        assertEquals(StatisticsSummary(1, 9, 10, 9), statistics.byMode[QuizMode.CAPITAL])
        assertEquals(StatisticsSummary(1, 4, 10, 4), statistics.byMode[QuizMode.COUNTRY])
    }

    @Test fun unplayedModesStayEmptyWhenAnotherModeHasGames() {
        val statistics = calculator.calculate(listOf(game(mode = QuizMode.FLAG, correct = 5)))
        listOf(QuizMode.CAPITAL, QuizMode.COUNTRY).forEach { mode ->
            val summary = statistics.byMode.getValue(mode)
            assertEquals(StatisticsSummary(0, 0, 0, null), summary)
            assertNull(summary.accuracyPercent)
        }
    }

    @Test fun accuracyWeightsAnswersInsteadOfAveragingRoundPercentages() {
        val summary = calculator.calculate(listOf(
            game("short", total = 2, correct = 2),
            game("normal", total = 10, correct = 0)
        )).overall
        assertEquals(12L, summary.totalAnswers)
        assertEquals(2L, summary.correctAnswers)
        // 2 / 12, not the 50% average of a perfect and an all-wrong round.
        assertEquals(100.0 / 6.0, requireNotNull(summary.accuracyPercent), 0.000001)
    }

    @Test fun shorterAndLongerRoundsDoNotQualifyForTenQuestionRecord() {
        val summary = calculator.calculate(listOf(
            game("short", total = 2, correct = 2),
            game("normal", total = 10, correct = 7),
            game("long", total = 20, correct = 20)
        )).overall
        assertEquals(StatisticsSummary(3, 29, 32, 7), summary)
    }

    @Test fun onlyNonstandardRoundsHaveAccuracyButNoTenQuestionRecord() {
        val statistics = calculator.calculate(listOf(game(total = 3, correct = 1)))
        assertEquals(StatisticsSummary(1, 1, 3, null), statistics.overall)
        assertEquals(100.0 / 3.0, requireNotNull(statistics.overall.accuracyPercent), 0.000001)
        assertNull(statistics.byMode.getValue(QuizMode.CAPITAL).bestScoreOutOfTen)
    }

    @Test fun inputOrderDoesNotChangeStatisticsOrMutateHistory() {
        val games = listOf(game("one", correct = 3), game("two", correct = 8))
        val snapshot = games.toList()
        assertEquals(calculator.calculate(games), calculator.calculate(games.reversed()))
        assertEquals(snapshot, games)
    }

    @Test fun recalculationDoesNotRetainPreviouslyObservedGames() {
        calculator.calculate(listOf(game(correct = 10)))
        assertEquals(StatisticsSummary(0, 0, 0, null), calculator.calculate(emptyList()).overall)
        assertEquals(StatisticsSummary(1, 1, 10, 1), calculator.calculate(listOf(game(correct = 1))).overall)
    }

    @Test fun summarySupportsAnswerCountsBeyondIntegerRange() {
        val summary = StatisticsSummary(
            gamesPlayed = 1, correctAnswers = 3_000_000_000L,
            totalAnswers = 4_000_000_000L, bestScoreOutOfTen = null
        )
        assertEquals(75.0, requireNotNull(summary.accuracyPercent), 0.000001)
    }

    private fun game(
        id: String = "game",
        mode: QuizMode = QuizMode.CAPITAL,
        total: Int = 10,
        correct: Int
    ): GameResult = GameResult(
        id = id,
        mode = mode,
        completedAtEpochMillis = 100,
        answers = (0 until total).map { index ->
            val code = "COUNTRY_$index"
            GameAnswer(
                countryCode = code, countryName = "Country $index",
                selectedOptionId = if (index < correct) code else "OTHER",
                selectedOptionText = if (index < correct) "Answer $index" else "Wrong answer",
                correctOptionId = code, correctOptionText = "Answer $index"
            )
        }
    )
}
