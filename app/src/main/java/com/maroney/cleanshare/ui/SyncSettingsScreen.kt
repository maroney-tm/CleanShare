package com.maroney.cleanshare.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.ui.theme.LocalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SyncSettingsViewModel = viewModel(factory = SyncSettingsViewModel.Factory)
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val failedDownloadCount by viewModel.failedDownloadCount.collectAsStateWithLifecycle()
    val isRetryingAll by viewModel.isRetryingAll.collectAsStateWithLifecycle()

    var draftHost by remember(config.manualHost, config.port) {
        val h = config.manualHost ?: ""
        mutableStateOf(if (h.isNotEmpty() && config.port != null) "$h:${config.port}" else h)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            ConnectionStatusRow(status)

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "Server address",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = draftHost,
                onValueChange = { draftHost = it },
                placeholder = { Text("192.168.1.x:8765") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = {
                    viewModel.setManualHost(draftHost)
                    viewModel.testConnection()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }

            if (failedDownloadCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Maintenance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedButton(
                    onClick = viewModel::retryAllFailedDownloads,
                    enabled = !isRetryingAll,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRetryingAll) "Retrying…" else "Retry All Failed Downloads ($failedDownloadCount)")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val colors = LocalColors.current.status
        val (dotColor, label) = when (status) {
            is ConnectionStatus.Connected -> {
                val portSuffix = if (status.port != null) ":${status.port}" else ""
                colors.ok to "Connected — ${status.host}$portSuffix"
            }
            is ConnectionStatus.Searching ->
                colors.pending to "Searching…"
            is ConnectionStatus.Disconnected ->
                colors.off to "Not connected"
        }
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            tint = dotColor,
            modifier = Modifier.size(IconSize.statusDot),
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
