package io.github.ivanbabiichuk.countryquest.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ivanbabiichuk.countryquest.domain.model.AnswerOption
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.model.QuizQuestion
import io.github.ivanbabiichuk.countryquest.feature.quiz.components.CountryFlagImage
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme

@Composable
fun QuizScreen(
    mode: QuizMode,
    uiState: QuizUiState,
    onRetry: () -> Unit,
    onSelectAnswer: (questionCode: String, optionId: String) -> Unit,
    onNextQuestion: (questionCode: String) -> Unit,
    onExitQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val questionIndex = (uiState as? QuizUiState.Active)?.questionIndex

    LaunchedEffect(questionIndex) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (mode) {
                QuizMode.FLAG -> "Guess the Flag"
                QuizMode.CAPITAL -> "Guess the Capital"
                QuizMode.COUNTRY -> "Guess the Country"
            },
            style = MaterialTheme.typography.headlineLarge
        )

        when (uiState) {
            QuizUiState.Idle,
            QuizUiState.Loading -> {
                CircularProgressIndicator()
                Text("Preparing your round…")
            }

            QuizUiState.LoadError -> {
                Text("Could not load saved countries.")

                Button(onClick = onRetry) {
                    Text("Try again")
                }
            }

            is QuizUiState.NotEnoughCountries -> {
                Text("Not enough countries for this challenge.")

                Text(
                    "Available: ${uiState.available}. " +
                            "Required: ${uiState.required}."
                )

                Text("Return to Home to update the country catalog.")

                OutlinedButton(onClick = onRetry) {
                    Text("Try again")
                }
            }

            is QuizUiState.Active -> {
                key(
                    uiState.questionIndex,
                    uiState.currentQuestion.country.code
                ) {
                    QuizQuestionContent(
                        state = uiState,
                        onSelectAnswer = onSelectAnswer,
                        onNextQuestion = onNextQuestion,
                    )
                }
            }

            is QuizUiState.Finished -> {
                Text("Round complete!")
            }
        }

        if (uiState !is QuizUiState.Finished) {
            OutlinedButton(onClick = onExitQuiz) {
                Text("Back to Home")
            }
        }
    }
}

@Composable
private fun QuizQuestionContent(
    state: QuizUiState.Active,
    onSelectAnswer: (questionCode: String, optionId: String) -> Unit,
    onNextQuestion: (questionCode: String) -> Unit
) {
    val question = state.currentQuestion
    val country = question.country
    val answer = state.currentAnswer

    var isFlagReady by remember(country.code, country.flag) {
        mutableStateOf(false)
    }

    val canAnswer = answer == null &&
            (question.mode != QuizMode.FLAG || isFlagReady)

    Text(
        text = "Question ${state.questionNumber} of ${state.totalQuestions}",
        style = MaterialTheme.typography.titleMedium
    )

    LinearProgressIndicator(
        progress = {
            state.answers.size.toFloat() / state.totalQuestions
        },
        modifier = Modifier.fillMaxWidth()
    )

    Text("Score: ${state.score}")

    when (question.mode) {
        QuizMode.FLAG -> {
            Text("Which country does this flag belong to?")

            CountryFlagImage(
                flag = country.flag,
                onReadyChanged = { isFlagReady = it }
            )
        }

        QuizMode.CAPITAL -> {
            Text(
                text = "What is the capital of ${country.name}?",
                style = MaterialTheme.typography.titleLarge
            )
        }

        QuizMode.COUNTRY -> {
            Text(
                text = "Which country matches these clues?",
                style = MaterialTheme.typography.titleLarge
            )

            Text("Capital: ${country.capital.orEmpty()}")
            Text("Region: ${country.region}")
            Text("Languages: ${country.languages.joinToString()}")
        }
    }

    question.options.forEach { option ->
        val label = when {
            answer == null -> option.text

            option.id == question.correctOptionId ->
                "${option.text} — Correct"

            option.id == answer.selectedOptionId ->
                "${option.text} — Your answer"

            else -> option.text
        }

        OutlinedButton(
            onClick = {
                onSelectAnswer(country.code, option.id)
            },
            enabled = canAnswer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label)
        }
    }

    if (answer != null) {
        val correctOption = question.options.first {
            it.id == question.correctOptionId
        }

        Text(
            text = if (answer.isCorrect) {
                "Correct!"
            } else {
                "Correct answer: ${correctOption.text}"
            },
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = country.name,
            style = MaterialTheme.typography.titleMedium
        )

        if (country.region.isNotBlank()) {
            Text("Region: ${country.region}")
        }

        country.capital?.let { capital ->
            Text("Capital: $capital")
        }

        if (country.languages.isNotEmpty()) {
            Text("Languages: ${country.languages.joinToString()}")
        }

        Button(
            onClick = { onNextQuestion(country.code) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.isLastQuestion) {
                    "Finish round"
                } else {
                    "Next question"
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuizScreenPreview() {
    val country = Country(
        code = "UA",
        name = "Ukraine",
        capital = "Kyiv",
        region = "Europe",
        languages = listOf("Ukrainian"),
        flag = CountryFlag(
            svgUrl = null,
            pngUrl = null
        )
    )

    val question = QuizQuestion(
        mode = QuizMode.CAPITAL,
        country = country,
        options = listOf(
            AnswerOption("UA", "Kyiv"),
            AnswerOption("FR", "Paris"),
            AnswerOption("PL", "Warsaw"),
            AnswerOption("DE", "Berlin")
        )
    )

    CountryQuestTheme {
        Surface {
            QuizScreen(
                mode = QuizMode.CAPITAL,
                uiState = QuizUiState.Active(
                    questions = listOf(question),
                ),
                onRetry = {},
                onSelectAnswer = { _, _ -> },
                onNextQuestion = {},
                onExitQuiz = {}
            )
        }
    }
}