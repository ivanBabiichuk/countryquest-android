package io.github.ivanbabiichuk.countryquest.data.local.seed

import kotlinx.serialization.Serializable

@Serializable
data class SeedCatalogDto(
    val schemaVersion: Int,
    val seedVersion: Int,
    val preparedAt: String,
    val countries: List<SeedCountryDto>
)

@Serializable
data class SeedCountryDto(
    val code: String,
    val name: String,
    val capital: String? = null,
    val region: String,
    val languages: List<String>,
    val flag: SeedFlagDto,
    val bundledFlagAsset: String
)

@Serializable
data class SeedFlagDto(
    val svgUrl: String? = null,
    val pngUrl: String? = null
)