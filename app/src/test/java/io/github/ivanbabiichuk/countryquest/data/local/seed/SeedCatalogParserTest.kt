package io.github.ivanbabiichuk.countryquest.data.local.seed

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SeedCatalogParserTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val parser = SeedCatalogParser(json)

    @Test
    fun `maps metadata country fields and local asset path`() {
        val result = parse(seed().copy(preparedAt = " 2026-10-21 ", countries = listOf(country().copy(
            name = " Ukraine ", capital = " Kyiv ", region = " Europe ",
            languages = listOf(" Ukrainian ", "ukrainian", "English"),
            flag = SeedFlagDto(" https://flagcdn.com/ua.svg ", " https://flagcdn.com/w320/ua.png ")
        ))))
        assertEquals(1, result.seedVersion)
        assertEquals("2026-10-21", result.preparedAt)
        assertEquals(mapOf("UA" to "flags/ua.svg"), result.bundledFlagAssets)
        assertEquals(
            listOf(Country("UA", "Ukraine", "Kyiv", "Europe", listOf("Ukrainian", "English"),
                CountryFlag("https://flagcdn.com/ua.svg", "https://flagcdn.com/w320/ua.png"))),
            result.countries
        )
    }

    @Test
    fun `missing capital and remote URLs do not invent values`() {
        for (capital in listOf(null, " ")) {
            val result = parse(seed().copy(countries = listOf(country().copy(
                capital = capital, flag = SeedFlagDto(svgUrl = " ", pngUrl = null)
            ))))
            assertNull(result.countries.single().capital)
            assertEquals(CountryFlag(null, null), result.countries.single().flag)
            assertEquals("flags/ua.svg", result.bundledFlagAssets["UA"])
        }
    }

    @Test
    fun `unsupported schema versions are rejected`() {
        for (version in listOf(-1, 0, 2)) rejected(seed().copy(schemaVersion = version))
    }

    @Test
    fun `nonpositive seed versions are rejected`() {
        for (version in listOf(-1, 0)) rejected(seed().copy(seedVersion = version))
    }

    @Test
    fun `blank preparation date is rejected`() {
        rejected(seed().copy(preparedAt = " "))
    }

    @Test
    fun `empty catalog is rejected`() {
        rejected(seed().copy(countries = emptyList()))
    }

    @Test
    fun `malformed JSON and wrong field types are rejected`() {
        for (content in listOf("not JSON", "{", "[]", "null", "{\"schemaVersion\":true}")) {
            assertThrows(SerializationException::class.java) { parser.parse(content) }
        }
    }

    @Test
    fun `missing required fields are rejected during decoding`() {
        val root = json.parseToJsonElement(json.encodeToString(seed())).jsonObject
        for (field in listOf("schemaVersion", "seedVersion", "preparedAt", "countries")) {
            assertThrows(SerializationException::class.java) {
                parser.parse(JsonObject(root - field).toString())
            }
        }
    }

    @Test
    fun `unknown fields are tolerated within supported schema`() {
        val content = json.encodeToString(seed()).replaceFirst("{", "{\"extraMetadata\":true,")
        assertEquals(parse(), parser.parse(content))
    }

    @Test
    fun `invalid country codes are rejected`() {
        for (code in listOf("", "ua", " UA", "U", "UKR", "U1", "УА")) {
            rejected(seed().copy(countries = listOf(country().copy(code = code))))
        }
    }

    @Test
    fun `blank names and regions are rejected`() {
        rejected(seed().copy(countries = listOf(country().copy(name = " "))))
        rejected(seed().copy(countries = listOf(country().copy(region = "\t"))))
    }

    @Test
    fun `empty or partly blank language lists are rejected`() {
        for (languages in listOf(emptyList(), listOf(" "), listOf("Ukrainian", " "))) {
            rejected(seed().copy(countries = listOf(country().copy(languages = languages))))
        }
    }

    @Test
    fun `duplicate codes are rejected even when records are identical`() {
        rejected(seed().copy(countries = listOf(country(), country())))
    }

    @Test
    fun `duplicate display names are rejected after trimming and case normalization`() {
        rejected(seed().copy(countries = listOf(country(), country("FR").copy(name = " ukraine "))))
    }

    @Test
    fun `local asset must match country code and cannot escape flags directory`() {
        for (path in listOf("", "flags/fr.svg", "flags/UA.svg", "../flags/ua.svg", "https://example.com/ua.svg")) {
            rejected(seed().copy(countries = listOf(country().copy(bundledFlagAsset = path))))
        }
    }

    @Test
    fun `invalid remote URLs reject seed for either image format`() {
        for (url in listOf("not a URL", "/ua.svg", "http://flagcdn.com/ua.svg",
            "file:///ua.svg", "https://user:password@example.com/ua.svg")) {
            rejected(seed().copy(countries = listOf(country().copy(flag = SeedFlagDto(svgUrl = url)))))
            rejected(seed().copy(countries = listOf(country().copy(flag = SeedFlagDto(pngUrl = url)))))
        }
    }

    @Test
    fun `shared corrections apply to seed as well as API mapping`() {
        val countries = listOf(
            country("KZ").copy(name = "Kazakhstan", capital = "Nur-Sultan"),
            country("SZ").copy(name = "Swaziland", capital = "Mbabane"),
            country("CW").copy(name = "Curaçao", capital = "Willemstad",
                languages = listOf("Dutch", "(Eastern) Punjabi", "English")),
            country("GQ").copy(name = "Equatorial Guinea", capital = "Malabo")
        )
        val mapped = parse(seed().copy(countries = countries)).countries.associateBy { it.code }
        assertEquals("Astana", mapped.getValue("KZ").capital)
        assertEquals("Eswatini", mapped.getValue("SZ").name)
        assertEquals(listOf("Dutch", "Papiamentu", "English"), mapped.getValue("CW").languages)
        assertEquals("Ciudad de la Paz", mapped.getValue("GQ").capital)
    }

    @Test
    fun `name collisions created by corrections are rejected`() {
        rejected(seed().copy(countries = listOf(
            country("SZ").copy(name = "Swaziland"), country("AA").copy(name = "Eswatini")
        )))
    }

    @Test
    fun `one invalid country prevents a partial seed result`() {
        rejected(seed().copy(countries = listOf(country(), country("FR").copy(region = " "))))
    }

    private fun parse(dto: SeedCatalogDto = seed()): SeedCatalog = parser.parse(json.encodeToString(dto))
    private fun rejected(dto: SeedCatalogDto) {
        assertThrows(IllegalArgumentException::class.java) { parse(dto) }
    }
    private fun seed() = SeedCatalogDto(1, 1, "2026-10-21", listOf(country()))
    private fun country(code: String = "UA") = SeedCountryDto(
        code = code, name = if (code == "UA") "Ukraine" else "Country $code", capital = "Kyiv",
        region = "Europe", languages = listOf("Ukrainian"),
        flag = SeedFlagDto("https://flagcdn.com/${code.lowercase()}.svg", "https://flagcdn.com/w320/${code.lowercase()}.png"),
        bundledFlagAsset = "flags/${code.lowercase()}.svg"
    )
}
