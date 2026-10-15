package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CountrySyncCoordinatorTest {
    @Test
    fun `initial state is idle and construction does not request data`() {
        val repository = FakeRepository()
        val coordinator = CountrySyncCoordinator(repository)
        assertEquals(CountrySyncState(), coordinator.state.value)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `initialize refreshes once and preserves the first result`() = runBlocking {
        val repository = FakeRepository()
        val coordinator = CountrySyncCoordinator(repository)
        coordinator.initialize()
        coordinator.initialize()
        assertEquals(1, repository.calls)
        assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
    }

    @Test
    fun `failed initialization is retried manually but not automatically`() = runBlocking {
        val failure = CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK)
        val repository = FakeRepository { failure }
        val coordinator = CountrySyncCoordinator(repository)
        coordinator.initialize()
        coordinator.initialize()
        assertEquals(1, repository.calls)
        assertEquals(CountrySyncState(lastResult = failure), coordinator.state.value)

        repository.response = { success }
        coordinator.refresh()
        assertEquals(2, repository.calls)
        assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
    }

    @Test
    fun `manual refresh before initialization prevents a redundant startup request`() = runBlocking {
        val repository = FakeRepository()
        val coordinator = CountrySyncCoordinator(repository)
        coordinator.refresh()
        coordinator.initialize()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `refresh exposes busy state and clears previous result until completion`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val repository = FakeRepository()
            val coordinator = CountrySyncCoordinator(repository)
            coordinator.initialize()
            val finish = CompletableDeferred<CountryRefreshResult>()
            repository.response = { finish.await() }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refresh() }

            assertEquals(CountrySyncState(isRefreshing = true), coordinator.state.value)
            val partial = CountryRefreshResult.Success(7, 3)
            finish.complete(partial)
            refresh.await()
            assertEquals(CountrySyncState(lastResult = partial), coordinator.state.value)
        }
    }

    @Test
    fun `overlapping startup and manual calls are ignored rather than queued`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val finish = CompletableDeferred<Unit>()
            val repository = FakeRepository { finish.await(); success }
            val coordinator = CountrySyncCoordinator(repository)
            val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.initialize() }
            coordinator.initialize()
            coordinator.refresh()
            coordinator.refresh()
            assertEquals(1, repository.calls)
            assertTrue(coordinator.state.value.isRefreshing)

            finish.complete(Unit)
            first.await()
            assertEquals(1, repository.calls)
            coordinator.refresh()
            assertEquals(2, repository.calls)
        }
    }

    @Test
    fun `cancellation clears busy state and releases lock for manual retry`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val repository = FakeRepository { awaitCancellation() }
            val coordinator = CountrySyncCoordinator(repository)
            val request = async(start = CoroutineStart.UNDISPATCHED) { coordinator.initialize() }
            assertTrue(coordinator.state.value.isRefreshing)
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertEquals(CountrySyncState(), coordinator.state.value)

            repository.response = { success }
            coordinator.refresh()
            assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
        }
    }

    @Test
    fun `unexpected exception propagates without leaving coordinator locked`() = runBlocking {
        val bug = IllegalStateException("Unexpected bug")
        val repository = FakeRepository { throw bug }
        val coordinator = CountrySyncCoordinator(repository)
        try {
            coordinator.refresh()
            fail("Unexpected exception must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(bug, actual)
        }
        assertEquals(CountrySyncState(), coordinator.state.value)
        repository.response = { success }
        withTimeout(5_000.milliseconds) { coordinator.refresh() }
        assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
    }

    private class FakeRepository(
        var response: suspend () -> CountryRefreshResult = { success },
    ) : CountryRepository {
        var calls = 0
            private set
        override fun observeCountries(): Flow<List<Country>> = flowOf(emptyList())
        override suspend fun getCountries(): List<Country> = emptyList()
        override suspend fun refreshCountries(): CountryRefreshResult {
            calls++
            return response()
        }
    }

    private companion object {
        val success = CountryRefreshResult.Success(10, 0)
    }
}
