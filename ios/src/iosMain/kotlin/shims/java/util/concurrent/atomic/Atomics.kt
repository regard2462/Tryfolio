package java.util.concurrent.atomic

import de.troetenrepublik.app.ios.Sperre

/* Ersatz fuer die Atomic-Klassen: jeder Zugriff unter der gemeinsamen Sperre, damit mehrere Threads sicher sind. */

class AtomicBoolean(initialValue: Boolean = false) {
    private var wert = initialValue
    fun get(): Boolean = Sperre.mit { wert }
    fun set(newValue: Boolean) = Sperre.mit { wert = newValue }
    fun lazySet(newValue: Boolean) = set(newValue)
    fun getAndSet(newValue: Boolean): Boolean = Sperre.mit { val alt = wert; wert = newValue; alt }
    fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean = Sperre.mit {
        if (wert != expectedValue) false else { wert = newValue; true }
    }
    fun weakCompareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean = compareAndSet(expectedValue, newValue)
    override fun toString(): String = get().toString()
}

class AtomicInteger(initialValue: Int = 0) : Number() {
    private var wert = initialValue
    fun get(): Int = Sperre.mit { wert }
    fun set(newValue: Int) = Sperre.mit { wert = newValue }
    fun lazySet(newValue: Int) = set(newValue)
    fun getAndSet(newValue: Int): Int = Sperre.mit { val alt = wert; wert = newValue; alt }
    fun compareAndSet(expectedValue: Int, newValue: Int): Boolean = Sperre.mit {
        if (wert != expectedValue) false else { wert = newValue; true }
    }
    fun getAndIncrement(): Int = Sperre.mit { wert++ }
    fun getAndDecrement(): Int = Sperre.mit { wert-- }
    fun incrementAndGet(): Int = Sperre.mit { ++wert }
    fun decrementAndGet(): Int = Sperre.mit { --wert }
    fun getAndAdd(delta: Int): Int = Sperre.mit { val alt = wert; wert += delta; alt }
    fun addAndGet(delta: Int): Int = Sperre.mit { wert += delta; wert }
    fun getAndUpdate(f: (Int) -> Int): Int = Sperre.mit { val alt = wert; wert = f(alt); alt }
    fun updateAndGet(f: (Int) -> Int): Int = Sperre.mit { wert = f(wert); wert }
    fun getAndAccumulate(x: Int, f: (Int, Int) -> Int): Int = Sperre.mit { val alt = wert; wert = f(alt, x); alt }
    fun accumulateAndGet(x: Int, f: (Int, Int) -> Int): Int = Sperre.mit { wert = f(wert, x); wert }
    override fun toByte(): Byte = get().toByte()
    override fun toShort(): Short = get().toShort()
    override fun toInt(): Int = get()
    override fun toLong(): Long = get().toLong()
    override fun toFloat(): Float = get().toFloat()
    override fun toDouble(): Double = get().toDouble()
    override fun toString(): String = get().toString()
}

class AtomicLong(initialValue: Long = 0L) : Number() {
    private var wert = initialValue
    fun get(): Long = Sperre.mit { wert }
    fun set(newValue: Long) = Sperre.mit { wert = newValue }
    fun lazySet(newValue: Long) = set(newValue)
    fun getAndSet(newValue: Long): Long = Sperre.mit { val alt = wert; wert = newValue; alt }
    fun compareAndSet(expectedValue: Long, newValue: Long): Boolean = Sperre.mit {
        if (wert != expectedValue) false else { wert = newValue; true }
    }
    fun getAndIncrement(): Long = Sperre.mit { wert++ }
    fun getAndDecrement(): Long = Sperre.mit { wert-- }
    fun incrementAndGet(): Long = Sperre.mit { ++wert }
    fun decrementAndGet(): Long = Sperre.mit { --wert }
    fun getAndAdd(delta: Long): Long = Sperre.mit { val alt = wert; wert += delta; alt }
    fun addAndGet(delta: Long): Long = Sperre.mit { wert += delta; wert }
    fun getAndUpdate(f: (Long) -> Long): Long = Sperre.mit { val alt = wert; wert = f(alt); alt }
    fun updateAndGet(f: (Long) -> Long): Long = Sperre.mit { wert = f(wert); wert }
    fun getAndAccumulate(x: Long, f: (Long, Long) -> Long): Long = Sperre.mit { val alt = wert; wert = f(alt, x); alt }
    fun accumulateAndGet(x: Long, f: (Long, Long) -> Long): Long = Sperre.mit { wert = f(wert, x); wert }
    override fun toByte(): Byte = get().toByte()
    override fun toShort(): Short = get().toShort()
    override fun toInt(): Int = get().toInt()
    override fun toLong(): Long = get()
    override fun toFloat(): Float = get().toFloat()
    override fun toDouble(): Double = get().toDouble()
    override fun toString(): String = get().toString()
}

class AtomicReference<V>(initialValue: V) {
    private var wert: V = initialValue
    fun get(): V = Sperre.mit { wert }
    fun set(newValue: V) = Sperre.mit { wert = newValue }
    fun lazySet(newValue: V) = set(newValue)
    fun getAndSet(newValue: V): V = Sperre.mit { val alt = wert; wert = newValue; alt }
    /** Wie in Java: Vergleich der Identitaet. */
    fun compareAndSet(expectedValue: V, newValue: V): Boolean = Sperre.mit {
        if (wert !== expectedValue) false else { wert = newValue; true }
    }
    fun getAndUpdate(f: (V) -> V): V = Sperre.mit { val alt = wert; wert = f(alt); alt }
    fun updateAndGet(f: (V) -> V): V = Sperre.mit { wert = f(wert); wert }
    override fun toString(): String = get().toString()
}
