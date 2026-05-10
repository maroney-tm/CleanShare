package com.maroney.cleanshare.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LayoutColors(val divider: Color)

@Immutable
data class CleanShareColors(val layout: LayoutColors)

internal val LightCleanShareColors = CleanShareColors(
    layout = LayoutColors(divider = Color(0xFFE0E0E0)),
)

internal val DarkCleanShareColors = CleanShareColors(
    layout = LayoutColors(divider = Color(0xFF3D3D3D)),
)

val LocalColors = staticCompositionLocalOf { LightCleanShareColors }
