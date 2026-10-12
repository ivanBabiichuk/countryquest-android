package io.github.ivanbabiichuk.countryquest.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
fun StatisticsScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text("Complete your first quiz to see your statistics.")

        OutlinedButton(onClick = onGoBack) {
            Text("Back")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatisticsScreenPreview() {
    CountryQuestTheme {
        Surface {
            StatisticsScreen(
                onGoBack = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}