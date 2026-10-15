package io.github.ivanbabiichuk.countryquest.data.repository

import io.github.ivanbabiichuk.countryquest.data.local.dao.CountryDao
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
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class OfflineFirstCountryRepositoryTest {

    @Test
    fun `refresh updates existing countries and inserts new ones`() = runBlocking {
        val dao = FakeCountryDao(listOf(entity(capital = "Old capital")))
        val api = FakeCountriesApi { listOf(dto(), dto("FR", "France", "Paris")) }
        val repository = OfflineFirstCountryRepository(api, dao)

        assertEquals(CountryRefreshResult.Success(2, 0), repository.refreshCountries())
        assertEquals(listOf(entity("FR", "France", "Paris"), entity()), dao.getCountries())
        assertEquals(1, dao.upsertCalls)
        assertEquals(1, api.calls)
    }

    @Test
    fun `mixed response saves valid countries and preserves skipped and absent countries`() = runBlocking {
        val germany = entity("DE", "Germany", "Berlin")
        val moldova = entity("MD", "Moldova", "Chișinău")
        val poland = entity("PL", "Poland", "Warsaw")
        val dao = FakeCountryDao(listOf(germany, moldova, poland))
        val france = dto("FR", "France", "Paris")
        val api = FakeCountriesApi {
            listOf(
                dto(), france, france,
                dto("DE", "Germany", "Berlin"),
                dto("DE", "Germany", "Bonn"),
                dto("MD", "   ", "Changed capital"),
            )
        }
        val repository = OfflineFirstCountryRepository(api, dao)

        assertEquals(CountryRefreshResult.Success(2, 4), repository.refreshCountries())
        assertEquals(
            listOf(germany, entity("FR", "France", "Paris"), moldova, poland, entity()),
            dao.getCountries(),
        )
        assertEquals(1, dao.upsertCalls)
    }

    @Test
    fun `identical countries after normalization are saved once`() = runBlocking {
        val dao = FakeCountryDao()
        val api = FakeCountriesApi {
            listOf(dto(), dto().copy(alpha2Code = " ua ", name = " Ukraine ", capital = " Kyiv "), dto())
        }
        val repository = OfflineFirstCountryRepository(api, dao)

        assertEquals(CountryRefreshResult.Success(1, 2), repository.refreshCountries())
        assertEquals(listOf(entity()), dao.getCountries())
    }

    @Test
    fun `conflicting group is skipped regardless of order or duplicate majority`() = runBlocking {
        val a = dto("DE", "Germany", "Berlin")
        val b = dto("DE", "Germany", "Bonn")
        for (group in listOf(listOf(a, a, b), listOf(b, a, a))) {
            val existing = entity("DE", "Germany", "Stored capital")
            val dao = FakeCountryDao(listOf(existing))
            val repository = OfflineFirstCountryRepository(FakeCountriesApi { group + dto() }, dao)

            assertEquals(CountryRefreshResult.Success(1, 3), repository.refreshCountries())
            assertEquals(listOf(existing, entity()), dao.getCountries())
        }
    }

    @Test
    fun `different flag URLs also make records conflicting`() = runBlocking {
        val existing = entity()
        val dao = FakeCountryDao(listOf(existing))
        val api = FakeCountriesApi {
            listOf(dto(), dto().copy(flags = FlagsDto(svg = "https://example.com/other.svg")))
        }

        assertRejectedWithoutWrite(api, dao, listOf(existing), CountryRefreshFailure.INVALID_DATA)
    }

    @Test
    fun `empty response leaves cached countries unchanged`() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { emptyList() }, FakeCountryDao(existing), existing,
            CountryRefreshFailure.INVALID_DATA,
        )
    }

    @Test
    fun `entirely invalid response leaves cached countries unchanged`() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { listOf(dto().copy(alpha2Code = "U1"), dto().copy(name = " ")) },
            FakeCountryDao(existing), existing, CountryRefreshFailure.INVALID_DATA,
        )
    }

    @Test
    fun `missing optional fields do not reject a country`() = runBlocking {
        val dao = FakeCountryDao()
        val api = FakeCountriesApi { listOf(CountryDto(alpha2Code = "UA", name = "Ukraine")) }
        val repository = OfflineFirstCountryRepository(api, dao)

        assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        assertEquals(
            listOf(CountryEntity("UA", "Ukraine", null, "", emptyList(), null, null)),
            dao.getCountries(),
        )
    }

    @Test
    fun `local reads map all fields without requesting API`() = runBlocking {
        val stored = entity("MD", "Moldova", "Chișinău").copy(languages = listOf("Română"))
        val dao = FakeCountryDao(listOf(stored))
        val api = FakeCountriesApi { error("Local reads must not use API") }
        val repository = OfflineFirstCountryRepository(api, dao)
        val expected = listOf(
            Country(
                code = "MD", name = "Moldova", capital = "Chișinău", region = "Europe",
                languages = listOf("Română"),
                flag = CountryFlag("https://flagcdn.com/md.svg", "https://flagcdn.com/w320/md.png"),
            ),
        )

        assertEquals(expected, repository.getCountries())
        assertEquals(expected, repository.observeCountries().first())
        assertEquals(0, api.calls)
    }

    @Test
    fun `existing collector observes countries after refresh`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val dao = FakeCountryDao()
            val repository = OfflineFirstCountryRepository(FakeCountriesApi { listOf(dto()) }, dao)
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
    fun `network failure preserves cache and next refresh can succeed`() = runBlocking {
        val existing = listOf(entity(capital = "Stored capital"))
        val dao = FakeCountryDao(existing)
        val api = FakeCountriesApi { throw IOException("Offline") }
        val repository = OfflineFirstCountryRepository(api, dao)

        assertEquals(CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK), repository.refreshCountries())
        assertEquals(existing, dao.getCountries())
        assertEquals(0, dao.upsertCalls)

        api.response = { listOf(dto()) }
        withTimeout(5_000.milliseconds) {
            assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        }
        assertEquals(listOf(entity()), dao.getCountries())
    }

    @Test
    fun `HTTP error preserves cache`() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { throw HttpException(Response.error<List<CountryDto>>(503, "Unavailable".toResponseBody())) },
            FakeCountryDao(existing), existing, CountryRefreshFailure.SERVER,
        )
    }

    @Test
    fun `decoding error preserves cache`() = runBlocking {
        val existing = listOf(entity())
        assertRejectedWithoutWrite(
            FakeCountriesApi { throw SerializationException("Invalid response") },
            FakeCountryDao(existing), existing, CountryRefreshFailure.INVALID_DATA,
        )
    }

    @Test
    fun `cancelling request propagates cancellation and releases refresh lock`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val dao = FakeCountryDao(listOf(entity()))
            val started = CompletableDeferred<Unit>()
            val api = FakeCountriesApi {
                started.complete(Unit)
                awaitCancellation()
            }
            val repository = OfflineFirstCountryRepository(api, dao)
            val request = async { repository.refreshCountries() }
            started.await()
            request.cancelAndJoin()
            try {
                request.await()
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                // Expected: cancellation is not converted to Failure.
            }
            assertEquals(0, dao.upsertCalls)
            assertEquals(listOf(entity()), dao.getCountries())

            api.response = { listOf(dto()) }
            assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        }
    }

    @Test
    fun `cancellation during storage is not converted to storage failure`() = runBlocking {
        val dao = FakeCountryDao(listOf(entity()))
        val cancellation = CancellationException("Cancelled before write")
        dao.beforeUpsert = { throw cancellation }
        val repository = OfflineFirstCountryRepository(FakeCountriesApi { listOf(dto()) }, dao)

        try {
            repository.refreshCountries()
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf(entity()), dao.getCountries())
        dao.beforeUpsert = {}
        withTimeout(5_000.milliseconds) {
            assertEquals(CountryRefreshResult.Success(1, 0), repository.refreshCountries())
        }
    }

    @Test
    fun `unexpected programming error propagates`() = runBlocking {
        val bug = IllegalStateException("Programming error")
        val dao = FakeCountryDao()
        val repository = OfflineFirstCountryRepository(FakeCountriesApi { throw bug }, dao)

        try {
            repository.refreshCountries()
            fail("Programming errors must not be hidden")
        } catch (actual: IllegalStateException) {
            assertSame(bug, actual)
        }
        assertEquals(0, dao.upsertCalls)
    }

    @Test
    fun `second refresh waits until first refresh has finished writing`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val dao = FakeCountryDao()
            val enteredWrite = CompletableDeferred<Unit>()
            val releaseWrite = CompletableDeferred<Unit>()
            dao.beforeUpsert = {
                if (dao.upsertCalls == 1) {
                    enteredWrite.complete(Unit)
                    releaseWrite.await()
                }
            }
            var version = 0
            val api = FakeCountriesApi {
                version++
                listOf(dto(capital = "Version $version"))
            }
            val repository = OfflineFirstCountryRepository(api, dao)
            val first = async { repository.refreshCountries() }
            enteredWrite.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.refreshCountries() }

            assertEquals(1, api.calls)
            assertEquals(1, dao.upsertCalls)
            releaseWrite.complete(Unit)
            assertEquals(CountryRefreshResult.Success(1, 0), first.await())
            assertEquals(CountryRefreshResult.Success(1, 0), second.await())
            assertEquals(2, api.calls)
            assertEquals("Version 2", dao.getCountries().single().capital)
        }
    }

    private suspend fun assertRejectedWithoutWrite(
        api: FakeCountriesApi,
        dao: FakeCountryDao,
        expected: List<CountryEntity>,
        reason: CountryRefreshFailure,
    ) {
        val repository = OfflineFirstCountryRepository(api, dao)
        assertEquals(CountryRefreshResult.Failure(reason), repository.refreshCountries())
        assertEquals(expected, dao.getCountries())
        assertEquals(0, dao.upsertCalls)
    }

    private fun dto(code: String = "UA", name: String = "Ukraine", capital: String = "Kyiv") = CountryDto(
        alpha2Code = code, name = name, capital = capital, region = "Europe",
        languages = listOf(LanguageDto("Test language")),
        flags = FlagsDto(
            png = "https://flagcdn.com/w320/${code.lowercase()}.png",
            svg = "https://flagcdn.com/${code.lowercase()}.svg",
        ),
    )

    private fun entity(code: String = "UA", name: String = "Ukraine", capital: String = "Kyiv") = CountryEntity(
        code = code, name = name, capital = capital, region = "Europe",
        languages = listOf("Test language"),
        flagSvgUrl = "https://flagcdn.com/${code.lowercase()}.svg",
        flagPngUrl = "https://flagcdn.com/w320/${code.lowercase()}.png",
    )

    private class FakeCountriesApi(var response: suspend () -> List<CountryDto>) : CountriesApi {
        var calls = 0
            private set

        override suspend fun getCountries(): List<CountryDto> {
            calls++
            return response()
        }
    }

    // This fake tests repository decisions; actual SQLite behavior is tested on Android.
    private class FakeCountryDao(initial: List<CountryEntity> = emptyList()) : CountryDao {
        private val countries = MutableStateFlow(initial.sortedBy { it.code })
        var upsertCalls = 0
            private set
        var beforeUpsert: suspend () -> Unit = {}

        override fun observeCountries(): Flow<List<CountryEntity>> = countries
        override suspend fun getCountries(): List<CountryEntity> = countries.value

        override suspend fun upsertCountries(countries: List<CountryEntity>) {
            upsertCalls++
            beforeUpsert()
            val merged = this.countries.value.associateBy { it.code }.toMutableMap()
            countries.forEach { merged[it.code] = it }
            this.countries.value = merged.values.sortedBy { it.code }
        }
    }
}
