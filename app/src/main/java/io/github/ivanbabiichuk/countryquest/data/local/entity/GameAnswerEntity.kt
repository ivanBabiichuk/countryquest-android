package io.github.ivanbabiichuk.countryquest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "game_answers",
    primaryKeys = ["gameId", "questionIndex"],
    foreignKeys = [
        ForeignKey(
            entity = GameResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GameAnswerEntity(
    val gameId: String,
    val questionIndex: Int,
    val countryCode: String,
    val countryName: String,
    val selectedOptionId: String,
    val selectedOptionText: String,
    val correctOptionId: String,
    val correctOptionText: String
)