package com.maroney.cleanshare.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class RecentSharesWidget : GlanceAppWidget() {

    companion object {
        const val EXTRA_DETAIL_ID = "extra_detail_id"

        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
        }
        private val bitmapLoader by lazy { WidgetBitmapLoader(httpClient) }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = ShareDatabase.getInstance(context)
        val shares = runCatching {
            db.shareDao().getAll().first().take(5)
        }.getOrElse { emptyList() }
        val allMetadata = runCatching {
            db.linkMetadataDao().observeAll().first()
        }.getOrElse { emptyList() }
        val byId = allMetadata.associateBy { it.shareRecordId }
        val items = shares.map { ShareRecordWithMetadata(it, byId[it.id]) }

        val bitmaps: List<Bitmap?> = coroutineScope {
            items.map { item ->
                async {
                    val url = item.metadata?.thumbnailUrl
                        ?: runCatching { item.record.cleanedText.toUri().host }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
                    url?.let { bitmapLoader.load(it) }
                }
            }.awaitAll()
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, items = items, bitmaps = bitmaps)
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
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
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
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(64.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                items.forEachIndexed { index, item ->
                    val bitmap = bitmaps.getOrNull(index)
                    val intent = Intent().apply {
                        component = ComponentName(context.packageName, "${context.packageName}.MainActivity")
                        putExtra(RecentSharesWidget.EXTRA_DETAIL_ID, item.record.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        data = "cleanshare://detail/${item.record.id}".toUri()
                    }
                    WidgetThumbnail(
                        bitmap = bitmap,
                        action = actionStartActivity(intent),
                        modifier = GlanceModifier.padding(2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.WidgetThumbnail(
    bitmap: Bitmap?,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .defaultWeight()
            .fillMaxHeight()
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier.width(64.dp).height(64.dp)
                .cornerRadius(8.dp),
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
                    modifier = GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.surfaceVariant),
                ) {}
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GlanceTheme {
        WidgetContent(
            context = LocalContext.current,
            items = listOf(),
            bitmaps = listOf(),
        )
    }
}
