package javax.crypto

import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import java.security.GeneralSecurityException

/*
 * Ersatz fuer javax.crypto.Cipher mit AES in den Arten, die der gemeinsame Code nutzt (verschluesselte PDFs):
 * AES/CBC/NoPadding, AES/CBC/PKCS5Padding und AES/ECB/... ueber CCCrypt aus CommonCrypto. Daten werden gesammelt
 * und in doFinal in einem Schritt verarbeitet.
 */

open class BadPaddingException(message: String? = null) : GeneralSecurityException(message)

open class IllegalBlockSizeException(message: String? = null) : GeneralSecurityException(message)

open class NoSuchPaddingException(message: String? = null) : GeneralSecurityException(message)

class Cipher private constructor(private val cbc: Boolean, private val auffuellen: Boolean) {
    private var modus = 0
    private var schluessel = ByteArray(0)
    private var iv: ByteArray? = null
    private val puffer = java.io.ByteArrayOutputStream()

    fun init(opmode: Int, key: java.security.Key) = init(opmode, key, null)

    fun init(opmode: Int, key: java.security.Key, params: java.security.spec.AlgorithmParameterSpec?) {
        require(opmode == ENCRYPT_MODE || opmode == DECRYPT_MODE) { "unsupported mode $opmode" }
        val k = key.encoded
        if (k.size != 16 && k.size != 24 && k.size != 32) throw java.security.InvalidKeyException("Invalid AES key length: ${k.size} bytes")
        modus = opmode
        schluessel = k
        iv = (params as? javax.crypto.spec.IvParameterSpec)?.iv
        if (cbc && iv?.size != 16) throw java.security.InvalidAlgorithmParameterException("Wrong IV length: must be 16 bytes long")
        puffer.reset()
    }

    fun update(input: ByteArray): ByteArray {
        puffer.write(input, 0, input.size)
        return ByteArray(0)
    }

    fun doFinal(): ByteArray = abschliessen()

    fun doFinal(input: ByteArray): ByteArray = doFinal(input, 0, input.size)

    fun doFinal(input: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
        puffer.write(input, inputOffset, inputLen)
        return abschliessen()
    }

    private fun abschliessen(): ByteArray {
        val daten = puffer.toByteArray()
        puffer.reset()
        if (!auffuellen && daten.size % 16 != 0) throw IllegalBlockSizeException("Input length not multiple of 16 bytes")
        val aus = ByteArray(daten.size + 16)
        val optionen = (if (auffuellen) kCCOptionPKCS7Padding else 0u) or (if (cbc) 0u else kCCOptionECBMode)
        val ein = if (daten.isEmpty()) ByteArray(1) else daten
        val v = iv ?: ByteArray(16)
        val n = memScoped {
            val bewegt = alloc<ULongVar>()
            val status = ein.usePinned { e ->
                aus.usePinned { a ->
                    schluessel.usePinned { k ->
                        v.usePinned { i ->
                            CCCrypt(
                                if (modus == ENCRYPT_MODE) kCCEncrypt else kCCDecrypt, kCCAlgorithmAES, optionen,
                                k.addressOf(0), schluessel.size.toULong(), if (cbc) i.addressOf(0) else null,
                                e.addressOf(0), daten.size.toULong(), a.addressOf(0), aus.size.toULong(), bewegt.ptr,
                            )
                        }
                    }
                }
            }
            if (status != kCCSuccess) throw BadPaddingException("CCCrypt status $status")
            bewegt.value.toInt()
        }
        return aus.copyOf(n)
    }

    companion object {
        const val ENCRYPT_MODE = 1
        const val DECRYPT_MODE = 2

        fun getInstance(transformation: String): Cipher {
            val t = transformation.uppercase().split('/')
            if (t[0] != "AES") throw java.security.NoSuchAlgorithmException("Cannot find any provider supporting $transformation")
            val art = t.getOrElse(1) { "ECB" }
            val fuellung = t.getOrElse(2) { "PKCS5PADDING" }
            if (art != "CBC" && art != "ECB") throw java.security.NoSuchAlgorithmException("Cannot find any provider supporting $transformation")
            if (fuellung != "NOPADDING" && fuellung != "PKCS5PADDING" && fuellung != "PKCS7PADDING") throw NoSuchPaddingException(transformation)
            return Cipher(art == "CBC", fuellung != "NOPADDING")
        }
    }
}
