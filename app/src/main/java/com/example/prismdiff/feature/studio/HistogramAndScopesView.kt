package com.example.prismdiff.feature.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.prismdiff.core.designsystem.PrismDiffTokens
import com.example.prismdiff.core.model.HistogramData
import kotlin.math.ln
import kotlin.math.max

/**
 * Renders live RGB + Luminance histograms (Linear or Logarithmic scale) cleanly
 * for Image A and Image B side-by-side or overlaid.
 */
@Composable
fun DualHistogramScope(
    histogramA: HistogramData?,
    histogramB: HistogramData?,
    isLogScale: Boolean,
    overlaid: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(PrismDiffTokens.HairlineDivider, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (overlaid) {
            Text(
                text = "Overlaid RGB + Luma Scope (${histogramA?.regionLabel ?: "Viewport"})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HistogramPlot(
                data = histogramA,
                compareData = histogramB,
                isLogScale = isLogScale,
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Slot A Histogram", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    HistogramPlot(
                        data = histogramA,
                        compareData = null,
                        isLogScale = isLogScale,
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Slot B Histogram", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    HistogramPlot(
                        data = histogramB,
                        compareData = null,
                        isLogScale = isLogScale,
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistogramPlot(
    data: HistogramData?,
    compareData: HistogramData?,
    isLogScale: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF06080B)),
    ) {
        if (data == null || data.sampleCount <= 0) return@Canvas

        fun transformVal(v: Int): Float {
            return if (isLogScale) ln((v + 1).toDouble()).toFloat() else v.toFloat()
        }

        var maxPeak = 1f
        for (i in 0 until 256) {
            maxPeak = max(maxPeak, transformVal(data.redBins[i]))
            maxPeak = max(maxPeak, transformVal(data.greenBins[i]))
            maxPeak = max(maxPeak, transformVal(data.blueBins[i]))
            maxPeak = max(maxPeak, transformVal(data.lumaBins[i]))
            if (compareData != null) {
                maxPeak = max(maxPeak, transformVal(compareData.lumaBins[i]))
            }
        }

        // Subtle IRE quarter grid lines
        for (q in 1..3) {
            val gx = size.width * (q / 4f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
        }

        fun drawChannel(bins: IntArray, strokeColor: Color, fillAlpha: Float) {
            val path = Path()
            val fillPath = Path()
            fillPath.moveTo(0f, size.height)
            for (i in 0 until 256) {
                val x = (i / 255f) * size.width
                val normY = (transformVal(bins[i]) / maxPeak).coerceIn(0f, 1f)
                val y = size.height - normY * (size.height - 4f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()
            if (fillAlpha > 0f) {
                drawPath(fillPath, color = strokeColor.copy(alpha = fillAlpha), style = Fill)
            }
            drawPath(path, color = strokeColor, style = Stroke(width = 1.6f))
        }

        drawChannel(data.redBins, Color(0xFFFF5252), 0.14f)
        drawChannel(data.greenBins, Color(0xFF4CAF50), 0.14f)
        drawChannel(data.blueBins, Color(0xFF448AFF), 0.14f)
        drawChannel(data.lumaBins, Color.White.copy(alpha = 0.85f), 0.10f)

        if (compareData != null) {
            drawChannel(compareData.lumaBins, Color(0xFFFFB74D), 0f)
        }
    }
}
