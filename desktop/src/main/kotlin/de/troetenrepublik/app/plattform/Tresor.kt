package de.troetenrepublik.app.plattform

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Geheimnisse wie Broker-Schluessel: AES-GCM mit einem zufaelligen Schluessel in einer Datei, die nur der eigene Benutzer
// lesen darf. Das schuetzt vor dem Mitlesen der Einstellungen und vor Sicherungskopien ohne Schluesseldatei, nicht vor
// Programmen, die unter demselben Benutzer laufen.
object Tresor {
    val verfuegbar: Boolean = true

    const val schutz: String = "datei"

    private val ordner: File get() = File(Plattform.daten, "tresor").apply { mkdirs(); nurIch(this) }
    private val random = SecureRandom()

    private fun nurIch(f: File) {
        runCatching {
            val rechte = if (f.isDirectory) setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            else setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(f.toPath(), rechte)
        }.onFailure {
            // Windows: nur der Besitzer
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
        }
    }

    private fun schluessel(): SecretKeySpec {
        val f = File(ordner, "schluessel")
        if (!f.exists() || f.length() != 32L) {
            val b = ByteArray(32).also { random.nextBytes(it) }
            SichereDatei(f).schreiben(b)
            nurIch(f)
        }
        return SecretKeySpec(f.readBytes(), "AES")
    }

    private fun datei(name: String) = File(ordner, name.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".bin")

    @Synchronized
    fun lesen(name: String): String? = runCatching {
        val f = datei(name)
        if (!f.exists()) return null
        val b = SichereDatei(f).lesen()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        c.doFinal(b.copyOfRange(12, b.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    fun schreiben(name: String, wert: String?) {
        val f = datei(name)
        if (wert == null) { f.delete(); return }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, schluessel(), GCMParameterSpec(128, iv))
        SichereDatei(f).schreiben(iv + c.doFinal(wert.toByteArray(Charsets.UTF_8)))
        nurIch(f)
    }
}
