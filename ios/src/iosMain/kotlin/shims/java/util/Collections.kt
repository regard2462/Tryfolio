package java.util

/* Ersatz fuer java.util.Collections, java.util.Arrays und java.util.Date (nur, was die App braucht, und Verwandtes). */

object Collections {
    /** Jeder Zugriff unter der gemeinsamen Sperre; Durchlaeufe muss der Aufrufer wie in Java selbst sperren. */
    fun <T> synchronizedList(list: MutableList<T>): MutableList<T> = GesperrteListe(list)
    fun <K, V> synchronizedMap(m: MutableMap<K, V>): MutableMap<K, V> = GesperrteMap(m)
    fun <T> synchronizedSet(s: MutableSet<T>): MutableSet<T> = GesperrteMenge(s)
    fun <T> synchronizedCollection(c: MutableCollection<T>): MutableCollection<T> = GesperrteMenge(c.toMutableSet())

    fun <T> unmodifiableList(list: List<T>): List<T> = list.toList()
    fun <K, V> unmodifiableMap(m: Map<out K, V>): Map<K, V> = m.toMap()
    fun <T> unmodifiableSet(s: Set<T>): Set<T> = s.toSet()

    fun <T> emptyList(): List<T> = kotlin.collections.emptyList()
    fun <K, V> emptyMap(): Map<K, V> = kotlin.collections.emptyMap()
    fun <T> emptySet(): Set<T> = kotlin.collections.emptySet()
    fun <T> singletonList(o: T): List<T> = listOf(o)
    fun <T> singleton(o: T): Set<T> = setOf(o)
    fun <K, V> singletonMap(key: K, value: V): Map<K, V> = mapOf(key to value)
    fun <T> nCopies(n: Int, o: T): List<T> = List(n) { o }

    fun <T : Comparable<T>> sort(list: MutableList<T>) = list.sort()
    fun <T> sort(list: MutableList<T>, c: Comparator<in T>?) {
        if (c == null) {
            @Suppress("UNCHECKED_CAST")
            (list as MutableList<Comparable<Any?>>).sortWith { a, b -> a.compareTo(b) }
        } else list.sortWith(c)
    }
    fun reverse(list: MutableList<*>) = list.reverse()
    fun swap(list: MutableList<*>, i: Int, j: Int) {
        @Suppress("UNCHECKED_CAST")
        val l = list as MutableList<Any?>
        val t = l[i]; l[i] = l[j]; l[j] = t
    }
    fun <T> reverseOrder(): Comparator<T> = Comparator { a, b ->
        @Suppress("UNCHECKED_CAST")
        (b as Comparable<Any?>).compareTo(a)
    }
    fun <T> reverseOrder(cmp: Comparator<T>?): Comparator<T> = if (cmp == null) reverseOrder() else Comparator { a, b -> cmp.compare(b, a) }
    fun frequency(c: Collection<*>, o: Any?): Int = c.count { it == o }
    fun <T> addAll(c: MutableCollection<in T>, vararg elements: T): Boolean {
        var geaendert = false
        for (e in elements) if (c.add(e)) geaendert = true
        return geaendert
    }
}

object Arrays {
    private fun pruefe(n: Int, von: Int, bis: Int) {
        if (von > bis) throw IllegalArgumentException("fromIndex($von) > toIndex($bis)")
        if (von < 0 || bis > n) throw IndexOutOfBoundsException("Array index out of range: ${if (von < 0) von else bis}")
    }

    /** Index des Schluessels oder -(Einfuegestelle) - 1; das Feld muss aufsteigend sortiert sein. */
    fun binarySearch(a: LongArray, key: Long): Int = binarySearch(a, 0, a.size, key)

    fun binarySearch(a: LongArray, fromIndex: Int, toIndex: Int, key: Long): Int {
        pruefe(a.size, fromIndex, toIndex)
        var lo = fromIndex
        var hi = toIndex - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = a[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun binarySearch(a: IntArray, key: Int): Int = binarySearch(a, 0, a.size, key)

    fun binarySearch(a: IntArray, fromIndex: Int, toIndex: Int, key: Int): Int {
        pruefe(a.size, fromIndex, toIndex)
        var lo = fromIndex
        var hi = toIndex - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = a[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun binarySearch(a: DoubleArray, key: Double): Int = binarySearch(a, 0, a.size, key)

    /** Wie Java: NaN gilt als groesser als alles, -0.0 als kleiner als 0.0. */
    fun binarySearch(a: DoubleArray, fromIndex: Int, toIndex: Int, key: Double): Int {
        pruefe(a.size, fromIndex, toIndex)
        var lo = fromIndex
        var hi = toIndex - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = a[mid]
            val c = when {
                v < key -> -1
                v > key -> 1
                else -> {
                    val x = v.toBits()
                    val y = key.toBits()
                    if (x == y) 0 else if (x < y) -1 else 1
                }
            }
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun <T> binarySearch(a: Array<out T>, key: T, c: Comparator<in T>?): Int {
        var lo = 0
        var hi = a.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            @Suppress("UNCHECKED_CAST")
            val r = c?.compare(a[mid], key) ?: (a[mid] as Comparable<Any?>).compareTo(key)
            when {
                r < 0 -> lo = mid + 1
                r > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun binarySearch(a: Array<out Any?>, key: Any?): Int = binarySearch(a, key, null)

    fun <T> asList(vararg a: T): List<T> = a.asList()

    fun fill(a: LongArray, v: Long) = a.fill(v)
    fun fill(a: IntArray, v: Int) = a.fill(v)
    fun fill(a: DoubleArray, v: Double) = a.fill(v)
    fun fill(a: ByteArray, v: Byte) = a.fill(v)
    fun fill(a: BooleanArray, v: Boolean) = a.fill(v)
    fun fill(a: CharArray, v: Char) = a.fill(v)
    fun <T> fill(a: Array<T>, v: T) = a.fill(v)

    fun sort(a: LongArray) = a.sort()
    fun sort(a: IntArray) = a.sort()
    fun sort(a: DoubleArray) = a.sort()

    fun equals(a: ByteArray?, b: ByteArray?): Boolean = a.contentEquals(b)
    fun equals(a: IntArray?, b: IntArray?): Boolean = a.contentEquals(b)
    fun equals(a: LongArray?, b: LongArray?): Boolean = a.contentEquals(b)
    fun hashCode(a: ByteArray?): Int = a.contentHashCode()
    fun toString(a: LongArray?): String = a?.joinToString(", ", "[", "]") ?: "null"
    fun toString(a: IntArray?): String = a?.joinToString(", ", "[", "]") ?: "null"
    fun toString(a: DoubleArray?): String = a?.joinToString(", ", "[", "]") ?: "null"
}

/** Ersatz fuer java.util.Date: Zeitpunkt in Millisekunden seit 1970 (UTC). */
class Date(var time: Long) : Comparable<Date> {
    constructor() : this(jsJetztMs().toLong())

    fun before(other: Date): Boolean = time < other.time
    fun after(other: Date): Boolean = time > other.time
    fun toInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(time)
    override fun compareTo(other: Date): Int = time.compareTo(other.time)
    override fun equals(other: Any?): Boolean = other is Date && other.time == time
    override fun hashCode(): Int = (time xor (time ushr 32)).toInt()

    /** Wie Java: "Thu Oct 15 14:00:00 GMT+02:00 2026" (Zone als Versatz der Standardzone). */
    override fun toString(): String {
        val z = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault())
        val tag = z.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
        val monat = z.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
        fun zwei(n: Int) = n.toString().padStart(2, '0')
        val off = z.offset.totalSeconds
        val zone = if (off == 0) "GMT" else "GMT" + (if (off < 0) "-" else "+") + zwei(kotlin.math.abs(off) / 3600) + ":" + zwei(kotlin.math.abs(off) / 60 % 60)
        return "$tag $monat ${zwei(z.dayOfMonth)} ${zwei(z.hour)}:${zwei(z.minute)}:${zwei(z.second)} $zone ${z.year}"
    }

    companion object {
        fun from(instant: java.time.Instant): Date = Date(instant.toEpochMilli())
    }
}

private fun jsJetztMs(): Double = de.troetenrepublik.app.ios.jetztMs().toDouble()

private inline fun <R> gesperrt(block: () -> R): R = de.troetenrepublik.app.ios.Sperre.mit(block)

private class GesperrteListe<T>(private val l: MutableList<T>) : MutableList<T> {
    override val size: Int get() = gesperrt { l.size }
    override fun contains(element: T): Boolean = gesperrt { l.contains(element) }
    override fun containsAll(elements: Collection<T>): Boolean = gesperrt { l.containsAll(elements) }
    override fun get(index: Int): T = gesperrt { l[index] }
    override fun indexOf(element: T): Int = gesperrt { l.indexOf(element) }
    override fun isEmpty(): Boolean = gesperrt { l.isEmpty() }
    override fun iterator(): MutableIterator<T> = l.iterator()
    override fun lastIndexOf(element: T): Int = gesperrt { l.lastIndexOf(element) }
    override fun add(element: T): Boolean = gesperrt { l.add(element) }
    override fun add(index: Int, element: T) = gesperrt { l.add(index, element) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = gesperrt { l.addAll(index, elements) }
    override fun addAll(elements: Collection<T>): Boolean = gesperrt { l.addAll(elements) }
    override fun clear() = gesperrt { l.clear() }
    override fun listIterator(): MutableListIterator<T> = l.listIterator()
    override fun listIterator(index: Int): MutableListIterator<T> = l.listIterator(index)
    override fun remove(element: T): Boolean = gesperrt { l.remove(element) }
    override fun removeAll(elements: Collection<T>): Boolean = gesperrt { l.removeAll(elements) }
    override fun removeAt(index: Int): T = gesperrt { l.removeAt(index) }
    override fun retainAll(elements: Collection<T>): Boolean = gesperrt { l.retainAll(elements) }
    override fun set(index: Int, element: T): T = gesperrt { l.set(index, element) }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = gesperrt { l.subList(fromIndex, toIndex).toMutableList() }
    override fun equals(other: Any?): Boolean = gesperrt { l == other }
    override fun hashCode(): Int = gesperrt { l.hashCode() }
    override fun toString(): String = gesperrt { l.toString() }
}

private class GesperrteMenge<T>(private val s: MutableSet<T>) : MutableSet<T> {
    override val size: Int get() = gesperrt { s.size }
    override fun contains(element: T): Boolean = gesperrt { s.contains(element) }
    override fun containsAll(elements: Collection<T>): Boolean = gesperrt { s.containsAll(elements) }
    override fun isEmpty(): Boolean = gesperrt { s.isEmpty() }
    override fun iterator(): MutableIterator<T> = s.iterator()
    override fun add(element: T): Boolean = gesperrt { s.add(element) }
    override fun addAll(elements: Collection<T>): Boolean = gesperrt { s.addAll(elements) }
    override fun clear() = gesperrt { s.clear() }
    override fun remove(element: T): Boolean = gesperrt { s.remove(element) }
    override fun removeAll(elements: Collection<T>): Boolean = gesperrt { s.removeAll(elements) }
    override fun retainAll(elements: Collection<T>): Boolean = gesperrt { s.retainAll(elements) }
    override fun equals(other: Any?): Boolean = gesperrt { s == other }
    override fun hashCode(): Int = gesperrt { s.hashCode() }
    override fun toString(): String = gesperrt { s.toString() }
}

private class GesperrteMap<K, V>(private val m: MutableMap<K, V>) : MutableMap<K, V> {
    override val size: Int get() = gesperrt { m.size }
    override fun containsKey(key: K): Boolean = gesperrt { m.containsKey(key) }
    override fun containsValue(value: V): Boolean = gesperrt { m.containsValue(value) }
    override fun get(key: K): V? = gesperrt { m[key] }
    override fun isEmpty(): Boolean = gesperrt { m.isEmpty() }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = m.entries
    override val keys: MutableSet<K> get() = m.keys
    override val values: MutableCollection<V> get() = m.values
    override fun clear() = gesperrt { m.clear() }
    override fun put(key: K, value: V): V? = gesperrt { m.put(key, value) }
    override fun putAll(from: Map<out K, V>) = gesperrt { m.putAll(from) }
    override fun remove(key: K): V? = gesperrt { m.remove(key) }
    override fun equals(other: Any?): Boolean = gesperrt { m == other }
    override fun hashCode(): Int = gesperrt { m.hashCode() }
    override fun toString(): String = gesperrt { m.toString() }
}
