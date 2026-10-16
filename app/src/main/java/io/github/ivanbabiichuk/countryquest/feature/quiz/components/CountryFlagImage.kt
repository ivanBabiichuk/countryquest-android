package io.github.ivanbabiichuk.countryquest.feature.quiz.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag

private enum class FlagLoadState {
    LOADING,
    READY,
    ERROR,
}

@Composable
fun CountryFlagImage(
    flag: CountryFlag,
    onReadyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = remember(flag) {
        listOfNotNull(flag.svgUrl, flag.pngUrl)
            .filter { it.isNotBlank() }
            .distinct()
    }

    var attempt by remember(flag) {
        mutableIntStateOf(0)
    }

    var sourceIndex by remember(flag, attempt) {
        mutableIntStateOf(0)
    }

    var loadState by remember(flag, attempt) {
        mutableStateOf(
            if (sources.isEmpty()) {
                FlagLoadState.ERROR
            } else {
                FlagLoadState.LOADING
            }
        )
    }

    val currentOnReadyChanged by rememberUpdatedState(onReadyChanged)

    LaunchedEffect(flag, attempt, loadState) {
        currentOnReadyChanged(loadState == FlagLoadState.READY)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        val source = sources.getOrNull(sourceIndex)

        if (source != null && loadState != FlagLoadState.ERROR) {
            key(flag, attempt, sourceIndex) {
                AsyncImage(
                    model = source,
                    contentDescription = "Country flag",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = {
                        loadState = FlagLoadState.READY
                    },
                    onError = {
                        if (sourceIndex < sources.lastIndex) {
                            sourceIndex += 1
                            loadState = FlagLoadState.LOADING
                        } else {
                            loadState = FlagLoadState.ERROR
                        }
                    },
                )
            }
        }

        when (loadState) {
            FlagLoadState.LOADING -> {
                CircularProgressIndicator()
            }

            FlagLoadState.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Could not load the flag.")

                    if (sources.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { attempt += 1 },
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }

            FlagLoadState.READY -> Unit
        }
    }
}