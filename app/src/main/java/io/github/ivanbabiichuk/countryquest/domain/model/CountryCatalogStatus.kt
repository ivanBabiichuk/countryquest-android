package io.github.ivanbabiichuk.countryquest.domain.model

data class CountryCatalogStatus(
    val countriesCount: Int,
    val lastSuccessfulSyncAtEpochMillis: Long?
)