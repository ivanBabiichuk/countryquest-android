package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.FlagsDto
import io.github.ivanbabiichuk.countryquest.data.remote.dto.LanguageDto
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryCorrectionMappingTest {
    @Test
    fun `API mapper normalizes fields before applying all verified corrections`() {
        for ((source, expected) in examples()) {
            assertEquals(expected, dto(source).toDomainOrNull())
        }
    }

    @Test
    fun `cached entity reads apply corrections without mutating stored values`() {
        for ((source, expected) in examples()) {
            val entity = entity(source)
            val snapshot = entity.copy(languages = entity.languages.toList())
            assertEquals(expected, entity.toDomain())
            assertEquals(snapshot, entity)
        }
    }

    @Test
    fun `API corrected data survives entity round trip without a second change`() {
        for ((source, expected) in examples()) {
            val mapped = requireNotNull(dto(source).toDomainOrNull())
            val stored = mapped.toEntity()
            assertEquals(expected, stored.toDomain())
            assertEquals(entity(expected), stored)
        }
    }

    @Test
    fun `API validation rejects invalid records before corrections can rescue them`() {
        assertNull(CountryDto(alpha2Code = "KZ", name = " ", capital = "Nur-Sultan").toDomainOrNull())
        assertNull(CountryDto(alpha2Code = "S1", name = "Swaziland").toDomainOrNull())
    }

    @Test
    fun `missing capitals and flags remain missing through both mapping paths`() {
        val source = country("KZ", "Kazakhstan", null).copy(flag = CountryFlag(null, null))
        val api = CountryDto(alpha2Code = source.code, name = source.name, capital = null,
            region = source.region, languages = source.languages.map { LanguageDto(it) })
        assertEquals(source, api.toDomainOrNull())
        assertEquals(source, entity(source).toDomain())
    }

    private fun examples(): List<Pair<Country, Country>> {
        val kz = country("KZ", "Kazakhstan", "Nur-Sultan")
        val sz = country("SZ", "Swaziland", "Mbabane")
        val gq = country("GQ", "Equatorial Guinea", "Malabo")
        val cw = country("CW", "Curaçao", "Willemstad").copy(
            languages = listOf("Dutch", "(Eastern) Punjabi", "English")
        )
        return listOf(
            kz to kz.copy(capital = "Astana"),
            sz to sz.copy(name = "Eswatini"),
            gq to gq.copy(capital = "Ciudad de la Paz"),
            cw to cw.copy(languages = listOf("Dutch", "Papiamentu", "English"))
        )
    }

    private fun country(code: String, name: String, capital: String?) = Country(
        code, name, capital, "Region", listOf("Language"),
        CountryFlag("https://flagcdn.com/${code.lowercase()}.svg", "https://flagcdn.com/w320/${code.lowercase()}.png")
    )

    private fun dto(country: Country) = CountryDto(
        alpha2Code = " ${country.code.lowercase()} ", name = " ${country.name} ",
        capital = country.capital?.let { " $it " }, region = " ${country.region} ",
        languages = country.languages.map { LanguageDto(" $it ") },
        flags = FlagsDto(svg = country.flag.svgUrl, png = country.flag.pngUrl)
    )

    private fun entity(country: Country) = CountryEntity(
        country.code, country.name, country.capital, country.region, country.languages.toList(),
        country.flag.svgUrl, country.flag.pngUrl
    )
}
