package io.github.ivanbabiichuk.countryquest.feature.quiz

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ivanbabiichuk.countryquest.domain.mapper.toGameAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.model.QuizAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizGenerationResult
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import io.github.ivanbabiichuk.countryquest.domain.repository.GameHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val roundGenerator: QuizRoundGenerator,
    private val countryRepository: CountryRepository,
    private val gameHistoryRepository: GameHistoryRepository
): ViewModel() {

    private companion object {
        const val TAG = "QuizViewModel"
    }

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Idle)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    @MainThread
    fun startRound(mode: QuizMode) {
        when (_uiState.value) {
            QuizUiState.Idle,
            QuizUiState.LoadError,
            is QuizUiState.NotEnoughCountries -> Unit

            QuizUiState.Loading,
            is QuizUiState.Active,
            is QuizUiState.Saving,
            is QuizUiState.SaveError,
            is QuizUiState.Finished -> return
        }

        _uiState.value = QuizUiState.Loading

        viewModelScope.launch {
            val countries = try {
                countryRepository.getCountries()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to load countries for quiz", exception)
                _uiState.value = QuizUiState.LoadError
                return@launch
            }

            _uiState.value = when (
                val result = roundGenerator.generate(countries, mode)
            ) {
                is QuizGenerationResult.Success -> {
                    QuizUiState.Active(questions = result.questions)
                }

                is QuizGenerationResult.InsufficientCountries -> {
                    QuizUiState.NotEnoughCountries(
                        available = result.available,
                        required = result.required
                    )
                }
            }
        }
    }

    fun selectAnswer(
        questionCode: String,
        optionId: String
    ) {
        _uiState.update { state ->
            if (state !is QuizUiState.Active) return@update state

            val question = state.currentQuestion

            if (question.country.code != questionCode) return@update state
            if (state.currentAnswer != null) return@update state
            if (question.options.none { it.id == optionId }) return@update state

            val answer = QuizAnswer(
                question = question,
                selectedOptionId = optionId,
            )

            state.copy(
                answers = state.answers + answer,
            )
        }
    }

    @MainThread
    fun nextQuestion(questionCode: String) {
        val state = _uiState.value as? QuizUiState.Active ?: return

        if (state.currentQuestion.country.code != questionCode) return
        if (state.currentAnswer == null) return

        if (!state.isLastQuestion) {
            _uiState.value = state.copy(questionIndex = state.questionIndex + 1)
            return
        }

        check(state.answers.size == state.totalQuestions) {
            "All questions must be answered before saving the round."
        }

        val result = GameResult(
            id = UUID.randomUUID().toString(),
            mode = state.currentQuestion.mode,
            completedAtEpochMillis = System.currentTimeMillis(),
            answers = state.answers.map { it.toGameAnswer() }
        )

        saveResult(result)
    }

    @MainThread
    fun retrySave() {
        val state = _uiState.value as? QuizUiState.SaveError ?: return
        saveResult(state.result)
    }

    @MainThread
    private fun saveResult(result: GameResult) {
        _uiState.value = QuizUiState.Saving(result)

        viewModelScope.launch {
            try {
                gameHistoryRepository.saveCompletedGame(result)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save completed game", exception)
                _uiState.value = QuizUiState.SaveError(result)
                return@launch
            }

            _uiState.value = QuizUiState.Finished(result)
        }
    }
}