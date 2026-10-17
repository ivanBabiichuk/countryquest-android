package io.github.ivanbabiichuk.countryquest.domain.repository

import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import kotlinx.coroutines.flow.Flow

interface GameHistoryRepository {

    fun observeResults(): Flow<List<GameResult>>

    suspend fun getResult(gameId: String): GameResult?

    suspend fun saveCompletedGame(result: GameResult)

}