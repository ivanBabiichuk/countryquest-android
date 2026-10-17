package io.github.ivanbabiichuk.countryquest.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ivanbabiichuk.countryquest.domain.model.GameAnswer
import io.github.ivanbabiichuk.countryquest.domain.model.GameResult
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme

@Composable
fun ResultsScreen(
    uiState: ResultsUiState,
    onRetry: () -> Unit,
    onPlayAgain: (QuizMode) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.headlineLarge
        )

        when (uiState) {
            ResultsUiState.Loading -> {
                CircularProgressIndicator()
                Text("Loading result…")
            }

            ResultsUiState.LoadError -> {
                Text("Could not load your result.")

                Button(onClick = onRetry) {
                    Text("Try again")
                }
            }

            ResultsUiState.NotFound -> {
                Text("This result could not be found.")
            }

            is ResultsUiState.Content -> {
                val result = uiState.result

                Text(
                    text = when (result.mode) {
                        QuizMode.FLAG -> "Guess the Flag"
                        QuizMode.CAPITAL -> "Guess the Capital"
                        QuizMode.COUNTRY -> "Guess the Country"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "${result.score} / ${result.totalQuestions}",
                    style = MaterialTheme.typography.displayMedium
                )

                Text("Correct answers")

                Button(
                    onClick = { onPlayAgain(result.mode) }
                ) {
                    Text("Play Again")
                }
            }
        }

        OutlinedButton(onClick = onGoHome) {
            Text("Back to Home")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenContentPreview() {
    val answers = (0 until 10).map { index ->
        val code = "PREVIEW_$index"

        GameAnswer(
            countryCode = code,
            countryName = "Country $index",
            selectedOptionId = if (index < 7) code else "OTHER",
            selectedOptionText = if (index < 7) {
                "Capital $index"
            } else {
                "Another capital"
            },
            correctOptionId = code,
            correctOptionText = "Capital $index"
        )
    }

    ResultsPreview(
        uiState = ResultsUiState.Content(
            GameResult(
                id = "preview",
                mode = QuizMode.CAPITAL,
                completedAtEpochMillis = 0,
                answers = answers
            )
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenLoadingPreview() {
    ResultsPreview(ResultsUiState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenErrorPreview() {
    ResultsPreview(ResultsUiState.LoadError)
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenNotFoundPreview() {
    ResultsPreview(ResultsUiState.NotFound)
}

@Composable
private fun ResultsPreview(uiState: ResultsUiState) {
    CountryQuestTheme {
        Surface {
            ResultsScreen(
                uiState = uiState,
                onRetry = {},
                onPlayAgain = {},
                onGoHome = {}
            )
        }
    }
}