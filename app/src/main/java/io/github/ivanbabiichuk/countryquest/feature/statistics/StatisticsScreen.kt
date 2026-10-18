package io.github.ivanbabiichuk.countryquest.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.model.QuizStatistics
import io.github.ivanbabiichuk.countryquest.domain.model.StatisticsSummary
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
    onOpenResult: (gameId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        when (uiState) {
            StatisticsUiState.Loading -> {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Loading statistics…")
                    }
                }
            }

            StatisticsUiState.Empty -> {
                item {
                    Text("Complete your first quiz to see your statistics.")
                }
            }

            StatisticsUiState.LoadError -> {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Could not load your statistics.")

                        Button(onClick = onRetry) {
                            Text("Try again")
                        }
                    }
                }
            }

            is StatisticsUiState.Content -> {
                item(key = "overall") {
                    StatisticsSummaryCard(
                        title = "Overall",
                        summary = uiState.statistics.overall,
                    )
                }

                items(
                    items = QuizMode.entries.toList(),
                    key = { it.name }
                ) { mode ->
                    StatisticsSummaryCard(
                        title = when (mode) {
                            QuizMode.FLAG -> "Guess the Flag"
                            QuizMode.CAPITAL -> "Guess the Capital"
                            QuizMode.COUNTRY -> "Guess the Country"
                        },
                        summary = uiState.statistics.byMode.getValue(mode)
                    )
                }

                item(key = "history_header") {
                    Text(
                        text = "Game history",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(
                    items = uiState.history,
                    key = { "game_${it.gameId}" }
                ) { game ->
                    GameHistoryCard(
                        game = game,
                        onClick = { onOpenResult(game.gameId) }
                    )
                }
            }
        }

        item {
            OutlinedButton(onClick = onGoBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun StatisticsSummaryCard(
    title: String,
    summary: StatisticsSummary,
    modifier: Modifier = Modifier
) {
    val accuracy = summary.accuracyPercent?.let { percent ->
        NumberFormat.getPercentInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(percent / 100.0)
    } ?: "—"

    val bestScore = summary.bestScoreOutOfTen?.let {
        "$it / 10"
    } ?: "—"

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )

            Text("Games played: ${summary.gamesPlayed}")

            Text(
                "Correct answers: " +
                        "${summary.correctAnswers} / ${summary.totalAnswers}"
            )

            Text("Accuracy: $accuracy")
            Text("Best score: $bestScore")
        }
    }
}

@Composable
private fun GameHistoryCard(
    game: GameHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedAt = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT
    ).format(Date(game.completedAtEpochMillis))

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (game.mode) {
                    QuizMode.FLAG -> "Guess the Flag"
                    QuizMode.CAPITAL -> "Guess the Capital"
                    QuizMode.COUNTRY -> "Guess the Country"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "${game.score} / ${game.totalQuestions}",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = completedAt,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "View result",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatisticsContentPreview() {
    StatisticsPreview(
        uiState = StatisticsUiState.Content(
            statistics = QuizStatistics(
                overall = StatisticsSummary(
                    gamesPlayed = 3,
                    correctAnswers = 24,
                    totalAnswers = 30,
                    bestScoreOutOfTen = 10
                ),
                byMode = mapOf(
                    QuizMode.FLAG to StatisticsSummary(2, 17, 20, 10),
                    QuizMode.CAPITAL to StatisticsSummary(1, 7, 10, 7),
                    QuizMode.COUNTRY to StatisticsSummary(0, 0, 0, null)
                )
            ),
            history = listOf(
                GameHistoryItem(
                    gameId = "preview-capital",
                    mode = QuizMode.CAPITAL,
                    completedAtEpochMillis = 1_760_000_000_000L,
                    score = 7,
                    totalQuestions = 10
                ),
                GameHistoryItem(
                    gameId = "preview-flag-perfect",
                    mode = QuizMode.FLAG,
                    completedAtEpochMillis = 1_759_999_000_000L,
                    score = 10,
                    totalQuestions = 10
                ),
                GameHistoryItem(
                    gameId = "preview-flag",
                    mode = QuizMode.FLAG,
                    completedAtEpochMillis = 1_759_998_000_000L,
                    score = 7,
                    totalQuestions = 10
                )
            )
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun StatisticsEmptyPreview() {
    StatisticsPreview(StatisticsUiState.Empty)
}

@Preview(showBackground = true)
@Composable
private fun StatisticsLoadingPreview() {
    StatisticsPreview(StatisticsUiState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun StatisticsErrorPreview() {
    StatisticsPreview(StatisticsUiState.LoadError)
}

@Composable
private fun StatisticsPreview(uiState: StatisticsUiState) {
    CountryQuestTheme {
        Surface {
            StatisticsScreen(
                uiState = uiState,
                onRetry = {},
                onGoBack = {},
                onOpenResult = {}
            )
        }
    }
}