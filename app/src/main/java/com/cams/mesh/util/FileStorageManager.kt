package com.cams.mesh.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

class FileStorageManager(private val context: Context) {

    private val materialsDir: File
        get() {
            val dir = File(context.filesDir, "materials")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val tempDir: File
        get() {
            val dir = File(context.cacheDir, "transfer_temp")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun sanitizeFilename(fileName: String): String {
        val cleanName = File(fileName).name
        return cleanName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    fun saveFileFromUri(uri: Uri, materialId: String): Pair<File, String> {
        val originalName = queryFileName(uri) ?: "attachment_$materialId"
        val safeName = sanitizeFilename(originalName)
        val targetFile = File(materialsDir, "${materialId}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot read file from Uri: $uri")

        val sha256 = HashUtil.calculateSha256(targetFile)
        return Pair(targetFile, sha256)
    }

    fun createTempFileForTransfer(materialId: String): File {
        return File(tempDir, "${materialId}.part")
    }

    fun finalizeReceivedFile(tempFile: File, materialId: String, originalFileName: String, expectedHash: String): File {
        val calculatedHash = HashUtil.calculateSha256(tempFile)
        if (!calculatedHash.equals(expectedHash, ignoreCase = true)) {
            tempFile.delete()
            throw IllegalStateException("Hash mismatch! Expected: $expectedHash, Actual: $calculatedHash")
        }

        val safeName = sanitizeFilename(originalFileName)
        val targetFile = File(materialsDir, "${materialId}_$safeName")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        return targetFile
    }

    fun deleteFile(path: String?): Boolean {
        if (path == null) return false
        val file = File(path)
        return if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
            file.delete()
        } else false
    }

    fun clearAllFiles(): Boolean {
        var success = true
        materialsDir.listFiles()?.forEach { file ->
            if (!file.delete()) success = false
        }
        tempDir.listFiles()?.forEach { file ->
            if (!file.delete()) success = false
        }
        return success
    }

    fun getMimeType(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName).lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun queryFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
