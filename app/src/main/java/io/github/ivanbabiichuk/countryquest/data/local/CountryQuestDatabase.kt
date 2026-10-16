package io.github.ivanbabiichuk.countryquest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.ivanbabiichuk.countryquest.data.local.converter.StringListConverter
import io.github.ivanbabiichuk.countryquest.data.local.dao.CountryDao
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity

@Database(
    entities = [CountryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class CountryQuestDatabase: RoomDatabase() {

    abstract fun countryDao(): CountryDao

}