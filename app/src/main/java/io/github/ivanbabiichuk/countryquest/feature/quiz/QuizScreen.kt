package io.github.ivanbabiichuk.countryquest.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme

@Composable
fun QuizScreen(
    onFinishQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Quiz",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text("Your geography challenge will appear here.")

        Button(onClick = onFinishQuiz) {
            Text("Finish Quiz")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuizScreenPreview() {
    CountryQuestTheme {
        Surface {
            QuizScreen(
                onFinishQuiz = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}