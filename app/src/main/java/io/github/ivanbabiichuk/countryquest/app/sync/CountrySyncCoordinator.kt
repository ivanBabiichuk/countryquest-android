package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountrySyncCoordinator @Inject constructor(
    private val countryRepository: CountryRepository,
    private val timeProvider: TimeProvider,
    private val freshnessPolicy: CatalogFreshnessPolicy
) {

    private val _state = MutableStateFlow(CountrySyncState())
    val state = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var hasInitialized = false

    suspend fun initialize() {
        refreshInternal(force = false)
    }

    suspend fun refresh() {
        refreshInternal(force = true)
    }

    private suspend fun refreshInternal(force: Boolean) {
        if (!refreshMutex.tryLock()) return

        try {
            if (!force && hasInitialized) return

            hasInitialized = true
            _state.value = CountrySyncState(isRefreshing = true)

            val initialization = countryRepository.initializeCatalog()

            if (!force && initialization.isSuccess) {
                val status = countryRepository.getCatalogStatus()
                    .getOrElse {
                        _state.value = CountrySyncState(
                            lastResult = CountryRefreshResult.Failure(CountryRefreshFailure.STORAGE)
                        )
                        return
                    }

                if (!freshnessPolicy.shouldRefresh(status = status, nowEpochMillis = timeProvider.currentTimeMillis())) {
                    return
                }
            }

            val result = countryRepository.refreshCountries()

            _state.update {
                it.copy(lastResult = result)
            }
        } finally {
            _state.update {
                it.copy(isRefreshing = false)
            }
            refreshMutex.unlock()
        }
    }
}