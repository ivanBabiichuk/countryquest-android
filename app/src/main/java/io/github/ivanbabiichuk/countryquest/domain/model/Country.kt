package io.github.ivanbabiichuk.countryquest.domain.model

data class Country(
    val code: String,
    val name: String,
    val capital: String?,
    val region: String,
    val languages: List<String>,
    val flag: CountryFlag
)

data class CountryFlag(
    val svgUrl: String?,
    val pngUrl: String?
)
