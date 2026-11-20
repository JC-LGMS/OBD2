package com.obd2.jeancyril

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obd2.jeancyril.ui.ObdApp
import com.obd2.jeancyril.ui.ObdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ObdViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            ObdTheme(light = settings.lightTheme) {
                ObdApp(viewModel)
            }
        }
    }
}
