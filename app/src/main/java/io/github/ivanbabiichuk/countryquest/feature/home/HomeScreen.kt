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
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefreshCountries: () -> Unit,
    onStartQuiz: (QuizMode) -> Unit,
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

        HomeCatalogStatus(
            uiState = uiState,
            onRefreshCountries = onRefreshCountries,
        )

        Text(
            text = "Choose your challenge",
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = { onStartQuiz(QuizMode.FLAG) },
            enabled = uiState.hasCountries && !uiState.hasCatalogReadError
        ) {
            Text("Guess the Flag")
        }

        Button(
            onClick = { onStartQuiz(QuizMode.CAPITAL) },
            enabled = uiState.hasCountries && !uiState.hasCatalogReadError
        ) {
            Text("Guess the Capital")
        }

        Button(
            onClick = { onStartQuiz(QuizMode.COUNTRY) },
            enabled = uiState.hasCountries && !uiState.hasCatalogReadError
        ) {
            Text("Guess the Country")
        }

        OutlinedButton(onClick = onOpenStatistics) {
            Text("Statistics")
        }
    }
}

@Composable
private fun HomeCatalogStatus(
    uiState: HomeUiState,
    onRefreshCountries: () -> Unit
) {
    when {
        uiState.isReadingCatalog -> {
            Text("Loading saved countries…")
        }

        uiState.hasCatalogReadError -> {
            Text(
                text = "Could not read saved countries.",
                color = MaterialTheme.colorScheme.error
            )
        }

        uiState.hasCountries -> {
            Text("Saved countries: ${uiState.countriesCount}")
        }

        else -> {
            Text("Download countries to start playing.")
        }
    }

    when (val result = uiState.lastRefreshResult) {
        is CountryRefreshResult.Success -> {
            Text(
                if (result.skippedRecords > 0) {
                    "Catalog updated. Skipped records: ${result.skippedRecords}"
                } else {
                    "Catalog updated."
                }
            )
        }

        is CountryRefreshResult.Failure -> {
            val message = when (result.reason) {
                CountryRefreshFailure.NETWORK ->
                    "Could not connect. Check your internet connection."

                CountryRefreshFailure.SERVER ->
                    "The server could not complete the request."

                CountryRefreshFailure.INVALID_DATA ->
                    "No usable country data was received."

                CountryRefreshFailure.STORAGE ->
                    "Could not save the country catalog."
            }

            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )

            if (uiState.hasCountries && !uiState.hasCatalogReadError) {
                Text("Your saved catalog is still available.")
            }
        }

        null -> Unit
    }

    OutlinedButton(
        onClick = onRefreshCountries,
        enabled = !uiState.isRefreshing
    ) {
        Text(
            when {
                uiState.isRefreshing -> "Updating…"

                uiState.hasCatalogReadError ||
                        uiState.lastRefreshResult is CountryRefreshResult.Failure ->
                    "Try again"

                else -> "Update countries"
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    CountryQuestTheme {
        Surface {
            HomeScreen(
                uiState = HomeUiState(
                    countriesCount = 250,
                    isReadingCatalog = false,
                ),
                onRefreshCountries = {},
                onStartQuiz = {},
                onOpenStatistics = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}