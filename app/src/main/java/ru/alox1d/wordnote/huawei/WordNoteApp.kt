package ru.alox1d.wordnote.huawei

import android.app.Application
import androidx.multidex.MultiDexApplication
import com.example.toothpick.annotation.ApplicationScope
import ru.alox1d.wordnote.huawei.device.service.drive.BackupScheduler
import ru.alox1d.wordnote.huawei.data.storage.PreferenceStorage
import toothpick.Scope
import toothpick.ktp.KTP
import toothpick.ktp.binding.bind
import toothpick.ktp.binding.module


class WordNoteApp : MultiDexApplication() {
    lateinit var scope: Scope

    override fun onCreate() {
        super.onCreate()

        scope = KTP.openScope(ApplicationScope::class.java)
                .installModules(module {
                    bind<Application>().toInstance { this@WordNoteApp }
                })
        getSharedPreferences("preferences", MODE_PRIVATE).edit()
            .remove(PreferenceStorage.at)
            .remove(PreferenceStorage.unionID)
            .apply()
        // Migrate existing backup preferences once per process start. A denied or
        // temporarily unavailable system scheduler must not prevent opening notes.
        runCatching { BackupScheduler(this).restoreSchedule() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        scope.release()
    }

    companion object {
        var isActivityVisible = false
            private set


        fun activityResumed() {
            isActivityVisible = true
        }


        fun activityPaused() {
            isActivityVisible = false
            isHiddenNotesUnlocked = false
        }

        var isHiddenNotesUnlocked = false

        const val IMAGE_PICKED = 2
    }

}