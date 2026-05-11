package com.maroney.cleanshare.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.maroney.cleanshare.ui.IconSize
import com.maroney.cleanshare.ui.Radius
import com.maroney.cleanshare.ui.Spacing
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.MainActivity
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class RecentSharesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CleanShareApplication

        val items = runCatching {
            app.shareRepository.getAll().first().take(5)
        }.getOrElse { emptyList() }

        val bitmaps: List<Bitmap?> = coroutineScope {
            items.map { item ->
                async {
                    val url = item.metadata?.thumbnailUrl
                        ?: runCatching { item.record.cleanedText.toUri().host }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
                    url?.let { app.widgetBitmapLoader.load(it) }
                }
            }.awaitAll()
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context, items, bitmaps)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    items: List<ShareRecordWithMetadata>,
    bitmaps: List<Bitmap?>,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = "CleanShare",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing shared yet - share a link to CleanShare to get started",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 11.sp,
                    ),
                )
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth().height(IconSize.thumbnail)) {
                items.forEachIndexed { index, item ->
                    val bitmap = bitmaps.getOrNull(index)
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_DETAIL_ID, item.record.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        data = "cleanshare://detail/${item.record.id}".toUri()
                    }
                    WidgetThumbnail(bitmap = bitmap, intent = intent)
                }
            }
        }
    }
}

@Composable
private fun RowScope.WidgetThumbnail(bitmap: Bitmap?, intent: Intent) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(Spacing.xs)
            .cornerRadius(Radius.md)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surfaceVariant),
            ) {}
        }
    }
}
