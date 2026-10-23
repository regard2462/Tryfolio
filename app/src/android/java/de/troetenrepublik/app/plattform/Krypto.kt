package de.troetenrepublik.app.plattform

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Verschluesselung der App-Sperre: AES-256-GCM, der Schluessel entsteht per PBKDF2-HMAC-SHA256 aus PIN oder Passwort.
// Gleichnamiges Gegenstueck fuer Windows und Linux, im Browser gibt es keine App-Sperre.
object Krypto {
    val verfuegbar: Boolean = true

    private val zufallsquelle = SecureRandom()

    fun zufall(n: Int): ByteArray = ByteArray(n).also { zufallsquelle.nextBytes(it) }

    // 32-Byte-Schluessel per PBKDF2-HMAC-SHA256.
    fun ableiten(geheimnis: String, salz: ByteArray, runden: Int): ByteArray {
        val spec = PBEKeySpec(geheimnis.toCharArray(), salz, runden, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // Ausgabe: 12 Byte zufaelliger Startwert, dann Chiffrat und Pruefsumme.
    fun verschluesseln(schluessel: ByteArray, klar: ByteArray): ByteArray {
        val iv = zufall(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(schluessel, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(klar)
    }

    // null bei falschem Schluessel oder veraenderten Daten.
    fun entschluesseln(schluessel: ByteArray, daten: ByteArray): ByteArray? = runCatching {
        if (daten.size < 12 + 16) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(schluessel, "AES"), GCMParameterSpec(128, daten, 0, 12))
        c.doFinal(daten, 12, daten.size - 12)
    }.getOrNull()
}
