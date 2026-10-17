package io.github.ivanbabiichuk.countryquest.feature.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.domain.model.*
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class ResultsViewModelTest {
    private val store = ViewModelStore()
    private var key = 0
    private fun result() = GameResult("saved-id", QuizMode.COUNTRY, 100,
        listOf(GameAnswer("UA", "Ukraine", "UA", "Ukraine", "UA", "Ukraine")))
    private class FakeRepository : GameHistoryRepository {
        val ids = mutableListOf<String>()
        var read: suspend () -> GameResult? = { null }
        override suspend fun getResult(gameId: String): GameResult? { ids.add(gameId); return read() }
        override fun observeResults(): Flow<List<GameResult>> = error("Must load only selected game")
        override suspend fun saveCompletedGame(result: GameResult) = error("Results must never save")
    }
    private fun create(repository: FakeRepository) = ResultsViewModel(
        SavedStateHandle(mapOf("gameId" to "saved-id")), repository
    ).also { store.put("results-${key++}", it) }
    private fun check(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(5_000.milliseconds) {
            withContext(Dispatchers.Main.immediate) {
                try { block() } finally { store.clear() }
            }
        }
    }
    @Test fun initialStateIsLoadingAndReadStartsOnSubscription() = check {
        val pending = CompletableDeferred<GameResult?>()
        val entered = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply { read = { entered.complete(Unit); pending.await() } }
        val vm = create(repository)
        assertEquals(ResultsUiState.Loading, vm.uiState.value)
        assertTrue(repository.ids.isEmpty())
        val observed = async { vm.uiState.first { it is ResultsUiState.Content } }
        entered.await()
        assertEquals(ResultsUiState.Loading, vm.uiState.value)
        assertEquals(listOf("saved-id"), repository.ids)
        pending.complete(result())
        assertEquals(ResultsUiState.Content(result()), observed.await())
    }
    @Test fun absentIdShowsNotFoundAndRetryIsIgnored() = check {
        val repository = FakeRepository()
        val vm = create(repository)
        assertEquals(ResultsUiState.NotFound, vm.uiState.first { it != ResultsUiState.Loading })
        vm.retry()
        yield()
        assertEquals(listOf("saved-id"), repository.ids)
        assertEquals(ResultsUiState.NotFound, vm.uiState.value)
    }
    @Test fun readFailureCanRetrySameIdAndRecover() = check {
        val repository = FakeRepository().apply { read = { throw IOException("Read failed") } }
        val vm = create(repository)
        val collector = launch { vm.uiState.collect() }
        try {
            vm.uiState.first { it == ResultsUiState.LoadError }
            val pending = CompletableDeferred<GameResult?>()
            repository.read = { pending.await() }
            vm.retry()
            vm.uiState.first { it == ResultsUiState.Loading }
            vm.retry()
            pending.complete(result())
            assertEquals(ResultsUiState.Content(result()), vm.uiState.first { it is ResultsUiState.Content })
            assertEquals(listOf("saved-id", "saved-id"), repository.ids)
        } finally { collector.cancelAndJoin() }
    }
    @Test fun successfulResultIgnoresRetryAndSharesReadAcrossCollectors() = check {
        val repository = FakeRepository().apply { read = { result() } }
        val vm = create(repository)
        val first = async { vm.uiState.first { it is ResultsUiState.Content } }
        val second = async { vm.uiState.first { it is ResultsUiState.Content } }
        assertEquals(first.await(), second.await())
        vm.retry()
        yield()
        assertEquals(1, repository.ids.size)
    }
    @Test fun recreatedViewModelReadsSavedRouteIdAgain() = check {
        val repository = FakeRepository().apply { read = { result() } }
        val original = create(repository)
        val state = original.uiState.first { it is ResultsUiState.Content }
        store.clear()
        val recreated = create(repository)
        assertEquals(state, recreated.uiState.first { it is ResultsUiState.Content })
        assertEquals(listOf("saved-id", "saved-id"), repository.ids)
    }
    @Test fun clearingViewModelCancelsReadWithoutPublishingError() = check {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply {
            read = {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        val vm = create(repository)
        val collector = launch { vm.uiState.collect() }
        try {
            entered.await()
            store.clear()
            cancelled.await()
            assertEquals(ResultsUiState.Loading, vm.uiState.value)
        } finally { collector.cancelAndJoin() }
    }
}
