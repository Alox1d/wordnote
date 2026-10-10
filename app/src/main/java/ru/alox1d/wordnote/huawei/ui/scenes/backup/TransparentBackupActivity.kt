package ru.alox1d.wordnote.huawei.ui.scenes.backup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.cloud.services.drive.model.File
import com.huawei.hms.common.ApiException
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import kotlinx.android.synthetic.main.activity_backup.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.service.drive.BackupForegroundService
import ru.alox1d.wordnote.huawei.device.service.drive.HuaweiDriveService


class TransparentBackupActivity : AppCompatActivity() {
    private lateinit var mHuaweiDriveService: HuaweiDriveService
    private val fileNameDB = TaskDatabase.DATABASE_NAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent_backup)
        mHuaweiDriveService = HuaweiDriveService(this)

        driveLogin()

    }
    private fun driveLogin() {
        startActivityForResult(mHuaweiDriveService.login(), BackupActivity.REQUEST_SIGN_IN_LOGIN)
    }
    private fun notifyService(data: Intent) {
        val intent = Intent(this, BackupForegroundService::class.java)
        intent.setAction(BackupForegroundService.ACTION_HANDLE_DATA)
        intent.putExtra(BackupForegroundService.BACKUP_DATA, data)
        startService(intent)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(BackupActivity.TAG, "onActivityResult, requestCode = $requestCode, resultCode = $resultCode")
        when (requestCode) {
            BackupActivity.REQUEST_SIGN_IN_LOGIN -> {
                confirmLogin(data)
            }
        }
    }
    private fun confirmLogin(data: Intent?) {
        val authHuaweiIdTask =
                HuaweiIdAuthManager.parseAuthResultFromIntent(data)
        if (authHuaweiIdTask.isSuccessful) {
            val huaweiAccount = authHuaweiIdTask.result
            val accessToken = huaweiAccount.accessToken
            val unionId = huaweiAccount.unionId
            val returnCode = mHuaweiDriveService.init(unionId, accessToken, mHuaweiDriveService.refreshAT)
            if (DriveCode.SUCCESS == returnCode) {
                showTips("Login Success")
                GlobalScope.launch(Dispatchers.IO) {
                        uploadData()
                        BackupForegroundService.Companion.stopService(applicationContext);

                }
            } else if (DriveCode.SERVICE_URL_NOT_ENABLED == returnCode) {
                showTips("Drive is not enabled")
            } else {
                showTips("Login error")
            }
        } else {
            Log.d(
                    BackupActivity.TAG,
                    "onActivityResult, signIn failed: " + (authHuaweiIdTask.exception as ApiException).statusCode
            )
            showTips("onActivityResult, signIn failed.")
        }
    }
    private suspend fun uploadData() {

        val dbFile = this.getDatabasePath(fileNameDB)
        val searchedDB = queryFiles(fileNameDB)
        if (searchedDB == null) {
            uploadFile(dbFile)
            withContext(Dispatchers.Main) {
                btn_hms_backup.isEnabled = true
                btn_hms_restore.isEnabled = true
                progress_backup.visibility = View.GONE
            }
            showTips("Backup success")
            return
        }

        updateFile(dbFile, searchedDB)
        withContext(Dispatchers.Main) {
            btn_hms_backup.isEnabled = true
            btn_hms_restore.isEnabled = true
            progress_backup.visibility = View.GONE
        }
        showTips("Backup success")
    }
    //Function to QueryFiles
    private suspend fun queryFiles(searchFileName: String): File? {
        try {
            return mHuaweiDriveService.queryFiles(searchFileName)
        } catch (ex: Exception) {
            Log.d(BackupActivity.TAG, "query", ex)
            showTips("query error $ex")
            return null
        }


    }

    private suspend fun uploadFile(file: java.io.File) {
        try {

            if (!file.exists()) {
                showTips("The input file does not exist.")
                return
            }
            // create file on cloud

            mHuaweiDriveService.uploadFile(file)
        } catch (ex: Exception) {
            Log.d(BackupActivity.TAG, "upload", ex)
            showTips("Upload error: $ex")
        }


    }
    private suspend fun updateFile(updatedFile: java.io.File, oldFile: File?) {
        try {

            if (!updatedFile.exists()) {
                showTips("The input file does not exist.")
                return
            }

            // update file on cloud

            mHuaweiDriveService.updateFile(updatedFile, oldFile)
        } catch (ex: Exception) {
            Log.d(BackupActivity.TAG, "upload", ex)
            showTips("Upload error: $ex")
        }


    }
    private fun showTips(toastText: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()

        }
    }
}