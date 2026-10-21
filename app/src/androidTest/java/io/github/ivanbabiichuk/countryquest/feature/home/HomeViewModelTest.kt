package io.github.ivanbabiichuk.countryquest.feature.home

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.app.AppViewModel
import io.github.ivanbabiichuk.countryquest.app.sync.CountrySyncCoordinator
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import io.github.ivanbabiichuk.countryquest.app.sync.CatalogFreshnessPolicy
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @Test
    fun homeObservesLocalChangesWithoutStartingRefresh() = runBlocking {
        withHome { repository, _, home ->
            assertEquals(0, home.awaitReady().countriesCount)
            repository.status.value = CountryCatalogStatus(1, null)
            val populated = home.uiState.first { it.countriesCount == 1 }
            assertTrue(populated.hasCountries)
            repository.status.value = CountryCatalogStatus(0, null)
            val empty = home.uiState.first { !it.isReadingCatalog && it.countriesCount == 0 }
            assertFalse(empty.hasCountries)
            assertEquals(0, repository.refreshCalls)
        }
    }

    @Test
    fun appInitializationIsSharedWithHomeAndRunsOnlyOnce() = runBlocking {
        withHome { repository, coordinator, home ->
            repository.status.value = CountryCatalogStatus(2, null)
            home.uiState.first { it.countriesCount == 2 }
            val finish = CompletableDeferred<CountryRefreshResult>()
            repository.refreshResponse = { finish.await() }
            val store = ViewModelStore()
            val app = AppViewModel(coordinator)
            store.put("app", app)
            try {
                app.initialize()
                home.uiState.first { it.isRefreshing }
                app.initialize()
                home.refreshCountries()
                assertEquals(1, repository.refreshCalls)
                val result = CountryRefreshResult.Success(1, 3)
                finish.complete(result)
                val completed = home.uiState.first { !it.isRefreshing && it.lastRefreshResult == result }
                // The stored catalog count must not be replaced by updatedCountries.
                assertEquals(2, completed.countriesCount)
                app.initialize()
                assertEquals(1, repository.refreshCalls)
            } finally {
                store.clear()
            }
        }
    }

    @Test
    fun manualRefreshFailurePreservesCatalogAndRetryClearsPreviousError() = runBlocking {
        withHome { repository, _, home ->
            repository.status.value = CountryCatalogStatus(1, null)
            home.uiState.first { it.countriesCount == 1 }
            val failure = CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK)
            repository.refreshResponse = { failure }
            home.refreshCountries()
            val failed = home.uiState.first { !it.isRefreshing && it.lastRefreshResult == failure }
            assertTrue(failed.hasCountries)
            assertFalse(failed.hasCatalogReadError)

            val finish = CompletableDeferred<CountryRefreshResult>()
            repository.refreshResponse = { finish.await() }
            home.refreshCountries()
            val pending = home.uiState.first { it.isRefreshing }
            assertNull(pending.lastRefreshResult)
            assertEquals(1, pending.countriesCount)
            finish.complete(CountryRefreshResult.Success(1, 0))
            home.uiState.first { !it.isRefreshing && it.lastRefreshResult is CountryRefreshResult.Success }
            assertEquals(2, repository.refreshCalls)
            assertEquals(1, repository.observationStarts)
        }
    }

    @Test
    fun retryAfterReadErrorCreatesNewSubscriptionAndReceivesLaterUpdates() = runBlocking {
        val repository = FakeRepository().apply { failNextObservation = true }
        withHome(repository) { repo, _, home ->
            val failed = home.uiState.first { it.hasCatalogReadError }
            assertFalse(failed.isReadingCatalog)
            assertEquals(0, repo.activeObservers.value)
            repo.status.value = CountryCatalogStatus(1, null)

            home.refreshCountries()
            val recovered = home.uiState.first { !it.hasCatalogReadError && it.countriesCount == 1 }
            assertFalse(recovered.isReadingCatalog)
            assertEquals(2, repo.observationStarts)
            assertEquals(1, repo.activeObservers.value)

            repo.status.value = CountryCatalogStatus(2, null)
            home.uiState.first { it.countriesCount == 2 }
            assertEquals(1, repo.refreshCalls)
        }
    }

    @Test
    fun subscriptionStopsAfterGracePeriodAndResumesWithoutNetworkRefresh() = runBlocking {
        withTimeout(12_000.milliseconds) {
            withContext(Dispatchers.Main.immediate) {
                val repository = FakeRepository()
                val home = HomeViewModel(repository, coordinator(repository))
                val store = ViewModelStore().apply { put("home", home) }
                val firstCollector = launch(start = CoroutineStart.UNDISPATCHED) { home.uiState.collect {} }
                try {
                    home.awaitReady()
                    assertEquals(1, repository.activeObservers.value)
                    firstCollector.cancelAndJoin()
                    repository.activeObservers.first { it == 0 }
                    repository.status.value = CountryCatalogStatus(1, null)
                    // A new collector restarts Room observation after WhileSubscribed stopped it.
                    home.uiState.first { !it.isReadingCatalog && it.countriesCount == 1 }
                    assertEquals(2, repository.observationStarts)
                    assertEquals(0, repository.refreshCalls)
                } finally {
                    firstCollector.cancelAndJoin()
                    store.clear()
                }
            }
        }
    }

    @Test
    fun clearingAppViewModelCancelsItsRequestAndAllowsManualRetry() = runBlocking {
        withHome { repository, coordinator, home ->
            home.awaitReady()
            val cancelled = CompletableDeferred<Unit>()
            repository.refreshResponse = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            val app = AppViewModel(coordinator)
            val store = ViewModelStore().apply { put("app", app) }
            try {
                app.initialize()
                home.uiState.first { it.isRefreshing }
                store.clear()
                cancelled.await()
                home.uiState.first { !it.isRefreshing }

                repository.refreshResponse = { CountryRefreshResult.Success(1, 0) }
                home.refreshCountries()
                home.uiState.first { it.lastRefreshResult is CountryRefreshResult.Success }
                assertEquals(2, repository.refreshCalls)
            } finally {
                store.clear()
            }
        }
    }

    @Test
    fun clearingHomeDoesNotCancelStartupRequestOwnedByApp() = runBlocking {
        withTimeout(5_000.milliseconds) {
            withContext(Dispatchers.Main.immediate) {
                val finish = CompletableDeferred<CountryRefreshResult>()
                val repository = FakeRepository().apply { refreshResponse = { finish.await() } }
                val coordinator = coordinator(repository)
                val app = AppViewModel(coordinator)
                val home = HomeViewModel(repository, coordinator)
                val appStore = ViewModelStore().apply { put("app", app) }
                val homeStore = ViewModelStore().apply { put("home", home) }
                try {
                    app.initialize()
                    home.uiState.first { it.isRefreshing }
                    homeStore.clear()
                    assertTrue(coordinator.state.value.isRefreshing)
                    val result = CountryRefreshResult.Success(2, 0)
                    finish.complete(result)
                    coordinator.state.first { !it.isRefreshing && it.lastResult == result }
                    assertEquals(1, repository.refreshCalls)
                } finally {
                    homeStore.clear()
                    appStore.clear()
                }
            }
        }
    }

    @Test
    fun syncTimeUpdatesEvenWhenCountryCountDoesNotChange() = runBlocking {
        withHome { repository, _, home ->
            repository.status.value = CountryCatalogStatus(2, 100L)
            val initial = home.uiState.first { it.lastSuccessfulSyncAtEpochMillis == 100L }
            assertEquals(2, initial.countriesCount)
            repository.status.value = CountryCatalogStatus(2, 200L)
            val updated = home.uiState.first { it.lastSuccessfulSyncAtEpochMillis == 200L }
            assertEquals(2, updated.countriesCount)
            assertEquals(0, repository.refreshCalls)
            assertEquals(1, repository.observationStarts)
        }
    }

    @Test
    fun failedRefreshKeepsStoredTimeAndSuccessfulRetryReceivesNewTime() = runBlocking {
        withHome { repository, _, home ->
            repository.status.value = CountryCatalogStatus(2, 100L)
            home.uiState.first { it.lastSuccessfulSyncAtEpochMillis == 100L }
            val failure = CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK)
            repository.refreshResponse = { failure }
            home.refreshCountries()
            val failed = home.uiState.first { !it.isRefreshing && it.lastRefreshResult == failure }
            assertEquals(100L, failed.lastSuccessfulSyncAtEpochMillis)
            assertEquals(2, failed.countriesCount)

            repository.refreshResponse = {
                repository.status.value = CountryCatalogStatus(2, 200L)
                CountryRefreshResult.Success(2, 0)
            }
            home.refreshCountries()
            val recovered = home.uiState.first {
                !it.isRefreshing &&
                    it.lastRefreshResult is CountryRefreshResult.Success &&
                    it.lastSuccessfulSyncAtEpochMillis == 200L
            }
            assertEquals(2, recovered.countriesCount)
            assertEquals(1, repository.observationStarts)
        }
    }

    @Test
    fun legacyCatalogHasUnknownTimeUntilSyncMetadataArrives() = runBlocking {
        withHome { repository, _, home ->
            repository.status.value = CountryCatalogStatus(1, null)
            val legacy = home.uiState.first { it.countriesCount == 1 }
            assertNull(legacy.lastSuccessfulSyncAtEpochMillis)
            repository.status.value = CountryCatalogStatus(1, 300L)
            home.uiState.first { it.lastSuccessfulSyncAtEpochMillis == 300L }
            assertEquals(0, repository.refreshCalls)
        }
    }

    private suspend fun HomeViewModel.awaitReady(): HomeUiState =
        uiState.first { !it.isReadingCatalog }

    private suspend fun withHome(
        repository: FakeRepository = FakeRepository(),
        block: suspend (FakeRepository, CountrySyncCoordinator, HomeViewModel) -> Unit,
    ) {
        withTimeout(5_000.milliseconds) {
            withContext(Dispatchers.Main.immediate) {
                coroutineScope {
                    val coordinator = coordinator(repository)
                    val home = HomeViewModel(repository, coordinator)
                    val store = ViewModelStore().apply { put("home", home) }
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) { home.uiState.collect {} }
                    try {
                        block(repository, coordinator, home)
                    } finally {
                        collector.cancelAndJoin()
                        store.clear()
                    }
                }
            }
        }
    }

    private fun coordinator(repository: CountryRepository) = CountrySyncCoordinator(
        repository,
        object : TimeProvider {
            override fun currentTimeMillis(): Long = 1_000L
        },
        CatalogFreshnessPolicy()
    )

    private class FakeRepository : CountryRepository {
        val status = MutableStateFlow(CountryCatalogStatus(0, null))
        val activeObservers = MutableStateFlow(0)
        var observationStarts = 0
        var refreshCalls = 0
        var failNextObservation = false
        var refreshResponse: suspend () -> CountryRefreshResult = { CountryRefreshResult.Success(1, 0) }

        override fun observeCountries(): Flow<List<Country>> =
            error("Home must observe catalog status instead of loading all countries")

        override fun observeCatalogStatus(): Flow<CountryCatalogStatus> = flow {
            observationStarts++
            activeObservers.value++
            try {
                if (failNextObservation) {
                    failNextObservation = false
                    throw IOException("Simulated local read failure")
                }
                emitAll(status)
            } finally {
                activeObservers.value--
            }
        }

        override suspend fun getCatalogStatus(): Result<CountryCatalogStatus> =
            Result.success(status.value)

        override suspend fun getCountries(): List<Country> = error("Home must not read all countries")
        override suspend fun refreshCountries(): CountryRefreshResult {
            refreshCalls++
            return refreshResponse()
        }
    }

}
