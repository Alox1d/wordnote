package ru.alox1d.wordnote.huawei.device.service.drive

import android.content.Context
import android.content.Intent
import com.huawei.cloud.base.auth.DriveCredential
import com.huawei.cloud.base.http.FileContent
import com.huawei.cloud.base.media.MediaHttpDownloader
import com.huawei.cloud.base.util.StringUtils
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.cloud.services.drive.Drive
import com.huawei.cloud.services.drive.DriveScopes
import com.huawei.cloud.services.drive.model.File
import com.huawei.cloud.services.drive.model.FileList
import com.huawei.hms.support.api.entity.auth.Scope
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper
import com.huawei.hms.support.hwid.service.HuaweiIdAuthService
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import ru.alox1d.wordnote.huawei.helpers.util.Utils
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity
import toothpick.InjectConstructor
import java.io.FileOutputStream
import java.util.*

@InjectConstructor
class HuaweiDriveService(val context: Context) : IDriveService {
    private var accessToken: String? = null
    private var mCredential: DriveCredential? = null

    private var localSavePath = context.getExternalFilesDir(null)?.path + "/" ?: ""
    private var preferenceStorage: PreferenceStorage

    init {
        PreferenceStorage.INSTANCE.init(context.getApplicationContext())
        preferenceStorage = PreferenceStorage.INSTANCE
    }

    override fun login(): Intent {
        val scopeList: MutableList<Scope> = LinkedList()
        //scopeList.add(HuaweiIdAuthAPIManager.HUAWEIID_BASE_SCOPE) // Basic account permissions.
        scopeList.add(Scope(DriveScopes.SCOPE_DRIVE_FILE)) // Permissions to view and manage files.
        scopeList.add(Scope(DriveScopes.SCOPE_DRIVE_APPDATA)) // Permissions to upload and store app data.
        val authParams = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                .setAccessToken()
                .setIdToken()
                .setScopeList(scopeList)
                .createParams()
        // Call the account API to obtain account information.
        val client: HuaweiIdAuthService = HuaweiIdAuthManager.getService(context, authParams)
        return client.signInIntent
    }


    override fun updateFile(updatedFile: java.io.File, oldFile: File?) {
        val mimeType = BackupActivity.MIME_TYPE_SQLITE
        val content = File()
                .setFileName(updatedFile.name)
                .setMimeType(mimeType)
                .setParentFolder(Collections.singletonList("applicationData"))
        val fileUploaded = buildDrive()?.files()
                ?.update(oldFile!!.id, content, FileContent(mimeType, updatedFile))
                ?.setFields("*")
                ?.execute()
    }

    override fun downloadFile(file: File?): java.io.File {
        val drive = buildDrive()
        val content = File()
        val request = drive.files()[file!!.id]
        content.setFileName(file.fileName).id = file.id
        val downloader: MediaHttpDownloader = request.mediaHttpDownloader
        downloader.setContentRange(0, file.getSize() - 1)
        val filePath = localSavePath + file.fileName + "_backup.DB"
        val downloadedFile = java.io.File(filePath)
        request.executeContentAndDownloadTo(FileOutputStream(downloadedFile))
        // showTips("download to $filePath")
        return downloadedFile
    }

    override fun queryFiles(searchFileName: String): File? {
        var queryFile = "fileName = '" + searchFileName + "'"
        var containers: String = "applicationData";
        queryFile = "'applicationData' in parentFolder and " + queryFile;
        val request = buildDrive()?.files()?.list()
        var files: FileList?
        while (true) {
            files = request
                    ?.setQueryParam(queryFile)
                    ?.setPageSize(10)
                    ?.setOrderBy("fileName")
                    ?.setFields("category,nextCursor,files(id,fileName,size)")
                    ?.setContainers(containers)
                    ?.execute()
            if (files == null || files.files.size > 0) {
                break
            }
            if (!StringUtils.isNullOrEmpty(files.nextCursor)) {
                request?.cursor = files.nextCursor
            } else {
                break
            }
        }
        var text: String
        var fileSearched: File? = null
        if (files != null && files.files.size > 0) {
            fileSearched = files.files[0]
            text = fileSearched.toString()
        } else {
            text = "empty"
        }
        //        val finalText = text
        //                    runOnUiThread { queryResult.text = finalText }
        //                    showTips("query ok")
        //                }
        return fileSearched

    }

    override fun uploadFile(file: java.io.File) {
        val mimeType = BackupActivity.MIME_TYPE_SQLITE
        val content = File()
                .setFileName(file.name)
                .setMimeType(mimeType)
                .setParentFolder(Collections.singletonList("applicationData"))
        val fileUploaded = buildDrive()?.files()
                ?.create(content, FileContent(mimeType, file))
                ?.setFields("*")
                ?.execute()
    }

    override val refreshAT = DriveCredential.AccessMethod {
        /**
         * Simplified code snippet for demonstration purposes. For the complete code snippet,
         * please go to Client Development > Obtaining Authentication Information > Store Authentication Information
         * in the HUAWEI Drive Kit Development Guide.
         **/
        return@AccessMethod accessToken
    }

    private fun buildDrive() = Drive.Builder(mCredential, context).build()

    /**
     * （unionId，countrycode，accessToken）drive。
     * accessTokenAccessMethod,accessToken。
     *
     * @param unionID   unionID from HwID
     * @param at        access token
     * @param refreshAT a callback to refresh AT
     */

    override fun init(unionID: String?, at: String?, refreshAT: DriveCredential.AccessMethod?): Int {
        return if (StringUtils.isNullOrEmpty(unionID) || StringUtils.isNullOrEmpty(at)) {
            DriveCode.ERROR
        } else {
            val builder = DriveCredential.Builder(unionID, refreshAT)
            val built = builder.build()
            mCredential = built.setAccessToken(at)

            preferenceStorage.putString(PreferenceStorage.unionID, mCredential!!.unionID)
            preferenceStorage.putString(PreferenceStorage.at, mCredential!!.accessToken)
            DriveCode.SUCCESS
        }
    }


}