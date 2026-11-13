package com.example.prismdiff.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ExifSnapshot(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val focalLengthMm: Double? = null,
    val equivalentFocalLength35mm: Int? = null,
    val apertureFNumber: Double? = null,
    val shutterSpeedSeconds: Double? = null,
    val shutterSpeedDisplay: String? = null,
    val iso: Int? = null,
    val exposureBiasEv: Double? = null,
    val flashFired: Boolean? = null,
    val whiteBalanceMode: String? = null,
    val meteringMode: String? = null,
    val dateTimeOriginal: String? = null,
    val software: String? = null,
    val colorSpace: String? = null,
    val bitsPerSample: Int? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val jpegSubsampling: String? = null,
    val jpegQualityEstimate: Int? = null,
    val hasUltraHdrGainMap: Boolean = false,
    val rawTags: Map<String, String> = emptyMap(),
)

@Immutable
@Serializable
data class ImageAsset(
    val id: String,
    val uriString: String,
    val displayName: String,
    val folderName: String = "Picker",
    val width: Int = 0,
    val height: Int = 0,
    val orientationDegrees: Int = 0,
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "image/jpeg",
    val dateTakenMs: Long = 0L,
    val dateModifiedMs: Long = 0L,
    val dHash: Long? = null,
    val sharpnessHint: Double? = null,
    val exifSnapshot: ExifSnapshot = ExifSnapshot(),
    val isSampleAsset: Boolean = false,
) {
    val megapixelCount: Double
        get() = (width.toLong() * height.toLong()) / 1_000_000.0

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    val bitsPerPixel: Double
        get() {
            val pixels = width.toLong() * height.toLong()
            return if (pixels > 0L && fileSizeBytes > 0L) (fileSizeBytes * 8.0) / pixels else 0.0
        }
}

sealed interface ImageLoadError {
    val message: String

    data class UnsupportedFormat(override val message: String) : ImageLoadError
    data class CorruptedFile(override val message: String) : ImageLoadError
    data class PermissionDenied(override val message: String) : ImageLoadError
    data class FileNotFound(override val message: String) : ImageLoadError
    data class OutOfMemory(override val message: String) : ImageLoadError
    data class Unknown(override val message: String) : ImageLoadError
}
