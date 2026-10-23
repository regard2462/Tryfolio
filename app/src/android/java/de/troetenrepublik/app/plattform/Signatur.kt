package de.troetenrepublik.app.plattform

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// HMAC und SHA-256 fuer Broker-Schnittstellen. Das Gegenstueck im Modul web kommt ohne Broker aus.
object Signatur {
    // algorithmus als JCA-Name, etwa HmacSHA256.
    fun hmac(algorithmus: String, schluessel: ByteArray, daten: ByteArray): ByteArray =
        Mac.getInstance(algorithmus).apply { init(SecretKeySpec(schluessel, algorithmus)) }.doFinal(daten)

    fun sha256(daten: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(daten)

    fun sha512(daten: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-512").digest(daten)

    // seed ist der private Schluessel mit 32 Byte.
    fun ed25519(seed: ByteArray, daten: ByteArray): ByteArray = de.troetenrepublik.app.engine.Ed25519.signieren(seed, daten, ::sha512)
}
