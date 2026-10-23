package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Vermoegensschaetzung je Abgeordnetem aus der Jahresmeldung (Financial Disclosure Report, Annual) an den Clerk of the
// House. Abschnitt A nennt jeden Vermoegenswert mit einer Wertspanne, Abschnitt D die Verbindlichkeiten. Die Summe
// der Spannen ergibt eine Spanne fuer das Nettovermoegen. Selbstgenutzte Wohnungen und staatliche Renten muessen nicht
// gemeldet werden; die Spanne ist also eine Untergrenze des tatsaechlichen Vermoegens. Mitgeliefert unter
// copytrading/vermoegen.json, gebaut von HausSammlung.
object Vermoegen {

    data class Kopf(val docId: String, val nachname: String, val vorname: String, val wahlkreis: String, val jahr: Int, val eingereicht: String)

    @Serializable
    data class E(
        val id: String,
        val n: String,
        val v: String,
        val w: String,
        // Berichtsjahr.
        val j: Int,
        // Vermoegen von bis; ob eine Spanne nach oben offen ist („Over $50,000,000“).
        val av: Long,
        val ab: Long,
        val ao: Boolean = false,
        // Verbindlichkeiten von bis.
        val sv: Long = 0,
        val sb: Long = 0,
        val so: Boolean = false,
        // Zahl der Vermoegenswerte.
        val z: Int = 0,
    ) {
        val person: String get() = "$n|$v|${w.take(2)}"
        val nettoVon: Long get() = av - sb
        val nettoBis: Long get() = ab - sv
    }

    @Serializable
    data class Datei(val format: Int, val stand: String = "", val personen: List<E> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    fun schreiben(d: Datei): String = Json.encodeToString(Datei.serializer(), d)

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()?.takeIf { it.format == 1 }

    private val TYP = Regex("\\[[A-Z0-9]{2}]")
    private val SPANNE = Regex("\\$\\s?([\\d,]+)\\s*-\\s*\\$\\s?([\\d,]+)")
    private val UEBER = Regex("(?:Spouse/DC\\s+)?Over\\s+\\$\\s?([\\d,]+)", RegexOption.IGNORE_CASE)
    private val KEINE = Regex("\\bNone\\b")

    private fun zahl(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    // Abschnitt X beginnt mit den Zeilen „S“, „X“, „:“ und endet beim naechsten Abschnitt. Die Abschnitte werden der
    // Reihe nach gesucht, jeder hinter dem vorigen: Beschriftungen wie „D:“ (Beschreibung) stehen auch mitten in A.
    private fun abschnitte(zeilen: List<String>): Map<Char, String> {
        val starts = LinkedHashMap<Char, Int>()
        var ab = 0
        for (b in 'A'..'I') {
            val s = (ab until zeilen.size - 2).firstOrNull { zeilen[it] == "S" && zeilen[it + 1] == b.toString() && zeilen[it + 2] == ":" } ?: continue
            starts[b] = s
            ab = s + 3
        }
        val liste = starts.entries.toList()
        return liste.mapIndexed { i, (b, s) ->
            val ende = liste.getOrNull(i + 1)?.value ?: zeilen.size
            b to zeilen.subList(s + 3, ende).joinToString(" ")
        }.toMap()
    }

    // Spanne nach einer Stelle: die erste Wertangabe vor dem naechsten Typkuerzel. null, wenn keine dasteht.
    private fun spanneAb(text: String, ab: Int, bis: Int): Triple<Long, Long, Boolean>? {
        val teil = text.substring(ab, bis)
        val s = SPANNE.find(teil)
        val u = UEBER.find(teil)
        val k = KEINE.find(teil)
        val erste = listOfNotNull(s?.range?.first, u?.range?.first, k?.range?.first).minOrNull() ?: return null
        return when (erste) {
            s?.range?.first -> Triple(zahl(s!!.groupValues[1]), zahl(s.groupValues[2]), false)
            u?.range?.first -> { val v = zahl(u!!.groupValues[1]); Triple(v + 1, v + 1, true) }
            else -> Triple(0L, 0L, false)
        }
    }

    fun auswerten(k: Kopf, zeilen: List<String>): E? {
        val teile = abschnitte(zeilen)
        val a = teile['A'] ?: return null
        var av = 0L
        var ab = 0L
        var ao = false
        var z = 0
        val typen = TYP.findAll(a).toList()
        typen.forEachIndexed { i, m ->
            val ende = typen.getOrNull(i + 1)?.range?.first ?: a.length
            val sp = spanneAb(a, m.range.last + 1, ende) ?: return@forEachIndexed
            av += sp.first
            ab += sp.second
            ao = ao || sp.third
            z++
        }
        var sv = 0L
        var sb = 0L
        var so = false
        teile['D']?.let { d ->
            SPANNE.findAll(d).forEach { m -> sv += zahl(m.groupValues[1]); sb += zahl(m.groupValues[2]) }
            UEBER.findAll(d).forEach { m -> val v = zahl(m.groupValues[1]) + 1; sv += v; sb += v; so = true }
        }
        return E(k.docId, k.nachname, k.vorname, k.wahlkreis, k.jahr, av, ab, ao, sv, sb, so, z)
    }
}
