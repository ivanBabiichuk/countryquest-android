package io.github.ivanbabiichuk.countryquest.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.Room
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import io.github.ivanbabiichuk.countryquest.data.local.CountryCatalogLocalDataSource
import io.github.ivanbabiichuk.countryquest.data.local.entity.CatalogSyncMetadataEntity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase
import io.github.ivanbabiichuk.countryquest.data.local.dao.CountryDao
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.data.remote.CountriesApi
import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.FlagsDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.LanguageDto
import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class CountryRepositoryRoomTest {
    private lateinit var database: CountryQuestDatabase
    private lateinit var dao: CountryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            CountryQuestDatabase::class.java,
        ).build()
        dao = database.countryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun partialRefreshPreservesConflictingInvalidAndAbsentCountries() = runBlocking {
        val germany = entity("DE", "Germany", "Stored Berlin")
        val france = entity("FR", "France", "Old Paris")
        val moldova = entity("MD", "Moldova", "Chișinău")
        val poland = entity("PL", "Poland", "Warsaw")
        dao.upsertCountries(listOf(germany, france, moldova, poland))
        val api = object : CountriesApi {
            override suspend fun getCountries() = listOf(
                dto("UA", "Ukraine", "Kyiv"),
                dto("FR", "France", "Paris"),
                dto("FR", "France", "Paris"),
                dto("DE", "Germany", "Berlin"),
                dto("DE", "Germany", "Bonn"),
                dto("MD", " ", "Invalid change"),
            )
        }
        val repository = OfflineFirstCountryRepository(
            api,
            CountryCatalogLocalDataSource(database),
            object : TimeProvider {
                override fun currentTimeMillis(): Long = 500L
            }
        )

        assertEquals(CountryRefreshResult.Success(2, 4), repository.refreshCountries())
        assertEquals(
            listOf(germany, entity("FR", "France", "Paris"), moldova, poland, entity("UA", "Ukraine", "Kyiv")),
            dao.getCountries(),
        )
        val observed = withTimeout(5_000) { repository.observeCountries().first() }
        assertEquals(listOf("DE", "FR", "MD", "PL", "UA"), observed.map { it.code })
        assertEquals("https://flagcdn.com/ua.svg", observed.last().flag.svgUrl)
        assertEquals("https://flagcdn.com/w320/ua.png", observed.last().flag.pngUrl)
    }

    @Test
    fun sqliteFailureRollsBackEntireBatchAndNextRefreshSucceeds() = runBlocking {
        val original = entity("FR", "France", "Stored Paris")
        dao.upsertCountries(listOf(original))
        // Fail the second country after the first has been updated in this batch.
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_test_country BEFORE INSERT ON countries
            WHEN NEW.code = 'ZZ'
            BEGIN
                SELECT RAISE(ABORT, 'forced write failure');
            END
            """.trimIndent(),
        )
        val api = object : CountriesApi {
            override suspend fun getCountries() = listOf(
                dto("FR", "France", "Paris"),
                dto("ZZ", "Test country", "Test capital"),
            )
        }
        val repository = OfflineFirstCountryRepository(
            api,
            CountryCatalogLocalDataSource(database),
            object : TimeProvider {
                override fun currentTimeMillis(): Long = 500L
            }
        )

        assertEquals(
            CountryRefreshResult.Failure(CountryRefreshFailure.STORAGE),
            repository.refreshCountries(),
        )
        assertEquals(listOf(original), dao.getCountries())
        assertNull(database.catalogSyncMetadataDao().getMetadata())

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_test_country")
        withTimeout(5_000.milliseconds) {
            assertEquals(CountryRefreshResult.Success(2, 0), repository.refreshCountries())
        }
        assertEquals(
            listOf(entity("FR", "France", "Paris"), entity("ZZ", "Test country", "Test capital")),
            dao.getCountries(),
        )
    }

    @Test
    fun metadataInsertFailureRollsBackCountriesAndDoesNotInventSyncTime() = runBlocking {
        verifyMetadataFailure(previousSyncTime = null)
    }

    @Test
    fun metadataUpdateFailureRollsBackCountriesAndPreservesPreviousSyncTime() = runBlocking {
        verifyMetadataFailure(previousSyncTime = 100L)
    }

    @Test
    fun emptyCatalogHasZeroCountriesAndUnknownSyncTime() = runBlocking {
        assertEquals(CountryCatalogStatus(0, null), repositoryForStatus().getCatalogStatus().getOrThrow())
    }

    @Test
    fun legacyCatalogKeepsUnknownSyncTime() = runBlocking {
        dao.upsertCountries(listOf(entity("UA", "Ukraine", "Kyiv")))
        assertEquals(CountryCatalogStatus(1, null), repositoryForStatus().getCatalogStatus().getOrThrow())
    }

    @Test
    fun catalogStatusReflectsAtomicSaveAndCountsUpsertsWithoutDuplicates() = runBlocking {
        val source = CountryCatalogLocalDataSource(database)
        source.saveSyncedCountries(listOf(entity("UA", "Ukraine", "Kyiv")), 100L)
        assertEquals(CountryCatalogStatus(1, 100L), repositoryForStatus().getCatalogStatus().getOrThrow())
        source.saveSyncedCountries(
            listOf(entity("UA", "Ukraine", "Updated Kyiv"), entity("FR", "France", "Paris")),
            200L,
        )
        assertEquals(CountryCatalogStatus(2, 200L), repositoryForStatus().getCatalogStatus().getOrThrow())
    }

    @Test
    fun catalogReadSqliteFailureIsReturnedWithoutNetworkRequest() = runBlocking {
        database.openHelper.writableDatabase.execSQL("DROP TABLE catalog_sync_metadata")
        val result = repositoryForStatus().getCatalogStatus()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SQLiteException)
    }

    @Test
    fun existingObserverReceivesCountriesMetadataAndAtomicSyncSnapshots() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val snapshots = Channel<CountryCatalogStatus>(Channel.UNLIMITED)
            val observer = launch(start = CoroutineStart.UNDISPATCHED) {
                repositoryForStatus().observeCatalogStatus().collect { snapshots.send(it) }
            }
            try {
                assertEquals(CountryCatalogStatus(0, null), snapshots.receive())
                dao.upsertCountries(listOf(entity("UA", "Ukraine", "Kyiv")))
                assertEquals(CountryCatalogStatus(1, null), snapshots.receive())
                database.catalogSyncMetadataDao().upsertMetadata(
                    CatalogSyncMetadataEntity(lastSuccessfulSyncAtEpochMillis = 100L)
                )
                assertEquals(CountryCatalogStatus(1, 100L), snapshots.receive())
                CountryCatalogLocalDataSource(database).saveSyncedCountries(
                    listOf(entity("UA", "Ukraine", "Kyiv"), entity("FR", "France", "Paris")),
                    200L
                )
                // No mixed snapshot such as new count with the previous timestamp.
                assertEquals(CountryCatalogStatus(2, 200L), snapshots.receive())
            } finally {
                observer.cancelAndJoin()
                snapshots.close()
            }
        }
    }

    private fun repositoryForStatus() = OfflineFirstCountryRepository(
        object : CountriesApi {
            override suspend fun getCountries(): List<CountryDto> =
                error("Reading catalog status must not request the API")
        },
        CountryCatalogLocalDataSource(database),
        object : TimeProvider {
            override fun currentTimeMillis(): Long = error("Reading status must not read the clock")
        }
    )

    private suspend fun verifyMetadataFailure(previousSyncTime: Long?) {
        val original = entity("FR", "France", "Stored Paris")
        dao.upsertCountries(listOf(original))
        previousSyncTime?.let {
            database.catalogSyncMetadataDao().upsertMetadata(
                CatalogSyncMetadataEntity(lastSuccessfulSyncAtEpochMillis = it)
            )
        }
        val operation = if (previousSyncTime == null) "INSERT" else "UPDATE"
        // This fails after country upserts, so only the outer transaction can undo them.
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_sync_time BEFORE $operation ON catalog_sync_metadata
            BEGIN
                SELECT RAISE(ABORT, 'forced metadata failure');
            END
            """.trimIndent()
        )
        var now = 500L
        val repository = OfflineFirstCountryRepository(
            object : CountriesApi {
                override suspend fun getCountries() = listOf(
                    dto("FR", "France", "Paris"),
                    dto("UA", "Ukraine", "Kyiv"),
                )
            },
            CountryCatalogLocalDataSource(database),
            object : TimeProvider {
                override fun currentTimeMillis(): Long = now
            }
        )
        assertEquals(
            CountryRefreshResult.Failure(CountryRefreshFailure.STORAGE),
            repository.refreshCountries()
        )
        assertEquals(listOf(original), dao.getCountries())
        assertEquals(
            previousSyncTime,
            database.catalogSyncMetadataDao().getMetadata()?.lastSuccessfulSyncAtEpochMillis
        )

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_sync_time")
        now = 600L
        withTimeout(5_000.milliseconds) {
            assertEquals(CountryRefreshResult.Success(2, 0), repository.refreshCountries())
        }
        assertEquals(
            listOf(entity("FR", "France", "Paris"), entity("UA", "Ukraine", "Kyiv")),
            dao.getCountries()
        )
        assertEquals(600L, database.catalogSyncMetadataDao().getMetadata()?.lastSuccessfulSyncAtEpochMillis)
    }

    private fun dto(code: String, name: String, capital: String) = CountryDto(
        alpha2Code = code, name = name, capital = capital, region = "Europe",
        languages = listOf(LanguageDto("Test language")),
        flags = FlagsDto(
            png = "https://flagcdn.com/w320/${code.lowercase()}.png",
            svg = "https://flagcdn.com/${code.lowercase()}.svg"
        ),
    )

    private fun entity(code: String, name: String, capital: String) = CountryEntity(
        code = code, name = name, capital = capital, region = "Europe",
        languages = listOf("Test language"),
        flagSvgUrl = "https://flagcdn.com/${code.lowercase()}.svg",
        flagPngUrl = "https://flagcdn.com/w320/${code.lowercase()}.png"
    )
}
