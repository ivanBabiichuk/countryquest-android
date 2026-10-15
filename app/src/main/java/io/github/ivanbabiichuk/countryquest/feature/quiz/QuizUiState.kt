package io.github.ivanbabiichuk.countryquest.feature.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.QuizAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.model.QuizQuestion

sealed interface QuizUiState {

    data object Idle: QuizUiState

    data object Loading : QuizUiState

    data object LoadError : QuizUiState

    data class NotEnoughCountries(
        val available: Int,
        val required: Int
    ): QuizUiState

    data class Active(
        val questions: List<QuizQuestion>,
        val questionIndex: Int = 0,
        val answers: List<QuizAnswer> = emptyList()
    ): QuizUiState {

        val currentQuestion: QuizQuestion
            get() = questions[questionIndex]

        val currentAnswer: QuizAnswer?
            get() = answers.getOrNull(questionIndex)

        val questionNumber: Int
            get() = questionIndex + 1

        val totalQuestions: Int
            get() = questions.size

        val score: Int
            get() = answers.count { it.isCorrect }

        val isLastQuestion: Boolean
            get() = questionIndex == questions.lastIndex
    }

    data class Finished(
        val mode: QuizMode,
        val answers: List<QuizAnswer>
    ): QuizUiState {

        val score: Int
            get() = answers.count { it.isCorrect }

        val totalQuestions: Int
            get() = answers.size
    }
}