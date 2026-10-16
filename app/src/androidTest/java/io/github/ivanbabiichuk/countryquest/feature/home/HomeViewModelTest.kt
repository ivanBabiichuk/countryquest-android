package io.github.ivanbabiichuk.countryquest.feature.home

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.app.AppViewModel
import io.github.ivanbabiichuk.countryquest.app.sync.CountrySyncCoordinator
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
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

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @Test
    fun homeObservesLocalChangesWithoutStartingRefresh() = runBlocking {
        withHome { repository, _, home ->
            assertEquals(0, home.awaitReady().countriesCount)
            repository.countries.value = listOf(country("UA"))
            val populated = home.uiState.first { it.countriesCount == 1 }
            assertTrue(populated.hasCountries)
            repository.countries.value = emptyList()
            val empty = home.uiState.first { !it.isReadingCatalog && it.countriesCount == 0 }
            assertFalse(empty.hasCountries)
            assertEquals(0, repository.refreshCalls)
        }
    }

    @Test
    fun appInitializationIsSharedWithHomeAndRunsOnlyOnce() = runBlocking {
        withHome { repository, coordinator, home ->
            repository.countries.value = listOf(country("UA"), country("FR"))
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
            repository.countries.value = listOf(country("UA"))
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
            repo.countries.value = listOf(country("UA"))

            home.refreshCountries()
            val recovered = home.uiState.first { !it.hasCatalogReadError && it.countriesCount == 1 }
            assertFalse(recovered.isReadingCatalog)
            assertEquals(2, repo.observationStarts)
            assertEquals(1, repo.activeObservers.value)

            repo.countries.value = listOf(country("UA"), country("FR"))
            home.uiState.first { it.countriesCount == 2 }
            assertEquals(1, repo.refreshCalls)
        }
    }

    @Test
    fun subscriptionStopsAfterGracePeriodAndResumesWithoutNetworkRefresh() = runBlocking {
        withTimeout(12_000) {
            withContext(Dispatchers.Main.immediate) {
                val repository = FakeRepository()
                val home = HomeViewModel(repository, CountrySyncCoordinator(repository))
                val store = ViewModelStore().apply { put("home", home) }
                val firstCollector = launch(start = CoroutineStart.UNDISPATCHED) { home.uiState.collect {} }
                try {
                    home.awaitReady()
                    assertEquals(1, repository.activeObservers.value)
                    firstCollector.cancelAndJoin()
                    repository.activeObservers.first { it == 0 }
                    repository.countries.value = listOf(country("UA"))
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
        withTimeout(5_000) {
            withContext(Dispatchers.Main.immediate) {
                val finish = CompletableDeferred<CountryRefreshResult>()
                val repository = FakeRepository().apply { refreshResponse = { finish.await() } }
                val coordinator = CountrySyncCoordinator(repository)
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

    private suspend fun HomeViewModel.awaitReady(): HomeUiState =
        uiState.first { !it.isReadingCatalog }

    private suspend fun withHome(
        repository: FakeRepository = FakeRepository(),
        block: suspend (FakeRepository, CountrySyncCoordinator, HomeViewModel) -> Unit,
    ) {
        withTimeout(5_000) {
            withContext(Dispatchers.Main.immediate) {
                coroutineScope {
                    val coordinator = CountrySyncCoordinator(repository)
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

    private class FakeRepository : CountryRepository {
        val countries = MutableStateFlow<List<Country>>(emptyList())
        val activeObservers = MutableStateFlow(0)
        var observationStarts = 0
        var refreshCalls = 0
        var failNextObservation = false
        var refreshResponse: suspend () -> CountryRefreshResult = { CountryRefreshResult.Success(1, 0) }

        override fun observeCountries(): Flow<List<Country>> = flow {
            observationStarts++
            activeObservers.value++
            try {
                if (failNextObservation) {
                    failNextObservation = false
                    throw IOException("Simulated local read failure")
                }
                emitAll(countries)
            } finally {
                activeObservers.value--
            }
        }

        override suspend fun getCountries(): List<Country> = countries.value
        override suspend fun refreshCountries(): CountryRefreshResult {
            refreshCalls++
            return refreshResponse()
        }
    }

    private fun country(code: String) = Country(
        code = code, name = "Country $code", capital = null, region = "Europe",
        languages = emptyList(), flag = CountryFlag(null, null),
    )
}
