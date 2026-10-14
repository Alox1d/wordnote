package ru.alox1d.wordnote.huawei.ui.scenes.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.toothpick.annotation.ApplicationScope
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.data.backup.CloudBackup
import ru.alox1d.wordnote.huawei.data.backup.NoCloudBackupException
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import ru.alox1d.wordnote.huawei.databinding.ActivityBackupBinding
import ru.alox1d.wordnote.huawei.device.service.drive.BackupScheduler
import ru.alox1d.wordnote.huawei.di.module.MainModule
import ru.alox1d.wordnote.huawei.domain.service.drive.IDriveService
import ru.alox1d.wordnote.huawei.helpers.applySystemInsets
import toothpick.ktp.KTP
import toothpick.ktp.delegate.inject
import toothpick.smoothie.lifecycle.closeOnDestroy

class BackupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBackupBinding
    private val drive: IDriveService by inject()
    private var pendingUpload: Boolean? = null
    private var selectedInterval = OFF
    private val preferences by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()
        KTP.openScopes(ApplicationScope::class.java, this).installModules(MainModule(applicationContext))
            .closeOnDestroy(this).inject(this)
        selectedInterval = preferences.getLong(PreferenceStorage.BACKUP_INTERVAL, 0).toInt().coerceIn(0, 3)
        binding.spinnerBackup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.intervals))
        binding.spinnerBackup.setSelection(selectedInterval, false)
        binding.spinnerBackup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == selectedInterval) return
                if (position != OFF && !preferences.getBoolean(ACCOUNT_READY, false)) {
                    showMessage(R.string.first_backup)
                    binding.spinnerBackup.setSelection(selectedInterval, false)
                    return
                }
                try {
                    BackupScheduler(applicationContext).setInterval(position)
                    selectedInterval = position
                } catch (_: Exception) {
                    binding.spinnerBackup.setSelection(selectedInterval, false)
                    showMessage(R.string.backup_schedule_failed)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.btnHmsBackup.setOnClickListener { startLogin(true) }
        binding.btnHmsRestore.setOnClickListener { startLogin(false) }
        if (savedInstanceState?.containsKey(PENDING_UPLOAD) == true) {
            pendingUpload = savedInstanceState.getBoolean(PENDING_UPLOAD)
            setBusy(true)
        }
    }

    private fun startLogin(upload: Boolean) {
        pendingUpload = upload
        setBusy(true)
        // lifecycleScope runs on Main; activity login must not be invoked from an IO worker.
        lifecycleScope.launch {
            try {
                startActivityForResult(drive.login(), REQUEST_SIGN_IN_LOGIN)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                pendingUpload = null
                setBusy(false)
                showMessage(R.string.backup_login_failed)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SIGN_IN_LOGIN) return
        val upload = pendingUpload ?: true
        pendingUpload = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            setBusy(false)
            showMessage(if (resultCode == Activity.RESULT_CANCELED) R.string.backup_login_cancelled else R.string.backup_login_failed)
            return
        }
        lifecycleScope.launch {
            try {
                val result = HuaweiIdAuthManager.parseAuthResultFromIntent(data)
                if (!result.isSuccessful) { showMessage(R.string.backup_login_failed); return@launch }
                val account = result.result
                if (drive.init(account.unionId, account.accessToken, drive.refreshAT) != DriveCode.SUCCESS) {
                    showMessage(R.string.backup_login_failed)
                    return@launch
                }
                preferences.edit().putBoolean(ACCOUNT_READY, true).apply()
                withContext(Dispatchers.IO) {
                    val cloud = CloudBackup(applicationContext, drive)
                    if (upload) cloud.backup() else {
                        val operationContext = currentCoroutineContext()
                        cloud.restore { operationContext.ensureActive() }
                    }
                }
                showMessage(if (upload) R.string.backup_success else R.string.restore_success)
                if (!upload) {
                    setResult(REQUEST_CODE_OK)
                    finish()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: NoCloudBackupException) { showMessage(R.string.cloud_empty) }
            catch (_: Exception) { showMessage(if (upload) R.string.backup_operation_failed else R.string.backup_restore_failed) }
            finally { setBusy(false) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingUpload?.let { outState.putBoolean(PENDING_UPLOAD, it) }
        super.onSaveInstanceState(outState)
    }

    private fun setBusy(busy: Boolean) {
        binding.btnHmsBackup.isEnabled = !busy
        binding.btnHmsRestore.isEnabled = !busy
        binding.spinnerBackup.isEnabled = !busy
        binding.progressBackup.visibility = if (busy) View.VISIBLE else View.GONE
    }
    private fun showMessage(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val REQUEST_CODE_OK = 10
        const val REQUEST_CODE_ERROR = 20
        const val REQUEST_SIGN_IN_LOGIN = 1002
        const val OFF = 0
        const val DAILY = 1
        const val WEEKLY = 2
        const val MONTHLY = 3
        private const val ACCOUNT_READY = "backup_account_ready"
        private const val PENDING_UPLOAD = "backup_pending_upload"
    }
}
