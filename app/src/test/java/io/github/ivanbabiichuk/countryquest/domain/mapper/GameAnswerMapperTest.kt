package io.github.ivanbabiichuk.countryquest.domain.mapper

import io.github.ivanbabiichuk.countryquest.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GameAnswerMapperTest {
    private fun question(mode: QuizMode) = QuizQuestion(
        mode, Country("UA", "Ukraine", "Kyiv", "Europe", listOf("Ukrainian"), CountryFlag(null, null)),
        listOf(AnswerOption("UA", if (mode == QuizMode.CAPITAL) "Kyiv" else "Ukraine"),
            AnswerOption("FR", if (mode == QuizMode.CAPITAL) "Paris" else "France"))
    )
    @Test fun savesCorrectAndWrongOptionSnapshotsForEveryMode() {
        QuizMode.entries.forEach { mode ->
            val question = question(mode)
            question.options.forEach { selected ->
                val result = QuizAnswer(question, selected.id).toGameAnswer()
                assertEquals(GameAnswer("UA", "Ukraine", selected.id, selected.text,
                    "UA", question.options.first().text), result)
                assertEquals(selected.id == "UA", result.isCorrect)
            }
        }
    }
    @Test fun missingSelectedOptionIsRejected() {
        assertThrows(NoSuchElementException::class.java) {
            QuizAnswer(question(QuizMode.FLAG), "UNKNOWN").toGameAnswer()
        }
    }
    @Test fun missingCorrectOptionIsRejected() {
        val question = question(QuizMode.FLAG)
        assertThrows(NoSuchElementException::class.java) {
            QuizAnswer(question.copy(options = question.options.drop(1)), "FR").toGameAnswer()
        }
    }
    @Test fun duplicateOptionIdsAreRejected() {
        val question = question(QuizMode.CAPITAL)
        assertThrows(IllegalArgumentException::class.java) {
            QuizAnswer(question.copy(options = question.options + question.options.first()), "UA").toGameAnswer()
        }
    }
}
