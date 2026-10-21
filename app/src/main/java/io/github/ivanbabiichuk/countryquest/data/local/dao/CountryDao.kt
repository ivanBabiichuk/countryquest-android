package io.github.ivanbabiichuk.countryquest.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountryDao {

    @Query("SELECT * FROM countries ORDER BY code")
    fun observeCountries(): Flow<List<CountryEntity>>

    @Query("SELECT * FROM countries ORDER BY code")
    suspend fun getCountries(): List<CountryEntity>

    @Upsert
    suspend fun upsertCountries(countries: List<CountryEntity>)

    @Query("SELECT COUNT(*) FROM countries")
    suspend fun getCountriesCount(): Int

}