package de.troetenrepublik.app.plattform

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream

// Datei, die beim Schreiben nie halb fertig liegen bleibt (Android: AtomicFile).
class SichereDatei(datei: File) {
    private val atomic = AtomicFile(datei)

    fun lesen(): ByteArray = atomic.readFully()

    fun schreiben(bytes: ByteArray) {
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
