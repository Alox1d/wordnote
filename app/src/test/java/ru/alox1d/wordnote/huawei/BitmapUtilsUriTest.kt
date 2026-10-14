package ru.alox1d.wordnote.huawei

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import ru.alox1d.wordnote.huawei.helpers.util.BitmapUtils
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapUtilsUriTest {
    private lateinit var provider: StreamImageProvider
    private lateinit var fixture: File

    @Before fun setup() {
        fixture = File.createTempFile("picked-image-", ".bin", RuntimeEnvironment.getApplication().cacheDir)
        provider = Robolectric.buildContentProvider(StreamImageProvider::class.java).create("synthetic.wordnote.images").get()
        provider.image = fixture
        ShadowContentResolver.registerProviderInternal("synthetic.wordnote.images", provider)
    }

    @After fun cleanup() {
        provider.opened.forEach { runCatching { it.close() } }
        fixture.delete()
    }

    @Test fun decodesAndBoundsPickedImageWithoutAFilePathColumn() {
        fixture.writeBytes(Base64.decode(PNG, Base64.DEFAULT))
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val bitmap = BitmapUtils.loadFromPath(controller.get(), PICKED_IMAGE, 4, 4)
            assertNotNull(bitmap)
            assertEquals(4, bitmap.width)
            assertEquals(2, bitmap.height)
            assertStreamsClosed()
            bitmap.recycle()
        }
    }

    @Test fun appliesExifRotationToPickedJpegWithoutAFilePathColumn() {
        fixture.writeBytes(Base64.decode(ORIENTED_JPEG, Base64.DEFAULT))
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val bitmap = BitmapUtils.loadFromPath(controller.get(), PICKED_IMAGE, 4, 4)
            assertNotNull(bitmap)
            // The fixture is 8x4 pixels with EXIF orientation 6: rotate 90 degrees clockwise.
            assertEquals(2, bitmap.width)
            assertEquals(4, bitmap.height)
            assertStreamsClosed()
            bitmap.recycle()
        }
    }

    @Test fun keepsExtremeAspectRatioImageAtLeastOnePixel() {
        fixture.writeBytes(Base64.decode(TALL_PNG, Base64.DEFAULT))
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val bitmap = BitmapUtils.loadFromPath(controller.get(), PICKED_IMAGE, 1, 1)
            assertNotNull(bitmap)
            assertEquals(1, bitmap.width)
            assertEquals(1, bitmap.height)
            assertStreamsClosed()
            bitmap.recycle()
        }
    }

    @Test fun rejectsMalformedPickedImageWithoutLeavingTemporaryFiles() {
        fixture.writeBytes(byteArrayOf(1, 2, 3, 4))
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            assertNull(BitmapUtils.loadFromPath(controller.get(), PICKED_IMAGE, 4, 4))
            assertStreamsClosed()
            assertNoTemporaryImages()
        }
    }

    @Test fun acceptsImageAtSizeLimitAndRejectsOneByteOverIt() {
        fixture.writeBytes(Base64.decode(PNG, Base64.DEFAULT))
        java.io.RandomAccessFile(fixture, "rw").use { it.setLength(32L * 1024 * 1024) }
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val bitmap = BitmapUtils.loadFromPath(controller.get(), PICKED_IMAGE, 4, 4)
            assertNotNull("A valid image at the copy limit remains readable", bitmap)
            assertEquals(4, bitmap.width)
            assertEquals(2, bitmap.height)
            bitmap.recycle()
            java.io.RandomAccessFile(fixture, "rw").use { it.setLength(32L * 1024 * 1024 + 1) }
            assertNull("An over-limit provider response must be rejected", BitmapUtils.loadFromPath(controller.get(), PICKED_IMAGE, 4, 4))
            assertStreamsClosed()
            assertNoTemporaryImages()
        }
    }

    private fun assertNoTemporaryImages() {
        assertTrue(RuntimeEnvironment.getApplication().cacheDir.listFiles()!!.none {
            it.name.startsWith("wordnote-ocr-")
        })
    }

    private fun assertStreamsClosed() {
        assertTrue("The picked URI must be opened through its provider", provider.opened.isNotEmpty())
        assertTrue("Image decoding must close provider descriptors", provider.opened.all {
            !runCatching { it.fileDescriptor.valid() }.getOrDefault(false)
        })
    }

    class StreamImageProvider : ContentProvider() {
        lateinit var image: File
        val opened = mutableListOf<ParcelFileDescriptor>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/*"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf("picked-image", image.length()))
            }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            require(mode == "r")
            return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY).also { opened.add(it) }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    companion object {
        private val PICKED_IMAGE = Uri.parse("content://synthetic.wordnote.images/picked/1")
        private const val TALL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAGQCAIAAADgOp2MAAAAGElEQVR4nGP4z8DAMIpH8SgexaN4FJOIAfuyjoAUKKEhAAAAAElFTkSuQmCC"
        private const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAECAIAAAA8r+mnAAAAEklEQVR4nGP4z8CAFWEXJUsCAFpeH+EeQoQoAAAAAElFTkSuQmCC"
        private const val ORIENTED_JPEG = "/9j/4AAQSkZJRgABAQAASABIAAD/4QAiRXhpZgAASUkqAAgAAAABABIBAwABAAAABgAAAAAAAAD/7QA4UGhvdG9zaG9wIDMuMAA4QklNBAQAAAAAAAA4QklNBCUAAAAAABDUHYzZjwCyBOmACZjs+EJ+/8AAEQgABAAIAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/bAEMAAgICAgICAwICAwUDAwMFBgUFBQUGCAYGBgYGCAoICAgICAgKCgoKCgoKCgwMDAwMDA4ODg4ODw8PDw8PDw8PD//bAEMBAgICBAQEBwQEBxALCQsQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEP/dAAQAAf/aAAwDAQACEQMRAD8A+L6KKK/lM/38P//Z"
    }
}
