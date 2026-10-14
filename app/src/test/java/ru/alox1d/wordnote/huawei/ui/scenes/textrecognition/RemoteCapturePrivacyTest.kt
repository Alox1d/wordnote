package ru.alox1d.wordnote.huawei.ui.scenes.textrecognition

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import androidx.core.content.FileProvider
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import ru.alox1d.wordnote.huawei.helpers.util.Constant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RemoteCapturePrivacyTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before fun setup() { clearProviderPathCache() }

    @After fun cleanup() {
        app.cacheDir.walkTopDown().filter { it.isFile && it.name.startsWith("wordnote-camera-") }
            .forEach { it.delete() }
        clearProviderPathCache()
    }

    private fun clearProviderPathCache() {
        // Robolectric uses a new application cache directory per method; AndroidX caches it statically.
        ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache").clear()
    }

    @Test fun cameraReceivesWritablePrivateCacheUriWithoutStoragePermission() {
        val camera = ResolveInfo().apply {
            isDefault = true
            activityInfo = ActivityInfo().apply {
                packageName = "synthetic.camera"
                name = "synthetic.camera.CaptureActivity"
                enabled = true
                exported = true
                applicationInfo = ApplicationInfo().apply { packageName = "synthetic.camera" }
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), camera)
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        // buildActivity attaches the real final activity; create()/onCreate() is deliberately omitted.
        val activity = Robolectric.buildActivity(RemoteDetectionActivity::class.java).get()
        ReflectionHelpers.setField(activity, "preview", ImageView(app))
        // Exercise the existing output boundary without onCreate, an HMS analyzer, or a real camera app.
        ReflectionHelpers.callInstanceMethod<Any?>(activity, "startCamera")
        val capture = shadowOf(activity).nextStartedActivityForResult?.intent
        assertNotNull("The camera handler must receive an output request", capture)
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, capture!!.action)
        val output = capture.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
        assertNotNull(output)
        assertEquals("content", output!!.scheme)
        assertEquals(app.packageName + ".camera", output.authority)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            capture.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertEquals(output, capture.clipData!!.getItemAt(0).uri)

        val info = app.packageManager.resolveContentProvider(output.authority!!, PackageManager.GET_META_DATA)
        assertNotNull("The private camera provider must be declared in the manifest", info)
        val provider = FileProvider()
        provider.attachInfo(app, info!!)
        ShadowContentResolver.registerProviderInternal(output.authority, provider)
        val bytes = byteArrayOf(11, 22, 33, 44)
        app.contentResolver.openOutputStream(output)!!.use { it.write(bytes) }
        assertArrayEquals(bytes, app.contentResolver.openInputStream(output)!!.use { it.readBytes() })
        val backingFile = app.cacheDir.walkTopDown().firstOrNull { it.isFile && it.name == output.lastPathSegment }
        assertNotNull("The capture must be stored inside the private cache", backingFile)
        assertArrayEquals(bytes, backingFile!!.readBytes())
    }

    @Test fun pendingCameraRecreationKeepsOriginalOutputWithoutRelaunching() {
        val (output, bytes) = privateCaptureOutput()
        val replacement = attachedActivity()
        val state = Bundle().apply {
            putParcelable("KEY_IMAGE_URI", output)
            putBoolean("KEY_AWAITING_IMAGE_RESULT", true)
        }
        replacement.resumeImageSelection(state, Constant.TYPE_TAKE_PHOTO)
        assertNull("A pending camera must not be launched a second time", shadowOf(replacement).nextStartedActivityForResult)
        assertEquals(output, ReflectionHelpers.getField<Uri>(replacement, "imageUri"))
        assertArrayEquals("The in-flight camera output must remain writable", bytes,
            app.contentResolver.openInputStream(output)!!.use { it.readBytes() })
        app.contentResolver.openOutputStream(output, "wa")!!.use { it.write(byteArrayOf(55)) }
        assertArrayEquals(bytes + byteArrayOf(55), app.contentResolver.openInputStream(output)!!.use { it.readBytes() })
    }

    @Test fun pendingPickerRecreationDoesNotLaunchAnotherPicker() {
        val replacement = attachedActivity()
        val state = Bundle().apply { putBoolean("KEY_AWAITING_IMAGE_RESULT", true) }
        replacement.resumeImageSelection(state, Constant.TYPE_SELECT_IMAGE)
        assertNull("The original picker owns the pending result", shadowOf(replacement).nextStartedActivityForResult)
    }

    @Test fun selectedImageRecreationKeepsUriInsteadOfTakingAnotherPhoto() {
        val (output, bytes) = privateCaptureOutput()
        val replacement = attachedActivity()
        val state = Bundle().apply {
            putParcelable("KEY_IMAGE_URI", output)
            putBoolean("KEY_AWAITING_IMAGE_RESULT", false)
        }
        replacement.resumeImageSelection(state, Constant.TYPE_TAKE_PHOTO)
        assertNull("A selected image must be restored without another camera launch", shadowOf(replacement).nextStartedActivityForResult)
        assertEquals(output, ReflectionHelpers.getField<Uri>(replacement, "imageUri"))
        assertArrayEquals(bytes, app.contentResolver.openInputStream(output)!!.use { it.readBytes() })
    }

    private fun attachedActivity(): RemoteDetectionActivity =
        Robolectric.buildActivity(RemoteDetectionActivity::class.java).get().also {
            ReflectionHelpers.setField(it, "preview", ImageView(app))
        }

    private fun privateCaptureOutput(): Pair<Uri, ByteArray> {
        val camera = ResolveInfo().apply {
            isDefault = true
            activityInfo = ActivityInfo().apply {
                packageName = "synthetic.camera"
                name = "synthetic.camera.CaptureActivity"
                enabled = true
                exported = true
                applicationInfo = ApplicationInfo().apply { packageName = "synthetic.camera" }
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), camera)
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val activity = attachedActivity()
        ReflectionHelpers.callInstanceMethod<Any?>(activity, "startCamera")
        val output = shadowOf(activity).nextStartedActivityForResult!!.intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
        val provider = FileProvider()
        provider.attachInfo(app, app.packageManager.resolveContentProvider(output.authority!!, PackageManager.GET_META_DATA)!!)
        ShadowContentResolver.registerProviderInternal(output.authority, provider)
        val bytes = byteArrayOf(11, 22, 33, 44)
        app.contentResolver.openOutputStream(output)!!.use { it.write(bytes) }
        return output to bytes
    }

}
