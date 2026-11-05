package java.util

/**
 * Ersatz fuer java.util.ArrayDeque auf Grundlage von kotlin.collections.ArrayDeque, mit den Java-Namen (peek, poll ...).
 * Wie in Java liefern peek und poll bei leerer Schlange null, auch wenn der Typ das nicht zeigt (Plattformtyp).
 */
@Suppress("UNCHECKED_CAST")
class ArrayDeque<E>(initialCapacity: Int = 16) : AbstractMutableList<E>() {
    private val d = kotlin.collections.ArrayDeque<E>(initialCapacity)

    override val size: Int get() = d.size
    override fun get(index: Int): E = d[index]
    override fun set(index: Int, element: E): E = d.set(index, element)
    override fun add(index: Int, element: E) = d.add(index, element)
    override fun removeAt(index: Int): E = d.removeAt(index)

    fun addFirst(e: E) = d.addFirst(e)
    fun addLast(e: E) = d.addLast(e)
    fun offerFirst(e: E): Boolean { d.addFirst(e); return true }
    fun offerLast(e: E): Boolean { d.addLast(e); return true }
    fun offer(e: E): Boolean = offerLast(e)
    fun push(e: E) = d.addFirst(e)
    fun pop(): E = removeFirst()
    fun peekFirst(): E = d.firstOrNull() as E
    fun peekLast(): E = d.lastOrNull() as E
    fun peek(): E = peekFirst()
    fun pollFirst(): E = d.removeFirstOrNull() as E
    fun pollLast(): E = d.removeLastOrNull() as E
    fun poll(): E = pollFirst()
    fun getFirst(): E = d.firstOrNull() ?: throw NoSuchElementException()
    fun getLast(): E = d.lastOrNull() ?: throw NoSuchElementException()
    fun element(): E = getFirst()
    fun remove(): E = removeFirst()
}
