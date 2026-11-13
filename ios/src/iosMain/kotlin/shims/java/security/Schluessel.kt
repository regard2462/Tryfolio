package java.security

/** Schluessel mit Bytes in der ueblichen Kodierung (bei oeffentlichen Schluesseln SubjectPublicKeyInfo). */
interface Key {
    val algorithm: String
    val format: String?
    val encoded: ByteArray
}

interface PublicKey : Key

open class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)

open class InvalidAlgorithmParameterException(message: String? = null) : GeneralSecurityException(message)
