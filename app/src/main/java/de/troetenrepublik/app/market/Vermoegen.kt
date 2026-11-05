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
    private val KEINE = Regex("\\b(?:None|Undetermined)\\b")

    private fun zahl(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    private val HAENGEND = Regex("\\$\\s?([\\d,]+)\\s*-(?!\\s*\\$\\s?[\\d,]+)")
    private val BETRAG = Regex("\\$\\s?([\\d,]+)")
    private val NUR_MINUS = Regex("^\\s*-")

    // Tabellenkoepfe der Tabellen in Abschnitt A und D, ohne Leerzeichen.
    private val KOEPFE = listOf(
        "AssetOwnerValueofAssetIncomeType(s)IncomeTx.>$1,000?",
        "OwnerCreditorDateIncurredTypeAmountofLiability",
    )

    // Bei einem Seitenumbruch mitten in einem Posten faellt der Tabellenkopf der Folgeseite in Buchstabenstuecken an.
    // Er wird hier weggelassen, bevor die Abschnitte zusammengesetzt werden: Passt eine Zeile zum Kopfanfang, so lange
    // Zeilen anhaengen, wie ein Anfang des Kopfs entsteht; nur bei vollstaendigem Kopf weglassen. Leere Zeilen laufen mit.
    private fun kopfResteEntfernen(zeilen: List<String>): List<String> {
        val weg = mutableSetOf<Int>()
        var i = 0
        while (i < zeilen.size) {
            val anfang = zeilen[i].replace(" ", "")
            val kopf = if (anfang.isEmpty()) null else KOEPFE.firstOrNull { it.startsWith(anfang) }
            if (kopf != null) {
                var stueck = anfang
                var j = i + 1
                while (stueck.length < kopf.length && j < zeilen.size) {
                    val n = zeilen[j].replace(" ", "")
                    if (!kopf.startsWith(stueck + n)) break
                    stueck += n
                    j++
                }
                if (stueck == kopf) for (k in i until j) weg += k
            }
            i++
        }
        return zeilen.filterIndexed { idx, _ -> idx !in weg }
    }

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

    // Spanne nach einer Stelle: die erste Wertangabe vor dem naechsten Typkuerzel. null, wenn keine dasteht. Bei einer
    // haengenden Untergrenze „$X -“, deren Obergrenze erst hinter dem Tabellenkopf der Folgeseite steht, gilt der
    // naechste alleinstehende Betrag dahinter als Obergrenze, sonst die Untergrenze selbst.
    private fun spanneAb(text: String, ab: Int, bis: Int): Triple<Long, Long, Boolean>? {
        val teil = text.substring(ab, bis)
        val s = SPANNE.find(teil)
        val u = UEBER.find(teil)
        val k = KEINE.find(teil)
        val h = HAENGEND.find(teil)
        val erste = listOfNotNull(s?.range?.first, u?.range?.first, k?.range?.first, h?.range?.first).minOrNull() ?: return null
        return when (erste) {
            h?.range?.first -> { val v = zahl(h!!.groupValues[1]); Triple(v, (alleinstehendNach(teil, h.range.last + 1) ?: v), false) }
            s?.range?.first -> Triple(zahl(s!!.groupValues[1]), zahl(s.groupValues[2]), false)
            u?.range?.first -> { val v = zahl(u!!.groupValues[1]); Triple(v + 1, v + 1, true) }
            else -> Triple(0L, 0L, false)
        }
    }

    // Naechster alleinstehender Betrag ab einer Stelle: keiner aus einer vollstaendigen Spanne und keiner, dem selbst
    // ein „ -“ folgt.
    private fun alleinstehendNach(text: String, ab: Int): Long? {
        val spannen = SPANNE.findAll(text).toList()
        return BETRAG.findAll(text)
            .firstOrNull { b -> b.range.first >= ab && spannen.none { s -> b.range.first in s.range } && !haengendAn(text, b.range.last + 1) }
            ?.let { zahl(it.groupValues[1]) }
    }

    // Zeigt der Betrag an dieser Stelle eine Untergrenze an, der keine Obergrenze folgt?
    private fun haengendAn(text: String, stelle: Int): Boolean = NUR_MINUS.containsMatchIn(text.substring(stelle))

    fun auswerten(k: Kopf, zeilen: List<String>): E? {
        val teile = abschnitte(kopfResteEntfernen(zeilen))
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
            val spannen = SPANNE.findAll(d).associateBy { it.range.first }
            UEBER.findAll(d).forEach { m -> val v = zahl(m.groupValues[1]) + 1; sv += v; sb += v; so = true }
            // Vollstaendige Spannen zaehlen, eine haengende Untergrenze mit dem naechsten alleinstehenden Betrag als
            // Obergrenze; der zaehlt dann nicht noch einmal. Alleinstehende Betraege ohne Untergrenze zaehlen nicht.
            BETRAG.findAll(d).forEach { b ->
                spannen[b.range.first]?.let { m -> sv += zahl(m.groupValues[1]); sb += zahl(m.groupValues[2]); return@forEach }
                if (haengendAn(d, b.range.last + 1)) {
                    sv += zahl(b.groupValues[1])
                    sb += alleinstehendNach(d, b.range.last + 1) ?: zahl(b.groupValues[1])
                }
            }
        }
        return E(k.docId, k.nachname, k.vorname, k.wahlkreis, k.jahr, av, ab, ao, sv, sb, so, z)
    }
}
