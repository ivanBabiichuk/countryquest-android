package io.github.ivanbabiichuk.countryquest.feature.quiz

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ivanbabiichuk.countryquest.domain.model.QuizAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizGenerationResult
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val roundGenerator: QuizRoundGenerator,
    private val countryRepository: CountryRepository
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

    fun nextQuestion(questionCode: String) {
        _uiState.update { state ->
            if (state !is QuizUiState.Active) return@update state

            if (state.currentQuestion.country.code != questionCode) {
                return@update state
            }

            if (state.currentAnswer == null) return@update state

            if (state.isLastQuestion) {
                QuizUiState.Finished(
                    mode = state.currentQuestion.mode,
                    answers = state.answers,
                )
            } else {
                state.copy(
                    questionIndex = state.questionIndex + 1,
                )
            }
        }
    }
}