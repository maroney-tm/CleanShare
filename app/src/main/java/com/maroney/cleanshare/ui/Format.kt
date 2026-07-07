package com.maroney.cleanshare.ui

/** Formats a byte count for display, e.g. in cache/offline-storage usage readouts. */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024        -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L               -> "%.0f KB".format(bytes / 1024.0)
    else                          -> "$bytes B"
}
