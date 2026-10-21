package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

fun CountryDto.toDomainOrNull(): Country? {
    val rawCode = alpha2Code.trim()
    if (rawCode.length != 2 || rawCode.any { it !in 'A'..'Z' && it !in 'a'..'z' }) {
        return null
    }
    val countryName = name.trim().takeIf { it.isNotEmpty() } ?: return null

    return Country(
        code = rawCode.uppercase(Locale.ROOT),
        name = countryName,
        capital = capital.cleanTextOrNull(),
        region = region.cleanTextOrNull().orEmpty(),
        languages = languages.orEmpty()
            .mapNotNull { it.name.cleanTextOrNull() }
            .distinctBy { it.lowercase(Locale.ROOT) },
        flag = CountryFlag(
            svgUrl = flags?.svg.toHttpsUrlOrNull(),
            pngUrl = flags?.png.toHttpsUrlOrNull()
        )
    ).withKnownDataCorrections()
}

private fun String?.toHttpsUrlOrNull(): String? =
    cleanTextOrNull()
        ?.toHttpUrlOrNull()
        ?.takeIf {
            it.isHttps &&
                    it.username.isEmpty() &&
                    it.password.isEmpty()
        }
        ?.toString()

private fun String?.cleanTextOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
