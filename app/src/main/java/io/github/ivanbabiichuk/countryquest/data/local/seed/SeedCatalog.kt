package io.github.ivanbabiichuk.countryquest.data.local.seed

import io.github.ivanbabiichuk.countryquest.domain.model.Country

data class SeedCatalog(
    val seedVersion: Int,
    val preparedAt: String,
    val countries: List<Country>,
    val bundledFlagAssets: Map<String, String>
)