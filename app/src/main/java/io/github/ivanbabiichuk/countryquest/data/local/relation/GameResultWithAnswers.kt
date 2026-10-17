package io.github.ivanbabiichuk.countryquest.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameAnswerEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameResultEntity

data class GameResultWithAnswers(
    @Embedded
    val result: GameResultEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "gameId"
    )
    val answers: List<GameAnswerEntity>
)