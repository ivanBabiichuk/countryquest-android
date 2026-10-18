package io.github.ivanbabiichuk.countryquest.feature.statistics

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.domain.model.*
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import io.github.ivanbabiichuk.countryquest.domain.statistics.QuizStatisticsCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class StatisticsViewModelTest {
    private val store = ViewModelStore()
    private class Repository : GameHistoryRepository {
        val subscriptions = AtomicInteger()
        val values = MutableStateFlow<List<GameResult>>(emptyList())
        var source: () -> Flow<List<GameResult>> = { values }
        override fun observeResults(): Flow<List<GameResult>> = flow {
            subscriptions.incrementAndGet()
            emitAll(source())
        }
        override suspend fun getResult(gameId: String): GameResult? = error("No individual reads expected")
        override suspend fun saveCompletedGame(result: GameResult) = error("Statistics must not write history")
    }
    private fun create(repository: Repository) = StatisticsViewModel(repository, QuizStatisticsCalculator())
        .also { store.put("statistics", it) }
    private fun check(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(5_000.milliseconds) { withContext(Dispatchers.Main.immediate) {
            try { block() } finally { store.clear() }
        } }
    }
    private fun game(id: String = "game", mode: QuizMode = QuizMode.FLAG) = GameResult(
        id, mode, 100, listOf(GameAnswer("UA", "Ukraine", "UA", "Ukraine", "UA", "Ukraine"))
    )

    @Test fun startsLoadingWithoutObservingUntilCollected() = check {
        val repository = Repository()
        val vm = create(repository)
        assertEquals(StatisticsUiState.Loading, vm.uiState.value)
        assertEquals(0, repository.subscriptions.get())
        assertEquals(StatisticsUiState.Empty, vm.uiState.first { it != StatisticsUiState.Loading })
        assertEquals(1, repository.subscriptions.get())
    }

    @Test fun ongoingSubscriptionRecalculatesWhenHistoryChangesAndCanBecomeEmpty() = check {
        val repository = Repository()
        val vm = create(repository)
        val collector = launch { vm.uiState.collect() }
        try {
            vm.uiState.first { it == StatisticsUiState.Empty }
            repository.values.value = listOf(game())
            val first = vm.uiState.first { it is StatisticsUiState.Content } as StatisticsUiState.Content
            assertEquals(1, first.statistics.overall.gamesPlayed)
            repository.values.value = listOf(game(), game("second", QuizMode.CAPITAL))
            val second = vm.uiState.first {
                it is StatisticsUiState.Content && it.statistics.overall.gamesPlayed == 2
            } as StatisticsUiState.Content
            assertEquals(2L, second.statistics.overall.correctAnswers)
            assertEquals(1, second.statistics.byMode.getValue(QuizMode.FLAG).gamesPlayed)
            assertEquals(1, second.statistics.byMode.getValue(QuizMode.CAPITAL).gamesPlayed)
            assertEquals(0, second.statistics.byMode.getValue(QuizMode.COUNTRY).gamesPlayed)
            repository.values.value = emptyList()
            vm.uiState.first { it == StatisticsUiState.Empty }
            assertEquals(1, repository.subscriptions.get())
        } finally { collector.cancelAndJoin() }
    }

    @Test fun failureIsNotEmptyAndRetryCreatesNewSubscription() = check {
        val attempts = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val repository = Repository().apply {
            source = { flow {
                if (attempts.incrementAndGet() == 1) throw IOException("Read failed")
                release.await()
                emit(listOf(game()))
            } }
        }
        val vm = create(repository)
        val collector = launch { vm.uiState.collect() }
        try {
            vm.uiState.first { it == StatisticsUiState.LoadError }
            vm.retry()
            vm.uiState.first { it == StatisticsUiState.Loading }
            vm.retry() // Loading must not trigger another attempt.
            release.complete(Unit)
            vm.uiState.first { it is StatisticsUiState.Content }
            assertEquals(2, repository.subscriptions.get())
        } finally { collector.cancelAndJoin() }
    }

    @Test fun retryOutsideErrorDoesNotResubscribe() = check {
        val repository = Repository()
        val vm = create(repository)
        vm.retry()
        val collector = launch { vm.uiState.collect() }
        try {
            vm.uiState.first { it == StatisticsUiState.Empty }
            vm.retry()
            repository.values.value = listOf(game())
            vm.uiState.first { it is StatisticsUiState.Content }
            vm.retry()
            yield()
            assertEquals(1, repository.subscriptions.get())
        } finally { collector.cancelAndJoin() }
    }

    @Test fun collectorsShareOneHistorySubscription() = check {
        val repository = Repository().apply { values.value = listOf(game()) }
        val vm = create(repository)
        val first = async { vm.uiState.first { it is StatisticsUiState.Content } }
        val second = async { vm.uiState.first { it is StatisticsUiState.Content } }
        assertEquals(first.await(), second.await())
        assertEquals(1, repository.subscriptions.get())
    }

    @Test fun clearingViewModelCancelsObservationWithoutErrorState() = check {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val repository = Repository().apply { source = { flow {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        } } }
        val vm = create(repository)
        val collector = launch { vm.uiState.collect() }
        try {
            entered.await()
            store.clear()
            cancelled.await()
            assertEquals(StatisticsUiState.Loading, vm.uiState.value)
        } finally { collector.cancelAndJoin() }
    }
}
