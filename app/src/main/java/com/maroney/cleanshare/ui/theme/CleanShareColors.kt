package com.maroney.cleanshare.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LayoutColors(val divider: Color)

@Immutable
data class StatusColors(
    val ok: Color,
    val pending: Color,
    val off: Color,
)

@Immutable
data class CleanShareColors(
    val layout: LayoutColors,
    val status: StatusColors,
)

internal val LightCleanShareColors = CleanShareColors(
    layout = LayoutColors(divider = Color(0xFFE0E0E0)),
    status = StatusColors(
        ok      = Color(0xFF4CAF50),
        pending = Color(0xFFFFC107),
        off     = Color(0xFF9E9E9E),
    ),
)

internal val DarkCleanShareColors = CleanShareColors(
    layout = LayoutColors(divider = Color(0xFF3D3D3D)),
    status = StatusColors(
        ok      = Color(0xFF4CAF50),
        pending = Color(0xFFFFC107),
        off     = Color(0xFF9E9E9E),
    ),
)

val LocalColors = staticCompositionLocalOf { LightCleanShareColors }
