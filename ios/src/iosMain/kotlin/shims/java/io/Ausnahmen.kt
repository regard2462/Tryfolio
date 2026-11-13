package java.io

/** Abruf abgebrochen oder ohne Antwort in der erlaubten Zeit (Oberklasse von java.net.SocketTimeoutException). */
open class InterruptedIOException(message: String? = null) : IOException(message)
