package io.github.ivanbabiichuk.countryquest.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode

@Composable
fun HomeRouteContent(
    onStartQuiz: (QuizMode) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onRefreshCountries = viewModel::refreshCountries,
        onStartQuiz = onStartQuiz,
        onOpenStatistics = onOpenStatistics,
        modifier = modifier,
    )

}