package java.util.zip

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/*
 * Ersatz fuer GZIPInputStream und GZIPOutputStream ueber die zlib des Systems (Fensterbits 31 = gzip-Rahmen).
 * Beide arbeiten auf dem ganzen Inhalt im Speicher; der Code der App packt damit nur kleine Zwischenspeicher.
 */

open class GZIPOutputStream(private val ziel: OutputStream, size: Int = 512, syncFlush: Boolean = false) : OutputStream() {
    private val puffer = ByteArrayOutputStream(maxOf(size, 32))
    private var fertig = false
    private var geschlossen = false

    override fun write(b: Int) {
        if (fertig) throw IOException("write beyond end of stream")
        puffer.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (fertig) throw IOException("write beyond end of stream")
        puffer.write(b, off, len)
    }

    open fun finish() {
        if (fertig) return
        fertig = true
        ziel.write(gzipPacken(puffer.toByteArray()))
    }

    override fun flush() = ziel.flush()

    override fun close() {
        if (geschlossen) return
        geschlossen = true
        try {
            finish()
        } finally {
            ziel.close()
        }
    }
}

open class GZIPInputStream(private val quelle: InputStream, size: Int = 512) : InputStream() {
    private val inhalt = ByteArrayInputStream(gzipEntpacken(quelle.readAllBytes()))

    override fun read(): Int = inhalt.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = inhalt.read(b, off, len)
    override fun available(): Int = inhalt.available()
    override fun close() = quelle.close()
}

private fun gzipPacken(daten: ByteArray): ByteArray {
    val strom = nativeHeap.alloc<z_stream>()
    try {
        strom.zalloc = null
        strom.zfree = null
        strom.opaque = null
        val r = deflateInit2_(strom.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 31, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().toInt())
        if (r != Z_OK) throw IOException("zlib deflateInit failed: $r")
        try {
            val aus = ByteArray(deflateBound(strom.ptr, daten.size.convert()).toInt() + 64)
            val eingabe = if (daten.isEmpty()) ByteArray(1) else daten
            var n = 0
            eingabe.usePinned { e ->
                aus.usePinned { a ->
                    strom.next_in = e.addressOf(0).reinterpret<UByteVar>()
                    strom.avail_in = daten.size.toUInt()
                    strom.next_out = a.addressOf(0).reinterpret<UByteVar>()
                    strom.avail_out = aus.size.toUInt()
                    val z = deflate(strom.ptr, Z_FINISH)
                    n = aus.size - strom.avail_out.toInt()
                    strom.next_in = null
                    strom.next_out = null
                    if (z != Z_STREAM_END) throw IOException("zlib deflate failed: $z")
                }
            }
            return aus.copyOf(n)
        } finally {
            deflateEnd(strom.ptr)
        }
    } finally {
        nativeHeap.free(strom)
    }
}

private fun gzipEntpacken(daten: ByteArray): ByteArray {
    if (daten.isEmpty()) throw EOFException()
    val strom = nativeHeap.alloc<z_stream>()
    try {
        strom.zalloc = null
        strom.zfree = null
        strom.opaque = null
        val r = inflateInit2_(strom.ptr, 31, ZLIB_VERSION, sizeOf<z_stream>().toInt())
        if (r != Z_OK) throw IOException("zlib inflateInit failed: $r")
        try {
            val out = ByteArrayOutputStream(maxOf(1024, minOf(daten.size * 4, 64_000_000)))
            val puffer = ByteArray(65_536)
            daten.usePinned { e ->
                puffer.usePinned { p ->
                    strom.next_in = e.addressOf(0).reinterpret<UByteVar>()
                    strom.avail_in = daten.size.toUInt()
                    try {
                        while (true) {
                            strom.next_out = p.addressOf(0).reinterpret<UByteVar>()
                            strom.avail_out = puffer.size.toUInt()
                            val z = inflate(strom.ptr, Z_NO_FLUSH)
                            val n = puffer.size - strom.avail_out.toInt()
                            if (n > 0) out.write(puffer, 0, n)
                            if (z == Z_STREAM_END) break
                            if (z != Z_OK && z != Z_BUF_ERROR) throw ZipException("invalid gzip data ($z)")
                            if (n == 0 && strom.avail_in == 0u) throw EOFException("Unexpected end of ZLIB input stream")
                        }
                    } finally {
                        strom.next_in = null
                        strom.next_out = null
                    }
                }
            }
            return out.toByteArray()
        } finally {
            inflateEnd(strom.ptr)
        }
    } finally {
        nativeHeap.free(strom)
    }
}
