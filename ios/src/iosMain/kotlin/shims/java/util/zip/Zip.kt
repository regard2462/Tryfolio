package java.util.zip

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_NEED_DICT
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_BUF_ERROR
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.inflateReset
import platform.zlib.z_stream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/*
 * Ersatz fuer java.util.zip auf iOS: Inflater ueber die zlib des Systems, dazu ZipInputStream und ZipFile fuer
 * Archive mit gespeicherten und mit Deflate gepackten Eintraegen (wie sie Behoerden und Tabellenprogramme liefern).
 */

open class ZipException(message: String? = null) : IOException(message)

open class DataFormatException(message: String? = null) : Exception(message)

open class ZipEntry(val name: String) {
    var size: Long = -1
    var compressedSize: Long = -1
    var time: Long = -1
    var comment: String? = null
    var method: Int = -1
    open val isDirectory: Boolean get() = name.endsWith("/")
    override fun toString(): String = name

    companion object {
        const val STORED = 0
        const val DEFLATED = 8
    }
}

/** Entpacken mit zlib; nowrap = rohes Deflate ohne zlib-Kopf (wie in ZIP-Archiven). */
open class Inflater(nowrap: Boolean = false) {
    private val strom = nativeHeap.alloc<z_stream>()
    private var offen = true
    private var eingabe = ByteArray(0)
    private var position = 0
    private var ende = 0
    private var fertig = false
    private var woerterbuch = false

    init {
        val r = inflateInit2_(strom.ptr, if (nowrap) -15 else 15, ZLIB_VERSION, sizeOf<z_stream>().toInt())
        if (r != Z_OK) {
            nativeHeap.free(strom)
            offen = false
            throw IllegalStateException("zlib init failed: $r")
        }
    }

    fun setInput(input: ByteArray) = setInput(input, 0, input.size)

    fun setInput(input: ByteArray, off: Int, len: Int) {
        eingabe = input
        position = off
        ende = off + len
    }

    fun inflate(b: ByteArray): Int = inflate(b, 0, b.size)

    fun inflate(b: ByteArray, off: Int, len: Int): Int {
        check(offen) { "Inflater has been closed" }
        if (len == 0 || fertig) return 0
        val vorher = ende - position
        var ergebnis = 0
        var geschrieben = 0
        var gelesen = 0
        val leer = ByteArray(1)
        val quelle = if (vorher > 0) eingabe else leer
        quelle.usePinned { ein ->
            b.usePinned { aus ->
                strom.next_in = (if (vorher > 0) ein.addressOf(position) else ein.addressOf(0)).reinterpret<UByteVar>()
                strom.avail_in = vorher.toUInt()
                strom.next_out = aus.addressOf(off).reinterpret<UByteVar>()
                strom.avail_out = len.toUInt()
                ergebnis = inflate(strom.ptr, Z_NO_FLUSH)
                gelesen = vorher - strom.avail_in.toInt()
                geschrieben = len - strom.avail_out.toInt()
                strom.next_in = null
                strom.next_out = null
            }
        }
        position += gelesen
        when (ergebnis) {
            Z_STREAM_END -> fertig = true
            Z_NEED_DICT -> woerterbuch = true
            Z_OK, Z_BUF_ERROR -> {}
            else -> throw DataFormatException("invalid deflate data ($ergebnis)")
        }
        return geschrieben
    }

    fun finished(): Boolean = fertig
    fun needsInput(): Boolean = position >= ende && !fertig
    fun needsDictionary(): Boolean = woerterbuch
    val remaining: Int get() = ende - position
    fun getRemaining(): Int = remaining

    fun reset() {
        if (offen) inflateReset(strom.ptr)
        eingabe = ByteArray(0); position = 0; ende = 0; fertig = false; woerterbuch = false
    }

    fun end() {
        if (!offen) return
        offen = false
        inflateEnd(strom.ptr)
        nativeHeap.free(strom)
    }
}

/** Liest ein ZIP-Archiv Eintrag fuer Eintrag aus dem Speicher (die Quelle wird ganz gelesen). */
open class ZipInputStream(quelle: InputStream) : InputStream() {
    private val daten: ByteArray = quelle.use { it.readAllBytes() }
    private var pos = 0
    private var inhalt: ByteArrayInputStream? = null

    val nextEntry: ZipEntry?
        get() {
            closeEntry()
            if (pos + 30 > daten.size || u32(daten, pos) != 0x04034b50L) return null
            val flags = u16(daten, pos + 6)
            val methode = u16(daten, pos + 8)
            var gepackt = u32(daten, pos + 18).toInt()
            var groesse = u32(daten, pos + 22)
            val nameLaenge = u16(daten, pos + 26)
            val extraLaenge = u16(daten, pos + 28)
            val name = daten.decodeToString(pos + 30, pos + 30 + nameLaenge)
            val start = pos + 30 + nameLaenge + extraLaenge
            val e = ZipEntry(name).also { it.method = methode }
            val bytes: ByteArray
            if (methode == ZipEntry.DEFLATED) {
                // Mit Datenbeschreibung (Bit 3) steht die Groesse erst hinter den Daten; Deflate kennt sein Ende selbst
                val inf = Inflater(true)
                try {
                    inf.setInput(daten, start, if (flags and 8 != 0 || gepackt <= 0) daten.size - start else gepackt)
                    val out = java.io.ByteArrayOutputStream(maxOf(1024, minOf(groesse, 64_000_000L).toInt()))
                    val buf = ByteArray(65_536)
                    while (!inf.finished()) {
                        val n = inf.inflate(buf)
                        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                        out.write(buf, 0, n)
                    }
                    if (flags and 8 != 0 || gepackt <= 0) gepackt = daten.size - start - inf.remaining
                    bytes = out.toByteArray()
                } finally {
                    inf.end()
                }
            } else if (methode == ZipEntry.STORED) {
                if (start + gepackt > daten.size) throw ZipException("truncated entry $name")
                bytes = daten.copyOfRange(start, start + gepackt)
            } else {
                throw ZipException("unsupported compression method $methode")
            }
            groesse = bytes.size.toLong()
            e.size = groesse
            e.compressedSize = gepackt.toLong()
            pos = start + gepackt
            if (flags and 8 != 0) {
                // Datenbeschreibung mit oder ohne Kennung
                pos += if (pos + 4 <= daten.size && u32(daten, pos) == 0x08074b50L) 16 else 12
            }
            inhalt = ByteArrayInputStream(bytes)
            return e
        }

    fun closeEntry() { inhalt = null }

    override fun read(): Int = inhalt?.read() ?: -1
    override fun read(b: ByteArray, off: Int, len: Int): Int = inhalt?.read(b, off, len) ?: -1
    override fun available(): Int = inhalt?.available() ?: 0
}

/** ZIP-Datei mit Inhaltsverzeichnis; die Datei wird ganz in den Speicher gelesen. */
open class ZipFile(datei: File) : Closeable {
    constructor(name: String) : this(File(name))

    private val daten = datei.readBytes()
    private val eintraege = LinkedHashMap<String, Pair<ZipEntry, Int>>()

    init {
        // Ende des Inhaltsverzeichnisses von hinten suchen
        var e = daten.size - 22
        while (e >= 0 && u32(daten, e) != 0x06054b50L) e--
        if (e < 0) throw ZipException("not a zip file")
        val anzahl = u16(daten, e + 10)
        var p = u32(daten, e + 16).toInt()
        repeat(anzahl) {
            if (p + 46 > daten.size || u32(daten, p) != 0x02014b50L) throw ZipException("broken central directory")
            val nameLaenge = u16(daten, p + 28)
            val extra = u16(daten, p + 30)
            val kommentar = u16(daten, p + 32)
            val name = daten.decodeToString(p + 46, p + 46 + nameLaenge)
            val z = ZipEntry(name).apply {
                method = u16(daten, p + 10)
                compressedSize = u32(daten, p + 20)
                size = u32(daten, p + 24)
            }
            eintraege[name] = z to u32(daten, p + 42).toInt()
            p += 46 + nameLaenge + extra + kommentar
        }
    }

    fun entries(): Iterator<ZipEntry> = eintraege.values.map { it.first }.iterator()
    fun getEntry(name: String): ZipEntry? = eintraege[name]?.first
    fun size(): Int = eintraege.size

    fun getInputStream(entry: ZipEntry): InputStream {
        val (z, kopf) = eintraege[entry.name] ?: throw ZipException("no entry ${entry.name}")
        val start = kopf + 30 + u16(daten, kopf + 26) + u16(daten, kopf + 28)
        val gepackt = z.compressedSize.toInt()
        val roh = daten.copyOfRange(start, start + gepackt)
        if (z.method == ZipEntry.STORED) return ByteArrayInputStream(roh)
        val inf = Inflater(true)
        try {
            inf.setInput(roh)
            val out = java.io.ByteArrayOutputStream(maxOf(1024, minOf(z.size, 64_000_000L).toInt()))
            val buf = ByteArray(65_536)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return ByteArrayInputStream(out.toByteArray())
        } finally {
            inf.end()
        }
    }

    override fun close() {}
}

private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

private fun u32(b: ByteArray, i: Int): Long =
    (u16(b, i).toLong()) or (u16(b, i + 2).toLong() shl 16)
