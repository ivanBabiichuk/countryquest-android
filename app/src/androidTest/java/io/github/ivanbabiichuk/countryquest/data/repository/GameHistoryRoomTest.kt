package io.github.ivanbabiichuk.countryquest.data.repository

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase
import io.github.ivanbabiichuk.countryquest.data.mapper.toEntity
import io.github.ivanbabiichuk.countryquest.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class GameHistoryRoomTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var db: CountryQuestDatabase
    private lateinit var repository: RoomGameHistoryRepository
    private val files = mutableListOf<String>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CountryQuestDatabase::class.java).build()
        repository = RoomGameHistoryRepository(db.gameHistoryDao())
    }
    @After fun tearDown() {
        db.close()
        files.forEach { context.deleteDatabase(it) }
    }
    private fun game(id: String = "game", time: Long = 100L) = GameResult(
        id, QuizMode.CAPITAL, time, listOf(
            GameAnswer("UA", "Ukraine", "UA", "Kyiv", "UA", "Kyiv"),
            GameAnswer("FR", "France", "DE", "Berlin", "FR", "Paris")
        )
    )
    private suspend fun history() = withTimeout(5_000) { repository.observeResults().first() }
    private fun answerCount(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM game_answers").use {
        it.moveToFirst(); it.getInt(0)
    }

    @Test fun emptyHistoryAndMissingResultAreDistinctFromFailure() = runBlocking {
        assertEquals(emptyList<GameResult>(), history())
        assertNull(repository.getResult("missing"))
    }
    @Test fun savesCompleteSnapshotWithoutCountryCatalog() = runBlocking {
        val game = game()
        repository.saveCompletedGame(game)
        assertEquals(game, repository.getResult(game.id))
        assertEquals(listOf(game), history())
        assertEquals(2, answerCount())
        assertTrue(db.countryDao().getCountries().isEmpty())
    }
    @Test fun duplicateAndConcurrentSavesProduceOneGame() = runBlocking {
        val game = game()
        coroutineScope {
            repeat(5) { launch(Dispatchers.Default) { repository.saveCompletedGame(game) } }
        }
        assertEquals(listOf(game), history())
        assertEquals(2, answerCount())
    }
    @Test fun conflictingIdDoesNotOverwriteOriginal() = runBlocking {
        val original = game()
        repository.saveCompletedGame(original)
        val conflicts = listOf(original.copy(completedAtEpochMillis = 200), original.copy(
            answers = original.answers.map { it.copy(selectedOptionText = "changed") }
        ))
        for (conflict in conflicts) {
            try { repository.saveCompletedGame(conflict); fail("Expected conflict") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(original, repository.getResult(original.id))
        assertEquals(2, answerCount())
    }
    @Test fun failureOnSecondAnswerRollsBackParentAndFirstAnswerThenRetryWorks() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_answer BEFORE INSERT ON game_answers
            WHEN NEW.questionIndex = 1
            BEGIN SELECT RAISE(ABORT, 'forced failure'); END
        """.trimIndent())
        try { repository.saveCompletedGame(game()); fail("Expected storage failure") }
        catch (_: SQLiteException) { }
        assertNull(repository.getResult("game"))
        assertEquals(0, answerCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_answer")
        repository.saveCompletedGame(game())
        assertEquals(game(), repository.getResult("game"))
    }
    @Test fun activeObserverReceivesCommittedGameAndHistoryHasStableOrder() = runBlocking {
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000.milliseconds) { repository.observeResults().first { it.size == 3 } }
        }
        repository.saveCompletedGame(game("old", 10))
        repository.saveCompletedGame(game("a", 20))
        repository.saveCompletedGame(game("b", 20))
        val results = update.await()
        assertEquals(listOf("b", "a", "old"), results.map { it.id })
        assertTrue(results.all { it.answers.size == 2 && it.score == 1 })
    }
    @Test fun invalidAnswerBatchesLeaveNoPartialGame() = runBlocking {
        val game = game()
        val rows = game.answers.mapIndexed { i, a -> a.toEntity(game.id, i) }
        val invalid = listOf(
            emptyList(),
            rows.map { it.copy(gameId = "wrong") },
            listOf(rows[0], rows[1].copy(questionIndex = 0)),
            listOf(rows[0], rows[1].copy(questionIndex = 2)),
            listOf(rows[0], rows[0].copy(questionIndex = 1))
        )
        for (batch in invalid) {
            try { db.gameHistoryDao().saveCompletedGame(game.toEntity(), batch); fail("Expected invalid batch") }
            catch (_: IllegalArgumentException) { }
            assertNull(repository.getResult(game.id))
            assertEquals(0, answerCount())
        }
    }
    @Test fun deletingResultCascadesToItsAnswersOnly() = runBlocking {
        repository.saveCompletedGame(game("first"))
        repository.saveCompletedGame(game("second"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM game_results WHERE id = ?", arrayOf("first"))
        assertNull(repository.getResult("first"))
        assertEquals(game("second"), repository.getResult("second"))
        assertEquals(2, answerCount())
    }
    @Test fun savedHistorySurvivesDatabaseReopen() = runBlocking {
        val name = "history-${UUID.randomUUID()}.db"
        files.add(name)
        db.close()
        db = Room.databaseBuilder(context, CountryQuestDatabase::class.java, name).build()
        repository = RoomGameHistoryRepository(db.gameHistoryDao())
        repository.saveCompletedGame(game())
        db.close()
        db = Room.databaseBuilder(context, CountryQuestDatabase::class.java, name).build()
        repository = RoomGameHistoryRepository(db.gameHistoryDao())
        assertEquals(game(), repository.getResult("game"))
    }
    @Test fun migrationFromExportedV1PreservesCatalogAndEnablesHistory() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        files.add(name)
        val schema = instrumentation.context.assets.open(
            "io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase/1.json"
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("""INSERT INTO countries (code, name, capital, region, languages, flagSvgUrl, flagPngUrl)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", arrayOf("UA", "Ukraine", "Kyiv", "Europe", "[\"Ukrainian\"]", "https://example.test/ua.svg", null))
            old.version = 1
        }
        db.close()
        // Use the same generated auto-migration path as the production builder.
        db = Room.databaseBuilder(context, CountryQuestDatabase::class.java, name).build()
        repository = RoomGameHistoryRepository(db.gameHistoryDao())
        val country = db.countryDao().getCountries().single()
        assertEquals("UA", country.code)
        assertEquals("Ukraine", country.name)
        assertEquals("Kyiv", country.capital)
        assertEquals("Europe", country.region)
        assertEquals(listOf("Ukrainian"), country.languages)
        assertEquals("https://example.test/ua.svg", country.flagSvgUrl)
        assertNull(country.flagPngUrl)
        assertEquals(2, db.openHelper.readableDatabase.version)
        assertTrue(history().isEmpty())
        repository.saveCompletedGame(game())
        assertEquals(game(), repository.getResult("game"))
    }
}
