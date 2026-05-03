package com.maroney.androidsharesanitizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maroney.androidsharesanitizer.domain.UrlSanitizer
import com.maroney.androidsharesanitizer.ui.SharePreviewScreen
import com.maroney.androidsharesanitizer.ui.theme.AndroidShareSanitizerTheme

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // T4 — Extract and sanitize shared text.
        // raw is null when the intent isn't ACTION_SEND or carries no text.
        val raw = intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()

        if (raw.isNullOrEmpty()) {
            finish()
            return
        }

        // Sanitize each whitespace-separated token (multi-URL support).
        val cleaned = UrlSanitizer.cleanText(raw)

        // T5 — Show preview UI.
        setContent {
            AndroidShareSanitizerTheme {
                SharePreviewScreen(
                    original = raw,
                    cleaned = cleaned,
                    onShare = { doShare(cleaned) },
                    onCopy = { doCopy(cleaned) },
                )
            }
        }
    }

    // T6 — Re-share the cleaned URL via system chooser, excluding ourselves.
    private fun doShare(cleaned: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cleaned)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(send, "Share cleaned link").apply {
            // minSdk = 33 (> API 24), so EXTRA_EXCLUDE_COMPONENTS is always available.
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@ShareActivity, ShareActivity::class.java)),
            )
        }
        startActivity(chooser)
        finish()
    }

    // T7 — Copy the cleaned URL to the clipboard and dismiss.
    private fun doCopy(cleaned: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Cleaned URL", cleaned))
        // Android 13+ shows its own confirmation toast — no additional UI needed.
        finish()
    }
}
