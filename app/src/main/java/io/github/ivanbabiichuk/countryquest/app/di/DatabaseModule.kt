package io.github.ivanbabiichuk.countryquest.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ivanbabiichuk.countryquest.data.local.CountryQuestDatabase
import io.github.ivanbabiichuk.countryquest.data.local.dao.CatalogSyncMetadataDao
import io.github.ivanbabiichuk.countryquest.data.local.dao.CountryDao
import io.github.ivanbabiichuk.countryquest.data.local.dao.GameHistoryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CountryQuestDatabase =
        Room.databaseBuilder(
            context = context,
            klass = CountryQuestDatabase::class.java,
            name = "countryquest.db"
        ).build()

    @Provides
    fun provideDao(database: CountryQuestDatabase): CountryDao = database.countryDao()

    @Provides
    fun provideGameHistoryDao(database: CountryQuestDatabase): GameHistoryDao = database.gameHistoryDao()

    @Provides
    fun provideCatalogSyncMetadataDao(database: CountryQuestDatabase): CatalogSyncMetadataDao = database.catalogSyncMetadataDao()

}