package io.github.ivanbabiichuk.countryquest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_sync_metadata")
data class CatalogSyncMetadataEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val lastSuccessfulSyncAtEpochMillis: Long
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}