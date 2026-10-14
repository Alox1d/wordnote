package ru.alox1d.wordnote.huawei

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ru.alox1d.wordnote.huawei.data.storage.AttachmentStore
import com.huawei.cloud.base.auth.DriveCredential
import com.huawei.cloud.client.exception.DriveCode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.data.backup.CloudBackup
import ru.alox1d.wordnote.huawei.data.backup.NoCloudBackupException
import ru.alox1d.wordnote.huawei.data.repository.TaskRepository
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.domain.model.ModelTask
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, instrumentedPackages = ["com.huawei.cloud"])
class CloudBackupTest {
    private lateinit var context: Context
    private lateinit var database: TaskDatabase
    private lateinit var repository: TaskRepository
    private lateinit var drive: FakeDrive
    private lateinit var cloud: CloudBackup

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        database = TaskDatabase.getInstance(context)
        repository = TaskRepository(database.readableDatabase, database.writableDatabase)
        drive = FakeDrive(context)
        cloud = CloudBackup(context, drive)
        repository.saveTask(ModelTask("artificial snapshot", "{\"nodes\":[]}", "", 0, 1, 1, 42, 1))
    }

    @After fun cleanUp() {
        database.close()
        context.deleteDatabase(TaskDatabase.DATABASE_NAME)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test fun backupUploadsRestorableZipSnapshotAndDeletesTemporaryFiles() {
        cloud.backup()
        val bytes = drive.remote.getValue("wordnote_backup_v1.zip")
        assertTrue(ZipInputStream(bytes.inputStream()).use { it.nextEntry != null })
        assertEquals(listOf("wordnote_backup_v1.zip"), drive.queries)
        assertFalse(drive.lastUpload!!.exists())
        assertFalse(drive.lastUpload!!.parentFile!!.exists())
        repository.title(42, "later local change")
        cloud.restore()
        assertEquals("artificial snapshot", repository.getTask(42)!!.title)
        assertFalse(drive.lastDownload!!.exists())
    }

    @Test fun queryFailureNeverCreatesOrUploadsAnArchive() {
        val filesBefore = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.path }.toSet()
        drive.queryFailure = IOException("offline artificial query")
        assertThrows(IOException::class.java) { cloud.backup() }
        assertTrue(drive.remote.isEmpty())
        assertNull(drive.lastUpload)
        assertEquals(filesBefore, context.cacheDir.walkTopDown().filter { it.isFile }.map { it.path }.toSet())
    }

    @Test fun uploadFailurePropagatesAndRemovesPrivateSnapshot() {
        drive.writeFailure = IOException("artificial failed upload")
        assertThrows(IOException::class.java) { cloud.backup() }
        assertTrue(drive.remote.isEmpty())
        assertFalse(drive.lastUpload!!.exists())
        assertFalse(drive.lastUpload!!.parentFile!!.exists())
    }

    @Test fun updateFailureLeavesCloudSnapshotUnchangedAndPropagates() {
        val original = byteArrayOf(1, 2, 3)
        drive.remote["wordnote_backup_v1.zip"] = original
        drive.writeFailure = IOException("artificial failed update")
        assertThrows(IOException::class.java) { cloud.backup() }
        assertArrayEquals(original, drive.remote["wordnote_backup_v1.zip"])
        assertFalse(drive.lastUpload!!.exists())
    }

    @Test fun existingZipIsUpdatedInsteadOfCreatingAnotherBackup() {
        drive.remote["wordnote_backup_v1.zip"] = byteArrayOf(1)
        cloud.backup()
        assertEquals(1, drive.remote.size)
        assertTrue(drive.updated)
        assertTrue(ZipInputStream(drive.remote.getValue("wordnote_backup_v1.zip").inputStream()).use { it.nextEntry != null })
    }

    @Test fun nullDownloadReportsFailureAndLeavesCurrentNotesIntact() {
        drive.remote["wordnote_backup_v1.zip"] = byteArrayOf(1)
        drive.nullDownload = true
        assertThrows(IOException::class.java) { cloud.restore() }
        assertEquals("artificial snapshot", repository.getTask(42)!!.title)
    }

    @Test fun downloadFailurePropagatesAndLeavesCurrentNotesIntact() {
        drive.remote["wordnote_backup_v1.zip"] = byteArrayOf(1)
        drive.downloadFailure = IOException("artificial failed download")
        assertThrows(IOException::class.java) { cloud.restore() }
        assertEquals("artificial snapshot", repository.getTask(42)!!.title)
    }

    @Test fun absentCloudIsDistinctFromNetworkFailure() {
        assertThrows(NoCloudBackupException::class.java) { cloud.restore() }
        assertEquals(listOf("wordnote_backup_v1.zip", "wordnote_database"), drive.queries)
        assertEquals("artificial snapshot", repository.getTask(42)!!.title)
    }

    @Test fun corruptPreferredZipDoesNotFallBackToLegacyDatabase() {
        drive.remote["wordnote_backup_v1.zip"] = "not an archive".toByteArray()
        drive.remote["wordnote_database"] = byteArrayOf(1)
        assertThrows(Exception::class.java) { cloud.restore() }
        assertEquals(listOf("wordnote_backup_v1.zip"), drive.queries)
        assertEquals("artificial snapshot", repository.getTask(42)!!.title)
        assertFalse(drive.lastDownload!!.exists())
    }

    @Test fun legacyDatabaseIsUsedOnlyWhenZipIsAbsent() {
        database.close()
        drive.remote["wordnote_database"] = context.getDatabasePath(TaskDatabase.DATABASE_NAME).readBytes()
        database = TaskDatabase.getInstance(context)
        repository = TaskRepository(database.readableDatabase, database.writableDatabase)
        repository.title(42, "later local change")
        cloud.restore()
        assertEquals(listOf("wordnote_backup_v1.zip", "wordnote_database"), drive.queries)
        assertEquals("artificial snapshot", repository.getTask(42)!!.title)
        assertFalse(drive.lastDownload!!.exists())
    }

    @Test fun cancelledBlockedRestoreCannotOverwriteNotesCreatedAfterCancellation() {
        val attachments = AttachmentStore(context)
        val image = attachments.saveBitmap(Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888))
        val originalImageBytes = image.readBytes()
        val description = """{"nodes":[{"type":"img","content":["${image.path}"],"childs":[{"type":"IMG_SUB","content":["<p>caption</p>"],"contentStyles":[],"textSettings":{"textColor":"#5E5E5E"}}]}]}"""
        repository.description(42, description)
        repository.descriptionHTML(42, "<img src='${image.path}'>")
        cloud.backup()
        val filesBefore = attachments.directory.walkTopDown().filter { it.isFile }.map { it.canonicalPath }.toSet()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        drive.downloadEntered = entered
        drive.downloadRelease = release
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val operation = scope.launch {
            val operationContext = coroutineContext
            cloud.restore { operationContext.ensureActive() }
        }
        try {
            assertTrue("The external download must reach the controlled block", entered.await(10, TimeUnit.SECONDS))
            operation.cancel()
            repository.saveTask(ModelTask("new artificial note after cancellation", "{\"nodes\":[]}", "", 0, 1, 1, 77, 0))
            release.countDown()
            runBlocking { withTimeout(15_000L) { operation.join() } }
            assertNotNull("Cancelled restore must preserve the newly created local note", repository.getTask(77))
            assertEquals("artificial snapshot", repository.getTask(42)!!.title)
            assertEquals(description, repository.getTask(42)!!.description)
            assertArrayEquals(originalImageBytes, image.readBytes())
            assertEquals(filesBefore, attachments.directory.walkTopDown().filter { it.isFile }.map { it.canonicalPath }.toSet())
            assertNotNull(drive.lastDownload)
            assertFalse(drive.lastDownload!!.exists())
            assertTrue(context.cacheDir.walkTopDown().none { it.isFile })
        } finally {
            release.countDown()
            scope.cancel()
            runBlocking { withTimeout(15_000L) { operation.join() } }
            attachments.directory.deleteRecursively()
        }
    }

    private class FakeDrive(private val context: Context) : IDriveService {
        val remote = linkedMapOf<String, ByteArray>()
        val queries = mutableListOf<String>()
        var queryFailure: IOException? = null
        var writeFailure: IOException? = null
        var downloadFailure: IOException? = null
        var nullDownload = false
        var updated = false
        var lastUpload: File? = null
        var lastDownload: File? = null
        var downloadEntered: CountDownLatch? = null
        var downloadRelease: CountDownLatch? = null
        override fun login() = Intent("artificial.login")
        override fun init(unionID: String?, at: String?, refreshAT: DriveCredential.AccessMethod?) = DriveCode.SUCCESS
        override val refreshAT = DriveCredential.AccessMethod { "artificial-token-never-logged" }
        override fun queryFiles(searchFileName: String): com.huawei.cloud.services.drive.model.File? {
            queries += searchFileName
            queryFailure?.let { throw it }
            val bytes = remote[searchFileName] ?: return null
            return com.huawei.cloud.services.drive.model.File().setId(searchFileName).setFileName(searchFileName).setSize(bytes.size.toLong())
        }
        override fun uploadFile(file: File) {
            lastUpload = file
            writeFailure?.let { throw it }
            remote[file.name] = file.readBytes()
        }
        override fun updateFile(updatedFile: File, oldFile: com.huawei.cloud.services.drive.model.File?) {
            lastUpload = updatedFile
            writeFailure?.let { throw it }
            check(oldFile!!.id == updatedFile.name)
            updated = true
            remote[oldFile.id] = updatedFile.readBytes()
        }
        override fun downloadFile(file: com.huawei.cloud.services.drive.model.File?): File? {
            downloadFailure?.let { throw it }
            downloadEntered?.countDown()
            check(downloadRelease?.await(15, TimeUnit.SECONDS) != false) { "Artificial download gate timed out" }
            if (nullDownload) return null
            return File.createTempFile("artificial-cloud-", ".download", context.cacheDir).also {
                it.writeBytes(remote.getValue(file!!.id))
                lastDownload = it
            }
        }
    }
}
