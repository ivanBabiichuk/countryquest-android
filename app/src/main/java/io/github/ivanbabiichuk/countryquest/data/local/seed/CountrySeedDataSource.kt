package io.github.ivanbabiichuk.countryquest.data.local.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CountrySeedDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: SeedCatalogParser
) {

    suspend fun load(): SeedCatalog = withContext(Dispatchers.IO) {
        val content = context.assets
            .open(CATALOG_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        ensureActive()

        val catalog = parser.parse(content)

        for ((code, assetPath) in catalog.bundledFlagAssets) {
            ensureActive()

            context.assets.open(assetPath).use { stream ->
                require(stream.read() != -1) {
                    "Empty bundled flag for $code"
                }
            }
        }

        catalog
    }

    private companion object {
        const val CATALOG_ASSET = "catalog/countries.json"
    }
}