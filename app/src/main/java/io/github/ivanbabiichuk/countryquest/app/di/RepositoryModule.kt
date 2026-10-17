package io.github.ivanbabiichuk.countryquest.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ivanbabiichuk.countryquest.data.repository.OfflineFirstCountryRepository
import io.github.ivanbabiichuk.countryquest.data.repository.RoomGameHistoryRepository
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCountryRepository(implementation: OfflineFirstCountryRepository): CountryRepository

    @Binds
    @Singleton
    abstract fun bindGameHistoryRepository(implementation: RoomGameHistoryRepository): GameHistoryRepository
}