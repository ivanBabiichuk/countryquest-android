package io.github.ivanbabiichuk.countryquest.data.repository

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
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
