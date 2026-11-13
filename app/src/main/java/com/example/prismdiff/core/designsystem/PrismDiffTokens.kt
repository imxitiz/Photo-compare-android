package com.example.prismdiff.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object PrismDiffTokens {
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp

    val MinTouchTarget = 48.dp
    val HairlineDivider = 1.dp
    val StudioRadiusSmall = 10.dp
    val StudioRadiusMedium = 16.dp
    val StudioRadiusPill = 28.dp

    val StandardSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

val PrismDiffShapes = Shapes(
    small = RoundedCornerShape(PrismDiffTokens.StudioRadiusSmall),
    medium = RoundedCornerShape(PrismDiffTokens.StudioRadiusMedium),
    large = RoundedCornerShape(PrismDiffTokens.StudioRadiusPill),
)
