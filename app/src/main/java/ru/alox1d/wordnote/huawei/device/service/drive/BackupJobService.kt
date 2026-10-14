package ru.alox1d.wordnote.huawei.device.service.drive

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(21)
class BackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (!BackupScheduler(this).isEnabled()) return false
        running[params.jobId]?.cancel()
        val operation = scope.launch(start = CoroutineStart.LAZY) {
            var retry = false
            try {
                BackgroundBackup.run(applicationContext)
            } catch (_: TimeoutCancellationException) {
                retry = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: BackupAuthenticationUnavailable) {
                // Periodic work remains scheduled; an unavailable account never opens UI or loops rapidly.
            } catch (_: Exception) {
                retry = true
            } finally {
                if (running[params.jobId] === coroutineContext[Job]) {
                    running.remove(params.jobId)
                    jobFinished(params, retry && BackupScheduler(this@BackupJobService).isEnabled())
                }
            }
        }
        running[params.jobId] = operation
        operation.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return BackupScheduler(this).isEnabled()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
