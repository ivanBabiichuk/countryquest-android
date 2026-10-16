package io.github.ivanbabiichuk.countryquest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey
    val code: String,
    val name: String,
    val capital: String?,
    val region: String,
    val languages: List<String>,
    val flagSvgUrl: String?,
    val flagPngUrl: String?
)
