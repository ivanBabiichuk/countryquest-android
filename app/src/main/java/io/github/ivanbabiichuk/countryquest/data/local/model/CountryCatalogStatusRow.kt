package io.github.ivanbabiichuk.countryquest.data.local.model

data class CountryCatalogStatusRow(
    val countriesCount: Int,
    val lastSuccessfulSyncAtEpochMillis: Long?
)