package com.maroney.cleanshare.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.maroney.cleanshare.ui.IconSize
import com.maroney.cleanshare.ui.Radius
import com.maroney.cleanshare.ui.Spacing
import com.maroney.cleanshare.ui.theme.CleanShareTheme

@Preview(name = "Widget — 5 items", widthDp = 360, showBackground = true)
@Composable
private fun RecentSharesWidgetPopulatedPreview() {
    CleanShareTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                text = "CleanShare",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            Row(modifier = Modifier.fillMaxWidth().height(IconSize.thumbnail)) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(Spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(IconSize.thumbnail)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Widget — empty", widthDp = 360, showBackground = true)
@Composable
private fun RecentSharesWidgetEmptyPreview() {
    CleanShareTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                text = "CleanShare",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IconSize.thumbnail),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing shared yet - share a link to CleanShare to get started",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
