package ru.alox1d.wordnote.huawei

import android.app.Application
import android.content.Context
import com.huawei.cloud.client.exception.DriveCode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.device.service.drive.HuaweiDriveService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DriveAuthenticationTest {
    @Test fun authenticationRemainsInMemoryInsteadOfBeingCopiedIntoBackedUpPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        preferences.edit().putString("at", "old-artificial-token").putString("unionID", "old-artificial-account").commit()
        val service = HuaweiDriveService(context)
        assertEquals(DriveCode.SUCCESS, service.init("artificial-account", "artificial-token", service.refreshAT))
        assertFalse(preferences.contains("at"))
        assertFalse(preferences.contains("unionID"))
        assertEquals("artificial-token", service.refreshAT.refreshToken())
    }

    @Test fun invalidAuthenticationIsRejectedWithoutKeepingThePreviousSession() {
        val context = RuntimeEnvironment.getApplication()
        val service = HuaweiDriveService(context)
        assertEquals(DriveCode.SUCCESS, service.init("artificial-account", "artificial-token", service.refreshAT))
        assertEquals(DriveCode.ERROR, service.init(null, null, service.refreshAT))
        assertThrows(IllegalStateException::class.java) { service.queryFiles("wordnote_backup_v1.zip") }
    }
}
