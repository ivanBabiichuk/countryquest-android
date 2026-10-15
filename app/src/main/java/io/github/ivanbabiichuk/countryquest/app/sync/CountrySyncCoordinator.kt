package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountrySyncCoordinator @Inject constructor(
    private val countryRepository: CountryRepository
) {
    private val _state = MutableStateFlow(CountrySyncState())
    val state = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var hasAttemptedRefresh = false

    suspend fun initialize() {
        refreshInternal(onlyIfNotAttempted = true)
    }

    suspend fun refresh() {
        refreshInternal(onlyIfNotAttempted = false)
    }

    private suspend fun refreshInternal(
        onlyIfNotAttempted: Boolean,
    ) {
        if (!refreshMutex.tryLock()) return

        try {
            if (onlyIfNotAttempted && hasAttemptedRefresh) return

            hasAttemptedRefresh = true

            _state.value = CountrySyncState(isRefreshing = true)

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