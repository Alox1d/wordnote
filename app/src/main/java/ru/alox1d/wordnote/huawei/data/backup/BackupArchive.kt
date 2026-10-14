package ru.alox1d.wordnote.huawei.data.backup

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.alox1d.wordnote.huawei.data.storage.AttachmentReferences
import ru.alox1d.wordnote.huawei.data.storage.AttachmentStore
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A portable, validated snapshot. Cloud transport never receives the live database file. */
class BackupArchive(private val context: Context) {
    private val helper get() = TaskDatabase.getInstance(context)
    private val attachments = AttachmentStore(context)

    fun create(): File = synchronized(lock) {
        val scratch = scratchDirectory()
        try {
            val source = File(scratch, "source.sqlite")
            helper.exportDB(source)
            val images = linkedMapOf<String, File>()
            val paths = HashMap<String, String>()
            val portable = TaskDatabase.readBackup(source).map { note -> AttachmentReferences.map(note) { path ->
                if (AttachmentReferences.isRemote(path)) path else paths.getOrPut(path) {
                    val original = attachments.resolve(path)
                    val copy = File(scratch, "image-${UUID.randomUUID()}")
                    original.inputStream().use { input -> copy.outputStream().use { out -> copyLimited(input, out, AttachmentStore.MAX_IMAGE_BYTES) } }
                    val hash = sha256(copy)
                    val existing = images[hash]
                    if (existing == null) images[hash] = copy else copy.delete()
                    TOKEN_PREFIX + hash
                }
            } }
            require(images.size <= MAX_IMAGES) { "Too many images in backup" }
            val database = File(scratch, DATABASE_ENTRY)
            TaskDatabase.writeBackup(database, portable)
            require(database.length() <= MAX_DATABASE_BYTES) { "Backup database is too large" }
            val totalBytes = images.values.sumOf { it.length() } + database.length()
            require(totalBytes <= MAX_ARCHIVE_BYTES) { "Backup is too large" }
            val manifest = JsonObject().apply {
                addProperty("version", 1)
                addProperty("databaseSha256", sha256(database))
                add("attachments", JsonArray().apply { images.keys.forEach { add(it) } })
            }
            val archive = File(scratch, FILE_NAME)
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                addFile(zip, DATABASE_ENTRY, database)
                images.forEach { (hash, file) -> addFile(zip, "images/$hash.bin", file) }
            }
            require(archive.length() <= MAX_ARCHIVE_BYTES) { "Backup is too large" }
            // Keep only the completed artifact until its cloud operation finishes.
            scratch.listFiles()?.filter { it != archive }?.forEach { it.deleteRecursively() }
            archive
        } catch (error: Exception) {
            scratch.deleteRecursively()
            throw error
        }
    }

    fun restore(file: File, beforeCommit: () -> Unit = {}) = synchronized(lock) {
        require(file.isFile && file.length() in 1..MAX_ARCHIVE_BYTES) { "Invalid backup file" }
        val scratch = scratchDirectory()
        var installedImages: File? = null
        var committed = false
        try {
            val zip = file.inputStream().use { input -> input.read() == 'P'.code && input.read() == 'K'.code }
            val notes: List<ModelTask>
            val imageFiles: Map<String, File>
            if (zip) {
                unpack(file, scratch)
                val manifestFile = File(scratch, MANIFEST_ENTRY)
                require(manifestFile.isFile && manifestFile.length() <= MAX_MANIFEST_BYTES) { "Missing backup manifest" }
                val manifestText = manifestFile.readText(Charsets.UTF_8)
                // The format contains only scalar metadata and a flat array of hashes.
                require(manifestText.count { it == '{' || it == '[' } <= 2) { "Invalid backup manifest" }
                val manifest = JsonParser().parse(manifestText).asJsonObject
                require(manifest.get("version")?.toString() == "1") { "Unsupported backup format" }
                val database = File(scratch, DATABASE_ENTRY)
                require(database.isFile && database.length() <= MAX_DATABASE_BYTES &&
                    manifest.get("databaseSha256")?.asString == sha256(database)) { "Damaged backup database" }
                val entries = manifest.getAsJsonArray("attachments")
                require(entries != null && entries.size() <= MAX_IMAGES) { "Invalid image manifest" }
                val hashes = linkedSetOf<String>()
                entries.forEach { entry ->
                    require(entry.isJsonPrimitive && entry.asJsonPrimitive.isString) { "Invalid image manifest" }
                    val hash = entry.asString
                    require(HASH.matches(hash) && hashes.add(hash)) { "Invalid image manifest" }
                }
                imageFiles = hashes.associateWith { hash ->
                    File(scratch, "images/$hash.bin").also { image ->
                        require(image.isFile && sha256(image) == hash) { "Damaged backup image" }
                    }
                }
                val expectedEntries = setOf(MANIFEST_ENTRY, DATABASE_ENTRY) + hashes.map { "images/$it.bin" }
                val actualEntries = scratch.walkTopDown().filter { it.isFile }.map { it.relativeTo(scratch).invariantSeparatorsPath }.toSet()
                require(expectedEntries == actualEntries) { "Unexpected backup contents" }
                notes = TaskDatabase.readBackup(database)
                val referenced = notes.flatMap { AttachmentReferences.paths(it) }.toSet()
                require(referenced.all { it.startsWith(TOKEN_PREFIX) && HASH.matches(it.removePrefix(TOKEN_PREFIX)) } &&
                    referenced.map { it.removePrefix(TOKEN_PREFIX) }.toSet() == hashes) { "Invalid image reference in backup" }
            } else {
                // Older releases uploaded a raw SQLite database. Recover local legacy images only
                // when every referenced file still exists; never silently restore broken paths.
                notes = TaskDatabase.readBackup(file)
                val legacyImages = linkedMapOf<String, File>()
                notes.forEach { note -> AttachmentReferences.paths(note).forEach { path -> legacyImages[path] = attachments.resolve(path) } }
                imageFiles = legacyImages
            }
            if (imageFiles.isNotEmpty()) {
                val imageRoot = File(attachments.directory, "restored-${UUID.randomUUID()}")
                check(imageRoot.mkdirs()) { "Cannot prepare restored images" }
                installedImages = imageRoot
            }
            val mapped = HashMap<String, String>()
            imageFiles.forEach { (key, source) ->
                val destination = File(installedImages!!, "${UUID.randomUUID()}.bin")
                source.inputStream().use { input -> destination.outputStream().use { output -> copyLimited(input, output, AttachmentStore.MAX_IMAGE_BYTES) } }
                // Validate actual image data as well as checksums before committing notes.
                attachments.resolve(destination.path)
                mapped[if (zip) TOKEN_PREFIX + key else key] = destination.path
            }
            val restored = notes.map { note -> AttachmentReferences.map(note) { path ->
                if (AttachmentReferences.isRemote(path)) path else requireNotNull(mapped[path]) { "Missing backup image" }
            } }
            beforeCommit()
            helper.replaceTasks(restored, beforeCommit)
            committed = true
        } finally {
            if (!committed) installedImages?.deleteRecursively()
            scratch.deleteRecursively()
        }
    }

    private fun unpack(archive: File, destination: File) {
        val seen = HashSet<String>()
        var total = 0L
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                require(!entry.isDirectory && seen.add(name) && seen.size <= MAX_IMAGES + 2 &&
                    (name == MANIFEST_ENTRY || name == DATABASE_ENTRY || IMAGE_ENTRY.matches(name))) { "Invalid backup entry" }
                val target = File(destination, name)
                require(target.canonicalPath.startsWith(destination.canonicalPath + File.separator)) { "Invalid backup path" }
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot prepare backup entry" }
                val limit = when (name) { MANIFEST_ENTRY -> MAX_MANIFEST_BYTES; DATABASE_ENTRY -> MAX_DATABASE_BYTES; else -> AttachmentStore.MAX_IMAGE_BYTES }
                zip.getInputStream(entry).use { input -> target.outputStream().use { output ->
                    total += copyLimited(input, output, minOf(limit, MAX_ARCHIVE_BYTES - total))
                } }
                require(total <= MAX_ARCHIVE_BYTES) { "Backup is too large" }
            }
        }
    }

    private fun scratchDirectory(): File = File(context.cacheDir, "backup-work-${UUID.randomUUID()}").also {
        check(it.mkdir()) { "Cannot prepare backup workspace" }
    }

    companion object {
        const val FILE_NAME = "wordnote_backup_v1.zip"
        const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024
        private const val MAX_DATABASE_BYTES = 64L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 1024L * 1024
        private const val MAX_IMAGES = 10_000
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val DATABASE_ENTRY = "database.sqlite"
        private const val TOKEN_PREFIX = "wordnote-attachment:"
        private val HASH = Regex("[0-9a-f]{64}")
        private val IMAGE_ENTRY = Regex("images/[0-9a-f]{64}\\.bin")
        private val lock = Any()

        private fun addFile(zip: ZipOutputStream, name: String, file: File) {
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(32 * 1024)
                while (true) { val count = input.read(bytes); if (count == -1) break; digest.update(bytes, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
            var total = 0L
            val bytes = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count == -1) return total
                total += count
                require(total <= limit) { "Backup entry is too large" }
                output.write(bytes, 0, count)
            }
        }
    }
}
