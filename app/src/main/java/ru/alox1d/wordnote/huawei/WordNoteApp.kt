package ru.alox1d.wordnote.huawei

import android.app.Application
import androidx.multidex.MultiDexApplication
import com.example.toothpick.annotation.ApplicationScope
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
        }

        var isHiddenNotesUnlocked = false

        const val IMAGE_PICKED = 2
    }

}