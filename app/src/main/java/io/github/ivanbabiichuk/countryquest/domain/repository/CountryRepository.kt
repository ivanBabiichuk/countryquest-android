package io.github.ivanbabiichuk.countryquest.domain.repository

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import kotlinx.coroutines.flow.Flow

interface CountryRepository {

    fun observeCountries(): Flow<List<Country>>

    suspend fun getCountries(): List<Country>

    suspend fun refreshCountries(): CountryRefreshResult

    suspend fun getCatalogStatus(): Result<CountryCatalogStatus>

    fun observeCatalogStatus(): Flow<CountryCatalogStatus>

    suspend fun initializeCatalog(): Result<Unit>

}