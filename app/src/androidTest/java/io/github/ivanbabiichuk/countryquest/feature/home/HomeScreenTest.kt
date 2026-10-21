package io.github.ivanbabiichuk.countryquest.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshFailure
import io.github.ivanbabiichuk.countryquest.domain.repository.CountryRefreshResult
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyCatalogDisablesGamesButAllowsDownloadAndStatistics() {
        var refreshes = 0
        var statistics = 0
        show(HomeUiState(isReadingCatalog = false), onRefresh = { refreshes++ }, onStatistics = { statistics++ })
        compose.onNodeWithText("Download countries to start playing.").assertIsDisplayed()
        modes.forEach { (label, _) -> compose.onNodeWithText(label).assertIsNotEnabled() }
        compose.onNodeWithText("Update countries").performClick()
        compose.onNodeWithText("Statistics").performClick()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(1, statistics)
        }
    }

    @Test
    fun refreshingKeepsCachedGamesEnabledAndBlocksAnotherRefresh() {
        val selected = mutableListOf<QuizMode>()
        show(HomeUiState(countriesCount = 20, isReadingCatalog = false, isRefreshing = true), onStart = { selected.add(it) })
        compose.onNodeWithText("Updating…").assertIsNotEnabled()
        modes.forEach { (label, _) -> compose.onNodeWithText(label).assertIsEnabled().performClick() }
        compose.runOnIdle { assertEquals(modes.map { it.second }, selected) }
    }

    @Test
    fun networkFailureShowsRetryAndKeepsCachedGamesAvailable() {
        var retries = 0
        show(
            HomeUiState(
                countriesCount = 20, isReadingCatalog = false,
                lastRefreshResult = CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK),
            ),
            onRefresh = { retries++ }
        )
        compose.onNodeWithText("Could not connect. Check your internet connection.").assertIsDisplayed()
        compose.onNodeWithText("Your saved catalog is still available.").assertIsDisplayed()
        modes.forEach { (label, _) -> compose.onNodeWithText(label).assertIsEnabled() }
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun readFailureDisablesGamesEvenWithPreviousCount() {
        show(HomeUiState(countriesCount = 20, isReadingCatalog = false, hasCatalogReadError = true))
        compose.onNodeWithText("Could not read saved countries.").assertIsDisplayed()
        modes.forEach { (label, _) -> compose.onNodeWithText(label).assertIsNotEnabled() }
        compose.onNodeWithText("Try again").assertIsEnabled()
    }

    @Test
    fun partialSuccessShowsSkippedRecordsAndStoredCount() {
        show(
            HomeUiState(
                countriesCount = 20, isReadingCatalog = false,
                lastRefreshResult = CountryRefreshResult.Success(2, 4)
            ),
        )
        compose.onNodeWithText("Saved countries: 20").assertIsDisplayed()
        compose.onNodeWithText("Catalog updated. Skipped records: 4").assertIsDisplayed()
    }

    @Test
    fun savedCatalogWithoutMetadataShowsUnknownTime() {
        show(HomeUiState(countriesCount = 20, isReadingCatalog = false))
        compose.onNodeWithText("Last update time is unknown.").assertIsDisplayed()
    }

    @Test
    fun timestampChangesOnUpdateAndRemainsVisibleAfterFailure() {
        val firstTime = 1_700_000_000_000L
        val secondTime = firstTime + 86_400_000L
        val state = mutableStateOf(
            HomeUiState(
                countriesCount = 20,
                isReadingCatalog = false,
                lastSuccessfulSyncAtEpochMillis = firstTime
            )
        )
        show(state = { state.value })
        compose.onNodeWithText(syncLabel(firstTime)).assertIsDisplayed()
        compose.onNodeWithText("Last update time is unknown.").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(lastSuccessfulSyncAtEpochMillis = secondTime)
        }
        compose.onNodeWithText(syncLabel(secondTime)).assertIsDisplayed()
        compose.onNodeWithText(syncLabel(firstTime)).assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(
                lastRefreshResult = CountryRefreshResult.Failure(CountryRefreshFailure.NETWORK)
            )
        }
        compose.onNodeWithText(syncLabel(secondTime)).assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsEnabled()
    }

    @Test
    fun emptyCatalogDoesNotDisplayMisleadingSyncTime() {
        show(HomeUiState(isReadingCatalog = false, lastSuccessfulSyncAtEpochMillis = 100L))
        compose.onNodeWithText("Last update time is unknown.").assertDoesNotExist()
        compose.onNodeWithText("Last updated:", substring = true).assertDoesNotExist()
    }

    @Test
    fun storageFailureUsesMessageApplicableToBothReadsAndWrites() {
        show(
            HomeUiState(
                isReadingCatalog = false,
                lastRefreshResult = CountryRefreshResult.Failure(CountryRefreshFailure.STORAGE)
            )
        )
        compose.onNodeWithText("Could not access the country catalog.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsEnabled()
    }

    private fun syncLabel(timestamp: Long): String = "Last updated: " +
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

    private fun show(
        state: HomeUiState,
        onRefresh: () -> Unit = {},
        onStart: (QuizMode) -> Unit = {},
        onStatistics: () -> Unit = {}
    ) {
        show(state = { state }, onRefresh, onStart, onStatistics)
    }

    private fun show(
        state: () -> HomeUiState,
        onRefresh: () -> Unit = {},
        onStart: (QuizMode) -> Unit = {},
        onStatistics: () -> Unit = {}
    ) {
        compose.setContent {
            CountryQuestTheme {
                Surface {
                    HomeScreen(
                        uiState = state(), onRefreshCountries = onRefresh,
                        onStartQuiz = onStart, onOpenStatistics = onStatistics,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private val modes = listOf(
        "Guess the Flag" to QuizMode.FLAG,
        "Guess the Capital" to QuizMode.CAPITAL,
        "Guess the Country" to QuizMode.COUNTRY
    )
}
