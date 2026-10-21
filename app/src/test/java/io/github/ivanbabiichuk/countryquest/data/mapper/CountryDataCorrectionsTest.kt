package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryDataCorrectionsTest {
    @Test
    fun `corrects only the known Kazakhstan capital`() {
        val original = country("KZ").copy(capital = "Nur-Sultan")
        assertEquals(original.copy(capital = "Astana"), original.withKnownDataCorrections())
    }

    @Test
    fun `corrects only the known Eswatini country name`() {
        val original = country("SZ").copy(name = "Swaziland")
        assertEquals(original.copy(name = "Eswatini"), original.withKnownDataCorrections())
    }

    @Test
    fun `corrects only the known Equatorial Guinea capital`() {
        val original = country("GQ").copy(capital = "Malabo")
        assertEquals(original.copy(capital = "Ciudad de la Paz"), original.withKnownDataCorrections())
    }

    @Test
    fun `corrects Curacao language while retaining other languages and their order`() {
        val original = country("CW").copy(languages = listOf("Dutch", "(Eastern) Punjabi", "English"))
        assertEquals(
            original.copy(languages = listOf("Dutch", "Papiamentu", "English")),
            original.withKnownDataCorrections()
        )
    }

    @Test
    fun `language replacement does not introduce case insensitive duplicates`() {
        val original = country("CW").copy(
            languages = listOf("Dutch", "(Eastern) Punjabi", "PAPIAMENTU", "English")
        )
        assertEquals(listOf("Dutch", "Papiamentu", "English"), original.withKnownDataCorrections().languages)
        val existingFirst = original.copy(languages = listOf("papiamentu", "(Eastern) Punjabi", "English"))
        assertEquals(listOf("papiamentu", "English"), existingFirst.withKnownDataCorrections().languages)
    }

    @Test
    fun `matching known source values ignores letter case`() {
        assertEquals("Astana", country("KZ").copy(capital = "nur-sultan").withKnownDataCorrections().capital)
        assertEquals("Eswatini", country("SZ").copy(name = "SWAZILAND").withKnownDataCorrections().name)
        assertEquals("Ciudad de la Paz", country("GQ").copy(capital = "MALABO").withKnownDataCorrections().capital)
        assertEquals(
            listOf("Papiamentu"),
            country("CW").copy(languages = listOf("(eastern) punjabi")).withKnownDataCorrections().languages
        )
    }

    @Test
    fun `same source values in another country are not changed`() {
        val original = country("UA").copy(
            name = "Swaziland", capital = "Nur-Sultan", languages = listOf("(Eastern) Punjabi")
        )
        assertEquals(original, original.withKnownDataCorrections())
        assertEquals(original.copy(capital = "Malabo"), original.copy(capital = "Malabo").withKnownDataCorrections())
    }

    @Test
    fun `unexpected and already correct values are preserved`() {
        for (capital in listOf("Astana", "Almaty", "Future capital")) {
            val original = country("KZ").copy(capital = capital)
            assertEquals(original, original.withKnownDataCorrections())
        }
        for (capital in listOf("Ciudad de la Paz", "Future capital")) {
            val original = country("GQ").copy(capital = capital)
            assertEquals(original, original.withKnownDataCorrections())
        }
        for (name in listOf("Eswatini", "Different name")) {
            val original = country("SZ").copy(name = name)
            assertEquals(original, original.withKnownDataCorrections())
        }
        val unrelatedLanguage = country("CW").copy(languages = listOf("Punjabi", "Dutch"))
        assertEquals(unrelatedLanguage, unrelatedLanguage.withKnownDataCorrections())
    }

    @Test
    fun `missing optional values are not invented`() {
        for (code in listOf("KZ", "GQ", "CW")) {
            val original = country(code).copy(capital = null, languages = emptyList())
            assertEquals(original, original.withKnownDataCorrections())
        }
    }

    @Test
    fun `applying corrections again is idempotent and leaves input intact`() {
        val originals = listOf(
            country("KZ").copy(capital = "Nur-Sultan"),
            country("SZ").copy(name = "Swaziland"),
            country("GQ").copy(capital = "Malabo"),
            country("CW").copy(languages = listOf("Dutch", "(Eastern) Punjabi", "English"))
        )
        val snapshots = originals.map { it.copy(languages = it.languages.toList()) }
        for (original in originals) {
            val corrected = original.withKnownDataCorrections()
            assertEquals(corrected, corrected.withKnownDataCorrections())
        }
        assertEquals(snapshots, originals)
    }

    private fun country(code: String) = Country(
        code = code, name = "Country $code", capital = "Unchanged capital", region = "Region",
        languages = listOf("Language"),
        flag = CountryFlag("https://example.com/$code.svg", "https://example.com/$code.png")
    )
}
