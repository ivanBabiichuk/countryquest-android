package io.github.ivanbabiichuk.countryquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import io.github.ivanbabiichuk.countryquest.app.AppViewModel
import io.github.ivanbabiichuk.countryquest.app.CountryQuestApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val appViewModel = ViewModelProvider(this)[AppViewModel::class.java]
        appViewModel.initialize()

        setContent {
            CountryQuestApp()
        }
    }
}