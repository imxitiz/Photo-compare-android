package com.example.prismdiff.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismdiff.core.model.CanvasBackdropMode
import kotlin.math.ceil

@Composable
fun StudioCanvasBackdrop(
    mode: CanvasBackdropMode,
    customArgb: Long,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        CanvasBackdropMode.CHECKERBOARD -> {
            Canvas(modifier = modifier.fillMaxSize()) {
                val cell = 14.dp.toPx()
                val cols = ceil(size.width / cell).toInt()
                val rows = ceil(size.height / cell).toInt()
                drawRect(PrismDiffColors.CheckerDark)
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        if ((r + c) % 2 == 0) {
                            drawRect(
                                color = PrismDiffColors.CheckerLight,
                                topLeft = Offset(c * cell, r * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }
                }
            }
        }
        else -> {
            val bgColor = when (mode) {
                CanvasBackdropMode.OLED_BLACK -> PrismDiffColors.OledBlack
                CanvasBackdropMode.NEUTRAL_18_GRAY -> PrismDiffColors.Neutral18Gray
                CanvasBackdropMode.STUDIO_WHITE -> Color.White
                CanvasBackdropMode.CUSTOM -> Color(customArgb)
                CanvasBackdropMode.CHECKERBOARD -> PrismDiffColors.OledBlack
            }
            Box(modifier = modifier.fillMaxSize().background(bgColor))
        }
    }
}

@Composable
fun StudioActionChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = PrismDiffTokens.MinTouchTarget)
            .clip(RoundedCornerShape(PrismDiffTokens.StudioRadiusPill))
            .background(bg)
            .border(PrismDiffTokens.HairlineDivider, borderColor, RoundedCornerShape(PrismDiffTokens.StudioRadiusPill))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StudioBadgePill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    darkOpticalOverlay: Boolean = true,
) {
    val bg = if (darkOpticalOverlay) {
        Color(0xD90B0E14)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
    }
    val fg = if (darkOpticalOverlay) {
        Color(0xFFF2F5FA)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(PrismDiffTokens.StudioRadiusSmall),
        border = androidx.compose.foundation.BorderStroke(
            PrismDiffTokens.HairlineDivider,
            accent.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun StudioSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StudioTouchIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    active: Boolean = false,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val iconColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else tint
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .clip(RoundedCornerShape(PrismDiffTokens.StudioRadiusPill))
            .background(bg)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
