package io.github.ivanbabiichuk.countryquest.feature.statistics

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.domain.model.*
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat

@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun show(state: StatisticsUiState, retry: () -> Unit = {}, back: () -> Unit = {}) {
        compose.setContent { CountryQuestTheme { Surface { StatisticsScreen(state, retry, back) } } }
    }
    @Test fun loadingShowsProgressAndAllowsBack() {
        var backs = 0
        show(StatisticsUiState.Loading, back = { backs++ })
        compose.onNodeWithText("Loading statistics…").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
    @Test fun emptyHistoryShowsInvitationWithoutInventedStatistics() {
        show(StatisticsUiState.Empty)
        compose.onNodeWithText("Complete your first quiz to see your statistics.").assertIsDisplayed()
        compose.onNodeWithText("Overall").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }
    @Test fun errorOffersRetryAndBack() {
        var retries = 0
        var backs = 0
        show(StatisticsUiState.LoadError, { retries++ }, { backs++ })
        compose.onNodeWithText("Could not load your statistics.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, backs) }
    }
    @Test fun contentShowsOverallAndEachModeIncludingUnplayedMode() {
        val empty = StatisticsSummary(0, 0, 0, null)
        show(StatisticsUiState.Content(QuizStatistics(
            StatisticsSummary(3, 24, 30, 10), mapOf(
                QuizMode.FLAG to StatisticsSummary(2, 17, 20, 10),
                QuizMode.CAPITAL to StatisticsSummary(1, 7, 10, 7),
                QuizMode.COUNTRY to empty
            )
        )))
        compose.onNodeWithText("Overall").assertIsDisplayed()
        compose.onNodeWithText("Games played: 3").assertExists()
        compose.onNodeWithText("Correct answers: 24 / 30").assertExists()
        val accuracy = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(0.8)
        compose.onNodeWithText("Accuracy: $accuracy").assertExists()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(2)
        compose.onNodeWithText("Guess the Flag").assertIsDisplayed()
        compose.onNodeWithText("Correct answers: 17 / 20").assertExists()
        list.performScrollToIndex(3)
        compose.onNodeWithText("Guess the Capital").assertIsDisplayed()
        compose.onNodeWithText("Best score: 7 / 10").assertExists()
        list.performScrollToIndex(4)
        compose.onNodeWithText("Guess the Country").assertIsDisplayed()
        compose.onNodeWithText("Games played: 0").assertExists()
        compose.onNodeWithText("Accuracy: —").assertExists()
        compose.onNodeWithText("Best score: —").assertExists()
    }
}
