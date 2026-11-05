package javax.crypto.spec

/** Schluessel aus rohen Bytes. */
class SecretKeySpec(key: ByteArray, override val algorithm: String) : java.security.Key, java.security.spec.KeySpec {
    private val bytes = key.copyOf()
    override val format: String? = "RAW"
    override val encoded: ByteArray get() = bytes.copyOf()
}

/** Startwert fuer CBC. */
class IvParameterSpec(iv: ByteArray) : java.security.spec.AlgorithmParameterSpec {
    val iv: ByteArray = iv.copyOf()
}
