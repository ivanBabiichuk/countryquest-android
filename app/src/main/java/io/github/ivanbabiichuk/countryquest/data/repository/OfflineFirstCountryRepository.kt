package io.github.ivanbabiichuk.countryquest.data.repository

import android.database.sqlite.SQLiteException
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import io.github.ivanbabiichuk.countryquest.data.local.CountryCatalogLocalDataSource
import io.github.ivanbabiichuk.countryquest.data.mapper.toDomain
import io.github.ivanbabiichuk.countryquest.data.mapper.toDomainOrNull
import io.github.ivanbabiichuk.countryquest.data.mapper.toEntity
import io.github.ivanbabiichuk.countryquest.data.remote.CountriesApi
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class OfflineFirstCountryRepository @Inject constructor(
    private val countriesApi: CountriesApi,
    private val localDataSource: CountryCatalogLocalDataSource,
    private val timeProvider: TimeProvider
) : CountryRepository {

    private val refreshMutex = Mutex()

    override fun observeCountries(): Flow<List<Country>> =
        localDataSource.observeCountries().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getCountries(): List<Country> =
        localDataSource.getCountries().map { it.toDomain() }

    override suspend fun refreshCountries(): CountryRefreshResult =
        refreshMutex.withLock {
            val response = try {
                countriesApi.getCountries()
            } catch (ex: IOException) {
                return@withLock failure(CountryRefreshFailure.NETWORK)
            } catch (ex: HttpException) {
                return@withLock failure(CountryRefreshFailure.SERVER)
            } catch (ex: SerializationException) {
                return@withLock failure(CountryRefreshFailure.INVALID_DATA)
            }

            val countries = response
                .mapNotNull { it.toDomainOrNull() }
                .groupBy { it.code }
                .values
                .mapNotNull { countriesWithSameCode ->
                    countriesWithSameCode
                        .distinct()
                        .singleOrNull()
                }

            if (countries.isEmpty()) {
                return@withLock failure(CountryRefreshFailure.INVALID_DATA)
            }

            val skippedRecords = response.size - countries.size
            val entities = countries.map { it.toEntity() }

            try {
                localDataSource.saveSyncedCountries(
                    countries = entities,
                    syncedAtEpochMillis = timeProvider.currentTimeMillis()
                )
            } catch (ex: SQLiteException) {
                return@withLock failure(CountryRefreshFailure.STORAGE)
            }

            CountryRefreshResult.Success(
                updatedCountries = countries.size,
                skippedRecords = skippedRecords,
            )
        }

    private fun failure(reason: CountryRefreshFailure): CountryRefreshResult.Failure =
        CountryRefreshResult.Failure(reason)

}