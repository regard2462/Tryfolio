package java.util

/*
 * Ersatz fuer java.util.TreeMap: Schluessel sortiert (natuerliche Ordnung oder Comparator), gespeichert als
 * sortierte Liste. Einfuegen am Ende (aufsteigende Schluessel, der Normalfall der App) kostet O(1), sonst O(n);
 * Suchen O(log n). headMap/tailMap/subMap liefern eine Kopie, keine Ansicht wie in Java.
 */

interface SortedMap<K, V> : MutableMap<K, V> {
    fun comparator(): Comparator<in K>?
    fun firstKey(): K
    fun lastKey(): K
    fun headMap(toKey: K): SortedMap<K, V>
    fun tailMap(fromKey: K): SortedMap<K, V>
    fun subMap(fromKey: K, toKey: K): SortedMap<K, V>
}

interface NavigableMap<K, V> : SortedMap<K, V> {
    // Java liefert bei leerer Map null; die App greift nur nach einer Pruefung auf isEmpty zu (Plattformtyp)
    fun firstEntry(): Map.Entry<K, V>
    fun lastEntry(): Map.Entry<K, V>
    fun pollFirstEntry(): Map.Entry<K, V>?
    fun pollLastEntry(): Map.Entry<K, V>?
    fun lowerEntry(key: K): Map.Entry<K, V>?
    fun lowerKey(key: K): K?
    fun floorEntry(key: K): Map.Entry<K, V>?
    fun floorKey(key: K): K?
    fun ceilingEntry(key: K): Map.Entry<K, V>?
    fun ceilingKey(key: K): K?
    fun higherEntry(key: K): Map.Entry<K, V>?
    fun higherKey(key: K): K?
    fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V>
    fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V>
    fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V>
    fun descendingMap(): NavigableMap<K, V>
    fun navigableKeySet(): Set<K>
    fun descendingKeySet(): Set<K>
}

open class TreeMap<K, V>(private val cmp: Comparator<in K>? = null) : AbstractMutableMap<K, V>(), NavigableMap<K, V> {

    private val schluessel = ArrayList<K>()
    private val werte = ArrayList<V>()

    constructor(m: Map<out K, V>) : this(if (m is SortedMap<*, *>) @Suppress("UNCHECKED_CAST") (m as SortedMap<K, V>).comparator() else null) {
        putAll(m)
    }

    @Suppress("UNCHECKED_CAST")
    private fun vergleiche(a: K, b: K): Int =
        cmp?.compare(a, b) ?: (a as Comparable<Any?>).compareTo(b)

    /** Index des Schluessels oder -(Einfuegestelle) - 1, wie Arrays.binarySearch. */
    private fun suche(key: K): Int {
        if (cmp == null && key == null) throw NullPointerException()
        var lo = 0
        var hi = schluessel.size - 1
        if (hi >= 0) {
            // schneller Weg fuer aufsteigendes Einfuegen
            val c = vergleiche(schluessel[hi], key)
            if (c < 0) return -(hi + 1) - 1
            if (c == 0) return hi
        }
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = vergleiche(schluessel[mid], key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    private fun eintrag(i: Int): Map.Entry<K, V>? =
        if (i in 0 until schluessel.size) Eintrag(schluessel[i], werte[i]) else null

    override val size: Int get() = schluessel.size

    override fun isEmpty(): Boolean = schluessel.isEmpty()

    @Suppress("UNCHECKED_CAST")
    override fun containsKey(key: K): Boolean = suche(key) >= 0

    override fun get(key: K): V? {
        val i = suche(key)
        return if (i >= 0) werte[i] else null
    }

    override fun put(key: K, value: V): V? {
        val i = suche(key)
        if (i >= 0) {
            val alt = werte[i]
            werte[i] = value
            return alt
        }
        val stelle = -i - 1
        schluessel.add(stelle, key)
        werte.add(stelle, value)
        return null
    }

    override fun remove(key: K): V? {
        val i = suche(key)
        if (i < 0) return null
        schluessel.removeAt(i)
        return werte.removeAt(i)
    }

    override fun clear() {
        schluessel.clear()
        werte.clear()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = schluessel.size
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                val alt = put(element.key, element.value)
                return alt != element.value
            }
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                private var naechster = 0
                private var letzter = -1
                override fun hasNext(): Boolean = naechster < schluessel.size
                override fun next(): MutableMap.MutableEntry<K, V> {
                    if (naechster >= schluessel.size) throw NoSuchElementException()
                    letzter = naechster++
                    val k = schluessel[letzter]
                    val idx = letzter
                    return object : MutableMap.MutableEntry<K, V> {
                        override val key: K get() = k
                        override val value: V get() = werte[idx]
                        override fun setValue(newValue: V): V { val alt = werte[idx]; werte[idx] = newValue; return alt }
                        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && other.key == key && other.value == value
                        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
                        override fun toString(): String = "$key=$value"
                    }
                }
                override fun remove() {
                    check(letzter >= 0)
                    schluessel.removeAt(letzter)
                    werte.removeAt(letzter)
                    naechster = letzter
                    letzter = -1
                }
            }
        }

    override fun comparator(): Comparator<in K>? = cmp

    override fun firstKey(): K = if (schluessel.isEmpty()) throw NoSuchElementException() else schluessel[0]
    override fun lastKey(): K = if (schluessel.isEmpty()) throw NoSuchElementException() else schluessel[schluessel.size - 1]

    override fun firstEntry(): Map.Entry<K, V> = eintrag(0) ?: throw NullPointerException("leere TreeMap")
    override fun lastEntry(): Map.Entry<K, V> = eintrag(schluessel.size - 1) ?: throw NullPointerException("leere TreeMap")

    override fun pollFirstEntry(): Map.Entry<K, V>? = eintrag(0)?.also { schluessel.removeAt(0); werte.removeAt(0) }
    override fun pollLastEntry(): Map.Entry<K, V>? = eintrag(schluessel.size - 1)?.also { schluessel.removeAt(schluessel.size - 1); werte.removeAt(werte.size - 1) }

    /** Index des groessten Schluessels <= key (oder < key, wenn [streng]); -1, wenn keiner. */
    private fun unten(key: K, streng: Boolean): Int {
        val i = suche(key)
        return if (i >= 0) (if (streng) i - 1 else i) else -i - 2
    }

    /** Index des kleinsten Schluessels >= key (oder > key); size, wenn keiner. */
    private fun oben(key: K, streng: Boolean): Int {
        val i = suche(key)
        return if (i >= 0) (if (streng) i + 1 else i) else -i - 1
    }

    override fun lowerEntry(key: K): Map.Entry<K, V>? = eintrag(unten(key, true))
    override fun lowerKey(key: K): K? = lowerEntry(key)?.key
    override fun floorEntry(key: K): Map.Entry<K, V>? = eintrag(unten(key, false))
    override fun floorKey(key: K): K? = floorEntry(key)?.key
    override fun ceilingEntry(key: K): Map.Entry<K, V>? = eintrag(oben(key, false))
    override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key
    override fun higherEntry(key: K): Map.Entry<K, V>? = eintrag(oben(key, true))
    override fun higherKey(key: K): K? = higherEntry(key)?.key

    private fun bereich(von: Int, bis: Int): TreeMap<K, V> {
        val m = TreeMap<K, V>(cmp)
        for (i in von until bis) {
            m.schluessel.add(schluessel[i])
            m.werte.add(werte[i])
        }
        return m
    }

    override fun headMap(toKey: K): SortedMap<K, V> = headMap(toKey, false)
    override fun tailMap(fromKey: K): SortedMap<K, V> = tailMap(fromKey, true)
    override fun subMap(fromKey: K, toKey: K): SortedMap<K, V> = subMap(fromKey, true, toKey, false)

    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> = bereich(0, unten(toKey, !inclusive) + 1)

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> = bereich(oben(fromKey, !inclusive), schluessel.size)

    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
        if (vergleiche(fromKey, toKey) > 0) throw IllegalArgumentException("fromKey > toKey")
        val von = oben(fromKey, !fromInclusive)
        val bis = unten(toKey, !toInclusive) + 1
        return if (bis <= von) TreeMap(cmp) else bereich(von, bis)
    }

    override fun descendingMap(): NavigableMap<K, V> {
        val m = TreeMap<K, V>(Comparator { a, b -> vergleiche(b, a) })
        for (i in schluessel.indices.reversed()) {
            m.schluessel.add(schluessel[i])
            m.werte.add(werte[i])
        }
        return m
    }

    override fun navigableKeySet(): Set<K> = LinkedHashSet(schluessel)
    override fun descendingKeySet(): Set<K> = LinkedHashSet(schluessel.asReversed())

    private class Eintrag<K, V>(override val key: K, override val value: V) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && other.key == key && other.value == value
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
        override fun toString(): String = "$key=$value"
    }
}
