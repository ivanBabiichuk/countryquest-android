package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult

data class CountrySyncState(
    val isRefreshing: Boolean = false,
    val lastResult: CountryRefreshResult? = null
)
