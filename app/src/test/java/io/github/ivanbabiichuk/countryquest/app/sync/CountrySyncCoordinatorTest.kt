package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider
import java.io.IOException
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
        val coordinator = coordinator(repository)
        assertEquals(CountrySyncState(), coordinator.state.value)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `initialize refreshes once and preserves the first result`() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        coordinator.initialize()
        coordinator.initialize()
        assertEquals(1, repository.calls)
        assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
    }

    @Test
    fun `failed initialization is retried manually but not automatically`() = runBlocking {
        val failure = CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK)
        val repository = FakeRepository { failure }
        val coordinator = coordinator(repository)
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
        val coordinator = coordinator(repository)
        coordinator.refresh()
        coordinator.initialize()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `refresh exposes busy state and clears previous result until completion`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val repository = FakeRepository()
            val coordinator = coordinator(repository)
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
            val coordinator = coordinator(repository)
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
            val coordinator = coordinator(repository)
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
        val coordinator = coordinator(repository)
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

    @Test
    fun `fresh catalog skips startup request and is checked once`() = runBlocking {
        val repository = FakeRepository()
        repository.statusResponse = { Result.success(CountryCatalogStatus(10, NOW - DAY + 1)) }
        val coordinator = coordinator(repository)
        coordinator.initialize()
        coordinator.initialize()
        assertEquals(1, repository.statusCalls)
        assertEquals(0, repository.calls)
        assertEquals(CountrySyncState(), coordinator.state.value)
    }

    @Test
    fun `stale catalog at exact age limit triggers request`() = runBlocking {
        val repository = FakeRepository()
        repository.statusResponse = { Result.success(CountryCatalogStatus(10, NOW - DAY)) }
        coordinator(repository).initialize()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `empty catalog refreshes even with recent timestamp`() = runBlocking {
        val repository = FakeRepository()
        repository.statusResponse = { Result.success(CountryCatalogStatus(0, NOW)) }
        coordinator(repository).initialize()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `legacy catalog without timestamp refreshes`() = runBlocking {
        val repository = FakeRepository()
        repository.statusResponse = { Result.success(CountryCatalogStatus(10, null)) }
        coordinator(repository).initialize()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `manual refresh bypasses freshness and status read`() = runBlocking {
        val repository = FakeRepository()
        repository.statusResponse = { Result.success(CountryCatalogStatus(10, NOW)) }
        val coordinator = coordinator(repository)
        coordinator.initialize()
        repository.statusResponse = { error("Manual refresh must not read status") }
        coordinator.refresh()
        assertEquals(1, repository.statusCalls)
        assertEquals(1, repository.calls)
        assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
    }

    @Test
    fun `status read failure reports storage error and allows manual retry`() = runBlocking {
        val repository = FakeRepository()
        repository.statusResponse = { Result.failure(IOException("Storage unavailable")) }
        val coordinator = coordinator(repository)
        coordinator.initialize()
        coordinator.initialize()
        assertEquals(1, repository.statusCalls)
        assertEquals(0, repository.calls)
        assertEquals(
            CountrySyncState(lastResult = CountryRefreshResult.Failure(CountryRefreshFailure.STORAGE)),
            coordinator.state.value,
        )
        coordinator.refresh()
        assertEquals(1, repository.calls)
        assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
    }

    @Test
    fun `cancellation while reading status propagates and releases lock`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val repository = FakeRepository()
            val reading = CompletableDeferred<Unit>()
            repository.statusResponse = {
                reading.complete(Unit)
                awaitCancellation()
            }
            val coordinator = coordinator(repository)
            val initialization = async { coordinator.initialize() }
            reading.await()
            initialization.cancelAndJoin()
            assertTrue(initialization.isCancelled)
            assertEquals(CountrySyncState(), coordinator.state.value)
            assertEquals(0, repository.calls)
            coordinator.refresh()
            assertEquals(CountrySyncState(lastResult = success), coordinator.state.value)
        }
    }

    @Test
    fun `overlapping refresh is ignored while startup reads status`() = runBlocking {
        withTimeout(5_000.milliseconds) {
            val repository = FakeRepository()
            val reading = CompletableDeferred<Unit>()
            val finishRead = CompletableDeferred<Unit>()
            repository.statusResponse = {
                reading.complete(Unit)
                finishRead.await()
                Result.success(CountryCatalogStatus(0, null))
            }
            val coordinator = coordinator(repository)
            val initialization = async { coordinator.initialize() }
            reading.await()
            coordinator.initialize()
            coordinator.refresh()
            assertEquals(1, repository.statusCalls)
            assertEquals(0, repository.calls)
            finishRead.complete(Unit)
            initialization.await()
            assertEquals(1, repository.calls)
            coordinator.refresh()
            assertEquals(2, repository.calls)
        }
    }

    private fun coordinator(repository: CountryRepository) = CountrySyncCoordinator(
        repository,
        object : TimeProvider {
            override fun currentTimeMillis(): Long = NOW
        },
        CatalogFreshnessPolicy()
    )

    private class FakeRepository(
        var response: suspend () -> CountryRefreshResult = { success },
    ) : CountryRepository {
        var statusCalls = 0
            private set
        var statusResponse: suspend () -> Result<CountryCatalogStatus> = {
            Result.success(CountryCatalogStatus(0, null))
        }
        override suspend fun getCatalogStatus(): Result<CountryCatalogStatus> {
            statusCalls++
            return statusResponse()
        }
        var calls = 0
            private set
        override fun observeCatalogStatus(): Flow<CountryCatalogStatus> =
            error("Coordinator must use a status snapshot, not observe it")
        override fun observeCountries(): Flow<List<Country>> = flowOf(emptyList())
        override suspend fun getCountries(): List<Country> = emptyList()
        override suspend fun refreshCountries(): CountryRefreshResult {
            calls++
            return response()
        }
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = DAY * 10
        val success = CountryRefreshResult.Success(10, 0)
    }
}
