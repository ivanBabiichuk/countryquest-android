package io.github.ivanbabiichuk.countryquest.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.ivanbabiichuk.countryquest.data.local.entity.CatalogSyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogSyncMetadataDao {

    @Query("SELECT * FROM catalog_sync_metadata WHERE id = 1")
    fun observeMetadata(): Flow<CatalogSyncMetadataEntity?>

    @Query("SELECT * FROM catalog_sync_metadata WHERE id = 1")
    suspend fun getMetadata(): CatalogSyncMetadataEntity?

    @Upsert
    suspend fun upsertMetadata(metadata: CatalogSyncMetadataEntity)
}