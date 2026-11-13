package java.security

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA384
import platform.CoreCrypto.CC_SHA512

/* Ersatz fuer java.security.MessageDigest: MD5, SHA-1 und SHA-256 in reinem Kotlin, SHA-384 und SHA-512 aus CommonCrypto. */

open class GeneralSecurityException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

open class NoSuchAlgorithmException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)

abstract class MessageDigest protected constructor(val algorithm: String) {

    abstract val digestLength: Int

    private val puffer = ByteArray(64)
    private var imPuffer = 0
    private var laenge = 0L

    /** Einen vollen 64-Byte-Block einarbeiten. */
    protected abstract fun block(b: ByteArray, off: Int)

    /** Zustand auf den Anfang. */
    protected abstract fun anfang()

    /** Ergebnis aus dem Zustand. */
    protected abstract fun ergebnis(): ByteArray

    /** MD5 haengt die Laenge little-endian an, SHA big-endian. */
    protected abstract val laengeKleinZuerst: Boolean

    open fun update(input: Byte) {
        puffer[imPuffer++] = input
        laenge++
        if (imPuffer == 64) { block(puffer, 0); imPuffer = 0 }
    }

    fun update(input: ByteArray) = update(input, 0, input.size)

    open fun update(input: ByteArray, offset: Int, len: Int) {
        if (offset < 0 || len < 0 || offset + len > input.size) throw IllegalArgumentException("Bad arguments")
        var i = offset
        val ende = offset + len
        laenge += len
        if (imPuffer > 0) {
            while (i < ende && imPuffer < 64) puffer[imPuffer++] = input[i++]
            if (imPuffer == 64) { block(puffer, 0); imPuffer = 0 }
        }
        while (ende - i >= 64) { block(input, i); i += 64 }
        while (i < ende) puffer[imPuffer++] = input[i++]
    }

    open fun digest(): ByteArray {
        val bits = laenge * 8
        val rest = ByteArray(if (imPuffer < 56) 64 - imPuffer else 128 - imPuffer)
        rest[0] = 0x80.toByte()
        for (k in 0 until 8) {
            val wert = (bits ushr (8 * k)).toByte()
            if (laengeKleinZuerst) rest[rest.size - 8 + k] = wert else rest[rest.size - 1 - k] = wert
        }
        val gesamt = laenge
        update(rest)
        laenge = gesamt
        val out = ergebnis()
        reset()
        return out
    }

    fun digest(input: ByteArray): ByteArray {
        update(input)
        return digest()
    }

    open fun reset() {
        imPuffer = 0
        laenge = 0
        anfang()
    }

    override fun toString(): String = "$algorithm Message Digest from Kotlin/Native"

    companion object {
        fun getInstance(algorithm: String): MessageDigest = when (algorithm.uppercase()) {
            "MD5" -> Md5()
            "SHA-1", "SHA1", "SHA" -> Sha1()
            "SHA-256", "SHA256" -> Sha256()
            "SHA-384", "SHA384" -> SystemHash("SHA-384", 48)
            "SHA-512", "SHA512" -> SystemHash("SHA-512", 64)
            else -> throw NoSuchAlgorithmException("$algorithm MessageDigest not available")
        }

        fun getInstance(algorithm: String, provider: String): MessageDigest = getInstance(algorithm)

        fun isEqual(digesta: ByteArray?, digestb: ByteArray?): Boolean {
            if (digesta === digestb) return true
            if (digesta == null || digestb == null || digesta.size != digestb.size) return false
            var r = 0
            for (i in digesta.indices) r = r or (digesta[i].toInt() xor digestb[i].toInt())
            return r == 0
        }
    }
}

private class Md5 : MessageDigest("MD5") {
    override val digestLength = 16
    override val laengeKleinZuerst = true
    private val h = IntArray(4)
    private val x = IntArray(16)

    init { anfang() }

    override fun anfang() {
        h[0] = 0x67452301; h[1] = -0x10325477; h[2] = -0x67452302; h[3] = 0x10325476
    }

    override fun block(b: ByteArray, off: Int) {
        for (i in 0 until 16) {
            val o = off + i * 4
            x[i] = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
        }
        var a = h[0]; var bb = h[1]; var c = h[2]; var d = h[3]
        for (i in 0 until 64) {
            val f: Int
            val g: Int
            when (i / 16) {
                0 -> { f = (bb and c) or (bb.inv() and d); g = i }
                1 -> { f = (d and bb) or (d.inv() and c); g = (5 * i + 1) % 16 }
                2 -> { f = bb xor c xor d; g = (3 * i + 5) % 16 }
                else -> { f = c xor (bb or d.inv()); g = (7 * i) % 16 }
            }
            val t = d
            d = c
            c = bb
            bb += (a + f + K[i] + x[g]).rotateLeft(S[i])
            a = t
        }
        h[0] += a; h[1] += bb; h[2] += c; h[3] += d
    }

    override fun ergebnis(): ByteArray = ByteArray(16) { i -> (h[i / 4] ushr (8 * (i % 4))).toByte() }

    companion object {
        private val S = intArrayOf(
            7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
        )
        /** K[i] = floor(|sin(i + 1)| * 2^32) */
        private val K = IntArray(64) { i -> (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt() }
    }
}

private class Sha1 : MessageDigest("SHA-1") {
    override val digestLength = 20
    override val laengeKleinZuerst = false
    private val h = IntArray(5)
    private val w = IntArray(80)

    init { anfang() }

    override fun anfang() {
        h[0] = 0x67452301; h[1] = -0x10325477; h[2] = -0x67452302; h[3] = 0x10325476; h[4] = -0x3c2d1e10
    }

    override fun block(b: ByteArray, off: Int) {
        for (i in 0 until 16) w[i] = grossInt(b, off + i * 4)
        for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
        var a = h[0]; var bb = h[1]; var c = h[2]; var d = h[3]; var e = h[4]
        for (i in 0 until 80) {
            val (f, k) = when (i / 20) {
                0 -> ((bb and c) or (bb.inv() and d)) to 0x5A827999
                1 -> (bb xor c xor d) to 0x6ED9EBA1
                2 -> ((bb and c) or (bb and d) or (c and d)) to -0x70e44324
                else -> (bb xor c xor d) to -0x359d3e2a
            }
            val t = a.rotateLeft(5) + f + e + k + w[i]
            e = d; d = c; c = bb.rotateLeft(30); bb = a; a = t
        }
        h[0] += a; h[1] += bb; h[2] += c; h[3] += d; h[4] += e
    }

    override fun ergebnis(): ByteArray = grossBytes(h)
}

private class Sha256 : MessageDigest("SHA-256") {
    override val digestLength = 32
    override val laengeKleinZuerst = false
    private val h = IntArray(8)
    private val w = IntArray(64)

    init { anfang() }

    override fun anfang() {
        h[0] = 0x6a09e667; h[1] = -0x4498517b; h[2] = 0x3c6ef372; h[3] = -0x5ab00ac6
        h[4] = 0x510e527f; h[5] = -0x64fa9774; h[6] = 0x1f83d9ab; h[7] = 0x5be0cd19
    }

    override fun block(b: ByteArray, off: Int) {
        for (i in 0 until 16) w[i] = grossInt(b, off + i * 4)
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h[0]; var bb = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and bb) xor (a and c) xor (bb and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1; d = c; c = bb; bb = a; a = t1 + t2
        }
        h[0] += a; h[1] += bb; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    override fun ergebnis(): ByteArray = grossBytes(h)

    companion object {
        private val K = longArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
        ).map { it.toInt() }.toIntArray()
    }
}

private fun grossInt(b: ByteArray, o: Int): Int =
    ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

private fun grossBytes(h: IntArray): ByteArray = ByteArray(h.size * 4) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }

/** SHA-384 und SHA-512 aus CommonCrypto: Die Eingabe wird gesammelt und am Ende in einem Schritt gerechnet. */
private class SystemHash(name: String, override val digestLength: Int) : MessageDigest(name) {
    private val gesammelt = java.io.ByteArrayOutputStream()
    override val laengeKleinZuerst: Boolean = false
    override fun block(b: ByteArray, off: Int) {}
    override fun anfang() {}
    override fun ergebnis(): ByteArray = ByteArray(0)
    override fun update(input: Byte) = gesammelt.write(input.toInt())
    override fun update(input: ByteArray, offset: Int, len: Int) {
        if (offset < 0 || len < 0 || offset + len > input.size) throw IllegalArgumentException("Bad arguments")
        gesammelt.write(input, offset, len)
    }
    override fun reset() = gesammelt.reset()
    override fun digest(): ByteArray {
        val d = gesammelt.toByteArray().let { if (it.isEmpty()) ByteArray(1) else it }
        val n = gesammelt.size()
        val aus = ByteArray(digestLength)
        d.usePinned { ein ->
            aus.usePinned { ziel ->
                val z = ziel.addressOf(0).reinterpret<UByteVar>()
                if (digestLength == 48) CC_SHA384(ein.addressOf(0), n.toUInt(), z) else CC_SHA512(ein.addressOf(0), n.toUInt(), z)
            }
        }
        reset()
        return aus
    }
}
