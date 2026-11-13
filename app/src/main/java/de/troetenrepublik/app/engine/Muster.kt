package de.troetenrepublik.app.engine

import java.util.concurrent.ConcurrentHashMap

// Feste Suchmuster einmal uebersetzt. Auf Android uebersetzt ICU jedes Regex neu; Muster mit Unicode-Klassen wie \p{L}
// kosten dabei viel Rechenzeit und nativen Speicher, wenn sie in Schleifen je Zeile oder Name entstehen.
object Muster {
    private val cache = ConcurrentHashMap<String, Regex>()

    fun re(muster: String): Regex = cache[muster] ?: Regex(muster).also { cache[muster] = it }

    fun re(muster: String, option: RegexOption): Regex {
        val k = option.name + "\u0000" + muster
        return cache[k] ?: Regex(muster, option).also { cache[k] = it }
    }
}
