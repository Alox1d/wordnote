package ru.alox1d.wordnote.huawei.ui.scenes.backup

import ru.alox1d.wordnote.huawei.helpers.applySystemInsets
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.toothpick.annotation.ApplicationScope
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.cloud.services.drive.model.File
import com.huawei.hms.common.ApiException
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import ru.alox1d.wordnote.huawei.databinding.ActivityBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.data.storage.TaskDatabase
import ru.alox1d.wordnote.huawei.device.alarm.AlarmHelper
import ru.alox1d.wordnote.huawei.di.module.MainModule
import ru.alox1d.wordnote.huawei.domain.model.Item
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject
import toothpick.smoothie.lifecycle.closeOnDestroy
import java.util.*


class BackupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBackupBinding

    interface BackupListener {
        fun onRestore(currentTasks: ArrayList<Item>, doneTasks: ArrayList<Item>)
    }

    var currentTasks: ArrayList<Item>? = null
    var doneTasks: ArrayList<Item>? = null
    private var upload: Boolean = true
    private val fileNameDB = TaskDatabase.DATABASE_NAME

    private val mHuaweiTaskService: IDriveService by inject()

    private lateinit var taskDatabase: TaskDatabase
    private lateinit var preferenceStorage: PreferenceStorage
    private lateinit var alarmHelper: AlarmHelper

    companion object {
        const val REQUEST_CODE_OK = 10
        const val REQUEST_CODE_ERROR = 20
        const val OFF = 0
        const val DAILY = 1
        const val WEEKLY = 2
        const val MONTHLY = 3

        const val MIME_TYPE_SQLITE: String = "application/x-sqlite3"

        private val MIME_TYPE_MAP: MutableMap<String, String> = HashMap()

        // Request code for displaying the authorization screen using the startActivityForResult method.
        // The value can be defined by developers.
        const val REQUEST_SIGN_IN_LOGIN = 1002
        const val TAG = "BackupActivity"

        init {
            MIME_TYPE_MAP.apply {
                put(".doc", "application/msword")
                put(".jpg", "image/jpeg")
                put(".mp3", "audio/x-mpeg")
                put(".mp4", "video/mp4")
                put(".pdf", "application/pdf")
                put(".png", "image/png")
                put(".txt", "text/plain")
            }
        }
    }

    private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        taskDatabase = TaskDatabase.getInstance(this)
        // 1. Open Activity scope as child of Application scope
        // 2. Install module inside Activity scope containing:
        //    2.1 hen injection IBackpackAdapter, use the class BackpackAdapter
        //    2.2 install backpack as a singleton on the Activity scope via binding
        // 3. Close when activity is destroyed
        // 4. Inject dependencies
        KTP.openScopes(ApplicationScope::class.java, this)
                .installModules(MainModule(applicationContext))
                .closeOnDestroy(this)
                .inject(this)
        PreferenceStorage.INSTANCE.init(this.getApplicationContext())
        preferenceStorage = PreferenceStorage.INSTANCE
        AlarmHelper.instance.init(this)
        alarmHelper = AlarmHelper.instance

        setUI()

    }

    private fun setUI() {
        val backupAdapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.intervals))
        binding.spinnerBackup.adapter = backupAdapter
//        binding.spinnerBackup.setSelected(false);  // must
        binding.spinnerBackup.setSelection(preferenceStorage.getLong(PreferenceStorage.BACKUP_INTERVAL).toInt(), false)
//        binding.spinnerBackup.setSelection(0,false)
//        binding.spinnerBackup.setSelection(Adapter.NO_SELECTION, true); //Add this line before setting listener
        var inhibit_spinner = true;
        binding.spinnerBackup.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                if (inhibit_spinner) {
                    inhibit_spinner = false;
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(PERMISSIONS_STORAGE, 1);
                    }
                    if (preferenceStorage.getString(PreferenceStorage.at) == "") {
                        showTips(getString(R.string.first_backup))
                        return
                    }
                    alarmHelper.removeAlarm(preferenceStorage.getLong(PreferenceStorage.BACKUP_INITIAL_DATE))
                    if (position != OFF) {
                        val calendar: Calendar = Calendar.getInstance()
                        calendar.setTimeInMillis(System.currentTimeMillis())

                        // for testing
//                        calendar.set(Calendar.HOUR_OF_DAY, 2)
//                        calendar.set(Calendar.MINUTE, 38)
//                        calendar.set(Calendar.SECOND, 0)
                        // date in string
//                        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                            SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a")
//                        } else {
//                            TODO("VERSION.SDK_INT < N")
//                        }
//                        val sr = format.format(calendar.getTime());

                        alarmHelper.setBackupAlarm(calendar.timeInMillis)
                        preferenceStorage.putLong(PreferenceStorage.BACKUP_INITIAL_DATE, calendar.timeInMillis)
                    }
                    preferenceStorage.putLong(PreferenceStorage.BACKUP_INTERVAL, position.toLong())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        binding.btnHmsBackup.setOnClickListener {
            setLoginProcessUI()
            GlobalScope.launch(Dispatchers.IO) {
                upload = true
                driveLogin() // upload or download after
            }
        }

        binding.btnHmsRestore.setOnClickListener {
            setLoginProcessUI()
            GlobalScope.launch(Dispatchers.IO) {
                upload = false
                driveLogin()
            }
        }
    }

    private fun setLoginProcessUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMISSIONS_STORAGE, 1);
        }
        binding.btnHmsBackup.isEnabled = false
        binding.btnHmsRestore.isEnabled = false
        binding.progressBackup.visibility = View.VISIBLE
    }

    private suspend fun uploadData() {
        val dbFile = this.getDatabasePath(fileNameDB)
        val searchedDB = queryFiles(fileNameDB)
        if (searchedDB == null) {
            uploadFile(dbFile)
            withContext(Dispatchers.Main) {
                binding.btnHmsBackup.isEnabled = true
                binding.btnHmsRestore.isEnabled = true
                binding.progressBackup.visibility = View.GONE
            }
            showTips(getString(R.string.backup_success))
            return
        }
        updateFile(dbFile, searchedDB)
        withContext(Dispatchers.Main) {
            binding.btnHmsBackup.isEnabled = true
            binding.btnHmsRestore.isEnabled = true
            binding.progressBackup.visibility = View.GONE
        }
        showTips(getString(R.string.backup_success))
    }

    private suspend fun downloadData() {
        val fileNameDone = "done_tasks" + ".wn"
        val searchedDB = queryFiles(fileNameDB)
        if (searchedDB == null) {
            withContext(Dispatchers.Main) {
                binding.btnHmsRestore.isEnabled = true
                binding.btnHmsBackup.isEnabled = true
                binding.progressBackup.visibility = View.GONE
            }
            showTips(getString(R.string.cloud_empty))
            setResult(REQUEST_CODE_ERROR, intent)
            return
        }
        val downloadedDB = downloadFile(searchedDB)
        if (downloadedDB == null) return

        taskDatabase.importDB(downloadedDB)
        withContext(Dispatchers.Main) {
            binding.btnHmsRestore.isEnabled = true
            binding.btnHmsBackup.isEnabled = true
            binding.progressBackup.visibility = View.GONE
        }
        showTips(getString(R.string.restore_success))
        restartApp()
    }

    private fun restartApp() {
        val i = baseContext.packageManager
                .getLaunchIntentForPackage(baseContext.packageName)
        i!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        finish()
        startActivity(i)
        System.exit(0)
    }


    private fun updateFile(updatedFile: java.io.File, oldFile: File?) {
        try {
            if (!updatedFile.exists()) {
                showTips(getString(R.string.file_doesnt_exist))
                return
            }

            // update file on cloud

            mHuaweiTaskService.updateFile(updatedFile, oldFile)
        } catch (ex: Exception) {
            Log.d(TAG, "upload", ex)
            showTips("Upload error: $ex")
        }


    }


    private fun uploadFile(file: java.io.File) {
        try {

            if (!file.exists()) {
                showTips("The input file does not exist.")
                return
            }

            // create file on cloud

            mHuaweiTaskService.uploadFile(file)
        } catch (ex: Exception) {
            Log.d(TAG, "upload", ex)
            showTips("Upload error: $ex")
        }
    }

    //Function to QueryFiles
    private fun queryFiles(searchFileName: String): File? {
        try {
            return mHuaweiTaskService.queryFiles(searchFileName)
        } catch (ex: Exception) {
            Log.d(TAG, "query", ex)
            showTips("query error $ex")
            return null
        }
    }


    private suspend fun downloadFile(file: File?): java.io.File? {

        try {
            return mHuaweiTaskService.downloadFile(file)
        } catch (ex: java.lang.Exception) {
            Log.d(TAG, "download error $ex")
            showTips("download error $ex")
            return null
        }

    }

    private fun driveLogin() {
        startActivityForResult(mHuaweiTaskService.login(), REQUEST_SIGN_IN_LOGIN)
    }

    // Abnormal process for obtaining account information. Obtain and save the related accessToken and unionID using this function.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult, requestCode = $requestCode, resultCode = $resultCode")
        when (requestCode) {
            REQUEST_SIGN_IN_LOGIN -> {
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
            val returnCode = mHuaweiTaskService.init(unionId, accessToken, mHuaweiTaskService.refreshAT)
            if (DriveCode.SUCCESS == returnCode) {
                showTips(getString(R.string.login_success))
                GlobalScope.launch(Dispatchers.IO) {
                    if (upload)
                        uploadData()
                    else downloadData()
                }
            } else if (DriveCode.SERVICE_URL_NOT_ENABLED == returnCode) {
                showTips("Drive is not enabled")
            } else {
                showTips("Login error")
            }
        } else {
            val ex = (authHuaweiIdTask.exception as ApiException)
            Log.d(
                    TAG,
                    "onActivityResult, signIn failed: " + ex.statusCode
            )

            showTips("onActivityResult, signIn failed.")
        }
    }


    private fun showTips(toastText: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
        }
    }
}