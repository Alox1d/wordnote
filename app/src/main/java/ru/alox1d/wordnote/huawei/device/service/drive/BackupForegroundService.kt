package ru.alox1d.wordnote.huawei.device.service.drive

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.alox1d.wordnote.huawei.R
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity

/** Foreground fallback is used only on API 19/20. API 21+ routes through JobScheduler. */
class BackupForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operation: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scheduler = BackupScheduler(this)
        if (Build.VERSION.SDK_INT >= 21) {
            runCatching { scheduler.enqueue() }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!scheduler.isEnabled()) { stopSelf(startId); return START_NOT_STICKY }
        if (operation?.isActive == true) return START_NOT_STICKY
        val pending = PendingIntent.getActivity(this, 0, Intent(this, BackupActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, NotificationCompat.Builder(this, "ForegroundService")
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.backup_is_running))
            .setSmallIcon(R.drawable.ic_check_white_48dp).setContentIntent(pending).build())
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WordNote:legacyBackup").apply { acquire(180_000L) }
        operation = scope.launch {
            var retry = false
            try {
                val network = (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
                if (network?.isConnected != true) retry = true else BackgroundBackup.run(applicationContext)
            } catch (_: TimeoutCancellationException) {
                retry = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: BackupAuthenticationUnavailable) {
                // The next periodic attempt may use a later successful manual login.
            } catch (_: Exception) { retry = true }
            finally {
                scheduler.afterLegacyAttempt(retry)
                releaseWakeLock()
                stopForeground(true)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { operation?.cancel(); stopSelf() }
    override fun onBind(intent: Intent): IBinder? = null
    override fun onDestroy() { scope.cancel(); releaseWakeLock(); super.onDestroy() }
    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }

    companion object {
        fun startService(context: Context, date: Long) { BackupScheduler(context).enqueue() }
        fun stopService(context: Context) { context.stopService(Intent(context, BackupForegroundService::class.java)) }
    }
}
