package ru.alox1d.wordnote.huawei.device.service.drive

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/** Enforces the local archive limit independently of untrusted remote size metadata. */
internal class LimitedDownloadOutputStream(output: OutputStream, private val maxBytes: Long) : FilterOutputStream(output) {
    private var written = 0L
    init { require(maxBytes >= 0) }

    override fun write(value: Int) {
        ensureRemaining(1)
        out.write(value)
        written++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        ensureRemaining(length.toLong())
        out.write(bytes, offset, length)
        written += length
    }

    override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)

    private fun ensureRemaining(length: Long) {
        if (length > maxBytes - written) throw IOException("Cloud backup exceeds the supported size")
    }
}
