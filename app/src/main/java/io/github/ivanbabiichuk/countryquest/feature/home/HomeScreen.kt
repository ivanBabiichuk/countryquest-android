package io.github.ivanbabiichuk.countryquest.feature.home

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
fun HomeScreen(
    onStartQuiz: () -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CountryQuest",
            style = MaterialTheme.typography.headlineLarge
        )

        Text("Explore. Guess. Learn.")

        Button(onClick = onStartQuiz) {
            Text("Start Quiz")
        }

        OutlinedButton(onClick = onOpenStatistics) {
            Text("Statistics")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    CountryQuestTheme {
        Surface {
            HomeScreen(
                onStartQuiz = {},
                onOpenStatistics = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}