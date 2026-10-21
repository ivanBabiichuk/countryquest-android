package io.github.ivanbabiichuk.countryquest.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ivanbabiichuk.countryquest.data.local.entity.CatalogSyncMetadataEntity
import io.github.ivanbabiichuk.countryquest.data.repository.RoomGameHistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class CatalogSyncMetadataDaoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var db: CountryQuestDatabase
    private val names = mutableListOf<String>()
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CountryQuestDatabase::class.java).build()
    }
    @After fun tearDown() { db.close(); names.forEach { context.deleteDatabase(it) } }
    private fun metadata(time: Long) = CatalogSyncMetadataEntity(lastSuccessfulSyncAtEpochMillis = time)

    @Test fun freshDatabaseHasUnknownSyncTime() = runBlocking {
        assertNull(db.catalogSyncMetadataDao().getMetadata())
        assertNull(withTimeout(5_000.milliseconds) { db.catalogSyncMetadataDao().observeMetadata().first() })
    }
    @Test fun upsertUpdatesSingleMetadataRow() = runBlocking {
        val dao = db.catalogSyncMetadataDao()
        dao.upsertMetadata(metadata(100))
        assertEquals(metadata(100), dao.getMetadata())
        dao.upsertMetadata(metadata(200))
        dao.upsertMetadata(metadata(200))
        assertEquals(metadata(200), dao.getMetadata())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM catalog_sync_metadata").use {
            assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
        }
    }
    @Test fun observerReceivesUpdatedSyncTime() = runBlocking {
        val dao = db.catalogSyncMetadataDao()
        dao.upsertMetadata(metadata(100))
        val initialObserved = CompletableDeferred<Unit>()
        val updated = async {
            withTimeout(5_000.milliseconds) {
                dao.observeMetadata().first {
                    if (it == metadata(100)) initialObserved.complete(Unit)
                    it == metadata(200)
                }
            }
        }
        withTimeout(5_000.milliseconds) { initialObserved.await() }
        dao.upsertMetadata(metadata(200))
        assertEquals(metadata(200), updated.await())
    }
    @Test fun metadataSurvivesDatabaseReopen() = runBlocking {
        val name = "metadata-${UUID.randomUUID()}.db"
        names.add(name)
        db.close()
        db = Room.databaseBuilder(context, CountryQuestDatabase::class.java, name).build()
        db.catalogSyncMetadataDao().upsertMetadata(metadata(123456789))
        db.close()
        db = Room.databaseBuilder(context, CountryQuestDatabase::class.java, name).build()
        assertEquals(metadata(123456789), db.catalogSyncMetadataDao().getMetadata())
    }
    @Test fun migrationV2ToV3PreservesCountryAndGameWithoutInventingSyncTime() = runBlocking {
        val name = "metadata-migration-${UUID.randomUUID()}.db"
        names.add(name)
        val schema = instrumentation.context.assets.open(
            "io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase/2.json"
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("""INSERT INTO countries VALUES ('UA', 'Ukraine', 'Kyiv', 'Europe', '["Ukrainian"]', NULL, NULL)""")
            old.execSQL("INSERT INTO game_results VALUES ('saved-game', 'CAPITAL', 1234)")
            old.execSQL("""INSERT INTO game_answers VALUES ('saved-game', 0, 'UA', 'Ukraine', 'UA', 'Kyiv', 'UA', 'Kyiv')""")
            old.version = 2
        }
        db.close()
        db = Room.databaseBuilder(context, CountryQuestDatabase::class.java, name).build()
        assertNull(db.catalogSyncMetadataDao().getMetadata())
        val country = db.countryDao().getCountries().single()
        assertEquals("UA", country.code)
        assertEquals("Kyiv", country.capital)
        assertEquals(listOf("Ukrainian"), country.languages)
        val game = requireNotNull(RoomGameHistoryRepository(db.gameHistoryDao()).getResult("saved-game"))
        assertEquals(1234L, game.completedAtEpochMillis)
        assertEquals(1, game.score)
        assertEquals("Kyiv", game.answers.single().selectedOptionText)
        assertEquals(3, db.openHelper.readableDatabase.version)
        db.catalogSyncMetadataDao().upsertMetadata(metadata(5000))
        assertEquals(metadata(5000), db.catalogSyncMetadataDao().getMetadata())
        assertEquals(game, RoomGameHistoryRepository(db.gameHistoryDao()).getResult("saved-game"))
    }
}
