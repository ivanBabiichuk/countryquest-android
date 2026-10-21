package io.github.ivanbabiichuk.countryquest.feature.home

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ivanbabiichuk.countryquest.app.sync.CountrySyncCoordinator
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val countryRepository: CountryRepository,
    private val countrySyncCoordinator: CountrySyncCoordinator
) : ViewModel() {

    private val catalogRetryTrigger = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val catalogState = catalogRetryTrigger
        .flatMapLatest {
            countryRepository.observeCatalogStatus()
                .map { status ->
                    HomeUiState(
                        countriesCount = status.countriesCount,
                        lastSuccessfulSyncAtEpochMillis = status.lastSuccessfulSyncAtEpochMillis,
                        isReadingCatalog = false
                    )
                }
                .onStart {
                    emit(HomeUiState())
                }
                .catch { cause ->
                    if (cause is CancellationException) throw cause

                    Log.e(TAG, "Failed to observe country catalog", cause)

                    emit(
                        HomeUiState(
                            isReadingCatalog = false,
                            hasCatalogReadError = true
                        )
                    )
                }
        }

    val uiState = combine(
        catalogState,
        countrySyncCoordinator.state,
    ) { catalog, sync ->
        catalog.copy(
            isRefreshing = sync.isRefreshing,
            lastRefreshResult = sync.lastResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    @MainThread
    fun refreshCountries() {
        if (uiState.value.hasCatalogReadError) {
            catalogRetryTrigger.update { it + 1 }
        }

        viewModelScope.launch {
            countrySyncCoordinator.refresh()
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}