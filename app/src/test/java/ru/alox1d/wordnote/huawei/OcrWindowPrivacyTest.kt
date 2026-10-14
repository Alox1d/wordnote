package ru.alox1d.wordnote.huawei

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.ui.scenes.textrecognition.BaseTextRecognitionActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OcrWindowPrivacyTest {
    @Test fun documentWindowIsSecureBeforeDisplayingRecognitionContent() {
        Robolectric.buildActivity(BaseTextRecognitionActivity::class.java).create().use { controller ->
            assertTrue("OCR must protect its own window before adding document views",
                controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            controller.get().setContentView(TextView(controller.get()).apply { text = "Synthetic private document" })
            controller.start().resume().visible()
            assertTrue(controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    @Test fun restoredRecognitionWindowRetainsCaptureProtection() {
        val state = Bundle()
        Robolectric.buildActivity(BaseTextRecognitionActivity::class.java).setup().use { controller ->
            controller.pause().saveInstanceState(state).stop()
        }
        Robolectric.buildActivity(BaseTextRecognitionActivity::class.java).create(state).start().resume().use { controller ->
            assertTrue("Recreated OCR content must remain capture-protected",
                controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }
}
