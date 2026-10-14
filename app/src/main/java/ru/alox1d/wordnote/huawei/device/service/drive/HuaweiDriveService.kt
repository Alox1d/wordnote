package ru.alox1d.wordnote.huawei.device.service.drive

import android.content.Context
import android.content.Intent
import com.huawei.cloud.base.auth.DriveCredential
import com.huawei.cloud.base.http.FileContent
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.cloud.services.drive.Drive
import com.huawei.cloud.services.drive.DriveScopes
import com.huawei.cloud.services.drive.model.File
import com.huawei.hms.support.api.entity.auth.Scope
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper
import ru.alox1d.wordnote.huawei.data.backup.BackupArchive
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import toothpick.InjectConstructor
import java.io.IOException

@InjectConstructor
class HuaweiDriveService(context: Context) : IDriveService {
    private val context = context.applicationContext
    private var accessToken: String? = null
    private var unionId: String? = null
    private var refreshAccessMethod: DriveCredential.AccessMethod? = null

    init {
        // Historical builds copied credentials into preferences. New sessions live in memory only.
        this.context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit()
            .remove("at").remove("unionID").apply()
    }

    override fun login(): Intent = HuaweiIdAuthManager.getService(context, authParams()).signInIntent

    override fun updateFile(updatedFile: java.io.File, oldFile: File?) {
        val drive = buildDrive()
        val id = oldFile?.id?.takeIf { it.isNotBlank() } ?: throw IOException("Missing cloud file identifier")
        val mimeType = mimeType(updatedFile)
        val metadata = File().setFileName(updatedFile.name).setMimeType(mimeType)
        val result = drive.files().update(id, metadata, FileContent(mimeType, updatedFile)).setFields("id").execute()
        if (result?.id.isNullOrBlank()) throw IOException("Cloud update did not confirm a file")
    }

    override fun downloadFile(file: File?): java.io.File {
        val drive = buildDrive()
        val id = file?.id?.takeIf { it.isNotBlank() } ?: throw IOException("Missing cloud file identifier")
        if (file.size != null && file.size > BackupArchive.MAX_ARCHIVE_BYTES) throw IOException("Cloud backup exceeds the supported size")
        // The remote name is untrusted metadata and never becomes a local path.
        val downloaded = java.io.File.createTempFile("wordnote-cloud-", ".download", context.cacheDir)
        var completed = false
        try {
            LimitedDownloadOutputStream(downloaded.outputStream(), BackupArchive.MAX_ARCHIVE_BYTES).use { stream ->
                drive.files()[id].executeContentAndDownloadTo(stream)
            }
            if (downloaded.length() == 0L) throw IOException("Cloud download is empty")
            completed = true
            return downloaded
        } finally {
            if (!completed) downloaded.delete()
        }
    }

    override fun queryFiles(searchFileName: String): File? {
        val escapedName = searchFileName.replace("\\", "\\\\").replace("'", "\\'")
        val request = buildDrive().files().list()
            .setQueryParam("'applicationData' in parentFolder and fileName = '$escapedName'")
            .setPageSize(10).setOrderBy("editedTime desc")
            .setFields("nextCursor,files(id,fileName,size)").setContainers("applicationData")
        do {
            val page = request.execute() ?: throw IOException("Cloud query did not return a result")
            page.files?.firstOrNull()?.let { return it }
            val next = page.nextCursor
            if (next.isNullOrEmpty()) return null
            request.cursor = next
        } while (true)
    }

    override fun uploadFile(file: java.io.File) {
        val drive = buildDrive()
        val mimeType = mimeType(file)
        val metadata = File().setFileName(file.name).setMimeType(mimeType)
            .setParentFolder(listOf("applicationData"))
        val result = drive.files().create(metadata, FileContent(mimeType, file)).setFields("id").execute()
        if (result?.id.isNullOrBlank()) throw IOException("Cloud create did not confirm a file")
    }

    override val refreshAT = DriveCredential.AccessMethod { accessToken }

    private fun buildDrive(): Drive {
        val account = unionId ?: throw IllegalStateException("Drive authentication is required")
        val token = accessToken ?: throw IllegalStateException("Drive authentication is required")
        val authenticated = DriveCredential.Builder(account, refreshAccessMethod ?: refreshAT).build().setAccessToken(token)
        return Drive.Builder(authenticated, context).build()
    }

    override fun init(unionID: String?, at: String?, refreshAT: DriveCredential.AccessMethod?): Int {
        unionId = null
        refreshAccessMethod = null
        accessToken = null
        if (unionID.isNullOrBlank() || at.isNullOrBlank()) return DriveCode.ERROR
        unionId = unionID
        accessToken = at
        refreshAccessMethod = refreshAT ?: this.refreshAT
        return DriveCode.SUCCESS
    }

    private fun mimeType(file: java.io.File): String {
        if (!file.isFile) throw IOException("Backup file is unavailable")
        return if (file.extension.equals("zip", ignoreCase = true)) "application/zip" else "application/x-sqlite3"
    }

    companion object {
        internal fun authParams() = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setAccessToken().setIdToken()
            .setScopeList(listOf(Scope(DriveScopes.SCOPE_DRIVE_FILE), Scope(DriveScopes.SCOPE_DRIVE_APPDATA)))
            .createParams()
    }
}
