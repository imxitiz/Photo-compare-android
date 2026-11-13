package com.example.prismdiff.core.vision

import com.example.prismdiff.core.model.BurstCluster
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.ImageCullingState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Computes 64-bit gradient perceptual hashes (dHash) and clusters similar scenes/bursts
 * with transparent, human-readable explanations.
 */
object BurstClusteringEngine {

    /**
     * Computes a 64-bit difference hash (9x8 downsampled luminance grid).
     * Resistant to compression, slight brightness changes, and resolution scaling.
     */
    fun computeDHash(pixels: IntArray, width: Int, height: Int): Long {
        if (width < 9 || height < 8 || pixels.size < width * height) return 0L
        val grid = IntArray(9 * 8)
        for (gy in 0 until 8) {
            val sy = ((gy + 0.5) * height / 8.0).toInt().coerceIn(0, height - 1)
            for (gx in 0 until 9) {
                val sx = ((gx + 0.5) * width / 9.0).toInt().coerceIn(0, width - 1)
                grid[gy * 9 + gx] = ColorMath.luma255(pixels[sy * width + sx])
            }
        }
        var hash = 0L
        var bit = 0
        for (gy in 0 until 8) {
            for (gx in 0 until 8) {
                val left = grid[gy * 9 + gx]
                val right = grid[gy * 9 + gx + 1]
                if (left > right) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun clusterAssets(
        assets: List<ImageAsset>,
        maxTimeGapMs: Long = 5_000L,
        maxDHashDistance: Int = 12,
    ): List<BurstCluster> {
        if (assets.isEmpty()) return emptyList()
        val remaining = assets.sortedBy { it.dateTakenMs.takeIf { ts -> ts > 0L } ?: it.dateModifiedMs }.toMutableList()
        val clusters = mutableListOf<BurstCluster>()
        var clusterIdx = 1

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val group = mutableListOf(seed)
            var matchedByTime = false
            var matchedByHash = false
            var matchedByCamera = false

            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val cand = iter.next()
                val seedTs = seed.dateTakenMs.takeIf { it > 0L } ?: seed.dateModifiedMs
                val candTs = cand.dateTakenMs.takeIf { it > 0L } ?: cand.dateModifiedMs
                val timeClose = seedTs > 0L && candTs > 0L && abs(candTs - seedTs) <= maxTimeGapMs

                val hashClose = seed.dHash != null && cand.dHash != null &&
                    hammingDistance(seed.dHash, cand.dHash) <= maxDHashDistance

                val sameFocal = seed.exifSnapshot.focalLengthMm != null &&
                    seed.exifSnapshot.focalLengthMm == cand.exifSnapshot.focalLengthMm

                if (hashClose || timeClose) {
                    group.add(cand)
                    iter.remove()
                    if (timeClose) matchedByTime = true
                    if (hashClose) matchedByHash = true
                    if (sameFocal) matchedByCamera = true
                }
            }

            if (group.size >= 2) {
                val reasons = buildList {
                    if (matchedByHash) add("visual dHash scene match")
                    if (matchedByTime) add("captured within ${maxTimeGapMs / 1000}s")
                    if (matchedByCamera) add("matching focal length")
                }
                val confidence = when {
                    matchedByHash && matchedByTime -> 0.94f
                    matchedByHash -> 0.85f
                    else -> 0.72f
                }
                clusters.add(
                    BurstCluster(
                        id = "cluster_${clusterIdx}",
                        title = "Scene Group #${clusterIdx} (${group.size} photos)",
                        reason = "Grouped by: ${reasons.joinToString(" + ").ifEmpty { "sequence proximity" }}",
                        assets = group,
                        confidence = confidence,
                    ),
                )
                clusterIdx++
            }
        }
        return clusters
    }
}

/**
 * Swiss / Elo pairwise ranking engine for efficiently selecting the top shots
 * from 5..100+ candidates without requiring O(N^2) exhaustive pair comparisons.
 */
object EloTournamentEngine {

    fun recordMatch(
        winner: ImageCullingState,
        loser: ImageCullingState,
        kFactor: Double = 32.0,
    ): Pair<ImageCullingState, ImageCullingState> {
        val expectedWinner = 1.0 / (1.0 + 10.0.pow((loser.eloRating - winner.eloRating) / 400.0))
        val expectedLoser = 1.0 - expectedWinner
        val newWinner = winner.copy(
            eloRating = winner.eloRating + kFactor * (1.0 - expectedWinner),
            comparisonsPlayed = winner.comparisonsPlayed + 1,
        )
        val newLoser = loser.copy(
            eloRating = loser.eloRating + kFactor * (0.0 - expectedLoser),
            comparisonsPlayed = loser.comparisonsPlayed + 1,
        )
        return newWinner to newLoser
    }

    /**
     * Suggests the next highest-information pair (closest Elo scores with fewest games played).
     */
    fun suggestNextPair(
        assetIds: List<String>,
        states: Map<String, ImageCullingState>,
    ): Pair<String, String>? {
        if (assetIds.size < 2) return null
        val sorted = assetIds.sortedWith(
            compareBy<String>(
                { states[it]?.comparisonsPlayed ?: 0 },
                { -(states[it]?.eloRating ?: 1200.0) },
            ),
        )
        val first = sorted[0]
        val firstRating = states[first]?.eloRating ?: 1200.0
        val second = sorted.drop(1).minByOrNull { id ->
            val r = states[id]?.eloRating ?: 1200.0
            abs(r - firstRating) + (states[id]?.comparisonsPlayed ?: 0) * 18.0
        } ?: sorted[1]
        return first to second
    }
}
