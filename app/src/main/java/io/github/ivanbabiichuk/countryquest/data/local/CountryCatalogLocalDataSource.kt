package io.github.ivanbabiichuk.countryquest.data.local

import androidx.room.withTransaction
import io.github.ivanbabiichuk.countryquest.data.local.entity.CatalogSyncMetadataEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CountryCatalogLocalDataSource @Inject constructor(
    private val database: CountryQuestDatabase
) {

    fun observeCountries(): Flow<List<CountryEntity>> = database.countryDao().observeCountries()

    suspend fun getCountries(): List<CountryEntity> = database.countryDao().getCountries()

    suspend fun saveSyncedCountries(
        countries: List<CountryEntity>,
        syncedAtEpochMillis: Long
    ) {
        require(countries.isNotEmpty())
        require(syncedAtEpochMillis >= 0)

        database.withTransaction {
            database.countryDao().upsertCountries(countries)

            database.catalogSyncMetadataDao().upsertMetadata(
                CatalogSyncMetadataEntity(lastSuccessfulSyncAtEpochMillis = syncedAtEpochMillis)
            )
        }
    }

    suspend fun getCatalogStatus(): CountryCatalogStatus =
        database.withTransaction {
            CountryCatalogStatus(
                countriesCount = database.countryDao().getCountriesCount(),
                lastSuccessfulSyncAtEpochMillis = database
                    .catalogSyncMetadataDao()
                    .getMetadata()
                    ?.lastSuccessfulSyncAtEpochMillis
            )
        }
}