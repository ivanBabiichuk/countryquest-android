package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import javax.inject.Inject

class CatalogFreshnessPolicy @Inject constructor() {

    fun shouldRefresh(
        status: CountryCatalogStatus,
        nowEpochMillis: Long
    ): Boolean {
        if (status.countriesCount <= 0) return true

        val lastSync = status.lastSuccessfulSyncAtEpochMillis ?: return true

        if (lastSync < 0 || nowEpochMillis < 0) return true
        if (lastSync > nowEpochMillis) return true

        return nowEpochMillis - lastSync >= MAX_AGE_MILLIS
    }

    private companion object {
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}