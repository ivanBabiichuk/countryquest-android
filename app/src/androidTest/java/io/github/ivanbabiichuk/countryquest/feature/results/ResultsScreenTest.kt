package io.github.ivanbabiichuk.countryquest.feature.results

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ivanbabiichuk.countryquest.domain.model.*
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun show(state: ResultsUiState, retry: () -> Unit = {}, home: () -> Unit = {}) {
        compose.setContent { CountryQuestTheme { Surface {
            ResultsScreen(state, retry, {}, home)
        } } }
    }
    @Test fun loadingAllowsHomeButNotReplayOrRetry() {
        var homes = 0
        show(ResultsUiState.Loading, home = { homes++ })
        compose.onNodeWithText("Loading result…").assertIsDisplayed()
        compose.onNodeWithText("Play Again").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Back to Home").performClick()
        compose.runOnIdle { assertEquals(1, homes) }
    }
    @Test fun loadErrorOffersRetryAndHome() {
        var retries = 0
        var homes = 0
        show(ResultsUiState.LoadError, { retries++ }, { homes++ })
        compose.onNodeWithText("Could not load your result.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Back to Home").performClick()
        compose.onNodeWithText("Play Again").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, homes) }
    }
    @Test fun missingResultDoesNotOfferReplayOrRetry() {
        show(ResultsUiState.NotFound)
        compose.onNodeWithText("This result could not be found.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Play Again").assertDoesNotExist()
        compose.onNodeWithText("Back to Home").assertIsEnabled()
    }
    @Test fun contentDisplaysStoredScoreAndReplaysStoredMode() {
        val mode = mutableStateOf(QuizMode.FLAG)
        val played = mutableListOf<QuizMode>()
        compose.setContent { CountryQuestTheme { Surface {
            val result = GameResult("id", mode.value, 100, listOf(
                GameAnswer("UA", "Ukraine", "UA", "Ukraine", "UA", "Ukraine"),
                GameAnswer("FR", "France", "UA", "Ukraine", "FR", "France")
            ))
            ResultsScreen(ResultsUiState.Content(result), {}, { played.add(it) }, {})
        } } }
        val labels = mapOf(QuizMode.FLAG to "Guess the Flag", QuizMode.CAPITAL to "Guess the Capital",
            QuizMode.COUNTRY to "Guess the Country")
        labels.forEach { (value, label) ->
            compose.runOnIdle { mode.value = value }
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithText("1 / 2").assertIsDisplayed()
            compose.onNodeWithText("Play Again").performClick()
        }
        compose.runOnIdle { assertEquals(labels.keys.toList(), played) }
    }
}
