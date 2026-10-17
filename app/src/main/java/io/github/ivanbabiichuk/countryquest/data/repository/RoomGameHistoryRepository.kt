package io.github.ivanbabiichuk.countryquest.data.repository

import io.github.ivanbabiichuk.countryquest.data.local.dao.GameHistoryDao
import io.github.ivanbabiichuk.countryquest.data.mapper.toDomain
import io.github.ivanbabiichuk.countryquest.data.mapper.toEntity
import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomGameHistoryRepository @Inject constructor(
    private val gameHistoryDao: GameHistoryDao
) : GameHistoryRepository {

    override fun observeResults(): Flow<List<GameResult>> =
        gameHistoryDao.observeResults().map { results ->
            results.map { it.toDomain() }
        }

    override suspend fun getResult(gameId: String): GameResult? =
        gameHistoryDao.getResult(gameId)?.toDomain()

    override suspend fun saveCompletedGame(result: GameResult) {
        val resultEntity = result.toEntity()

        val answerEntities = result.answers.mapIndexed { index, answer ->
            answer.toEntity(
                gameId = result.id,
                questionIndex = index,
            )
        }

        gameHistoryDao.saveCompletedGame(
            result = resultEntity,
            answers = answerEntities,
        )
    }
}