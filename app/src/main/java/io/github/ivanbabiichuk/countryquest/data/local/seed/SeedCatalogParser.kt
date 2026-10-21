package io.github.ivanbabiichuk.countryquest.data.local.seed

import io.github.ivanbabiichuk.countryquest.data.mapper.withKnownDataCorrections
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import javax.inject.Inject

class SeedCatalogParser @Inject constructor(
    private val json: Json
) {

    fun parse(content: String): SeedCatalog {
        val dto = json.decodeFromString<SeedCatalogDto>(content)

        require(dto.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported seed schema: ${dto.schemaVersion}"
        }
        require(dto.seedVersion > 0) {
            "Seed version must be positive"
        }
        require(dto.preparedAt.isNotBlank()) {
            "Seed preparation date is missing"
        }
        require(dto.countries.isNotEmpty()) {
            "Seed catalog is empty"
        }

        val countries = dto.countries.map { it.toCountry() }

        require(countries.map { it.code }.distinct().size == countries.size) {
            "Seed contains duplicate country codes"
        }
        require(
            countries
                .map { it.name.lowercase(Locale.ROOT) }
                .distinct()
                .size == countries.size
        ) {
            "Seed contains duplicate country names"
        }

        val bundledFlags = dto.countries.associate { country ->
            val expectedPath =
                "flags/${country.code.lowercase(Locale.ROOT)}.svg"

            require(country.bundledFlagAsset == expectedPath) {
                "Unexpected flag asset for ${country.code}"
            }

            country.code to country.bundledFlagAsset
        }

        return SeedCatalog(
            seedVersion = dto.seedVersion,
            preparedAt = dto.preparedAt.trim(),
            countries = countries,
            bundledFlagAssets = bundledFlags
        )
    }

    private fun SeedCountryDto.toCountry(): Country {
        require(code.length == 2 && code.all { it in 'A'..'Z' }) {
            "Invalid seed country code: $code"
        }

        val cleanName = name.trim()
        val cleanRegion = region.trim()
        val cleanLanguages = languages.map { it.trim() }

        require(cleanName.isNotEmpty()) {
            "Missing name for $code"
        }
        require(cleanRegion.isNotEmpty()) {
            "Missing region for $code"
        }
        require(
            cleanLanguages.isNotEmpty() &&
                    cleanLanguages.all { it.isNotEmpty() }
        ) {
            "Missing or blank languages for $code"
        }

        return Country(
            code = code,
            name = cleanName,
            capital = capital?.trim()?.takeIf { it.isNotEmpty() },
            region = cleanRegion,
            languages = cleanLanguages.distinctBy { it.lowercase(Locale.ROOT) },
            flag = CountryFlag(
                svgUrl = flag.svgUrl.validatedFlagUrl(),
                pngUrl = flag.pngUrl.validatedFlagUrl()
            )
        ).withKnownDataCorrections()
    }

    private fun String?.validatedFlagUrl(): String? {
        val value = this?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val url = requireNotNull(value.toHttpUrlOrNull()) {
            "Invalid seed flag URL: $value"
        }

        require(
            url.isHttps &&
                    url.username.isEmpty() &&
                    url.password.isEmpty()
        ) {
            "Seed flag URL must use HTTPS without credentials"
        }

        return url.toString()
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}