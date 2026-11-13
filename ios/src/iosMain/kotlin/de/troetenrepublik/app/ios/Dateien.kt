package de.troetenrepublik.app.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.memcpy
import platform.posix.stat

/** Dateizugriffe fuer den Ersatz von java.io.File, ueber POSIX und NSFileManager im Sandkasten der App. */
object Dateien {
    private val fm get() = NSFileManager.defaultManager

    private fun art(pfad: String): Int = memScoped {
        val s = alloc<stat>()
        if (stat(pfad, s.ptr) != 0) -1 else (s.st_mode.toInt() and S_IFMT.toInt())
    }

    fun existiert(pfad: String): Boolean = art(pfad) >= 0
    fun istDatei(pfad: String): Boolean = art(pfad) == S_IFREG.toInt()
    fun istOrdner(pfad: String): Boolean = art(pfad) == S_IFDIR.toInt()

    fun groesse(pfad: String): Long = memScoped {
        val s = alloc<stat>()
        if (stat(pfad, s.ptr) != 0) 0L else s.st_size
    }

    /** Letzte Aenderung in Millisekunden seit 1970, 0 wenn die Datei fehlt. */
    fun zeit(pfad: String): Long = memScoped {
        val s = alloc<stat>()
        if (stat(pfad, s.ptr) != 0) 0L else s.st_mtimespec.tv_sec * 1000L + s.st_mtimespec.tv_nsec / 1_000_000L
    }

    fun zeitSetzen(pfad: String, ms: Long): Boolean =
        fm.setAttributes(mapOf<Any?, Any?>(NSFileModificationDate to NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)), pfad, null)

    fun ordnerMachen(pfad: String): Boolean =
        istOrdner(pfad) || fm.createDirectoryAtPath(pfad, withIntermediateDirectories = true, attributes = null, error = null)

    fun loeschen(pfad: String): Boolean = existiert(pfad) && (istDatei(pfad) || (kinder(pfad).isEmpty())) && fm.removeItemAtPath(pfad, null)

    /** Wie File.renameTo: ein vorhandenes Ziel wird ersetzt (POSIX rename). */
    fun umbenennen(von: String, nach: String): Boolean = platform.posix.rename(von, nach) == 0

    fun kinder(pfad: String): List<String> =
        fm.contentsOfDirectoryAtPath(pfad, null)?.mapNotNull { it as? String }?.sorted() ?: emptyList()

    fun lesen(pfad: String): ByteArray? {
        if (!istDatei(pfad)) return null
        val d = NSData.dataWithContentsOfFile(pfad) ?: return null
        return d.alsBytes()
    }

    /** Schreibt in eine Nachbardatei und benennt dann um; halbe Dateien bleiben nie liegen. */
    fun schreiben(pfad: String, bytes: ByteArray): Boolean = bytes.alsNSData().writeToFile(pfad, atomically = true)
}

/** NSData als ByteArray (Kopie). */
fun NSData.alsBytes(): ByteArray {
    val n = length.toInt()
    val b = ByteArray(n)
    if (n > 0) b.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return b
}

/** ByteArray als NSData (Kopie). */
fun ByteArray.alsNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
