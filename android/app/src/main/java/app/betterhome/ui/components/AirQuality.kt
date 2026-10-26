package app.betterhome.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * PM2.5 bands (µg/m³), following the WHO/EPA breakpoints. Colours are fixed,
 * not themed: green must always mean clean air.
 */
enum class AirBand(val upTo: Int, val label: String, private val light: Long, private val dark: Long) {
    Excellent(12, "Excellent", 0xFF1E8E5A, 0xFF6FDBA4),
    Good(35, "Good", 0xFF6B8E00, 0xFFC2DE6A),
    Moderate(55, "Moderate", 0xFFB27500, 0xFFFFC765),
    Poor(150, "Poor", 0xFFC2410C, 0xFFFF9E6B),
    VeryPoor(250, "Very poor", 0xFFB3261E, 0xFFFF8A80),
    Hazardous(Int.MAX_VALUE, "Hazardous", 0xFF7B1FA2, 0xFFE1A6FF);

    @Composable
    fun color(): Color = Color(if (isSystemInDarkTheme()) dark else light)

    companion object {
        fun of(pm25: Int): AirBand = entries.first { pm25 <= it.upTo }
    }
}
