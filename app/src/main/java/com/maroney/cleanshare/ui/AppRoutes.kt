package com.maroney.cleanshare.ui

data object HistoryRoute

/**
 * @param orderedIds The id sequence of the currently filtered/sorted history list, captured at
 * navigation time — lets the detail screen figure out which entry is "next"/"previous" for
 * swipe-between-videos, matching whatever sort/filter was active in the list. Empty when there's
 * no such context (e.g. opened via a widget deep link), in which case swiping is a no-op.
 */
data class DetailRoute(
    val id: Long,
    val orderedIds: List<Long> = emptyList(),
)
