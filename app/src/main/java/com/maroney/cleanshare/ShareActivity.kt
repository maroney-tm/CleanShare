package com.maroney.cleanshare

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.domain.UrlSanitizer
import com.maroney.cleanshare.widget.RecentSharesWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                (application as CleanShareApplication).shareRepository
                    .insert(ShareRecord(originalText = raw, cleanedText = cleaned))
            }
            RecentSharesWidget().updateAll(this@ShareActivity)

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, cleaned)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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
