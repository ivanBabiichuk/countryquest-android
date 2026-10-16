package io.github.ivanbabiichuk.countryquest.data.remote

import io.github.ivanbabiichuk.countryquest.app.di.NetworkModule
import io.github.ivanbabiichuk.countryquest.data.mapper.toDomainOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class CountriesApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CountriesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = NetworkModule.provideRetrofit(
            client = NetworkModule.provideOkHttpClient(),
            json = NetworkModule.provideJson(),
        )
            .newBuilder()
            .baseUrl(server.url("/"))
            .build()

        api = retrofit.create(CountriesApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses countries and ignores additional fields`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "alpha2Code": "UA",
                        "name": "Ukraine",
                        "capital": "Kyiv",
                        "region": "Europe",
                        "languages": [
                          {"name": "Ukrainian", "iso639_1": "uk"}
                        ],
                        "flags": {
                          "png": "https://flagcdn.com/w320/ua.png",
                          "svg": "https://flagcdn.com/ua.svg"
                        },
                        "population": 100
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val countries = api.getCountries()
        val country = countries.single()

        assertEquals("UA", country.alpha2Code)
        assertEquals("Ukraine", country.name)
        assertEquals("Kyiv", country.capital)
        assertEquals("Ukrainian", country.languages?.single()?.name)
        assertEquals(
            "https://flagcdn.com/w320/ua.png",
            country.flags?.png,
        )

        assertEquals("https://flagcdn.com/ua.svg", country.flags?.svg)

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("GET", request?.method)
        assertEquals("/countries", request?.path)
    }

    @Test
    fun `accepts missing and null optional fields`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "alpha2Code": "XX",
                    "name": "Test country",
                    "capital": null
                  }
                ]
                """.trimIndent(),
            ),
        )

        val country = api.getCountries().single()

        assertNull(country.capital)
        assertNull(country.region)
        assertNull(country.languages)
        assertNull(country.flags)
    }

    @Test
    fun `server error is not treated as an empty catalog`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500),
        )

        try {
            api.getCountries()
            fail("Expected HTTP error")
        } catch (exception: HttpException) {
            assertEquals(500, exception.code())
        }
    }

    @Test
    fun `missing required field fails parsing`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [{"name": "Country without code"}]
                """.trimIndent(),
            ),
        )

        try {
            api.getCountries()
            fail("Expected serialization error")
        } catch (_: SerializationException) {
            // Очікувано: немає обов'язкового alpha2Code.
        }
    }

    @Test
    fun `UTF-8 response preserves accents through domain mapping`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "alpha2Code": "MD",
                      "name": "Moldova",
                      "capital": "Chișinău",
                      "flag": "🇲🇩",
                      "languages": [{"name": "Română"}]
                    }]
                    """.trimIndent(),
                ),
        )

        val country = requireNotNull(api.getCountries().single().toDomainOrNull())

        assertEquals("Chișinău", country.capital)
        assertEquals(listOf("Română"), country.languages)
    }
    @Test
    fun `parses flags with only SVG or only PNG`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"alpha2Code":"UA","name":"Ukraine",
                       "flags":{"svg":"https://flagcdn.com/ua.svg"}},
                      {"alpha2Code":"MD","name":"Moldova",
                       "flags":{"png":"https://flagcdn.com/w320/md.png"}}
                    ]
                    """.trimIndent(),
                ),
        )

        val countries = api.getCountries()

        assertEquals(2, countries.size)
        val svgOnly = countries[0]
        assertEquals("https://flagcdn.com/ua.svg", svgOnly.flags?.svg)
        assertNull(svgOnly.flags?.png)
        val svgCountry = requireNotNull(svgOnly.toDomainOrNull())
        assertEquals("https://flagcdn.com/ua.svg", svgCountry.flag.svgUrl)
        assertNull(svgCountry.flag.pngUrl)

        val pngOnly = countries[1]
        assertEquals("https://flagcdn.com/w320/md.png", pngOnly.flags?.png)
        assertNull(pngOnly.flags?.svg)
        val pngCountry = requireNotNull(pngOnly.toDomainOrNull())
        assertEquals("https://flagcdn.com/w320/md.png", pngCountry.flag.pngUrl)
        assertNull(pngCountry.flag.svgUrl)
    }

    @Test
    fun `parses null and empty flags without losing the country`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"alpha2Code":"UA","name":"Ukraine","flags":null},
                  {"alpha2Code":"MD","name":"Moldova","flags":{}},
                  {"alpha2Code":"PL","name":"Poland","flags":{"png":null,"svg":null}}
                ]
                """.trimIndent(),
            ),
        )

        val countries = api.getCountries()
        assertEquals(3, countries.size)
        assertNull(countries[0].flags)
        for (dto in countries) {
            assertNull(dto.flags?.png)
            assertNull(dto.flags?.svg)
            val country = requireNotNull(dto.toDomainOrNull())
            assertNull(country.flag.pngUrl)
            assertNull(country.flag.svgUrl)
        }
    }
}