package ru.alox1d.wordnote.huawei

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import com.huawei.hmf.tasks.Task
import com.huawei.hmf.tasks.TaskCompletionSource
import com.huawei.hms.mlsdk.common.MLFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.alox1d.wordnote.huawei.device.service.tr.camera.FrameMetadata
import ru.alox1d.wordnote.huawei.domain.services.tr.transactor.BaseTransactor
import ru.alox1d.wordnote.huawei.ui.views.overlay.GraphicOverlay
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OcrFailureRecoveryTest {
    private class Probe : BaseTransactor<String>() {
        val requests = mutableListOf<TaskCompletionSource<String>>()
        val frames = mutableListOf<ByteBuffer?>()
        val successes = mutableListOf<String>()
        val failures = mutableListOf<Exception?>()
        var failImmediately = false
        override fun detectInImage(image: MLFrame?): Task<String> {
            frames += image?.byteBuffer
            return TaskCompletionSource<String>().also {
                requests += it
                if (failImmediately) it.setException(IllegalStateException("Synthetic immediate failure"))
            }.task
        }
        override suspend fun onSuccess(originalCameraImage: Bitmap?, results: String,
            frameMetadata: FrameMetadata?, graphicOverlay: GraphicOverlay?) { successes += results }
        override fun onFailure(e: Exception?) { failures += e }
    }

    private val overlay get() = GraphicOverlay(RuntimeEnvironment.getApplication(), null)
    private val metadata = FrameMetadata.Builder().setWidth(4).setHeight(4).setRotation(0).setCameraFacing(0).build()
    private fun frame(value: Byte = 100) = ByteBuffer.wrap(ByteArray(24) { value })
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun fail(request: TaskCompletionSource<String>) = request.setException(IllegalStateException("Synthetic transient analyzer failure"))

    @Test fun failedFrameAllowsLaterCameraFrameToReachAnalyzer() {
        val probe = Probe()
        probe.process(frame(), metadata, overlay)
        fail(probe.requests.single()); idle()
        assertEquals(1, probe.failures.size)
        probe.process(frame(), metadata, overlay); idle()
        assertEquals(2, probe.requests.size)
    }

    @Test fun failedFrameAdvancesToNewestQueuedFrame() {
        val probe = Probe()
        val first = frame(1); val newest = frame(3)
        probe.process(first, metadata, overlay)
        probe.process(frame(2), metadata, overlay)
        probe.process(newest, metadata, overlay)
        assertEquals(1, probe.requests.size)
        fail(probe.requests[0]); idle()
        assertEquals(2, probe.requests.size)
        assertSame(newest, probe.frames[1])
    }

    @Test fun consecutiveFailuresContinueProcessingNewFrames() {
        val probe = Probe()
        repeat(3) { index ->
            probe.process(frame(), metadata, overlay)
            assertEquals(index + 1, probe.requests.size)
            fail(probe.requests[index]); idle()
        }
        assertEquals(3, probe.failures.size)
    }

    @Test fun alreadyFailedTasksDoNotWedgeLaterCameraFrames() {
        val probe = Probe().apply { failImmediately = true }
        repeat(20) {
            probe.process(frame(), metadata, overlay); idle()
        }
        assertEquals(20, probe.requests.size)
        assertEquals(20, probe.failures.size)
    }

    @Test fun successfulFrameStillAdvancesToNewestQueuedFrame() {
        val probe = Probe()
        val newest = frame(3)
        probe.process(frame(1), metadata, overlay)
        probe.process(frame(2), metadata, overlay)
        probe.process(newest, metadata, overlay)
        probe.requests[0].setResult("recognized"); idle()
        assertEquals(listOf("recognized"), probe.successes)
        assertEquals(2, probe.requests.size)
        assertSame(newest, probe.frames[1])
    }

    @Test fun stopDropsPendingFailureQueuedFrameAndFutureFrames() {
        val probe = Probe()
        probe.process(frame(), metadata, overlay)
        probe.process(frame(2), metadata, overlay)
        probe.stop()
        fail(probe.requests[0]); idle()
        probe.process(frame(3), metadata, overlay); idle()
        assertTrue(probe.failures.isEmpty())
        assertEquals(1, probe.requests.size)
    }

    @Test fun stopDropsPendingSuccessAndQueuedFrame() {
        val probe = Probe()
        probe.process(frame(), metadata, overlay)
        probe.process(frame(2), metadata, overlay)
        probe.requests[0].setResult("stale")
        probe.stop(); idle()
        assertTrue(probe.successes.isEmpty())
        assertEquals(1, probe.requests.size)
    }

    @Test fun stopPreventsNewStillImageAnalysis() {
        val probe = Probe()
        probe.stop()
        probe.process(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), overlay); idle()
        assertTrue(probe.requests.isEmpty())
    }
}
