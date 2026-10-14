package ru.alox1d.wordnote.huawei

import android.app.Activity
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.device.service.tr.camera.CameraConfiguration
import ru.alox1d.wordnote.huawei.device.service.tr.camera.LensEngine
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LensEngineLifecycleTest {
    @Test fun stoppedCameraCanBeReleasedAndReleasedAgain() {
        Robolectric.buildActivity(Activity::class.java).setup().use { c ->
            val engine = LensEngine(c.get(), CameraConfiguration(), GraphicOverlay(c.get(), null))
            engine.stop()
            engine.release()
            engine.release()
            assertNull(engine.camera)
        }
    }
    @Test fun releaseDoesNotJoinAWorkerWhileHoldingItsProcessingLock() {
        Robolectric.buildActivity(Activity::class.java).setup().use { c ->
            val entered = java.util.concurrent.CountDownLatch(1)
            val once = java.util.concurrent.atomic.AtomicBoolean()
            lateinit var worker: Thread
            val engine = object : LensEngine(c.get(), CameraConfiguration(), GraphicOverlay(c.get(), null)) {
                override fun stop() {
                    if (once.compareAndSet(false, true)) {
                        val lock = org.robolectric.util.ReflectionHelpers.getField<Any>(this, "transactorLock")
                        worker = Thread {
                            entered.countDown()
                            synchronized(lock) { /* A last frame needs the processing lock to finish. */ }
                        }
                        org.robolectric.util.ReflectionHelpers.setField(this, "transactingThread", worker)
                        worker.start()
                        org.junit.Assert.assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    super.stop()
                }
            }
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val release = executor.submit { engine.release() }
            try {
                release.get(2, java.util.concurrent.TimeUnit.SECONDS)
            } finally {
                release.cancel(true)
                executor.shutdownNow()
                if (once.get()) worker.join(1000)
            }
        }
    }

}
