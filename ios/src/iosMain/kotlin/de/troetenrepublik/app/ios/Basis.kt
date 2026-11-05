package de.troetenrepublik.app.ios

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970

// Kleine Bausteine, die die Ersatzklassen fuer java.* und die erzeugten Jvm.kt-Dateien auf iOS brauchen.

/** Jetzt in Millisekunden seit 1970. */
fun jetztMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

/** Monotone Uhr in Nanosekunden (fuer Zeitmessungen, wie System.nanoTime). */
fun uhrNs(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()

/** Dispatchers.IO aus kotlinx.coroutines; auf Kotlin/Native nur ueber den Import erreichbar. */
val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

/**
 * Sperre fuer synchronized-Bloecke des gemeinsamen Codes. Auf der JVM sperrt jeder Block sein eigenes Objekt; hier gibt
 * es eine gemeinsame, wiedereintrittsfaehige Sperre. Das ist grober, kann aber keine neue Verklemmung erzeugen.
 */
object Sperre {
    @PublishedApi
    internal val sperre = reentrantLock()

    inline fun <R> mit(block: () -> R): R = sperre.withLock(block)
}
