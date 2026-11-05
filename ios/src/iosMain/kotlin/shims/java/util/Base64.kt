package java.util

import kotlin.io.encoding.ExperimentalEncodingApi

/* Ersatz fuer java.util.Base64 ueber kotlin.io.encoding.Base64 (normal, URL-sicher und MIME mit Zeilenumbruechen). */
@OptIn(ExperimentalEncodingApi::class)
object Base64 {
    class Decoder internal constructor(private val art: kotlin.io.encoding.Base64, private val mime: Boolean) {
        fun decode(src: String): ByteArray = decode(src.encodeToByteArray())
        fun decode(src: ByteArray): ByteArray {
            val text = src.decodeToString().let { s -> if (mime) s.filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' } else s }
            return art.decode(text)
        }
    }

    class Encoder internal constructor(private val art: kotlin.io.encoding.Base64, private val auffuellen: Boolean) {
        fun encodeToString(src: ByteArray): String = art.encode(src).let { if (auffuellen) it else it.trimEnd('=') }
        fun encode(src: ByteArray): ByteArray = encodeToString(src).encodeToByteArray()
        fun withoutPadding(): Encoder = Encoder(art, false)
    }

    private val normal = kotlin.io.encoding.Base64.Default.withPadding(kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL)
    private val url = kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL)

    fun getDecoder(): Decoder = Decoder(normal, false)
    fun getUrlDecoder(): Decoder = Decoder(url, false)
    fun getMimeDecoder(): Decoder = Decoder(normal, true)
    fun getEncoder(): Encoder = Encoder(kotlin.io.encoding.Base64.Default, true)
    fun getUrlEncoder(): Encoder = Encoder(kotlin.io.encoding.Base64.UrlSafe, true)
    fun getMimeEncoder(): Encoder = Encoder(kotlin.io.encoding.Base64.Mime, true)
}
