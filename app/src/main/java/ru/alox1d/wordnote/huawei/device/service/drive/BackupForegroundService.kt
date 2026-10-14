package ru.alox1d.wordnote.huawei.device.service.drive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.cloud.services.drive.DriveScopes
import com.huawei.cloud.services.drive.model.File
import com.huawei.hmf.tasks.Task
import com.huawei.hms.common.ApiException
import com.huawei.hms.mlsdk.common.MLApplication
import com.huawei.hms.support.account.AccountAuthManager
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.huawei.hms.support.account.result.AuthAccount
import com.huawei.hms.support.account.service.AccountAuthService
import com.huawei.hms.support.api.entity.auth.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.alarm.AlarmReceiver
import ru.alox1d.wordnote.huawei.helpers.API_KEY
import java.util.*


class BackupForegroundService : Service() {
    private val CHANNEL_ID = "ForegroundService"
    private lateinit var mHuaweiDriveService: HuaweiDriveService

    companion object {

        val TAG = BackupForegroundService::class.java.name
        val BACKUP_DATA: String = "BACKUP_DATA"
        val ACTION_HANDLE_DATA: String = "ACTION_HANDLE_DATA"

        fun startService(context: Context, date: Long) {
            val startIntent = Intent(context, BackupForegroundService::class.java)
            startIntent.putExtra(BACKUP_DATA, date)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, BackupForegroundService::class.java)
            context.stopService(stopIntent)
        }

    }

    override fun onCreate() {
        super.onCreate()
//        android.os.Debug.waitForDebugger();
        createNotificationChannel()
        val notificationIntent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
                this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.backup_is_running))
                .setSmallIcon(R.drawable.ic_baseline_sync_24)
                .setContentIntent(pendingIntent)
                .build()

        startForeground(1, notification)
    }

    fun setApiKey() {
        val config = AGConnectServicesConfig.fromContext(application)
        MLApplication.getInstance().apiKey =
            config.getString(API_KEY)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        val input = intent?.getLongExtra(BACKUP_DATA, -1)

//        PreferenceHelper.instance.init(getApplicationContext())
//        val preferenceHelper = PreferenceHelper.instance
//        val unionId = preferenceHelper.getString(PreferenceHelper.unionID)
//        val at = preferenceHelper.getString(PreferenceHelper.at)
        GlobalScope.launch(Dispatchers.IO) {
            setApiKey()
            mHuaweiDriveService = HuaweiDriveService(applicationContext)
//            val returnCode = mHuaweiTaskService.init(unionId, at, mHuaweiTaskService.refreshAT)
//
//            if (DriveCode.SUCCESS == returnCode) {
//                Utils.appendLog("Start Backup")
//                uploadData()
//                Utils.appendLog("End Backup")
//            } else {
//                Utils.appendLog("Not Success Backup: Unauthorized")
//            }
            loginSilently()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        // Unregistered or disconnect what you need to
        // For example: mGoogleApiClient.disconnect();

        // Or break the while loop condition
        // mDoWhile = false;
        super.onDestroy()
    }

    private fun uploadData() {
        val fileNameDB = TaskDatabase.DATABASE_NAME
        val dbFile = this.getDatabasePath(fileNameDB)
        val searchedDB = queryFiles(fileNameDB)
        if (searchedDB == null) {
            uploadFile(dbFile)
            return
        }
        updateFile(dbFile, searchedDB)
        GlobalScope.launch(Dispatchers.Main) {
            stopForeground(true)
            stopSelf()
        }
    }

    //Function to QueryFiles
    private fun queryFiles(searchFileName: String): File? {
        try {
//            Utils.appendLog("Backup: Success queryFiles")
            Log.d(TAG, "Backup: Success queryFiles")
            return mHuaweiDriveService.queryFiles(searchFileName)

        } catch (ex: Exception) {
            Log.d(TAG, "query", ex)
//            Utils.appendLog("Backup error $ex")
            return null
        }

    }

    private fun uploadFile(file: java.io.File) {
        try {
            if (!file.exists()) {
                return
            }
            // create file on cloud
            mHuaweiDriveService.uploadFile(file)
//            Utils.appendLog("Backup: Success uploadFile")
            Log.d(TAG, "Backup: Success uploadFile")
        } catch (ex: Exception) {
            Log.d(TAG, "upload", ex)
//            Utils.appendLog("Backup error $ex")
        }

    }

    private fun updateFile(updatedFile: java.io.File, oldFile: File?) {
        try {
            if (!updatedFile.exists()) {
                return
            }
            // update file on cloud
            mHuaweiDriveService.updateFile(updatedFile, oldFile)
//            Utils.appendLog("Backup: Success updateFile")
            Log.d(TAG, "Backup: Success updateFile")
        } catch (ex: Exception) {
            Log.d(TAG, "upload", ex)
//            Utils.appendLog("Backup error $ex")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Backup Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT)

            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    private fun loginSilently() {
        val scopeList: MutableList<Scope> = LinkedList()
//        scopeList.add(HuaweiIdAuthAPIManager.HUAWEIID_BASE_SCOPE) // Basic account permissions.
//        scopeList.add(Scope(DriveScopes.SCOPE_DRIVE)) // Permissions to view and manage files.
        scopeList.add(Scope(DriveScopes.SCOPE_DRIVE_FILE)) // Permissions to view and manage files.
        scopeList.add(Scope(DriveScopes.SCOPE_DRIVE_APPDATA)) // Permissions to upload and store app data.
        val authParams: AccountAuthParams = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                .setAccessToken()
                .setIdToken()
                .setScopeList(scopeList)
                .createParams()
        // Call the account API to obtain account information.
        val service: AccountAuthService = AccountAuthManager.getService(this, authParams)
        val task: Task<AuthAccount>? = service.silentSignIn()
        task?.addOnSuccessListener { authAccount ->
            // Obtain the ID type (0: HUAWEI ID; 1: AppTouch ID).
//            Log.i(TAG, "accountFlag:" + authAccount.accessToken);
            val accessToken = authAccount.accessToken
            val unionId = authAccount.unionId
            val returnCode = mHuaweiDriveService.init(unionId, accessToken, mHuaweiDriveService.refreshAT)

            if (DriveCode.SUCCESS == returnCode) {
//                Utils.appendLog("Start Backup")
                Log.d(TAG, "Start Backup")
                GlobalScope.launch(Dispatchers.IO) {

                    uploadData()
                }
//                Utils.appendLog("End Backup")
                Log.d(TAG, "End Backup")

            } else if (DriveCode.SERVICE_URL_NOT_ENABLED == returnCode) {
//                Utils.appendLog("Drive is not enabled")
                Log.d(TAG, "Drive is not enabled")
            } else {
//                Utils.appendLog("Login error")
                Log.d(TAG, "Login error")
            }
        }

        task?.addOnFailureListener {
            val ex = (it as ApiException)
//            Utils.appendLog("onActivityResult, signIn failed: " + ex.statusCode)
            Log.d(TAG, "signIn failed: " + ex.statusCode)
        }
    }
}