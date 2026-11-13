package com.example.prismdiff.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class AnalysisMetricReport(
    val id: String,
    val title: String,
    val algorithmName: String,
    val valueFormatted: String,
    val numericValue: Double,
    val unit: String,
    val regionScope: String,
    val alignmentApplied: Boolean,
    val colorNormalized: Boolean,
    val explanation: String,
    val limitationNote: String,
)

@Immutable
data class ComparisonMetricsBundle(
    val ssim: Double,
    val psnrDb: Double,
    val mae: Double,
    val rmse: Double,
    val maxDelta: Int,
    val changedPixelCount: Int,
    val changedPixelPercent: Double,
    val meanDeltaE2000: Double,
    val maxDeltaE2000: Double,
    val sharpnessA: Double,
    val sharpnessB: Double,
    val noiseFloorA: Double,
    val noiseFloorB: Double,
    val clippingHighPercentA: Double,
    val clippingShadowPercentA: Double,
    val clippingHighPercentB: Double,
    val clippingShadowPercentB: Double,
    val scopeLabel: String,
    val alignmentApplied: Boolean,
    val colorNormalized: Boolean,
    val reports: List<AnalysisMetricReport>,
)

@Immutable
data class HistogramData(
    val lumaBins: IntArray,
    val redBins: IntArray,
    val greenBins: IntArray,
    val blueBins: IntArray,
    val sampleCount: Int,
    val regionLabel: String = "Full Frame",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistogramData) return false
        return sampleCount == other.sampleCount &&
            regionLabel == other.regionLabel &&
            lumaBins.contentEquals(other.lumaBins) &&
            redBins.contentEquals(other.redBins) &&
            greenBins.contentEquals(other.greenBins) &&
            blueBins.contentEquals(other.blueBins)
    }

    override fun hashCode(): Int {
        var result = lumaBins.contentHashCode()
        result = 31 * result + redBins.contentHashCode()
        result = 31 * result + greenBins.contentHashCode()
        result = 31 * result + blueBins.contentHashCode()
        result = 31 * result + sampleCount
        result = 31 * result + regionLabel.hashCode()
        return result
    }
}

enum class AlignmentMethod {
    PHASE_CORRELATION,
    BLOCK_FEATURE_RANSAC,
    MANUAL_ANCHOR,
}

@Immutable
data class FeatureMatchPair(
    val normAx: Float,
    val normAy: Float,
    val normBx: Float,
    val normBy: Float,
    val isInlier: Boolean,
)

@Immutable
data class AlignmentResult(
    val method: AlignmentMethod,
    val offset: RelativeSlotOffset,
    val confidence: Float,
    val matchedFeatures: Int,
    val inlierRatio: Float,
    val isReliable: Boolean,
    val summaryNote: String,
    val featurePairs: List<FeatureMatchPair> = emptyList(),
)

@Immutable
data class BurstCluster(
    val id: String,
    val title: String,
    val reason: String,
    val assets: List<ImageAsset>,
    val confidence: Float,
)

@Immutable
data class ExifDiffEntry(
    val key: String,
    val label: String,
    val valueA: String,
    val valueB: String,
    val deltaSummary: String?,
    val differs: Boolean,
    val isSensitiveGps: Boolean = false,
)

@Serializable
enum class CullingDecision {
    UNDECIDED,
    KEEP,
    FAVORITE,
    REJECT,
    MAYBE,
}

@Immutable
@Serializable
data class ImageCullingState(
    val assetId: String,
    val decision: CullingDecision = CullingDecision.UNDECIDED,
    val stars: Int = 0,
    val colorLabel: String? = null,
    val eloRating: Double = 1200.0,
    val comparisonsPlayed: Int = 0,
    val note: String = "",
)
