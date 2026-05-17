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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.maroney.cleanshare.ui.theme.LocalColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maroney.cleanshare.sync.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SyncSettingsViewModel = viewModel(factory = SyncSettingsViewModel.Factory)
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()

    // Reconstruct "host:port" for display so what the user sees matches what they typed.
    // For a manual host, use the stored override port; for a discovered host, use resolvedPort.
    var draftHost by remember(config.manualHost, config.resolvedHost, config.port, config.resolvedPort) {
        val h = config.manualHost ?: config.resolvedHost ?: ""
        val p = if (config.manualHost != null) config.port else config.resolvedPort
        mutableStateOf(if (h.isNotEmpty() && p != null) "$h:$p" else h)
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
            // Connection status
            ConnectionStatusRow(status)

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.md))

            // Auto-discover toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Discover automatically",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = config.autoDiscover,
                    onCheckedChange = { viewModel.setAutoDiscover(it) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Manual host field (enabled only when auto-discover is off)
            Text(
                text = "Server address",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = draftHost,
                onValueChange = { draftHost = it },
                enabled = !config.autoDiscover,
                placeholder = { Text("192.168.1.x:8765 or myserver.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = {
                    viewModel.setManualHost(draftHost)
                    viewModel.testConnection()
                },
                enabled = !config.autoDiscover,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test connection")
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
