package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.FlagsDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.LanguageDto
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizGenerationResult
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryMapperTest {

    @Test
    fun `maps and trims fields without changing display names`() {
        val dto = countryDto().copy(
            alpha2Code = " ua ",
            name = " Ukraine ",
            capital = " Kyiv ",
            region = " Europe ",
            languages = listOf(LanguageDto(" Ukrainian ")),
            flags = FlagsDto(
                png = " https://flagcdn.com/w320/ua.png ",
                svg = " https://flagcdn.com/ua.svg ",
            ),
        )

        assertEquals(
            Country(
                code = "UA",
                name = "Ukraine",
                capital = "Kyiv",
                region = "Europe",
                languages = listOf("Ukrainian"),
                flag = CountryFlag(
                    svgUrl = "https://flagcdn.com/ua.svg",
                    pngUrl = "https://flagcdn.com/w320/ua.png",
                ),
            ),
            dto.toDomainOrNull(),
        )
    }

    @Test
    fun `rejects malformed codes including non ASCII letters`() {
        for (code in listOf("", " ", "U", "UKR", "U1", "U A", "УА", "ß", "ıa")) {
            assertNull("Code: $code", countryDto().copy(alpha2Code = code).toDomainOrNull())
        }
    }

    @Test
    fun `rejects blank names`() {
        for (name in listOf("", " ", "\n\t")) {
            assertNull(countryDto().copy(name = name).toDomainOrNull())
        }
    }

    @Test
    fun `keeps countries with missing optional data`() {
        val country = CountryDto(alpha2Code = "UA", name = "Ukraine").toDomainOrNull()

        assertEquals(Country("UA", "Ukraine", null, "", emptyList(), CountryFlag(null, null)), country)
    }

    @Test
    fun `normalizes blank optional text and removes duplicate languages`() {
        val country = requireNotNull(countryDto().copy(
            capital = " ",
            region = "\t",
            languages = listOf(
                LanguageDto(null), LanguageDto(" "),
                LanguageDto(" Romanian "), LanguageDto("romanian"),
                LanguageDto("English"),
            ),
        ).toDomainOrNull())

        assertNull(country.capital)
        assertEquals("", country.region)
        assertEquals(listOf("Romanian", "English"), country.languages)
    }

    @Test
    fun `preserves Unicode accents and emoji`() {
        val country = requireNotNull(countryDto().copy(
            alpha2Code = "MD",
            name = "Moldova 🇲🇩",
            capital = "Chișinău",
            languages = listOf(LanguageDto("Română")),
        ).toDomainOrNull())

        assertEquals("Moldova 🇲🇩", country.name)
        assertEquals("Chișinău", country.capital)
        assertEquals(listOf("Română"), country.languages)
    }

    @Test
    fun `invalid PNG does not discard a valid SVG`() {
        for (url in invalidUrls()) {
            val country = requireNotNull(countryDto().copy(
                flags = FlagsDto(png = url, svg = "https://flagcdn.com/ua.svg"),
            ).toDomainOrNull())

            assertNull("PNG: $url", country.flag.pngUrl)
            assertEquals("https://flagcdn.com/ua.svg", country.flag.svgUrl)
        }
    }

    @Test
    fun `invalid SVG does not discard a valid PNG`() {
        for (url in invalidUrls()) {
            val country = requireNotNull(countryDto().copy(
                flags = FlagsDto(png = "https://flagcdn.com/w320/ua.png", svg = url),
            ).toDomainOrNull())

            assertNull("SVG: $url", country.flag.svgUrl)
            assertEquals("https://flagcdn.com/w320/ua.png", country.flag.pngUrl)
        }
    }

    @Test
    fun `invalid URLs in both formats do not discard the country`() {
        for (url in invalidUrls()) {
            val country = requireNotNull(countryDto().copy(
                flags = FlagsDto(png = url, svg = url),
            ).toDomainOrNull())

            assertEquals(CountryFlag(svgUrl = null, pngUrl = null), country.flag)
            assertEquals("Kyiv", country.capital)
        }
    }

    @Test
    fun `accepts HTTPS flags from different providers`() {
        for (host in listOf("flagcdn.com", "upload.wikimedia.org")) {
            val png = "https://$host/example/flag.png"
            val svg = "https://$host/example/flag.svg"
            val country = requireNotNull(countryDto().copy(
                flags = FlagsDto(png = png, svg = svg),
            ).toDomainOrNull())

            assertEquals(CountryFlag(svgUrl = svg, pngUrl = png), country.flag)
        }
    }

    private fun invalidUrls(): List<String?> = listOf(
        null, "", " ", "not a URL", "/flags/ua.png",
        "http://flagcdn.com/ua.png", "file:///flags/ua.png",
        "https://user:password@example.com/ua.png",
    )

    @Test
    fun `mapped countries with no flags remain playable in capital mode`() {
        val countries = (0 until 10).map { index ->
            countryDto().copy(
                alpha2Code = "A${'A' + index}",
                name = "Country $index",
                capital = "Capital $index",
                flags = null,
            )
        }.mapNotNull { it.toDomainOrNull() }

        assertTrue(QuizRoundGenerator().generate(countries, QuizMode.CAPITAL)
            is QuizGenerationResult.Success)
        assertEquals(QuizGenerationResult.InsufficientCountries(0, 10),
            QuizRoundGenerator().generate(countries, QuizMode.FLAG))
    }

    private fun countryDto() = CountryDto(
        alpha2Code = "UA",
        name = "Ukraine",
        capital = "Kyiv",
        region = "Europe",
        languages = listOf(LanguageDto("Ukrainian")),
        flags = FlagsDto(
            png = "https://flagcdn.com/w320/ua.png",
            svg = "https://flagcdn.com/ua.svg",
        ),
    )
}
