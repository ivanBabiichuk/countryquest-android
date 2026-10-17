package io.github.ivanbabiichuk.countryquest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_results")
data class GameResultEntity(
    @PrimaryKey
    val id: String,
    val mode: String,
    val completedAtEpochMillis: Long
)
