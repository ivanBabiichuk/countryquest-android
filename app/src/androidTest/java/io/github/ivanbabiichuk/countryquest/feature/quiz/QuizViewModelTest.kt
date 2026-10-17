package io.github.ivanbabiichuk.countryquest.feature.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class QuizViewModelTest {
    private val store = ViewModelStore()
    private var nextKey = 0

    @Test
    fun answerIsAcceptedOnlyOnce() = quizTest {
        val viewModel = startedViewModel()
        val question = activeState(viewModel).currentQuestion
        val wrongOption = question.options.first { it.id != question.correctOptionId }
        viewModel.selectAnswer(question.country.code, question.correctOptionId)
        viewModel.selectAnswer(question.country.code, wrongOption.id)

        val state = activeState(viewModel)
        assertEquals(1, state.answers.size)
        assertEquals(1, state.score)
        assertEquals(question.correctOptionId, state.currentAnswer?.selectedOptionId)
    }

    @Test
    fun cannotSkipUnansweredQuestion() = quizTest {
        val viewModel = startedViewModel()
        viewModel.nextQuestion(activeState(viewModel).currentQuestion.country.code)
        assertEquals(0, activeState(viewModel).questionIndex)
        assertTrue(activeState(viewModel).answers.isEmpty())
    }

    @Test
    fun invalidOptionIsIgnored() = quizTest {
        val viewModel = startedViewModel()
        viewModel.selectAnswer(activeState(viewModel).currentQuestion.country.code, "INVALID")
        assertTrue(activeState(viewModel).answers.isEmpty())
    }

    @Test
    fun oldQuestionEventsCannotAnswerOrSkipNextQuestion() = quizTest {
        val viewModel = startedViewModel()
        val previous = activeState(viewModel).currentQuestion
        viewModel.selectAnswer(previous.country.code, previous.correctOptionId)
        viewModel.nextQuestion(previous.country.code)
        val current = activeState(viewModel).currentQuestion

        viewModel.selectAnswer(previous.country.code, current.correctOptionId)
        viewModel.nextQuestion(previous.country.code)

        assertEquals(1, activeState(viewModel).questionIndex)
        assertEquals(1, activeState(viewModel).answers.size)
        assertNull(activeState(viewModel).currentAnswer)
    }

    @Test
    fun roundFinishesWithTenAnswersAndCorrectScore() = quizTest {
        val viewModel = startedViewModel()
        repeat(10) { index ->
            val question = activeState(viewModel).currentQuestion
            val answer = if (index < 7) question.correctOptionId
            else question.options.first { it.id != question.correctOptionId }.id
            viewModel.selectAnswer(question.country.code, answer)
            viewModel.nextQuestion(question.country.code)
        }

        val finished = viewModel.uiState.first { it is QuizUiState.Finished } as QuizUiState.Finished
        assertEquals(10, finished.totalQuestions)
        assertEquals(7, finished.score)
        assertEquals(QuizMode.CAPITAL, finished.result.mode)
    }

    @Test
    fun startingAgainDoesNotResetActiveRoundOrReadCatalogAgain() = quizTest {
        val repository = FakeCountryRepository()
        val viewModel = startedViewModel(repository)
        val initial = activeState(viewModel)
        viewModel.startRound(QuizMode.FLAG)
        assertEquals(initial, viewModel.uiState.value)
        assertEquals(1, repository.readCalls)
    }

    @Test
    fun insufficientDataAllowsRetryFromUpdatedCatalog() = quizTest {
        val repository = FakeCountryRepository(emptyList())
        val viewModel = createViewModel(repository)
        viewModel.startRound(QuizMode.CAPITAL)
        assertEquals(
            QuizUiState.NotEnoughCountries(0, 10),
            viewModel.uiState.first { it is QuizUiState.NotEnoughCountries },
        )

        repository.countries = countries()
        viewModel.startRound(QuizMode.CAPITAL)
        awaitActive(viewModel)
        assertEquals(2, repository.readCalls)
    }

    @Test
    fun constructionStaysIdleWithoutReadingOrRefreshing() = quizTest {
        val repository = FakeCountryRepository()
        val viewModel = createViewModel(repository)
        assertEquals(QuizUiState.Idle, viewModel.uiState.value)
        assertEquals(0, repository.readCalls)
        assertEquals(0, repository.refreshCalls)
        assertEquals(0, repository.observationCalls)
    }

    @Test
    fun loadingPreventsDuplicateStartsAndIgnoresAnswerEvents() = quizTest {
        val pending = CompletableDeferred<List<Country>>()
        val repository = FakeCountryRepository().apply { read = { pending.await() } }
        val viewModel = createViewModel(repository)
        viewModel.startRound(QuizMode.CAPITAL)
        assertEquals(QuizUiState.Loading, viewModel.uiState.value)

        viewModel.startRound(QuizMode.FLAG)
        viewModel.selectAnswer("TEST_1", "TEST_1")
        viewModel.nextQuestion("TEST_1")
        assertEquals(QuizUiState.Loading, viewModel.uiState.value)
        assertEquals(1, repository.readCalls)

        pending.complete(countries())
        val state = awaitActive(viewModel)
        assertTrue(state.questions.all { it.mode == QuizMode.CAPITAL })
        assertTrue(state.answers.isEmpty())
    }

    @Test
    fun readFailureShowsLoadErrorAndAllowsRetry() = quizTest {
        val repository = FakeCountryRepository().apply { read = { throw IOException("Read failed") } }
        val viewModel = createViewModel(repository)
        viewModel.startRound(QuizMode.CAPITAL)
        assertEquals(QuizUiState.LoadError, viewModel.uiState.first { it == QuizUiState.LoadError })
        viewModel.selectAnswer("TEST_1", "TEST_1")
        viewModel.nextQuestion("TEST_1")
        assertEquals(QuizUiState.LoadError, viewModel.uiState.value)

        repository.read = { repository.countries }
        viewModel.startRound(QuizMode.CAPITAL)
        awaitActive(viewModel)
        assertEquals(2, repository.readCalls)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun eachModeUsesOneSnapshotFromRoomWithoutNetworkOrObservation() = quizTest {
        for (mode in QuizMode.entries) {
            val repository = FakeCountryRepository()
            val viewModel = createViewModel(repository)
            viewModel.startRound(mode)
            val state = awaitActive(viewModel)
            assertEquals(10, state.questions.size)
            assertEquals(10, state.questions.map { it.country.code }.distinct().size)
            assertTrue(state.questions.all { it.mode == mode && it.options.size == 4 })
            assertEquals(1, repository.readCalls)
            assertEquals(0, repository.refreshCalls)
            assertEquals(0, repository.observationCalls)
        }
    }

    @Test
    fun catalogReplacementDoesNotChangeQuestionsOrSubmittedAnswers() = quizTest {
        val repository = FakeCountryRepository()
        val viewModel = startedViewModel(repository)
        val first = activeState(viewModel).currentQuestion
        viewModel.selectAnswer(first.country.code, first.correctOptionId)
        val snapshot = activeState(viewModel)

        repository.countries = countries().map { it.copy(name = "Changed", capital = "Changed capital") }
        viewModel.startRound(QuizMode.CAPITAL)
        assertEquals(snapshot, viewModel.uiState.value)
        viewModel.nextQuestion(first.country.code)
        assertEquals(snapshot.questions, activeState(viewModel).questions)
        assertEquals(snapshot.answers, activeState(viewModel).answers)
        assertEquals(1, repository.readCalls)
    }

    @Test
    fun finishedRoundIgnoresRestartAndLateEvents() = quizTest {
        val repository = FakeCountryRepository()
        val viewModel = startedViewModel(repository)
        var lastCode = ""
        repeat(10) {
            val question = activeState(viewModel).currentQuestion
            lastCode = question.country.code
            viewModel.selectAnswer(lastCode, question.correctOptionId)
            viewModel.nextQuestion(lastCode)
        }
        val finished = viewModel.uiState.first { it is QuizUiState.Finished } as QuizUiState.Finished
        assertEquals(10, finished.score)
        viewModel.startRound(QuizMode.FLAG)
        viewModel.nextQuestion(lastCode)
        viewModel.selectAnswer(lastCode, lastCode)
        assertEquals(finished, viewModel.uiState.value)
        assertEquals(1, repository.readCalls)
    }

    @Test
    fun clearingViewModelCancelsPendingReadWithoutPublishingLoadError() = quizTest {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val repository = FakeCountryRepository().apply {
            read = {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val viewModel = createViewModel(repository)
        viewModel.startRound(QuizMode.CAPITAL)
        entered.await()
        assertEquals(QuizUiState.Loading, viewModel.uiState.value)
        store.clear()
        cancelled.await()
        // The cancelled ViewModel must not report cancellation as a loading failure.
        assertEquals(QuizUiState.Loading, viewModel.uiState.value)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun savesOnlyAfterFinalConfirmationAndWaitsForStorage() = quizTest {
        val pending = CompletableDeferred<Unit>()
        val history = FakeGameHistoryRepository().apply { save = { pending.await() } }
        val viewModel = createViewModel(FakeCountryRepository(), history)
        viewModel.startRound(QuizMode.CAPITAL)
        awaitActive(viewModel)
        val last = answerRound(viewModel)
        assertTrue(history.attempts.isEmpty())
        val before = System.currentTimeMillis()
        viewModel.nextQuestion(last)
        val saving = viewModel.uiState.value as QuizUiState.Saving
        val saved = history.attempts.single()
        assertEquals(saving.result, saved)
        assertEquals(10, saved.totalQuestions)
        assertEquals(7, saved.score)
        assertEquals(QuizMode.CAPITAL, saved.mode)
        assertTrue(saved.id.isNotBlank())
        assertTrue(saved.completedAtEpochMillis in before..System.currentTimeMillis())
        pending.complete(Unit)
        val finished = viewModel.uiState.first { it is QuizUiState.Finished } as QuizUiState.Finished
        assertEquals(saved, finished.result)
    }

    @Test
    fun savingIgnoresDoubleFinishRetryRestartAndAnswerEvents() = quizTest {
        val pending = CompletableDeferred<Unit>()
        val history = FakeGameHistoryRepository().apply { save = { pending.await() } }
        val countries = FakeCountryRepository()
        val viewModel = createViewModel(countries, history)
        viewModel.startRound(QuizMode.CAPITAL)
        awaitActive(viewModel)
        val last = answerRound(viewModel)
        viewModel.nextQuestion(last)
        val saving = viewModel.uiState.value
        repeat(3) {
            viewModel.nextQuestion(last)
            viewModel.retrySave()
            viewModel.startRound(QuizMode.FLAG)
            viewModel.selectAnswer(last, last)
        }
        assertEquals(saving, viewModel.uiState.value)
        assertEquals(1, history.attempts.size)
        assertEquals(1, countries.readCalls)
        pending.complete(Unit)
        viewModel.uiState.first { it is QuizUiState.Finished }
        viewModel.retrySave()
        assertEquals(1, history.attempts.size)
    }

    @Test
    fun repeatedSaveFailuresAndRecoveryReuseExactSnapshot() = quizTest {
        val history = FakeGameHistoryRepository().apply { save = { throw IOException("Disk unavailable") } }
        val countries = FakeCountryRepository()
        val viewModel = createViewModel(countries, history)
        viewModel.startRound(QuizMode.COUNTRY)
        awaitActive(viewModel)
        val last = answerRound(viewModel)
        viewModel.nextQuestion(last)
        val error = viewModel.uiState.first { it is QuizUiState.SaveError } as QuizUiState.SaveError
        countries.countries = emptyList()
        viewModel.startRound(QuizMode.FLAG)
        viewModel.nextQuestion(last)
        viewModel.selectAnswer(last, last)
        assertEquals(error, viewModel.uiState.value)
        assertEquals(1, history.attempts.size)
        viewModel.retrySave()
        viewModel.uiState.first { it is QuizUiState.SaveError }
        assertEquals(2, history.attempts.size)
        val pending = CompletableDeferred<Unit>()
        history.save = { pending.await() }
        viewModel.retrySave()
        viewModel.retrySave()
        assertTrue(viewModel.uiState.value is QuizUiState.Saving)
        assertEquals(3, history.attempts.size)
        assertTrue(history.attempts.all { it == error.result })
        pending.complete(Unit)
        val finished = viewModel.uiState.first { it is QuizUiState.Finished } as QuizUiState.Finished
        assertEquals(error.result, finished.result)
        assertEquals(1, countries.readCalls)
    }

    @Test
    fun retryOutsideSaveErrorDoesNotWriteAnything() = quizTest {
        val history = FakeGameHistoryRepository()
        val viewModel = createViewModel(FakeCountryRepository(), history)
        viewModel.retrySave()
        assertEquals(QuizUiState.Idle, viewModel.uiState.value)
        viewModel.startRound(QuizMode.FLAG)
        awaitActive(viewModel)
        viewModel.retrySave()
        assertTrue(viewModel.uiState.value is QuizUiState.Active)
        assertTrue(history.attempts.isEmpty())
    }

    @Test
    fun clearingViewModelCancelsSaveWithoutReportingStorageErrorOrSuccess() = quizTest {
        val cancelled = CompletableDeferred<Unit>()
        val history = FakeGameHistoryRepository().apply {
            save = { try { awaitCancellation() } finally { cancelled.complete(Unit) } }
        }
        val viewModel = createViewModel(FakeCountryRepository(), history)
        viewModel.startRound(QuizMode.FLAG)
        awaitActive(viewModel)
        viewModel.nextQuestion(answerRound(viewModel))
        val saving = viewModel.uiState.value as QuizUiState.Saving
        store.clear()
        cancelled.await()
        assertEquals(saving, viewModel.uiState.value)
    }

    private fun answerRound(viewModel: QuizViewModel): String {
        var lastCode = ""
        repeat(10) { index ->
            val question = activeState(viewModel).currentQuestion
            lastCode = question.country.code
            val option = if (index < 7) question.correctOptionId
                else question.options.first { it.id != question.correctOptionId }.id
            viewModel.selectAnswer(lastCode, option)
            if (index < 9) viewModel.nextQuestion(lastCode)
        }
        return lastCode
    }

    private class FakeGameHistoryRepository : GameHistoryRepository {
        val attempts = mutableListOf<GameResult>()
        var save: suspend (GameResult) -> Unit = {}
        override suspend fun saveCompletedGame(result: GameResult) {
            attempts.add(result)
            save(result)
        }
        override fun observeResults(): Flow<List<GameResult>> = error("Quiz must not observe history")
        override suspend fun getResult(gameId: String): GameResult? = error("Quiz must not read history")
    }

    private fun quizTest(block: suspend () -> Unit) = runBlocking {
        withTimeout(5_000.milliseconds) {
            withContext(Dispatchers.Main.immediate) {
                try {
                    block()
                } finally {
                    store.clear()
                }
            }
        }
    }

    private fun createViewModel(
        repository: FakeCountryRepository,
        history: FakeGameHistoryRepository = FakeGameHistoryRepository()
    ) =
        QuizViewModel(QuizRoundGenerator(Random(42)), repository, history).also {
            store.put("quiz-${nextKey++}", it)
        }

    private suspend fun startedViewModel(
        repository: FakeCountryRepository = FakeCountryRepository(),
    ): QuizViewModel = createViewModel(repository).also {
        it.startRound(QuizMode.CAPITAL)
        awaitActive(it)
    }

    private suspend fun awaitActive(viewModel: QuizViewModel): QuizUiState.Active =
        viewModel.uiState.first { it is QuizUiState.Active } as QuizUiState.Active

    private fun activeState(viewModel: QuizViewModel): QuizUiState.Active =
        viewModel.uiState.value as QuizUiState.Active

    private class FakeCountryRepository(
        var countries: List<Country> = countries(),
    ) : CountryRepository {
        var readCalls = 0
        var refreshCalls = 0
        var observationCalls = 0
        var read: suspend () -> List<Country> = { countries }

        override suspend fun getCountries(): List<Country> {
            readCalls++
            return read()
        }

        override fun observeCountries(): Flow<List<Country>> {
            observationCalls++
            error("A quiz must read a snapshot instead of observing catalog changes")
        }

        override suspend fun refreshCountries(): CountryRefreshResult {
            refreshCalls++
            error("Starting a quiz must not refresh the API")
        }
    }

    private companion object {
        fun countries(): List<Country> = (1..12).map { index ->
            Country(
                code = "TEST_$index", name = "Country $index", capital = "Capital $index",
                region = "Test region", languages = listOf("Test language"),
                flag = CountryFlag(null, "https://example.com/flags/test_$index.png"),
            )
        }
    }
}
