package ru.alox1d.wordnote.huawei

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.ui.scenes.backup.BackupActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupUiTest {
    @Test fun cancelledLoginResetsControlsWithoutParsingOrCloudOperation() {
        checkLoginResult(Activity.RESULT_CANCELED)
    }

    @Test fun missingLoginResultFailsSafelyAndResetsControls() {
        checkLoginResult(Activity.RESULT_OK)
    }

    private fun checkLoginResult(result: Int) {
        Robolectric.buildActivity(BackupActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val backup = activity.findViewById<Button>(R.id.btn_hms_backup)
            val restore = activity.findViewById<Button>(R.id.btn_hms_restore)
            val progress = activity.findViewById<View>(R.id.progress_backup)
            backup.isEnabled = false
            restore.isEnabled = false
            progress.visibility = View.VISIBLE
            val callback = BackupActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Intent::class.java)
            callback.isAccessible = true
            callback.invoke(activity, BackupActivity.REQUEST_SIGN_IN_LOGIN, result, null)
            assertTrue(backup.isEnabled)
            assertTrue(restore.isEnabled)
            assertEquals(View.GONE, progress.visibility)
        }
    }
}
