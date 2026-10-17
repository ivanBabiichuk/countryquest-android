package io.github.ivanbabiichuk.countryquest.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.ivanbabiichuk.countryquest.data.local.converter.StringListConverter
import io.github.ivanbabiichuk.countryquest.data.local.dao.CountryDao
import io.github.ivanbabiichuk.countryquest.data.local.dao.GameHistoryDao
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameAnswerEntity
import io.github.ivanbabiichuk.countryquest.data.local.entity.GameResultEntity

@Database(
    entities = [
        CountryEntity::class,
        GameResultEntity::class,
        GameAnswerEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(StringListConverter::class)
abstract class CountryQuestDatabase: RoomDatabase() {

    abstract fun countryDao(): CountryDao

    abstract fun gameHistoryDao(): GameHistoryDao

}