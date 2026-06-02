package com.maroney.cleanshare.domain

import androidx.compose.runtime.Composable
import com.maroney.cleanshare.data.IngestionRecord

/**
 * A platform-specific handler that can match a URL, extract structured metadata
 * from its path, and render a rich detail section in the UI.
 *
 * Pure URL parsing only — no network calls.
 */
interface DomainHandler {
    fun matches(url: String): Boolean
    fun extractUrlMetadata(url: String): DomainUrlMetadata

    @Composable
    fun DetailSection(urlMetadata: DomainUrlMetadata, ingestion: IngestionRecord?, videoUrl: String?)
}

/** Marker sealed class for per-platform URL metadata derived from path parsing. */
sealed class DomainUrlMetadata

class DomainHandlerRegistry(private val handlers: List<DomainHandler>) {
    fun findHandler(url: String): DomainHandler? = handlers.firstOrNull { it.matches(url) }
}
