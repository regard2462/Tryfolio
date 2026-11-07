package de.troetenrepublik.app.plattform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

// Auf iOS ohne Verschluesselung der App-Sperre (AES-256-GCM, PBKDF2); die App zeigt den Hinweis, dass die Sperre
// nur in den Apps fuer Android und Desktop geht. Nicht geprueftes AES-GCM soll keine Depots unlesbar machen.
object Krypto {
    val verfuegbar: Boolean = false

    // Zufall aus dem Zufallsgenerator des Systems.
    fun zufall(n: Int): ByteArray = ByteArray(n).also { b ->
        if (n > 0) b.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, n.toULong(), it.addressOf(0)) == 0) { "no random" } }
    }

    fun ableiten(geheimnis: String, salz: ByteArray, runden: Int): ByteArray = throw UnsupportedOperationException("app lock is not available on iOS yet")

    fun verschluesseln(schluessel: ByteArray, klar: ByteArray): ByteArray = throw UnsupportedOperationException("app lock is not available on iOS yet")

    fun entschluesseln(schluessel: ByteArray, daten: ByteArray): ByteArray? = null
}

// Auf iOS ohne Tresor (Schluesselbund nicht angebunden).
object Tresor {
    val verfuegbar: Boolean = false

    const val schutz: String = "keiner"

    fun lesen(name: String): String? = null

    fun schreiben(name: String, wert: String?) {}
}
