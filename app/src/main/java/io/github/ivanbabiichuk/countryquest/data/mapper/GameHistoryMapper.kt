package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.data.local.entity.GameAnswerEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameResultEntity
import io.github.ivanbabiichuk.countryquest.data.local.relation.GameResultWithAnswers
import io.github.ivanbabiichuk.countryquest.domain.model.GameAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode

fun GameResult.toEntity(): GameResultEntity =
    GameResultEntity(
        id = id,
        mode = mode.name,
        completedAtEpochMillis = completedAtEpochMillis
    )

fun GameAnswer.toEntity(
    gameId: String,
    questionIndex: Int
): GameAnswerEntity =
    GameAnswerEntity(
        gameId = gameId,
        questionIndex = questionIndex,
        countryCode = countryCode,
        countryName = countryName,
        selectedOptionId = selectedOptionId,
        selectedOptionText = selectedOptionText,
        correctOptionId = correctOptionId,
        correctOptionText = correctOptionText
    )

fun GameAnswerEntity.toDomain(): GameAnswer =
    GameAnswer(
        countryCode = countryCode,
        countryName = countryName,
        selectedOptionId = selectedOptionId,
        selectedOptionText = selectedOptionText,
        correctOptionId = correctOptionId,
        correctOptionText = correctOptionText
    )

fun GameResultWithAnswers.toDomain(): GameResult {
    val orderedAnswers = answers.sortedBy { it.questionIndex }

    require(orderedAnswers.all { it.gameId == result.id }) {
        "All answers must belong to the loaded game."
    }

    require(
        orderedAnswers.map { it.questionIndex } ==
                orderedAnswers.indices.toList()
    ) {
        "Stored question indices must be unique and start at zero without gaps."
    }

    return GameResult(
        id = result.id,
        mode = QuizMode.valueOf(result.mode),
        completedAtEpochMillis = result.completedAtEpochMillis,
        answers = orderedAnswers.map { it.toDomain() }
    )
}