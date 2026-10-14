package ru.alox1d.wordnote.huawei.device.service.drive

import android.content.Context
import com.huawei.cloud.client.exception.DriveCode
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.alox1d.wordnote.huawei.data.backup.CloudBackup
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class BackupAuthenticationUnavailable : IOException("Manual Huawei sign-in is required")

internal object BackgroundBackup {
    suspend fun run(context: Context) = withTimeout(120_000L) {
        val drive = HuaweiDriveService(context)
        // HMS account operations are invoked on Main; no background Activity is launched.
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    HuaweiIdAuthManager.getService(context, HuaweiDriveService.authParams()).silentSignIn()
                        .addOnSuccessListener { account ->
                            if (continuation.isActive) {
                                try {
                                    if (drive.init(account.unionId, account.accessToken, drive.refreshAT) == DriveCode.SUCCESS) {
                                        continuation.resume(Unit)
                                    } else continuation.resumeWithException(BackupAuthenticationUnavailable())
                                } catch (_: Exception) { continuation.resumeWithException(BackupAuthenticationUnavailable()) }
                            }
                        }.addOnFailureListener {
                            if (continuation.isActive) continuation.resumeWithException(BackupAuthenticationUnavailable())
                        }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(BackupAuthenticationUnavailable())
                }
            }
        }
        withContext(Dispatchers.IO) { CloudBackup(context, drive).backup() }
    }
}
