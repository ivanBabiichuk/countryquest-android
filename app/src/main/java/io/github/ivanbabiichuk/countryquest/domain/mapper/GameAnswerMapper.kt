package io.github.ivanbabiichuk.countryquest.domain.mapper

import io.github.ivanbabiichuk.countryquest.domain.model.GameAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.QuizAnswer

fun QuizAnswer.toGameAnswer(): GameAnswer {
    val selectedOption = question.options.single {
        it.id == selectedOptionId
    }

    val correctOption = question.options.single {
        it.id == question.correctOptionId
    }

    return GameAnswer(
        countryCode = question.country.code,
        countryName = question.country.name,
        selectedOptionId = selectedOption.id,
        selectedOptionText = selectedOption.text,
        correctOptionId = correctOption.id,
        correctOptionText = correctOption.text
    )
}