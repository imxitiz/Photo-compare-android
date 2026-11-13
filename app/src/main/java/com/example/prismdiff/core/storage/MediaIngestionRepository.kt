package com.example.prismdiff.core.storage

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.prismdiff.core.decoder.MetadataExtractor
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Zero-permission media ingestion and safe Trash/Delete gateway.
 *
 * Uses Android Photo Picker, Storage Access Framework (SAF), and ACTION_SEND intents
 * without requesting broad READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE permissions.
 */
class MediaIngestionRepository(private val context: Context) {

    suspend fun ingestUris(
        uris: List<Uri>,
        folderLabel: String = "Selected",
    ): List<ImageAsset> = withContext(Dispatchers.IO) {
        uris.distinct().mapNotNull { uri ->
            runCatching {
                var name = uri.lastPathSegment ?: "image.jpg"
                var size = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                    }
                }
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                MetadataExtractor.inspectAsset(
                    context = context,
                    uri = uri,
                    displayName = name,
                    folderName = folderLabel,
                    fileSizeBytes = size,
                    mimeType = mime,
                    dateModifiedMs = System.currentTimeMillis(),
                )
            }.getOrNull()
        }
    }

    suspend fun listSafFolderImages(treeUri: Uri): List<ImageAsset> = withContext(Dispatchers.IO) {
        val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val folderName = docTree.name ?: "Folder"
        val imageDocs = docTree.listFiles()
            .filter { it.isFile && (it.type?.startsWith("image/") == true || isImageExtension(it.name)) }
            .take(250)

        imageDocs.mapNotNull { doc ->
            runCatching {
                MetadataExtractor.inspectAsset(
                    context = context,
                    uri = doc.uri,
                    displayName = doc.name ?: "image",
                    folderName = folderName,
                    fileSizeBytes = doc.length(),
                    mimeType = doc.type ?: "image/jpeg",
                    dateModifiedMs = doc.lastModified(),
                )
            }.getOrNull()
        }
    }

    fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list ?: emptyList()
            }
            else -> emptyList()
        }
    }

    fun createSystemTrashOrDeleteIntent(
        assets: List<ImageAsset>,
        permanentDelete: Boolean,
    ): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val mediaUris = assets.mapNotNull { asset ->
            val u = Uri.parse(asset.uriString)
            if (u.scheme == "content" && u.authority == MediaStore.AUTHORITY) u else null
        }
        if (mediaUris.isEmpty()) return null
        return runCatching {
            if (permanentDelete) {
                MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
            } else {
                MediaStore.createTrashRequest(context.contentResolver, mediaUris, true)
            }
        }.getOrNull()
    }

    suspend fun deleteDirectSafOrCacheAssets(assets: List<ImageAsset>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (asset in assets) {
            val uri = Uri.parse(asset.uriString)
            val ok = when (uri.scheme) {
                "file" -> uri.path?.let { File(it).delete() } == true
                "content" -> runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }.getOrDefault(false)
                else -> false
            }
            if (ok) deleted++
        }
        deleted
    }

    private fun isImageExtension(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".heif") ||
            lower.endsWith(".avif") || lower.endsWith(".dng") || lower.endsWith(".bmp")
    }

    companion object {
        fun sortAssets(assets: List<ImageAsset>, order: SortOrder): List<ImageAsset> {
            return when (order) {
                SortOrder.EXIF_DATE_DESC -> assets.sortedByDescending { it.dateTakenMs.takeIf { ts -> ts > 0L } ?: it.dateModifiedMs }
                SortOrder.EXIF_DATE_ASC -> assets.sortedBy { it.dateTakenMs.takeIf { ts -> ts > 0L } ?: it.dateModifiedMs }
                SortOrder.FILENAME_NATURAL_ASC -> assets.sortedWith { a, b -> naturalCompare(a.displayName, b.displayName) }
                SortOrder.FILENAME_NATURAL_DESC -> assets.sortedWith { a, b -> naturalCompare(b.displayName, a.displayName) }
                SortOrder.MODIFIED_DESC -> assets.sortedByDescending { it.dateModifiedMs }
                SortOrder.FILE_SIZE_DESC -> assets.sortedByDescending { it.fileSizeBytes }
                SortOrder.RESOLUTION_DESC -> assets.sortedByDescending { it.width.toLong() * it.height.toLong() }
                SortOrder.SHARPNESS_DESC -> assets.sortedByDescending { it.sharpnessHint ?: 0.0 }
            }
        }

        /** Natural alphanumeric comparator so IMG_2.jpg precedes IMG_10.jpg. */
        fun naturalCompare(a: String, b: String): Int {
            var ia = 0
            var ib = 0
            while (ia < a.length && ib < b.length) {
                val ca = a[ia]
                val cb = b[ib]
                if (ca.isDigit() && cb.isDigit()) {
                    var endA = ia
                    while (endA < a.length && a[endA].isDigit()) endA++
                    var endB = ib
                    while (endB < b.length && b[endB].isDigit()) endB++
                    val numA = a.substring(ia, endA).toBigIntegerOrNull()
                    val numB = b.substring(ib, endB).toBigIntegerOrNull()
                    if (numA != null && numB != null && numA != numB) {
                        return numA.compareTo(numB)
                    }
                    ia = endA
                    ib = endB
                } else {
                    val diff = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                    if (diff != 0) return diff
                    ia++
                    ib++
                }
            }
            return a.length.compareTo(b.length)
        }
    }
}
