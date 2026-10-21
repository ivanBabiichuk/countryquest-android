package io.github.ivanbabiichuk.countryquest.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import io.github.ivanbabiichuk.countryquest.data.local.CountryCatalogLocalDataSource
import io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase
import io.github.ivanbabiichuk.countryquest.data.local.entity.CatalogSyncMetadataEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.data.remote.CountriesApi
import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.FlagsDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.LanguageDto
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class OfflineFirstCountryRepositoryTest {
    private val databases = mutableListOf<CountryQuestDatabase>()

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    @Test
    fun refreshUpdatesExistingCountriesAndInsertsNewOnes() = runBlocking {
        val dao = createStore(listOf(entity(capital = "Old capital")))
        val api = FakeCountriesApi { listOf(dto(), dto("FR", "France", "Paris")) }
        val repository = repository(api, dao)

        assertEquals(CountryRefreshResult.Success(2, 0), repository.refreshCountries())
        assertEquals(listOf(entity("FR", "France", "Paris"), entity()), dao.getCountries())
        assertEquals(1, api.calls)
        assertEquals(200L, dao.metadataTime())
    }

    @Test
    fun mixedResponseSavesValidCountriesAndPreservesSkippedAndAbsentCountries() = runBlocking {
        val germany = entity("DE", "Germany", "Berlin")
        val moldova = entity("MD", "Moldova", "Chișinău")
        val poland = entity("PL", "Poland", "Warsaw")
        val dao = createStore(listOf(germany, moldova, poland))
        val france = dto("FR", "France", "Paris")
        val api = FakeCountriesApi {
            listOf(
                dto(), france, france,
                dto("DE", "Germany", "Berlin"),
                dto("DE", "Germany", "Bonn"),
                dto("MD", "   ", "Changed capital")
            )
        }
        val repository = repository(api, dao)

        assertEquals(CountryRefreshResult.Success(2, 4), repository.refreshCountries())
        assertEquals(
            listOf(germany, entity("FR", "France", "Paris"), moldova, poland, entity()),
            dao.getCountries()
        )
        assertEquals(200L, dao.metadataTime())
    }

    @Test
    fun identicalCountriesAfterNormalizationAreSavedOnce() = runBlocking {
        val dao = createStore()
        val api = FakeCountriesApi {
            listOf(dto(), dto().copy(alpha2Code = " ua ", name = " Ukraine ", capital = " Kyiv "), dto())
        }
        val repository = repository(api, dao)

        assertEquals(CountryRefreshResult.Success(1, 2), repository.refreshCountries())
        assertEquals(listOf(entity()), dao.getCountries())
    }

    @Test
    fun conflictingGroupIsSkippedRegardlessOfOrderOrDuplicateMajority() = runBlocking {
        val a = dto("DE", "Germany", "Berlin")
        val b = dto("DE", "Germany", "Bonn")
        for (group in listOf(listOf(a, a, b), listOf(b, a, a))) {
            val existing = entity("DE", "Germany", "Stored capital")
            val dao = createStore(listOf(existing))
            val repository = repository(FakeCountriesApi { group + dto() }, dao)

            assertEquals(CountryRefreshResult.Success(1, 3), repository.refreshCountries())
            assertEquals(listOf(existing, entity()), dao.getCountries())
        }
    }

    @Test
    fun differentFlagUrlsAlsoMakeRecordsConflicting() = runBlocking {
        val existing = entity()
        val dao = createStore(listOf(existing))
        val api = FakeCountriesApi {
            listOf(dto(), dto().copy(flags = FlagsDto(svg = "https://example.com/other.svg")))
        }

        assertRejectedWithoutWrite(api, dao, listOf(existing), CountryRefreshFailure.INVALID_DATA)
    }

    @Test
    fun emptyResponseLeavesCachedCountriesUnchanged() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { emptyList() }, createStore(existing), existing,
            CountryRefreshFailure.INVALID_DATA
        )
    }

    @Test
    fun entirelyInvalidResponseLeavesCachedCountriesUnchanged() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { listOf(dto().copy(alpha2Code = "U1"), dto().copy(name = " ")) },
            createStore(existing), existing, CountryRefreshFailure.INVALID_DATA
        )
    }

    @Test
    fun missingOptionalFieldsDoNotRejectACountry() = runBlocking {
        val dao = createStore()
        val api = FakeCountriesApi { listOf(CountryDto(alpha2Code = "UA", name = "Ukraine")) }
        val repository = repository(api, dao)

        assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        assertEquals(
            listOf(CountryEntity("UA", "Ukraine", null, "", emptyList(), null, null)),
            dao.getCountries()
        )
    }

    @Test
    fun localReadsMapAllFieldsWithoutRequestingApi() = runBlocking {
        val stored = entity("MD", "Moldova", "Chișinău").copy(languages = listOf("Română"))
        val dao = createStore(listOf(stored))
        val api = FakeCountriesApi { error("Local reads must not use API") }
        val repository = repository(api, dao)
        val expected = listOf(
            Country(
                code = "MD", name = "Moldova", capital = "Chișinău", region = "Europe",
                languages = listOf("Română"),
                flag = CountryFlag("https://flagcdn.com/md.svg", "https://flagcdn.com/w320/md.png")
            )
        )

        assertEquals(expected, repository.getCountries())
        assertEquals(expected, repository.observeCountries().first())
        assertEquals(0, api.calls)
    }

    @Test
    fun existingCollectorObservesCountriesAfterRefresh() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val dao = createStore()
            val repository = repository(FakeCountriesApi { listOf(dto()) }, dao)
            val emissions = Channel<List<Country>>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.observeCountries().collect { emissions.send(it) }
            }
            try {
                assertTrue(emissions.receive().isEmpty())
                assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
                val updated = emissions.receive().single()
                assertEquals("UA", updated.code)
                assertEquals("Kyiv", updated.capital)
                assertEquals("https://flagcdn.com/ua.svg", updated.flag.svgUrl)
                assertEquals("https://flagcdn.com/w320/ua.png", updated.flag.pngUrl)
            } finally {
                collector.cancelAndJoin()
                emissions.close()
            }
        }
    }

    @Test
    fun networkFailurePreservesCacheAndNextRefreshCanSucceed() = runBlocking {
        val existing = listOf(entity(capital = "Stored capital"))
        val dao = createStore(existing)
        val api = FakeCountriesApi { throw IOException("Offline") }
        val repository = repository(api, dao)

        assertEquals(CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK), repository.refreshCountries())
        assertEquals(existing, dao.getCountries())
        assertEquals(100L, dao.metadataTime())

        api.response = { listOf(dto()) }
        withTimeout(5_000.milliseconds) {
            assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        }
        assertEquals(listOf(entity()), dao.getCountries())
        assertEquals(200L, dao.metadataTime())
    }

    @Test
    fun HTTPErrorPreservesCache() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { throw HttpException(Response.error<List<CountryDto>>(503, "Unavailable".toResponseBody())) },
            createStore(existing), existing, CountryRefreshFailure.SERVER
        )
    }

    @Test
    fun decodingErrorPreservesCache() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { throw SerializationException("Invalid response") },
            createStore(existing), existing, CountryRefreshFailure.INVALID_DATA
        )
    }

    @Test
    fun cancellingRequestPropagatesCancellationAndReleasesRefreshLock() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val dao = createStore(listOf(entity()))
            val started = CompletableDeferred<Unit>()
            val api = FakeCountriesApi {
                started.complete(Unit)
                awaitCancellation()
            }
            val repository = repository(api, dao)
            val request = async { repository.refreshCountries() }
            started.await()
            request.cancelAndJoin()
            try {
                request.await()
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                // Expected: cancellation is not converted to Failure.
            }

            assertEquals(listOf(entity()), dao.getCountries())
            assertEquals(100L, dao.metadataTime())

            api.response = { listOf(dto()) }
            assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        }
    }

    @Test
    fun cancellationBeforeStoragePropagatesAndPreservesCatalogAndTimestamp() = runBlocking {
        val dao = createStore(listOf(entity(capital = "Stored capital")))
        val cancellation = CancellationException("Cancelled before storage")
        var cancel = true
        val repository = repository(FakeCountriesApi { listOf(dto()) }, dao, object : TimeProvider {
            override fun currentTimeMillis(): Long {
                if (cancel) throw cancellation
                return 200L
            }
        })
        try {
            repository.refreshCountries()
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals("Stored capital", dao.getCountries().single().capital)
        assertEquals(100L, dao.metadataTime())
        cancel = false
        withTimeout(5_000.milliseconds) {
            assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        }
        assertEquals(200L, dao.metadataTime())
    }

    @Test
    fun unexpectedProgrammingErrorPropagates() = runBlocking {
        val bug = IllegalStateException("Programming error")
        val dao = createStore()
        val repository = repository(FakeCountriesApi { throw bug }, dao)

        try {
            repository.refreshCountries()
            fail("Programming errors must not be hidden")
        } catch (actual: IllegalStateException) {
            assertSame(bug, actual)
        }
        assertTrue(dao.getCountries().isEmpty())
        assertEquals(100L, dao.metadataTime())
    }

    @Test
    fun secondRefreshWaitsUntilFirstRefreshCompletes() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val dao = createStore()
            val enteredRequest = CompletableDeferred<Unit>()
            val releaseRequest = CompletableDeferred<Unit>()
            var version = 0
            val api = FakeCountriesApi {
                version++
                if (version == 1) {
                    enteredRequest.complete(Unit)
                    releaseRequest.await()
                }
                listOf(dto(capital = "Version $version"))
            }
            var clock = 199L
            val repository = repository(api, dao, object : TimeProvider {
                override fun currentTimeMillis(): Long = ++clock
            })
            val first = async { repository.refreshCountries() }
            enteredRequest.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.refreshCountries() }
            assertEquals(1, api.calls)
            assertEquals(100L, dao.metadataTime())
            releaseRequest.complete(Unit)
            assertEquals(CountryRefreshResult.Success(1, 0), first.await())
            assertEquals(CountryRefreshResult.Success(1, 0), second.await())
            assertEquals(2, api.calls)
            assertEquals("Version 2", dao.getCountries().single().capital)
            assertEquals(201L, dao.metadataTime())
        }
    }

    @Test
    fun syncTimeIsReadAfterResponseValidationAndAdvancesOnNextSuccess() = runBlocking {
        val dao = createStore()
        var now = 100L
        val api = FakeCountriesApi {
            now += 50
            listOf(dto())
        }
        val repository = repository(api, dao, object : TimeProvider {
            override fun currentTimeMillis(): Long = now
        })
        assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        assertEquals(150L, dao.metadataTime())
        assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        assertEquals(200L, dao.metadataTime())
    }

    private suspend fun assertRejectedWithoutWrite(
        api: FakeCountriesApi,
        dao: Store,
        expected: List<CountryEntity>,
        reason: CountryRefreshFailure,
    ) {
        val repository = repository(api, dao)
        assertEquals(CountryRefreshResult.Failure(reason), repository.refreshCountries())
        assertEquals(expected, dao.getCountries())
        assertEquals(100L, dao.metadataTime())
    }

    private fun dto(code: String = "UA", name: String = "Ukraine", capital: String = "Kyiv") = CountryDto(
        alpha2Code = code, name = name, capital = capital, region = "Europe",
        languages = listOf(LanguageDto("Test language")),
        flags = FlagsDto(
            png = "https://flagcdn.com/w320/${code.lowercase()}.png",
            svg = "https://flagcdn.com/${code.lowercase()}.svg"
        )
    )

    private fun entity(code: String = "UA", name: String = "Ukraine", capital: String = "Kyiv") = CountryEntity(
        code = code, name = name, capital = capital, region = "Europe",
        languages = listOf("Test language"),
        flagSvgUrl = "https://flagcdn.com/${code.lowercase()}.svg",
        flagPngUrl = "https://flagcdn.com/w320/${code.lowercase()}.png"
    )

    private class FakeCountriesApi(var response: suspend () -> List<CountryDto>) : CountriesApi {
        var calls = 0
            private set

        override suspend fun getCountries(): List<CountryDto> {
            calls++
            return response()
        }
    }

    private suspend fun createStore(initial: List<CountryEntity> = emptyList()): Store {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            CountryQuestDatabase::class.java,
        ).build()
        databases += database
        database.countryDao().upsertCountries(initial)
        database.catalogSyncMetadataDao().upsertMetadata(
            CatalogSyncMetadataEntity(lastSuccessfulSyncAtEpochMillis = 100L)
        )
        return Store(database)
    }

    private fun repository(
        api: CountriesApi,
        store: Store,
        clock: TimeProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = 200L
        }
    ) = OfflineFirstCountryRepository(api, CountryCatalogLocalDataSource(store.database), clock)

    private class Store(val database: CountryQuestDatabase) {
        fun observeCountries(): Flow<List<CountryEntity>> = database.countryDao().observeCountries()
        suspend fun getCountries(): List<CountryEntity> = database.countryDao().getCountries()
        suspend fun metadataTime(): Long? =
            database.catalogSyncMetadataDao().getMetadata()?.lastSuccessfulSyncAtEpochMillis
    }
}
