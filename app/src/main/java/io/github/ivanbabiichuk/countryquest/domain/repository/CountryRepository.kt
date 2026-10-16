package io.github.ivanbabiichuk.countryquest.domain.repository

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import kotlinx.coroutines.flow.Flow

interface CountryRepository {

    fun observeCountries(): Flow<List<Country>>

    suspend fun getCountries(): List<Country>

    suspend fun refreshCountries(): CountryRefreshResult

}