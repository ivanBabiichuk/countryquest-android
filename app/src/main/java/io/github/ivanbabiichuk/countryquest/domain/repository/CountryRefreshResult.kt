package io.github.ivanbabiichuk.countryquest.domain.repository

sealed interface CountryRefreshResult {

    data class Success(
        val updatedCountries: Int,
        val skippedRecords: Int
    ): CountryRefreshResult

    data class Failure(
        val reason: CountryRefreshFailure
    ) : CountryRefreshResult
}

enum class CountryRefreshFailure {
    NETWORK,
    SERVER,
    INVALID_DATA,
    STORAGE,
}