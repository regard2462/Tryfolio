package java.security.cert

/** Zertifikat mit oeffentlichem Schluessel; auf iOS bekommt der gemeinsame Code keine Zertifikate zu sehen. */
abstract class Certificate(val type: String) {
    abstract val publicKey: java.security.PublicKey
    abstract val encoded: ByteArray
}

open class CertificateException(message: String? = null) : java.security.GeneralSecurityException(message)
