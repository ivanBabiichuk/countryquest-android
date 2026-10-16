package io.github.ivanbabiichuk.countryquest.feature.home

import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult

data class HomeUiState(
    val countriesCount: Int = 0,
    val isReadingCatalog: Boolean = true,
    val hasCatalogReadError: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastRefreshResult: CountryRefreshResult? = null
) {
    val hasCountries: Boolean
        get() = countriesCount > 0
}