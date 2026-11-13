package java.io

/*
 * Ersatz fuer die Byte-Ströme aus java.io (ohne Dateien; java.io.File liegt in File.kt).
 * readBytes() und copyTo() sind in Kotlin/JVM Erweiterungen aus kotlin.io; hier stehen sie als Mitglieder,
 * damit der Code der App sie ohne Import findet.
 */

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
}

open class EOFException(message: String? = null) : IOException(message)

open class FileNotFoundException(message: String? = null) : IOException(message)

open class UnsupportedEncodingException(message: String? = null) : IOException(message)

open class UncheckedIOException(message: String?, cause: IOException) : RuntimeException(message, cause) {
    constructor(cause: IOException) : this(cause.toString(), cause)
}

interface Closeable : AutoCloseable {
    override fun close()
}

interface Flushable {
    fun flush()
}

abstract class InputStream : Closeable {

    /** Ein Byte (0 bis 255) oder -1 am Ende. */
    abstract fun read(): Int

    open fun read(b: ByteArray): Int = read(b, 0, b.size)

    open fun read(b: ByteArray, off: Int, len: Int): Int {
        pruefeBereich(b.size, off, len)
        if (len == 0) return 0
        var c = read()
        if (c == -1) return -1
        b[off] = c.toByte()
        var i = 1
        while (i < len) {
            c = read()
            if (c == -1) break
            b[off + i] = c.toByte()
            i++
        }
        return i
    }

    open fun readAllBytes(): ByteArray = readNBytes(Int.MAX_VALUE)

    open fun readNBytes(len: Int): ByteArray {
        require(len >= 0) { "len < 0" }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var rest = len
        while (rest > 0) {
            val n = read(buf, 0, minOf(buf.size, rest))
            if (n < 0) break
            out.write(buf, 0, n)
            rest -= n
        }
        return out.toByteArray()
    }

    open fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
        pruefeBereich(b.size, off, len)
        var n = 0
        while (n < len) {
            val r = read(b, off + n, len - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    open fun skip(n: Long): Long {
        var rest = n
        while (rest > 0) {
            if (read() < 0) break
            rest--
        }
        return n - rest.coerceAtLeast(0)
    }

    open fun skipNBytes(n: Long) {
        if (skip(n) < n) throw EOFException()
    }

    open fun available(): Int = 0

    override fun close() {}

    open fun mark(readlimit: Int) {}

    open fun reset() { throw IOException("mark/reset not supported") }

    open fun markSupported(): Boolean = false

    open fun transferTo(out: OutputStream): Long {
        val buf = ByteArray(8192)
        var summe = 0L
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
            summe += n
        }
        return summe
    }

    /** Wie kotlin.io.readBytes: alles bis zum Ende. */
    fun readBytes(): ByteArray = readAllBytes()

    /** Wie kotlin.io.copyTo. */
    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long {
        val buf = ByteArray(bufferSize)
        var summe = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            summe += n
        }
        return summe
    }

    companion object {
        fun nullInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    }
}

abstract class OutputStream : Closeable, Flushable {

    abstract fun write(b: Int)

    open fun write(b: ByteArray) = write(b, 0, b.size)

    open fun write(b: ByteArray, off: Int, len: Int) {
        pruefeBereich(b.size, off, len)
        for (i in off until off + len) write(b[i].toInt())
    }

    override fun flush() {}

    override fun close() {}

    companion object {
        fun nullOutputStream(): OutputStream = object : OutputStream() {
            override fun write(b: Int) {}
        }
    }
}

open class ByteArrayInputStream(buf: ByteArray, offset: Int = 0, length: Int = buf.size) : InputStream() {
    protected var buf: ByteArray = buf
    protected var pos: Int = offset
    protected var count: Int = minOf(offset + length, buf.size)
    protected var mark: Int = offset

    override fun read(): Int = if (pos < count) buf[pos++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        pruefeBereich(b.size, off, len)
        if (pos >= count) return -1
        val n = minOf(len, count - pos)
        if (n <= 0) return 0
        buf.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }

    override fun readAllBytes(): ByteArray {
        val out = buf.copyOfRange(pos, count)
        pos = count
        return out
    }

    override fun skip(n: Long): Long {
        val k = minOf(n, (count - pos).toLong()).coerceAtLeast(0)
        pos += k.toInt()
        return k
    }

    override fun available(): Int = count - pos

    override fun markSupported(): Boolean = true

    override fun mark(readlimit: Int) { mark = pos }

    override fun reset() { pos = mark }

    override fun transferTo(out: OutputStream): Long {
        val n = count - pos
        out.write(buf, pos, n)
        pos = count
        return n.toLong()
    }
}

open class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
    protected var buf: ByteArray
    protected var count: Int = 0

    init {
        require(size >= 0) { "Negative initial size: $size" }
        buf = ByteArray(size)
    }

    private fun platz(n: Int) {
        val noetig = count + n
        if (noetig > buf.size) buf = buf.copyOf(maxOf(noetig, buf.size * 2, 32))
    }

    override fun write(b: Int) {
        platz(1)
        buf[count++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        pruefeBereich(b.size, off, len)
        platz(len)
        b.copyInto(buf, count, off, off + len)
        count += len
    }

    fun writeBytes(b: ByteArray) = write(b, 0, b.size)

    fun writeTo(out: OutputStream) = out.write(buf, 0, count)

    fun reset() { count = 0 }

    fun toByteArray(): ByteArray = buf.copyOf(count)

    fun size(): Int = count

    /** Inhalt als UTF-8 (Standardzeichensatz von Android und Java 18+). */
    override fun toString(): String = buf.decodeToString(0, count)

    /** Inhalt im genannten Zeichensatz. */
    fun toString(charsetName: String): String =
        java.net.bytesText(toByteArray(), java.nio.charset.Charset.forName(charsetName))
}

private fun pruefeBereich(groesse: Int, off: Int, len: Int) {
    if (off < 0 || len < 0 || len > groesse - off) throw IndexOutOfBoundsException("Range [$off, $off + $len) out of bounds for length $groesse")
}
