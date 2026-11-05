package java.util.concurrent

import de.troetenrepublik.app.ios.Sperre

/*
 * Ersatz fuer ConcurrentHashMap/ConcurrentMap: eine HashMap, jeder Zugriff unter der gemeinsamen Sperre. Durchlaeufe
 * gehen wie in Java ohne ConcurrentModificationException ueber eine Momentaufnahme; Entfernen beim Durchlauf wirkt
 * auf die Karte. Die Java-8-Methoden (putIfAbsent, computeIfAbsent, ...) sind als Mitglieder da.
 * Wie in Java sind null-Schluessel und null-Werte verboten.
 */

interface ConcurrentMap<K, V> : MutableMap<K, V> {
    fun putIfAbsent(key: K, value: V): V?
    fun remove(key: K, value: V): Boolean
    fun replace(key: K, value: V): V?
    fun replace(key: K, oldValue: V, newValue: V): Boolean
}

open class ConcurrentHashMap<K, V> private constructor(private val m: HashMap<K, V>, @Suppress("UNUSED_PARAMETER") marke: Unit) :
    ConcurrentMap<K, V> {

    constructor() : this(HashMap(), Unit)
    constructor(initialCapacity: Int) : this(HashMap(maxOf(initialCapacity, 0)), Unit)
    constructor(initialCapacity: Int, loadFactor: Float) : this(HashMap(maxOf(initialCapacity, 0), loadFactor), Unit)
    constructor(initialCapacity: Int, loadFactor: Float, @Suppress("UNUSED_PARAMETER") concurrencyLevel: Int) :
        this(HashMap(maxOf(initialCapacity, 0), loadFactor), Unit)
    constructor(map: Map<out K, V>) : this(HashMap(), Unit) { putAll(map) }

    private fun nichtNull(x: Any?) { if (x == null) throw NullPointerException() }

    override val size: Int get() = Sperre.mit { m.size }
    override fun isEmpty(): Boolean = Sperre.mit { m.isEmpty() }
    override fun containsKey(key: K): Boolean = Sperre.mit { m.containsKey(key) }
    override fun containsValue(value: V): Boolean = Sperre.mit { m.containsValue(value) }
    override fun get(key: K): V? = Sperre.mit { m[key] }
    override fun remove(key: K): V? = Sperre.mit { m.remove(key) }
    override fun clear() = Sperre.mit { m.clear() }

    override fun put(key: K, value: V): V? {
        nichtNull(key); nichtNull(value)
        return Sperre.mit { m.put(key, value) }
    }

    override fun putAll(from: Map<out K, V>) {
        for ((k, v) in from) put(k, v)
    }

    override fun putIfAbsent(key: K, value: V): V? {
        nichtNull(key); nichtNull(value)
        return Sperre.mit {
            val alt = m[key]
            if (alt == null) m[key] = value
            alt
        }
    }

    override fun remove(key: K, value: V): Boolean = Sperre.mit {
        if (m.containsKey(key) && m[key] == value) { m.remove(key); true } else false
    }

    override fun replace(key: K, value: V): V? {
        nichtNull(value)
        return Sperre.mit { if (m.containsKey(key)) m.put(key, value) else null }
    }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean {
        nichtNull(newValue)
        return Sperre.mit { if (m.containsKey(key) && m[key] == oldValue) { m[key] = newValue; true } else false }
    }

    fun getOrDefault(key: K, defaultValue: V): V = Sperre.mit { m[key] ?: defaultValue }

    fun computeIfAbsent(key: K, mappingFunction: (K) -> V?): V? {
        nichtNull(key)
        return Sperre.mit {
            m[key] ?: mappingFunction(key)?.also { m[key] = it }
        }
    }

    fun computeIfPresent(key: K, remappingFunction: (K, V) -> V?): V? = Sperre.mit {
        val alt = m[key] ?: return@mit null
        val neu = remappingFunction(key, alt)
        if (neu == null) m.remove(key) else m[key] = neu
        neu
    }

    fun compute(key: K, remappingFunction: (K, V?) -> V?): V? {
        nichtNull(key)
        return Sperre.mit {
            val neu = remappingFunction(key, m[key])
            if (neu == null) m.remove(key) else m[key] = neu
            neu
        }
    }

    fun merge(key: K, value: V, remappingFunction: (V, V) -> V?): V? {
        nichtNull(key); nichtNull(value)
        return Sperre.mit {
            val alt = m[key]
            val neu = if (alt == null) value else remappingFunction(alt, value)
            if (neu == null) m.remove(key) else m[key] = neu
            neu
        }
    }

    fun contains(value: Any?): Boolean = Sperre.mit { m.containsValue(@Suppress("UNCHECKED_CAST") (value as V)) }

    fun mappingCount(): Long = size.toLong()

    fun keySet(mappedValue: V): KeySetView<K, V> = KeySetView(this, mappedValue)

    override val keys: MutableSet<K> get() = KeySetView(this, null)

    override val values: MutableCollection<V>
        get() = object : AbstractMutableCollection<V>() {
            override val size: Int get() = this@ConcurrentHashMap.size
            override fun add(element: V): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableIterator<V> = durchlauf { it.value }
        }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@ConcurrentHashMap.size
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = durchlauf { e -> Eintrag(e.key, e.value) }
        }

    // Durchlauf ueber eine Momentaufnahme; remove() entfernt den zuletzt gelieferten Schluessel aus der Karte
    internal fun <T> durchlauf(f: (Map.Entry<K, V>) -> T): MutableIterator<T> {
        val bild = Sperre.mit { m.entries.map { Eintrag(it.key, it.value) } }
        return object : MutableIterator<T> {
            var i = 0
            var zuletzt: K? = null
            var da = false
            override fun hasNext(): Boolean = i < bild.size
            override fun next(): T {
                if (i >= bild.size) throw NoSuchElementException()
                val e = bild[i++]
                zuletzt = e.key
                da = true
                return f(e)
            }
            override fun remove() {
                check(da)
                da = false
                @Suppress("UNCHECKED_CAST")
                Sperre.mit { m.remove(zuletzt as K) }
            }
        }
    }

    private inner class Eintrag(override val key: K, private var wert: V) : MutableMap.MutableEntry<K, V> {
        override val value: V get() = wert
        override fun setValue(newValue: V): V {
            val alt = wert
            put(key, newValue)
            wert = newValue
            return alt
        }
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && other.key == key && other.value == wert
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (wert?.hashCode() ?: 0)
        override fun toString(): String = "$key=$wert"
    }

    override fun equals(other: Any?): Boolean = Sperre.mit { m == other }
    override fun hashCode(): Int = Sperre.mit { m.hashCode() }
    override fun toString(): String = Sperre.mit { m.toString() }

    /** Menge der Schluessel; mit [mappedValue] lassen sich auch Schluessel hinzufuegen (newKeySet). */
    class KeySetView<K, V> internal constructor(private val karte: ConcurrentHashMap<K, V>, val mappedValue: V?) :
        AbstractMutableSet<K>() {
        override val size: Int get() = karte.size
        override fun contains(element: K): Boolean = karte.containsKey(element)
        override fun add(element: K): Boolean {
            val v = mappedValue ?: throw UnsupportedOperationException()
            return karte.putIfAbsent(element, v) == null
        }
        override fun remove(element: K): Boolean = Sperre.mit { karte.m.containsKey(element).also { if (it) karte.m.remove(element) } }
        override fun clear() = karte.clear()
        override fun iterator(): MutableIterator<K> = karte.durchlauf { it.key }
        fun getMap(): ConcurrentHashMap<K, V> = karte
    }

    companion object {
        fun <K> newKeySet(): KeySetView<K, Boolean> = KeySetView(ConcurrentHashMap(), true)
        fun <K> newKeySet(initialCapacity: Int): KeySetView<K, Boolean> = KeySetView(ConcurrentHashMap(initialCapacity), true)
    }
}
