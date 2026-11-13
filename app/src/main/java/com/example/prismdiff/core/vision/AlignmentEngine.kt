package com.example.prismdiff.core.vision

import com.example.prismdiff.core.model.AlignmentMethod
import com.example.prismdiff.core.model.AlignmentResult
import com.example.prismdiff.core.model.FeatureMatchPair
import com.example.prismdiff.core.model.RelativeSlotOffset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic geometric image registration engine.
 *
 * Implements two complementary offline alignment strategies:
 * 1. Coarse-to-fine Normalized Cross-Correlation (NCC) Translation Alignment.
 * 2. Gradient-corner Feature Extraction + Patch Matching + RANSAC Similarity
 *    Estimation (translation + rotation + uniform scale) with explainable confidence.
 */
object AlignmentEngine {

    fun autoAlign(
        pixelsA: IntArray,
        pixelsB: IntArray,
        width: Int,
        height: Int,
        allowRotationAndScale: Boolean = true,
    ): AlignmentResult {
        if (width < 24 || height < 24 || pixelsA.size < width * height || pixelsB.size < width * height) {
            return AlignmentResult(
                method = AlignmentMethod.PHASE_CORRELATION,
                offset = RelativeSlotOffset(),
                confidence = 0f,
                matchedFeatures = 0,
                inlierRatio = 0f,
                isReliable = false,
                summaryNote = "Image resolution too small for reliable registration.",
            )
        }

        val lumaA = FloatArray(width * height) { ColorMath.luma255(pixelsA[it]).toFloat() }
        val lumaB = FloatArray(width * height) { ColorMath.luma255(pixelsB[it]).toFloat() }

        if (allowRotationAndScale) {
            val featureResult = alignByFeaturesRansac(lumaA, lumaB, width, height)
            if (featureResult.isReliable) return featureResult
        }
        return alignByTranslationNcc(lumaA, lumaB, width, height)
    }

    private fun alignByTranslationNcc(
        lumaA: FloatArray,
        lumaB: FloatArray,
        width: Int,
        height: Int,
    ): AlignmentResult {
        val maxShiftX = max(4, width / 5)
        val maxShiftY = max(4, height / 5)

        var bestDx = 0
        var bestDy = 0
        var bestScore = -1.0

        // Coarse search step 3
        val step = 3
        val marginX = maxShiftX + 2
        val marginY = maxShiftY + 2
        for (dy in -maxShiftY..maxShiftY step step) {
            for (dx in -maxShiftX..maxShiftX step step) {
                val score = patchNcc(lumaA, lumaB, width, height, dx, dy, marginX, marginY, stride = 3)
                if (score > bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        // Fine search around best coarse shift
        val cDx = bestDx
        val cDy = bestDy
        for (dy in (cDy - 3)..(cDy + 3)) {
            for (dx in (cDx - 3)..(cDx + 3)) {
                val score = patchNcc(lumaA, lumaB, width, height, dx, dy, marginX, marginY, stride = 2)
                if (score > bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        val du = bestDx.toFloat() / width.toFloat()
        val dv = bestDy.toFloat() / height.toFloat()
        val conf = bestScore.toFloat().coerceIn(0f, 1f)
        val reliable = conf >= 0.45f

        return AlignmentResult(
            method = AlignmentMethod.PHASE_CORRELATION,
            offset = RelativeSlotOffset(deltaU = du, deltaV = dv),
            confidence = conf,
            matchedFeatures = 1,
            inlierRatio = conf,
            isReliable = reliable,
            summaryNote = if (reliable) {
                "Translation aligned (${bestDx}px, ${bestDy}px) at ${(conf * 100).toInt()}% NCC confidence."
            } else {
                "Low correlation (${(conf * 100).toInt()}%); scenes may have strong perspective or lighting differences."
            },
        )
    }

    private fun alignByFeaturesRansac(
        lumaA: FloatArray,
        lumaB: FloatArray,
        width: Int,
        height: Int,
    ): AlignmentResult {
        val kpsA = detectCornerKeypoints(lumaA, width, height, maxKeypoints = 48)
        val kpsB = detectCornerKeypoints(lumaB, width, height, maxKeypoints = 64)
        if (kpsA.size < 4 || kpsB.size < 4) {
            return AlignmentResult(
                method = AlignmentMethod.BLOCK_FEATURE_RANSAC,
                offset = RelativeSlotOffset(),
                confidence = 0f,
                matchedFeatures = 0,
                inlierRatio = 0f,
                isReliable = false,
                summaryNote = "Insufficient distinct corners for feature matching.",
            )
        }

        val matches = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        for (ka in kpsA) {
            var bestIdx = -1
            var bestCorr = -1.0
            var secondCorr = -1.0
            for (j in kpsB.indices) {
                val kb = kpsB[j]
                val corr = localWindowNcc(lumaA, lumaB, width, height, ka.first, ka.second, kb.first, kb.second, radius = 4)
                if (corr > bestCorr) {
                    secondCorr = bestCorr
                    bestCorr = corr
                    bestIdx = j
                } else if (corr > secondCorr) {
                    secondCorr = corr
                }
            }
            if (bestIdx >= 0 && bestCorr >= 0.65 && (bestCorr - secondCorr) >= 0.04) {
                matches.add(ka to kpsB[bestIdx])
            }
        }

        if (matches.size < 3) {
            return AlignmentResult(
                method = AlignmentMethod.BLOCK_FEATURE_RANSAC,
                offset = RelativeSlotOffset(),
                confidence = 0.2f,
                matchedFeatures = matches.size,
                inlierRatio = 0f,
                isReliable = false,
                summaryNote = "Only ${matches.size} feature matches found; falling back to translation search.",
            )
        }

        // RANSAC 2-point similarity transform: B = s * R(theta) * (A - c) + c + t
        val cx = width * 0.5
        val cy = height * 0.5
        var bestScale = 1.0
        var bestAngleRad = 0.0
        var bestTx = 0.0
        var bestTy = 0.0
        var bestInliers = BooleanArray(matches.size)
        var bestInlierCount = 0
        val tolerancePx = max(3.5, width * 0.025)

        val maxPairs = min(matches.size, 20)
        for (i in 0 until maxPairs) {
            for (j in (i + 1) until maxPairs) {
                val (a1, b1) = matches[i]
                val (a2, b2) = matches[j]
                val vAx = (a2.first - a1.first).toDouble()
                val vAy = (a2.second - a1.second).toDouble()
                val vBx = (b2.first - b1.first).toDouble()
                val vBy = (b2.second - b1.second).toDouble()
                val distA = hypot(vAx, vAy)
                val distB = hypot(vBx, vBy)
                if (distA < 8.0 || distB < 8.0) continue

                val scale = (distB / distA).coerceIn(0.6, 1.65)
                val angle = atan2(vBy, vBx) - atan2(vAy, vAx)
                val cosA = cos(angle) * scale
                val sinA = sin(angle) * scale

                val a1x = a1.first - cx
                val a1y = a1.second - cy
                val tx = (b1.first - cx) - (cosA * a1x - sinA * a1y)
                val ty = (b1.second - cy) - (sinA * a1x + cosA * a1y)

                val flags = BooleanArray(matches.size)
                var inliers = 0
                for (m in matches.indices) {
                    val (ma, mb) = matches[m]
                    val ax = ma.first - cx
                    val ay = ma.second - cy
                    val predX = cx + (cosA * ax - sinA * ay + tx)
                    val predY = cy + (sinA * ax + cosA * ay + ty)
                    if (hypot(predX - mb.first, predY - mb.second) <= tolerancePx) {
                        flags[m] = true
                        inliers++
                    }
                }
                if (inliers > bestInlierCount) {
                    bestInlierCount = inliers
                    bestInliers = flags
                    bestScale = scale
                    bestAngleRad = angle
                    bestTx = tx
                    bestTy = ty
                }
            }
        }

        val inlierRatio = bestInlierCount.toFloat() / matches.size.toFloat()
        val reliable = bestInlierCount >= 3 && inlierRatio >= 0.45f
        val angleDeg = (-Math.toDegrees(bestAngleRad)).toFloat()
        val deltaZoom = (1.0 / bestScale).toFloat().coerceIn(0.5f, 2.0f)
        val du = (bestTx / width).toFloat().coerceIn(-0.6f, 0.6f)
        val dv = (bestTy / height).toFloat().coerceIn(-0.6f, 0.6f)

        val pairs = matches.mapIndexed { idx, (a, b) ->
            FeatureMatchPair(
                normAx = a.first.toFloat() / width,
                normAy = a.second.toFloat() / height,
                normBx = b.first.toFloat() / width,
                normBy = b.second.toFloat() / height,
                isInlier = bestInliers[idx],
            )
        }

        val confidence = ((inlierRatio * 0.7f) + (min(1f, bestInlierCount / 12f) * 0.3f)).coerceIn(0f, 1f)
        return AlignmentResult(
            method = AlignmentMethod.BLOCK_FEATURE_RANSAC,
            offset = RelativeSlotOffset(
                deltaU = du,
                deltaV = dv,
                deltaZoom = deltaZoom,
                deltaRotationDeg = angleDeg,
            ),
            confidence = confidence,
            matchedFeatures = matches.size,
            inlierRatio = inlierRatio,
            isReliable = reliable,
            summaryNote = if (reliable) {
                "RANSAC matched $bestInlierCount/${matches.size} features (rot ${String.format("%.1f", angleDeg)}°, scale ${String.format("%.2f", deltaZoom)}x)."
            } else {
                "Feature match confidence low ($bestInlierCount/${matches.size} inliers)."
            },
            featurePairs = pairs,
        )
    }

    private fun detectCornerKeypoints(
        luma: FloatArray,
        width: Int,
        height: Int,
        maxKeypoints: Int,
    ): List<Pair<Int, Int>> {
        val candidates = mutableListOf<Triple<Int, Int, Float>>()
        val margin = 6
        for (y in margin until height - margin step 2) {
            for (x in margin until width - margin step 2) {
                val i = y * width + x
                val dx = luma[i + 1] - luma[i - 1]
                val dy = luma[i + width] - luma[i - width]
                val dxy = abs(luma[i + width + 1] - luma[i - width - 1])
                val score = min(abs(dx), abs(dy)) + dxy * 0.35f
                if (score > 22f) {
                    candidates.add(Triple(x, y, score))
                }
            }
        }
        candidates.sortByDescending { it.third }
        val selected = mutableListOf<Pair<Int, Int>>()
        val minDist = max(6f, width / 22f)
        for (c in candidates) {
            if (selected.all { hypot((it.first - c.first).toFloat(), (it.second - c.second).toFloat()) >= minDist }) {
                selected.add(c.first to c.second)
                if (selected.size >= maxKeypoints) break
            }
        }
        return selected
    }

    private fun patchNcc(
        a: FloatArray,
        b: FloatArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
        marginX: Int,
        marginY: Int,
        stride: Int,
    ): Double {
        var sumA = 0.0
        var sumB = 0.0
        var count = 0
        for (y in marginY until height - marginY step stride) {
            val rowA = y * width
            val rowB = (y + dy) * width
            for (x in marginX until width - marginX step stride) {
                sumA += a[rowA + x]
                sumB += b[rowB + x + dx]
                count++
            }
        }
        if (count == 0) return 0.0
        val meanA = sumA / count
        val meanB = sumB / count
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (y in marginY until height - marginY step stride) {
            val rowA = y * width
            val rowB = (y + dy) * width
            for (x in marginX until width - marginX step stride) {
                val da = a[rowA + x] - meanA
                val db = b[rowB + x + dx] - meanB
                num += da * db
                denA += da * da
                denB += db * db
            }
        }
        val den = sqrt(denA * denB)
        return if (den > 1e-6) (num / den) else 0.0
    }

    private fun localWindowNcc(
        a: FloatArray,
        b: FloatArray,
        width: Int,
        height: Int,
        ax: Int,
        ay: Int,
        bx: Int,
        by: Int,
        radius: Int,
    ): Double {
        var meanA = 0.0
        var meanB = 0.0
        var count = 0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                meanA += a[(ay + dy) * width + (ax + dx)]
                meanB += b[(by + dy) * width + (bx + dx)]
                count++
            }
        }
        meanA /= count
        meanB /= count
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val da = a[(ay + dy) * width + (ax + dx)] - meanA
                val db = b[(by + dy) * width + (bx + dx)] - meanB
                num += da * db
                denA += da * da
                denB += db * db
            }
        }
        val den = sqrt(denA * denB)
        return if (den > 1e-6) num / den else 0.0
    }
}
