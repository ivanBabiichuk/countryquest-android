package io.github.ivanbabiichuk.countryquest.feature.quiz

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

@RunWith(AndroidJUnit4::class)
class QuizSaveScreenTest {
    @get:Rule val compose = createComposeRule()
    private val result = GameResult("game", QuizMode.CAPITAL, 100L,
        listOf(GameAnswer("UA", "Ukraine", "UA", "Kyiv", "UA", "Kyiv")))

    @Test fun savingShowsProgressAndHidesRetryAndExit() {
        show(QuizUiState.Saving(result))
        compose.onNodeWithText("Saving result…").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Back to Home").assertDoesNotExist()
        compose.onNodeWithText("Round complete!").assertDoesNotExist()
    }
    @Test fun saveFailureOffersRetryAndWarnsBeforeExit() {
        var retries = 0
        var exits = 0
        show(QuizUiState.SaveError(result), { retries++ }, { exits++ })
        compose.onNodeWithText("Could not save your result.").assertIsDisplayed()
        compose.onNodeWithText("Leaving this screen will discard the pending result.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Back to Home").performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, exits) }
    }
    private fun show(state: QuizUiState, retry: () -> Unit = {}, exit: () -> Unit = {}) {
        compose.setContent { CountryQuestTheme { Surface {
            QuizScreen(QuizMode.CAPITAL, state, retry, { _, _ -> }, {}, exit)
        } } }
    }
}
