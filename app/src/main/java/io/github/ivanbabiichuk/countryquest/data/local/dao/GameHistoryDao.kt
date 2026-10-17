package io.github.ivanbabiichuk.countryquest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameAnswerEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameResultEntity
import io.github.ivanbabiichuk.countryquest.data.local.relation.GameResultWithAnswers
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GameHistoryDao {

    @Transaction
    @Query(
        """
        SELECT * FROM game_results
        ORDER BY completedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeResults(): Flow<List<GameResultWithAnswers>>

    @Transaction
    @Query("SELECT * FROM game_results WHERE id = :gameId")
    abstract suspend fun getResult(gameId: String): GameResultWithAnswers?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertResult(result: GameResultEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAnswers(answers: List<GameAnswerEntity>)

    @Transaction
    open suspend fun saveCompletedGame(
        result: GameResultEntity,
        answers: List<GameAnswerEntity>,
    ) {
        require(answers.isNotEmpty()) {
            "A completed game must contain answers."
        }

        require(answers.all { it.gameId == result.id }) {
            "All answers must belong to the saved game."
        }

        val orderedAnswers = answers.sortedBy { it.questionIndex }

        require(
            orderedAnswers.map { it.questionIndex } ==
                    orderedAnswers.indices.toList()
        ) {
            "Question indices must be unique and start at zero without gaps."
        }

        require(
            orderedAnswers.map { it.countryCode }.distinct().size ==
                    orderedAnswers.size
        ) {
            "A completed game must not contain repeated countries."
        }

        val existing = getResult(result.id)

        if (existing != null) {
            require(
                existing.result == result &&
                        existing.answers.sortedBy { it.questionIndex } ==
                        orderedAnswers
            ) {
                "A game with this ID already exists with different data."
            }

            return
        }

        insertResult(result)
        insertAnswers(orderedAnswers)
    }
}