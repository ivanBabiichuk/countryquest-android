package io.github.ivanbabiichuk.countryquest.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CountryDto(
    val alpha2Code: String,
    val name: String,
    val capital: String? = null,
    val region: String? = null,
    val languages: List<LanguageDto>? = null,
    val flags: FlagsDto? = null
)

@Serializable
data class LanguageDto(
    val name: String? = null
)

@Serializable
data class FlagsDto(
    val png: String? = null,
    val svg: String? = null
)