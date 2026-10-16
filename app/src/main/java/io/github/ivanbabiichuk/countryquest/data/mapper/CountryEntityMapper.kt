package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag

fun Country.toEntity(): CountryEntity =
    CountryEntity(
        code = code,
        name = name,
        capital = capital,
        region = region,
        languages = languages.toList(),
        flagSvgUrl = flag.svgUrl,
        flagPngUrl = flag.pngUrl
    )

fun CountryEntity.toDomain(): Country =
    Country(
        code = code,
        name = name,
        capital = capital,
        region = region,
        languages = languages.toList(),
        flag = CountryFlag(
            svgUrl = flagSvgUrl,
            pngUrl = flagPngUrl
        )
    )