package io.github.ivanbabiichuk.countryquest.domain.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.QuizQuestion

sealed interface QuizGenerationResult {

    data class Success(
        val questions: List<QuizQuestion>
    ): QuizGenerationResult

    data class InsufficientCountries(
        val available: Int,
        val required: Int
    ): QuizGenerationResult
}