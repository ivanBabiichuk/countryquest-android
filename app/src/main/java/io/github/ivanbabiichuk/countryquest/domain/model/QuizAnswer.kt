package io.github.ivanbabiichuk.countryquest.domain.model

data class QuizAnswer(
    val question: QuizQuestion,
    val selectedOptionId: String
) {
    val isCorrect: Boolean
        get() = selectedOptionId == question.correctOptionId
}
