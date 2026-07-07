package com.maroney.cleanshare.domain

import timber.log.Timber

/**
 * Pure Kotlin URL sanitizer — no Android imports, no I/O, no logging.
 *
 * Strips well-known tracking query parameters while preserving the rest of
 * the URL (scheme, host, port, path, surviving query params, fragment).
 *
 * Implementation uses a plain String parser (split on '?' / '#') so that
 * this object is usable in JVM unit tests without Robolectric.  See
 * DECISIONS.md §T3.
 *
 * ## Deny-list (removed, case-insensitive key match)
 * - `si`                           — YouTube share ID
 * - `utm_*`                        — UTM campaign tags (any key starting with utm_)
 * - `fbclid`                       — Facebook Click ID
 * - `igshid`, `igsh`               — Instagram share ID
 * - `feature`                      — YouTube share variant
 * - `mc_cid`, `mc_eid`             — Mailchimp email campaign
 * - `gclid`, `dclid`, `gbraid`, `wbraid` — Google Ads
 * - `yclid`                        — Yandex Click ID
 * - `ref`, `ref_src`, `ref_url`    — Generic referrer
 * - `_hsenc`, `_hsmi`, `__hstc`, `__hssc`, `__hsfp` — HubSpot
 *
 * ## Allow-list (always preserved; beats deny-list on conflict)
 * - `t`, `start`   — YouTube timestamp
 * - `v`            — YouTube watch ID
 * - `list`, `index`— YouTube playlist context
 */
object UrlSanitizer {

    private val DENY_SET = setOf(
        "si",
        "fbclid",
        "igshid", "igsh",
        "feature",
        "mc_cid", "mc_eid",
        "gclid", "dclid", "gbraid", "wbraid",
        "yclid",
        "ref", "ref_src", "ref_url",
        "_hsenc", "_hsmi", "__hstc", "__hssc", "__hsfp"
    )

    private val ALLOW_SET = setOf("t", "start", "v", "list", "index")

    /**
     * Returns [input] with known tracking parameters removed.
     * If [input] cannot be processed, returns it verbatim — never throws.
     * Parameter order of surviving params is preserved.
     */
    fun clean(input: String): String = try {
        cleanInternal(input)
    } catch (e: Exception) {
        Timber.w(e, "URL sanitization failed, returning input")
        input
    }

    /**
     * Splits [raw] on whitespace, calls [clean] on each token, and rejoins
     * with a single space.  Tokens that are not URLs are returned unchanged
     * (the sanitizer passes them through).
     */
    fun cleanText(raw: String): String =
        raw.trim().split(Regex("\\s+")).joinToString(" ") { clean(it) }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun cleanInternal(input: String): String {
        // 1. Separate fragment (#…) — must be kept verbatim.
        val hashIdx = input.indexOf('#')
        val fragment = if (hashIdx >= 0) input.substring(hashIdx) else ""
        val beforeFragment = if (hashIdx >= 0) input.substring(0, hashIdx) else input

        // 2. Find query string.
        val queryIdx = beforeFragment.indexOf('?')
        if (queryIdx < 0) return input   // no query params → nothing to do

        val base = beforeFragment.substring(0, queryIdx)
        val queryString = beforeFragment.substring(queryIdx + 1)

        if (queryString.isEmpty()) return "$base$fragment"

        // 3. Parse, filter, and reconstruct.
        val kept = queryString.split('&')
            .filter { it.isNotEmpty() }
            .filterNot { param ->
                val key = param.substringBefore('=').lowercase()
                !ALLOW_SET.contains(key) && isDenied(key)
            }

        return if (kept.isEmpty()) {
            "$base$fragment"
        } else {
            "$base?${kept.joinToString("&")}$fragment"
        }
    }

    private fun isDenied(key: String): Boolean =
        DENY_SET.contains(key) || key.startsWith("utm_")

    /**
     * Returns the exact `key=value` query-string tokens present in [original] but not in
     * [cleaned] — i.e. what [clean] stripped. Used to highlight the removed portion when
     * displaying the original URL instead of a separate cleaned-URL block.
     */
    fun removedQueryParams(original: String, cleaned: String): Set<String> {
        val cleanedTokens = queryTokens(cleaned).toSet()
        return queryTokens(original).filterNot { it in cleanedTokens }.toSet()
    }

    private fun queryTokens(url: String): List<String> {
        val beforeFragment = url.substringBefore('#')
        val queryIdx = beforeFragment.indexOf('?')
        if (queryIdx < 0) return emptyList()
        return beforeFragment.substring(queryIdx + 1).split('&').filter { it.isNotEmpty() }
    }
}
