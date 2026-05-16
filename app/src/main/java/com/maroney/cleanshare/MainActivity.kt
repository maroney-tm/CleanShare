package com.maroney.cleanshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.maroney.cleanshare.ui.DetailRoute
import com.maroney.cleanshare.ui.DetailScreen
import com.maroney.cleanshare.ui.HistoryRoute
import com.maroney.cleanshare.ui.HistoryScreen
import com.maroney.cleanshare.ui.HistoryViewModel
import com.maroney.cleanshare.ui.SyncSettingsScreen
import com.maroney.cleanshare.ui.theme.CleanShareTheme
import com.maroney.cleanshare.widget.RecentSharesWidget

data object SyncSettingsRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val detailId = intent?.getLongExtra(RecentSharesWidget.EXTRA_DETAIL_ID, -1L)?.takeIf { it >= 0L }

        setContent {
            CleanShareTheme {
                val backStack = remember {
                    mutableStateListOf(*initialBackStack(detailId).toTypedArray())
                }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            HistoryRoute -> NavEntry(key) {
                                val viewModel: HistoryViewModel =
                                    viewModel(factory = HistoryViewModel.Factory)
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { id -> backStack.add(DetailRoute(id)) },
                                    onNavigateToSettings = { backStack.add(SyncSettingsRoute) },
                                )
                            }
                            is DetailRoute -> NavEntry(key) {
                                DetailScreen(
                                    id = key.id,
                                    onNavigateBack = { backStack.removeLastOrNull() },
                                )
                            }
                            SyncSettingsRoute -> NavEntry(key) {
                                SyncSettingsScreen(
                                    onNavigateBack = { backStack.removeLastOrNull() },
                                )
                            }
                            else -> NavEntry(key) { }
                        }
                    },
                )
            }
        }
    }
}

internal fun initialBackStack(detailId: Long?): List<Any> =
    if (detailId != null) listOf(HistoryRoute, DetailRoute(detailId))
    else listOf(HistoryRoute)
