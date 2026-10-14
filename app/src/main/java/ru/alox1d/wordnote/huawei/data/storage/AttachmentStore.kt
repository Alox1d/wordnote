package ru.alox1d.wordnote.huawei.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import java.io.File
import java.io.IOException
import java.util.UUID

/** Note-owned images stay in app-private storage, including images in visible notes. */
class AttachmentStore(private val context: Context) {
    val directory: File get() = File(context.filesDir, "attachments")

    fun saveBitmap(bitmap: Bitmap): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot prepare private image storage" }
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cannot save image")
                it.flush()
            }
            return file
        } catch (error: Exception) { file.delete(); throw error }
    }

    fun resolve(path: String): File {
        val file = if (path.startsWith("file://")) File(requireNotNull(Uri.parse(path).path)) else File(path)
        val candidate = file.canonicalFile
        @Suppress("DEPRECATION") val roots = listOf(directory,
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WordNote"),
            File(Environment.getExternalStorageDirectory(), "WordNote"))
        require(roots.any { contains(it, candidate) } && candidate.isFile && candidate.length() in 1..MAX_IMAGE_BYTES) { "Missing or unsupported note image" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(candidate.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000) { "Invalid note image" }
        return candidate
    }

    /** Never delete public gallery originals, or an image still referenced by another note. */
    fun deleteIfUnused(path: String): Boolean {
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        if (!contains(directory, file)) return false
        val db = TaskDatabase.getInstance(context)
        val remaining = TaskRepository(db.readableDatabase, db.writableDatabase).getTasks(null, null, null)
        val stillUsed = runCatching { remaining.any { note -> AttachmentReferences.paths(note).any {
            runCatching { File(it).canonicalFile == file }.getOrDefault(true)
        } } }.getOrDefault(true)
        return !stillUsed && file.delete()
    }

    companion object {
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        internal fun contains(root: File, file: File) = file.canonicalPath.startsWith(root.canonicalPath + File.separator)
    }
}
