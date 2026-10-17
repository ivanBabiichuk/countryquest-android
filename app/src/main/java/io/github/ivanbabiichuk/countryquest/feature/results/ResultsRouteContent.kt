package io.github.ivanbabiichuk.countryquest.feature.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode

@Composable
fun ResultsRouteContent(
    onPlayAgain: (QuizMode) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ResultsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onPlayAgain = onPlayAgain,
        onGoHome = onGoHome,
        modifier = modifier,
    )
}