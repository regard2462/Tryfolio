package de.troetenrepublik.app.plattform

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Datei, die beim Schreiben nie halb fertig liegen bleibt: erst in eine Nachbardatei schreiben, auf die Platte
// bringen und dann in einem Schritt umbenennen.
class SichereDatei(private val datei: File) {
    private val neu = File(datei.parentFile, datei.name + ".neu")

    fun lesen(): ByteArray = datei.readBytes()

    @Synchronized
    fun schreiben(bytes: ByteArray) {
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
}
