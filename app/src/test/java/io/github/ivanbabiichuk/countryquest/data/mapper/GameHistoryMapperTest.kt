package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.data.local.relation.GameResultWithAnswers
import io.github.ivanbabiichuk.countryquest.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GameHistoryMapperTest {
    private val answers = listOf(
        GameAnswer("UA", "Ukraine", "UA", "Kyiv", "UA", "Kyiv"),
        GameAnswer("FR", "France", "DE", "Berlin", "FR", "Paris")
    )
    private fun game(mode: QuizMode = QuizMode.CAPITAL) = GameResult("game", mode, 1234L, answers)
    private fun stored() = game().let { result ->
        GameResultWithAnswers(result.toEntity(), result.answers.mapIndexed { i, a -> a.toEntity(result.id, i) })
    }

    @Test fun allModesRoundTripWithoutLosingSnapshots() {
        QuizMode.entries.forEach { mode ->
            val original = game(mode)
            val stored = GameResultWithAnswers(original.toEntity(), original.answers.mapIndexed { i, a -> a.toEntity(original.id, i) })
            assertEquals(original, stored.toDomain())
            assertEquals(1, stored.toDomain().score)
            assertEquals(2, stored.toDomain().totalQuestions)
        }
    }
    @Test fun unorderedRowsRestoreQuestionOrder() {
        val stored = stored()
        assertEquals(game(), stored.copy(answers = stored.answers.reversed()).toDomain())
    }
    @Test fun missingAndDuplicateIndicesAreRejected() {
        val stored = stored()
        listOf(-1, 0, 2).forEach { index ->
            assertThrows(IllegalArgumentException::class.java) {
                stored.copy(answers = listOf(stored.answers[0], stored.answers[1].copy(questionIndex = index))).toDomain()
            }
        }
    }
    @Test fun answersFromAnotherGameAreRejected() {
        val stored = stored()
        assertThrows(IllegalArgumentException::class.java) {
            stored.copy(answers = stored.answers.map { it.copy(gameId = "other") }).toDomain()
        }
    }
    @Test fun unknownStoredModeIsNotSilentlyReplaced() {
        val stored = stored()
        assertThrows(IllegalArgumentException::class.java) {
            stored.copy(result = stored.result.copy(mode = "UNKNOWN")).toDomain()
        }
    }
    @Test fun emptyHistoryAndRepeatedCountriesAreRejected() {
        val stored = stored()
        assertThrows(IllegalArgumentException::class.java) { stored.copy(answers = emptyList()).toDomain() }
        assertThrows(IllegalArgumentException::class.java) {
            stored.copy(answers = listOf(stored.answers[0], stored.answers[0].copy(questionIndex = 1))).toDomain()
        }
    }
    @Test fun correctnessUsesIdsRatherThanDisplayedText() {
        assertTrue(answers[0].copy(selectedOptionText = "Different text").isCorrect)
        assertFalse(answers[1].copy(selectedOptionText = "Paris").isCorrect)
    }
}
