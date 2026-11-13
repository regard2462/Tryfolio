package de.troetenrepublik.app.plattform

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

// Datei, die beim Schreiben nie halb fertig liegen bleibt: erst in eine Nachbardatei schreiben, auf die Platte
// bringen und dann in einem Schritt umbenennen. [0.71] Wie auf dem Desktop mit den Java-Mitteln statt AtomicFile,
// damit derselbe Code auch in den Einheiten-Test laeuft.
class SichereDatei(private val datei: File) {
    private val neu = File(datei.parentFile, datei.name + ".neu")

    // Sperre je Dateipfad (nicht je Instanz): Aufrufer legen fuer dieselbe Datei laufend neue Instanzen an und
    // duerfen sich die Nachbardatei trotzdem nicht teilen.
    private val sperre: Any = sperren.computeIfAbsent(pfad(datei)) { Any() }

    fun lesen(): ByteArray = synchronized(sperre) { datei.readBytes() }

    fun schreiben(bytes: ByteArray) {
        synchronized(sperre) { schreibenOhneSperre(bytes) }
    }

    private fun schreibenOhneSperre(bytes: ByteArray) {
        datei.parentFile?.mkdirs()
        FileOutputStream(neu).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(neu.toPath(), datei.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(neu.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        val sperren = ConcurrentHashMap<String, Any>()

        fun pfad(f: File): String = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
    }
}
