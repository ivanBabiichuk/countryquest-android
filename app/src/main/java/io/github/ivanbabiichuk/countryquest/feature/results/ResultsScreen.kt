package io.github.ivanbabiichuk.countryquest.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme

@Composable
fun ResultsScreen(
    score: Int,
    totalQuestions: Int,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            text = "$score / $totalQuestions",
            style = MaterialTheme.typography.displayMedium
        )

        Text("Correct answers")

        Button(onClick = onPlayAgain) {
            Text("Play Again")
        }

        OutlinedButton(onClick = onGoHome) {
            Text("Back to Home")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenPreview() {
    CountryQuestTheme {
        Surface {
            ResultsScreen(
                score = 7,
                totalQuestions = 10,
                onPlayAgain = {},
                onGoHome = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}