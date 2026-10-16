package io.github.ivanbabiichuk.countryquest.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ivanbabiichuk.countryquest.app.sync.CountrySyncCoordinator
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val countrySyncCoordinator: CountrySyncCoordinator
) : ViewModel() {

    fun initialize() {
        viewModelScope.launch { countrySyncCoordinator.initialize() }
    }
}