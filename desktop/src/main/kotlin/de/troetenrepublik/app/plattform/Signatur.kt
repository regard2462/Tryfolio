package de.troetenrepublik.app.plattform

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Signaturen fuer Broker-Schnittstellen: HMAC und SHA-256 aus der Java-Kryptografie.
object Signatur {
    // HMAC mit algorithmus wie "HmacSHA512" oder "HmacSHA256".
    fun hmac(algorithmus: String, schluessel: ByteArray, daten: ByteArray): ByteArray =
        Mac.getInstance(algorithmus).apply { init(SecretKeySpec(schluessel, algorithmus)) }.doFinal(daten)

    fun sha256(daten: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(daten)

    fun sha512(daten: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-512").digest(daten)

    // Ed25519-Signatur mit dem privaten Schluessel seed (32 Byte), siehe de.troetenrepublik.app.engine.Ed25519.
    fun ed25519(seed: ByteArray, daten: ByteArray): ByteArray = de.troetenrepublik.app.engine.Ed25519.signieren(seed, daten, ::sha512)
}
