package io.github.ivanbabiichuk.countryquest.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ivanbabiichuk.countryquest.app.di.NetworkModule
import io.github.ivanbabiichuk.countryquest.app.sync.CatalogFreshnessPolicy
import io.github.ivanbabiichuk.countryquest.app.sync.CountrySyncCoordinator
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import io.github.ivanbabiichuk.countryquest.data.local.CountryCatalogLocalDataSource
import io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase
import io.github.ivanbabiichuk.countryquest.data.local.entity.CatalogSyncMetadataEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.data.local.seed.CountrySeedDataSource
import io.github.ivanbabiichuk.countryquest.data.local.seed.SeedCatalogParser
import io.github.ivanbabiichuk.countryquest.data.remote.CountriesApi
import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import io.github.ivanbabiichuk.countryquest.domain.model.GameAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class CountryCatalogInitializationTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CountryQuestDatabase
    private lateinit var local: CountryCatalogLocalDataSource
    private val clock = object : TimeProvider {
        override fun currentTimeMillis(): Long = 1_000_000_000L
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, CountryQuestDatabase::class.java).build()
        local = CountryCatalogLocalDataSource(database)
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun emptyDatabaseReceivesSeedWithoutInventingSyncTimeOrCallingApi() = runBlocking {
        val repository = repository()
        assertTrue(repository.initializeCatalog().isSuccess)
        assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
        assertEquals("Kyiv", repository.getCountries().single { it.code == "UA" }.capital)
        assertTrue(repository.initializeCatalog().isSuccess)
        assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
    }

    @Test
    fun existingCatalogSkipsAssetReadsAndPreservesDataAndTimestamp() = runBlocking {
        val original = entity("UA", "Stored capital")
        local.saveSyncedCountries(listOf(original), 123L)
        val forbiddenAssets = object : ContextWrapper(context) {
            override fun getAssets(): AssetManager = error("Existing catalog must skip seed assets")
        }
        assertTrue(repository(seedContext = forbiddenAssets).initializeCatalog().isSuccess)
        assertEquals(listOf(original), database.countryDao().getCountries())
        assertEquals(CountryCatalogStatus(1, 123L), local.getCatalogStatus())
        assertFalse(local.seedCountriesIfEmpty(listOf(entity("FR"))))
        assertEquals(listOf(original), database.countryDao().getCountries())
        assertEquals(CountryCatalogStatus(1, 123L), local.getCatalogStatus())
    }

    @Test
    fun concurrentSeedTransactionsPopulateOnlyOnceAndClearOrphanedMetadata() = runBlocking {
        withTimeout(5_000.milliseconds) {
            oldMetadata()
            val results = awaitAll(
                async { local.seedCountriesIfEmpty(listOf(entity("UA"))) },
                async { local.seedCountriesIfEmpty(listOf(entity("FR"))) }
            )
            assertEquals(1, results.count { it })
            val stored = database.countryDao().getCountries()
            assertTrue(stored == listOf(entity("UA")) || stored == listOf(entity("FR")))
            assertEquals(CountryCatalogStatus(1, null), local.getCatalogStatus())
        }
    }

    @Test
    fun countryInsertFailureRollsBackWholeSeedAndRetrySucceeds() = runBlocking {
        oldMetadata()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_seed BEFORE INSERT ON countries
            WHEN NEW.code = 'FR'
            BEGIN SELECT RAISE(ABORT, 'forced seed failure'); END
        """.trimIndent())
        try {
            local.seedCountriesIfEmpty(listOf(entity("UA"), entity("FR")))
            fail("Expected storage failure")
        } catch (_: SQLiteException) { }
        assertEquals(CountryCatalogStatus(0, 123L), local.getCatalogStatus())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_seed")
        assertTrue(local.seedCountriesIfEmpty(listOf(entity("UA"), entity("FR"))))
        assertEquals(CountryCatalogStatus(2, null), local.getCatalogStatus())
    }

    @Test
    fun metadataCleanupFailureRollsBackAlreadyInsertedCountries() = runBlocking {
        oldMetadata()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_seed_cleanup BEFORE DELETE ON catalog_sync_metadata
            BEGIN SELECT RAISE(ABORT, 'forced metadata cleanup failure'); END
        """.trimIndent())
        val repository = repository()
        val result = repository.initializeCatalog()
        assertTrue(result.exceptionOrNull() is SQLiteException)
        assertEquals(CountryCatalogStatus(0, 123L), local.getCatalogStatus())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_seed_cleanup")
        assertTrue(repository.initializeCatalog().isSuccess)
        assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
    }

    @Test
    fun seedingAndRepeatedInitializationPreserveSavedGameSnapshots() = runBlocking {
        val history = RoomGameHistoryRepository(database.gameHistoryDao())
        val saved = GameResult("old-game", QuizMode.CAPITAL, 100L, listOf(
            GameAnswer("KZ", "Kazakhstan", "KZ", "Nur-Sultan", "KZ", "Nur-Sultan")
        ))
        history.saveCompletedGame(saved)
        val repository = repository()
        assertTrue(repository.initializeCatalog().isSuccess)
        assertEquals(saved, history.getResult(saved.id))
        assertTrue(repository.initializeCatalog().isSuccess)
        assertEquals(listOf(saved), history.observeResults().first())
    }

    @Test
    fun seedIsVisibleWhileStartupApiRequestIsPendingAndSurvivesNetworkFailure() = runBlocking {
        withTimeout(10_000.milliseconds) {
            val enteredApi = CompletableDeferred<Unit>()
            val finishApi = CompletableDeferred<Unit>()
            val repository = repository(object : CountriesApi {
                override suspend fun getCountries(): List<CountryDto> {
                    assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
                    enteredApi.complete(Unit)
                    finishApi.await()
                    throw IOException("Offline")
                }
            })
            val coordinator = CountrySyncCoordinator(repository, clock, CatalogFreshnessPolicy())
            val initialization = async { coordinator.initialize() }
            enteredApi.await()
            val visible = repository.observeCatalogStatus().first()
            assertEquals(CountryCatalogStatus(250, null), visible)
            assertTrue(coordinator.state.value.isRefreshing)
            finishApi.complete(Unit)
            initialization.await()
            assertEquals(
                CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK),
                coordinator.state.value.lastResult
            )
            assertFalse(coordinator.state.value.isRefreshing)
            assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
        }
    }

    @Test
    fun successfulStartupApiUpdateRunsAfterSeedAndPersistsRealSyncTime() = runBlocking {
        var calls = 0
        val repository = repository(object : CountriesApi {
            override suspend fun getCountries(): List<CountryDto> {
                calls++
                assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
                return listOf(CountryDto("UA", "Ukraine", capital = "API capital"))
            }
        })
        val coordinator = CountrySyncCoordinator(repository, clock, CatalogFreshnessPolicy())
        coordinator.initialize()
        coordinator.initialize()
        assertEquals(1, calls)
        assertEquals(CountryRefreshResult.Success(1, 0), coordinator.state.value.lastResult)
        assertEquals(CountryCatalogStatus(250, clock.currentTimeMillis()), local.getCatalogStatus())
        assertEquals("API capital", repository.getCountries().single { it.code == "UA" }.capital)
    }

    @Test
    fun brokenSeedAssetsFallBackToApiWithoutPartialCatalog() = runBlocking {
        val problem = IOException("Unavailable seed assets")
        val broken = object : ContextWrapper(context) {
            override fun getAssets(): AssetManager = throw problem
        }
        var calls = 0
        val repository = repository(object : CountriesApi {
            override suspend fun getCountries(): List<CountryDto> {
                calls++
                assertEquals(CountryCatalogStatus(0, null), local.getCatalogStatus())
                return listOf(CountryDto("UA", "Ukraine", capital = "Kyiv"))
            }
        }, broken)
        assertSame(problem, repository.initializeCatalog().exceptionOrNull())
        CountrySyncCoordinator(repository, clock, CatalogFreshnessPolicy()).initialize()
        assertEquals(1, calls)
        assertEquals(CountryCatalogStatus(1, clock.currentTimeMillis()), local.getCatalogStatus())
    }

    @Test
    fun cancellationDuringSeedReadPropagatesAndReleasesRepositoryLock() = runBlocking {
        val cancellation = CancellationException("Cancelled seed")
        var cancel = true
        val seedContext = object : ContextWrapper(context) {
            override fun getAssets(): AssetManager {
                if (cancel) throw cancellation
                return super.getAssets()
            }
        }
        val repository = repository(seedContext = seedContext)
        try {
            repository.initializeCatalog()
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(CountryCatalogStatus(0, null), local.getCatalogStatus())
        cancel = false
        withTimeout(10_000.milliseconds) { assertTrue(repository.initializeCatalog().isSuccess) }
        assertEquals(CountryCatalogStatus(250, null), local.getCatalogStatus())
    }

    @Test
    fun initializationWaitsForRefreshThenKeepsItsCatalogWithoutReadingAssets() = runBlocking {
        withTimeout(10_000.milliseconds) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val forbiddenAssets = object : ContextWrapper(context) {
                override fun getAssets(): AssetManager = error("API catalog must not be overwritten with seed")
            }
            val repository = repository(object : CountriesApi {
                override suspend fun getCountries(): List<CountryDto> {
                    entered.complete(Unit)
                    release.await()
                    return listOf(CountryDto("UA", "API country", capital = "API capital"))
                }
            }, forbiddenAssets)
            val refresh = async { repository.refreshCountries() }
            entered.await()
            val initialization = async(start = CoroutineStart.UNDISPATCHED) { repository.initializeCatalog() }
            assertFalse(initialization.isCompleted)
            release.complete(Unit)
            assertEquals(CountryRefreshResult.Success(1, 0), refresh.await())
            assertTrue(initialization.await().isSuccess)
            assertEquals(1, database.countryDao().getCountriesCount())
            assertEquals("API country", repository.getCountries().single().name)
        }
    }

    private suspend fun oldMetadata() = database.catalogSyncMetadataDao().upsertMetadata(
        CatalogSyncMetadataEntity(lastSuccessfulSyncAtEpochMillis = 123L)
    )

    private fun repository(
        api: CountriesApi = object : CountriesApi {
            override suspend fun getCountries(): List<CountryDto> = error("Initialization must not request API")
        },
        seedContext: Context = context
    ) = OfflineFirstCountryRepository(
        api, local, clock,
        CountrySeedDataSource(seedContext, SeedCatalogParser(NetworkModule.provideJson()))
    )

    private fun entity(code: String, capital: String = "Capital $code") = CountryEntity(
        code, "Country $code", capital, "Region", listOf("Language"), null, null
    )
}
