package io.github.ivanbabiichuk.countryquest.domain.model

data class Country(
    val code: String,
    val name: String,
    val capitals: List<String>,
    val region: String,
    val languages: List<String>,
    val flagImage: String?
)
