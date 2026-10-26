package app.betterhome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ln

private const val SWEEP = 270f
private const val START = 135f

/**
 * PM2.5 dial. The arc is log-scaled so the range people actually live in
 * (0–75) uses most of the sweep instead of a sliver of 0–600.
 */
@Composable
fun AirGauge(pm25: Int?, active: Boolean, modifier: Modifier = Modifier) {
    val band = pm25?.let(AirBand::of)
    val target = pm25?.let { (ln(1f + it) / ln(1f + 300f)).coerceIn(0.02f, 1f) } ?: 0f
    val fraction by animateFloatAsState(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow), label = "gauge")
    val tint by animateColorAsState(
        band?.color() ?: MaterialTheme.colorScheme.outline,
        tween(600), label = "tint",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val dimmed = if (active) 1f else 0.45f

    Box(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = pm25?.let { "PM2.5 $it micrograms, ${band!!.label}" } ?: "No reading" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) {
                drawArc(
                    tint.copy(alpha = dimmed), START, SWEEP * fraction, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                pm25?.toString() ?: "–",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "PM2.5 · µg/m³",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (band != null) {
                Surface(
                    color = tint.copy(alpha = 0.16f),
                    contentColor = tint,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(band.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
    }
}
