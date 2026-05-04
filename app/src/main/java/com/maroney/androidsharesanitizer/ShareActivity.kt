package com.maroney.androidsharesanitizer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.maroney.androidsharesanitizer.data.ShareDatabase
import com.maroney.androidsharesanitizer.data.ShareRecord
import com.maroney.androidsharesanitizer.data.ShareRepository
import com.maroney.androidsharesanitizer.domain.UrlSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent trampoline activity.
 *
 * The user never sees this activity. Theme.Transparent keeps the calling app
 * visible in the background while we clean the URL and re-fire to the Android
 * Sharesheet. The full sequence is:
 *
 *   1. Receive ACTION_SEND with text/plain.
 *   2. Strip tracking params via UrlSanitizer.
 *   3. Persist original + cleaned text to Room (on IO dispatcher).
 *   4. Re-fire ACTION_SEND to the Android Sharesheet (excludes ourselves).
 *   5. finish() — activity is gone before the Sharesheet animation completes.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()

        if (raw.isNullOrEmpty()) {
            finish()
            return
        }

        val cleaned = UrlSanitizer.cleanText(raw)

        // All three steps (write → startActivity → finish) run in the coroutine
        // so the activity stays alive long enough for the Room write to complete.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ShareRepository(ShareDatabase.getInstance(applicationContext).shareDao())
                    .insert(ShareRecord(originalText = raw, cleanedText = cleaned))
            }

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, cleaned)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Exclude ourselves so Clean Share doesn't appear in its own Sharesheet.
            // minSdk = 33 > API 24, so EXTRA_EXCLUDE_COMPONENTS is always available.
            val chooser = Intent.createChooser(send, null).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(this@ShareActivity, ShareActivity::class.java)),
                )
            }
            startActivity(chooser)
            finish()
        }
    }
}
