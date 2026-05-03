package com.maroney.androidsharesanitizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Transient share preview screen — no Scaffold, no TopAppBar.
 *
 * Shows the [original] and [cleaned] text side-by-side with a subtle hint
 * when nothing was actually cleaned.  The user can re-share via [onShare]
 * or copy to clipboard via [onCopy].
 */
@Composable
fun SharePreviewScreen(
    original: String,
    cleaned: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Original ────────────────────────────────────────────────────────
        Text(
            text = "Original",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = original,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Cleaned ─────────────────────────────────────────────────────────
        Text(
            text = "Cleaned",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cleaned,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (original == cleaned) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nothing to clean",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Actions ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Text("Share")
            }
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Copy")
            }
        }
    }
}
