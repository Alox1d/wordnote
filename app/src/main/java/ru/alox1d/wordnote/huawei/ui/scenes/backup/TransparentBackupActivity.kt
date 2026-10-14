package ru.alox1d.wordnote.huawei.ui.scenes.backup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Historical entry point delegates to the single lifecycle-owned manual backup flow. */
class TransparentBackupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, BackupActivity::class.java))
        finish()
    }
}
