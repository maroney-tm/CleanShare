package com.maroney.cleanshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maroney.cleanshare.ui.HistoryScreen
import com.maroney.cleanshare.ui.HistoryViewModel
import com.maroney.cleanshare.ui.theme.CleanShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CleanShareTheme {
                // Factory uses APPLICATION_KEY from the Compose viewModel() extras,
                // so no context needs to be passed explicitly here.
                val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
                HistoryScreen(viewModel)
            }
        }
    }
}
