package io.github.ivanbabiichuk.countryquest.feature.quiz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode

@Composable
fun QuizRouteContent(
    mode: QuizMode,
    onFinishQuiz: (score: Int, totalQuestions: Int) -> Unit,
    onExitQuiz: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnFinishQuiz by rememberUpdatedState(onFinishQuiz)

    LaunchedEffect(mode, viewModel) {
        viewModel.startRound(mode)
    }

    val finished = uiState as? QuizUiState.Finished

    LaunchedEffect(finished) {
        if (finished != null) {
            currentOnFinishQuiz(
                finished.score,
                finished.totalQuestions,
            )
        }
    }

    QuizScreen(
        mode = mode,
        uiState = uiState,
        onRetry = {
            if (uiState is QuizUiState.SaveError) {
                viewModel.retrySave()
            } else {
                viewModel.startRound(mode)
            }
        },
        onSelectAnswer = viewModel::selectAnswer,
        onNextQuestion = viewModel::nextQuestion,
        onExitQuiz = onExitQuiz,
        modifier = modifier
    )
}