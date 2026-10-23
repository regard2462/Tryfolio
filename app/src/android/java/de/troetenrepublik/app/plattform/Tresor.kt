package de.troetenrepublik.app.plattform

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Geheimnisse wie Broker-Schluessel: AES-GCM mit einem Schluessel im Android-Keystore, der das Geraet nicht
// verlaesst. Die Dateien liegen im privaten Ordner der App, der nicht gesichert wird (allowBackup=false).
// Gegenstueck fuer Windows und Linux: Tresor.kt im Modul desktop.
object Tresor {
    val verfuegbar: Boolean = true

    // Art des Schutzes, fuer den Hinweis in der App.
    const val schutz: String = "keystore"

    private const val ALIAS = "troete_tresor"
    private val ordner: File get() = File(Plattform.kontext.filesDir, "tresor").apply { mkdirs() }

    private fun schluessel(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        fun erzeugen(nurEntsperrt: Boolean): SecretKey {
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // Nur nutzbar, solange das Geraet entsperrt ist (ab Android 9)
                    .apply { if (nurEntsperrt && Build.VERSION.SDK_INT >= 28) setUnlockedDeviceRequired(true) }
                    .build(),
            )
            return g.generateKey()
        }
        // Bindung an die Entsperrung nur mit Bildschirmsperre; manche Geraete lehnen sie ab, dann ohne
        val gesperrt = runCatching { (Plattform.kontext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure }.getOrDefault(false)
        return if (gesperrt) runCatching { erzeugen(true) }.getOrElse { erzeugen(false) } else erzeugen(false)
    }

    private fun datei(name: String) = File(ordner, name.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".bin")

    @Synchronized
    fun lesen(name: String): String? = runCatching {
        val f = datei(name)
        if (!f.exists()) return null
        val b = SichereDatei(f).lesen()
        val n = b[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, b.copyOfRange(1, 1 + n)))
        c.doFinal(b.copyOfRange(1 + n, b.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    // null loescht den Eintrag.
    @Synchronized
    fun schreiben(name: String, wert: String?) {
        val f = datei(name)
        if (wert == null) { f.delete(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, schluessel())
        val iv = c.iv
        SichereDatei(f).schreiben(byteArrayOf(iv.size.toByte()) + iv + c.doFinal(wert.toByteArray(Charsets.UTF_8)))
    }
}
