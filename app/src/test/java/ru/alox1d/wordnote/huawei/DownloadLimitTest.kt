package ru.alox1d.wordnote.huawei

import org.junit.Assert.*
import org.junit.Test
import ru.alox1d.wordnote.huawei.device.service.drive.LimitedDownloadOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class DownloadLimitTest {
    @Test fun oversizedCloudResponseStopsBeforeWritingBytesBeyondTheLimit() {
        val bytes = ByteArrayOutputStream()
        val output = LimitedDownloadOutputStream(bytes, 8)
        output.write(byteArrayOf(1, 2, 3, 4))
        output.write(byteArrayOf(5, 6, 7, 8))
        assertThrows(IOException::class.java) { output.write(9) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), bytes.toByteArray())
    }

    @Test fun oversizedChunkIsRejectedEvenWhenRemoteMetadataWasSmaller() {
        val bytes = ByteArrayOutputStream()
        val output = LimitedDownloadOutputStream(bytes, 4)
        assertThrows(IOException::class.java) { output.write(ByteArray(5)) }
        assertEquals(0, bytes.size())
    }

    @Test fun writesCountOnlyTheSelectedRange() {
        val bytes = ByteArrayOutputStream()
        val output = LimitedDownloadOutputStream(bytes, 3)
        output.write(byteArrayOf(0, 1, 2, 3, 4), 1, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), bytes.toByteArray())
        assertThrows(IOException::class.java) { output.write(byteArrayOf(8), 0, 1) }
    }
}
