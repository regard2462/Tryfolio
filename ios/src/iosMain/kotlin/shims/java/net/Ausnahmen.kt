package java.net

/** Zeitueberschreitung beim Verbinden oder Lesen. */
open class SocketTimeoutException(message: String? = null) : java.io.InterruptedIOException(message)
