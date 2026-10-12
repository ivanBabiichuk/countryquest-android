package io.github.ivanbabiichuk.countryquest.domain.model

data class QuizQuestion(
    val mode: QuizMode,
    val country: Country,
    val options: List<AnswerOption>,
) {
    val correctOptionId: String
        get() = country.code
}

data class AnswerOption(
    val id: String,
    val text: String
)
