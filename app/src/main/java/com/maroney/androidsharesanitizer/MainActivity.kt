package com.maroney.androidsharesanitizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maroney.androidsharesanitizer.ui.HistoryScreen
import com.maroney.androidsharesanitizer.ui.HistoryViewModel
import com.maroney.androidsharesanitizer.ui.theme.AndroidShareSanitizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidShareSanitizerTheme {
                // Factory uses APPLICATION_KEY from the Compose viewModel() extras,
                // so no context needs to be passed explicitly here.
                val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
                HistoryScreen(viewModel)
            }
        }
    }
}
