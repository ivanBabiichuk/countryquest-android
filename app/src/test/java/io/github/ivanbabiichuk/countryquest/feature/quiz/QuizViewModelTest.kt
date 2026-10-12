package io.github.ivanbabiichuk.countryquest.feature.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizViewModelTest {

    @Test
    fun `answer is accepted only once`() {
        val viewModel = startedViewModel()
        val question = activeState(viewModel).currentQuestion
        val wrongOption = question.options.first {
            it.id != question.correctOptionId
        }

        viewModel.selectAnswer(
            questionCode = question.country.code,
            optionId = question.correctOptionId,
        )

        viewModel.selectAnswer(
            questionCode = question.country.code,
            optionId = wrongOption.id,
        )

        val state = activeState(viewModel)

        assertEquals(1, state.answers.size)
        assertEquals(1, state.score)
        assertEquals(
            question.correctOptionId,
            state.currentAnswer?.selectedOptionId,
        )
    }

    @Test
    fun `cannot skip unanswered question`() {
        val viewModel = startedViewModel()
        val question = activeState(viewModel).currentQuestion

        viewModel.nextQuestion(question.country.code)

        assertEquals(0, activeState(viewModel).questionIndex)
        assertTrue(activeState(viewModel).answers.isEmpty())
    }

    @Test
    fun `invalid option is ignored`() {
        val viewModel = startedViewModel()
        val question = activeState(viewModel).currentQuestion

        viewModel.selectAnswer(
            questionCode = question.country.code,
            optionId = "INVALID",
        )

        assertTrue(activeState(viewModel).answers.isEmpty())
    }

    @Test
    fun `old question events cannot answer the next question`() {
        val viewModel = startedViewModel()
        val previousQuestion = activeState(viewModel).currentQuestion

        viewModel.selectAnswer(
            previousQuestion.country.code,
            previousQuestion.correctOptionId,
        )
        viewModel.nextQuestion(previousQuestion.country.code)

        val currentQuestion = activeState(viewModel).currentQuestion

        viewModel.selectAnswer(
            questionCode = previousQuestion.country.code,
            optionId = currentQuestion.correctOptionId,
        )

        assertEquals(1, activeState(viewModel).answers.size)
        assertEquals(null, activeState(viewModel).currentAnswer)
    }

    @Test
    fun `round finishes with ten answers and correct score`() {
        val viewModel = startedViewModel()

        repeat(10) { index ->
            val question = activeState(viewModel).currentQuestion

            val optionId = if (index < 7) {
                question.correctOptionId
            } else {
                question.options.first {
                    it.id != question.correctOptionId
                }.id
            }

            viewModel.selectAnswer(question.country.code, optionId)
            viewModel.nextQuestion(question.country.code)
        }

        val state = viewModel.uiState.value
        assertTrue(state is QuizUiState.Finished)

        val finished = state as QuizUiState.Finished
        assertEquals(10, finished.totalQuestions)
        assertEquals(7, finished.score)
        assertEquals(QuizMode.CAPITAL, finished.mode)
    }

    @Test
    fun `starting again does not reset an active round`() {
        val viewModel = startedViewModel()
        val initialState = viewModel.uiState.value

        viewModel.startRound(countries(), QuizMode.FLAG)

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun `insufficient data allows retry`() {
        val viewModel = QuizViewModel(
            QuizRoundGenerator(Random(42)),
        )

        viewModel.startRound(emptyList(), QuizMode.CAPITAL)

        assertEquals(
            QuizUiState.NotEnoughCountries(0, 10),
            viewModel.uiState.value,
        )

        viewModel.startRound(countries(), QuizMode.CAPITAL)

        assertTrue(viewModel.uiState.value is QuizUiState.Active)
    }

    private fun startedViewModel(): QuizViewModel =
        QuizViewModel(
            roundGenerator = QuizRoundGenerator(Random(42)),
        ).apply {
            startRound(countries(), QuizMode.CAPITAL)
        }

    private fun activeState(viewModel: QuizViewModel): QuizUiState.Active =
        viewModel.uiState.value as QuizUiState.Active

    private fun countries(): List<Country> =
        (1..12).map { index ->
            Country(
                code = "TEST_$index",
                name = "Country $index",
                capital = "Capital $index",
                region = "Test region",
                languages = listOf("Test language"),
                flagImage = "file:///android_asset/flags/test_$index.png",
            )
        }
}