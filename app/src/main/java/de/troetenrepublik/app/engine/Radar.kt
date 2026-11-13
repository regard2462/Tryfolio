package de.troetenrepublik.app.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// Frueherkennung „Radar“ (5.92): fuehrt Hinweise zusammen, die oft auftauchen, bevor ein Wert breit beachtet wird.
// Jede Quelle liefert Signale (Wert, Art, Tag, Staerke 0 bis 1); das Radar gewichtet sie nach Art und Alter, belohnt
// verschiedene Arten beim selben Wert und daempft Werte, die schon im Gespraech sind. Reine Rechnung, die Abrufe
// stehen in RadarQuellen.
object Radar {

    // Gewicht und Halbwertszeit in Tagen je Art: Insider- und Grossaktionaersmeldungen wirken laenger als ein Tag
    // mit viel Umsatz.
    enum class Art(val gewicht: Double, val halbwert: Double) {
        ERWAEHNUNGEN(1.0, 3.0),
        WIKIPEDIA(0.8, 5.0),
        INSIDER_GRUPPE(1.6, 21.0),
        INSIDER_KAUF(1.0, 21.0),
        KONGRESS(1.2, 30.0),
        REGIERUNG(1.2, 30.0),
        GROSSAKTIONAER(1.4, 30.0),
        OPTIONEN(1.2, 3.0),
        VOLUMEN(1.0, 2.0),
        PROGNOSE(0.8, 7.0),
    }

    // tag: Tage seit 1970. zahl und zahl2 tragen die Werte fuer den Text (etwa Personen und Betrag), text einen Namen.
    data class Signal(
        val symbol: String,
        val art: Art,
        val tag: Long,
        val staerke: Double,
        val zahl: Double = 0.0,
        val zahl2: Double = 0.0,
        val text: String = "",
    )

    data class Treffer(
        val symbol: String,
        // Staerkste zuerst
        val signale: List<Signal>,
        // 0 bis 100
        val punkte: Double,
        val neuester: Long,
        val arten: Int,
        // Schon breit im Gespraech (gedaempft)
        val bekannt: Boolean,
    )

    // Signale aelter als das zaehlen nicht mehr.
    const val MAX_ALTER = 90L

    // Einfluss eines Signals nach Alter: nach halbwert Tagen die Haelfte.
    fun neuheit(alter: Long, halbwert: Double): Double = 0.5.pow(max(0L, alter) / halbwert)

    fun wert(s: Signal, heute: Long): Double = s.art.gewicht * s.staerke.coerceIn(0.0, 1.0) * neuheit(heute - s.tag, s.art.halbwert)

    // Je Wert: je Art das staerkste Signal voll, weitere derselben Art mit einem Drittel; mehrere Arten zusammen
    // zaehlen je weitere Art 30 % mehr. Bekannte Werte (bekannt) zaehlen 60 %. Punkte 0 bis 100 ueber 1 - e^(-x/2).
    fun bewerten(signale: List<Signal>, heute: Long, bekannt: Set<String> = emptySet()): List<Treffer> =
        signale.filter { heute - it.tag in -1..MAX_ALTER && it.staerke > 0 }.groupBy { it.symbol }.map { (symbol, l) ->
            val jeArt = l.groupBy { it.art }.mapValues { (_, a) ->
                val w = a.map { wert(it, heute) }.sortedDescending()
                w.first() + w.drop(1).sum() / 3.0
            }
            val roh = jeArt.values.sum() * (1.0 + 0.3 * (jeArt.size - 1))
            val gedaempft = if (symbol in bekannt) roh * 0.6 else roh
            Treffer(
                symbol, l.sortedByDescending { wert(it, heute) }, 100.0 * (1.0 - exp(-gedaempft / 2.0)),
                l.maxOf { it.tag }, jeArt.size, symbol in bekannt,
            )
        }.sortedWith(compareByDescending<Treffer> { it.punkte }.thenByDescending { it.neuester }.thenBy { it.symbol })

    // Erwaehnungen auf Reddit: Staerke aus dem Anstieg gegen den Vortag (ab 50 % mehr, mindestens 15 Erwaehnungen);
    // mit Verlauf (aelteste zuerst, letzter Eintrag heute) zaehlt der Schnitt der Woche davor. null ohne Anstieg.
    fun erwaehnungen(heute: Int, gestern: Int?, verlauf: List<Int> = emptyList()): Double? {
        if (heute < 15) return null
        val vorher = verlauf.dropLast(1).takeLast(7).filter { it > 0 }.takeIf { it.size >= 3 }?.average() ?: gestern?.toDouble() ?: return null
        val faktor = (heute + 5.0) / (vorher + 5.0)
        if (faktor < 1.5) return null
        return (ln(faktor) / ln(6.0)).coerceIn(0.15, 1.0)
    }

    // Wikipedia-Aufrufe: ab 50 % mehr als in den vier Wochen davor, voll bei +300 %.
    fun wikipedia(veraenderung: Double): Double? = if (veraenderung < 50.0) null else (veraenderung / 300.0).coerceIn(0.15, 1.0)

    // Relatives Volumen (RelativesVolumen): ab dem 2,5-fachen, voll beim 8-fachen.
    fun volumen(heute: Double, schnitt: Double): Double? {
        val r = RelativesVolumen.wert(heute, schnitt) ?: return null
        return if (r < 2.5) null else ((r - 2.0) / 6.0).coerceIn(0.1, 1.0)
    }

    // Kauf einer Person aus dem Unternehmen (Betrag in Euro, Rolle wie InsiderTrend).
    data class InsiderKauf(val person: String, val rolle: InsiderTrend.Rolle, val betrag: Double, val tag: Long)

    // Insidersignale eines Werts: drei oder mehr verschiedene Kaeufer in 30 Tagen ergeben ein Gruppensignal (Staerke
    // aus Zahl der Kaeufer und Summe), sonst zaehlen einzelne grosse Kaeufe der Spitze oder Fuehrung ab 100.000 Euro.
    fun insider(symbol: String, kaeufe: List<InsiderKauf>): List<Signal> {
        if (kaeufe.isEmpty()) return emptyList()
        val sortiert = kaeufe.sortedBy { it.tag }
        var bestes: Signal? = null
        for (i in sortiert.indices) {
            val fenster = sortiert.filter { it.tag >= sortiert[i].tag && it.tag <= sortiert[i].tag + 30 }
            val personen = fenster.map { it.person.trim().lowercase() }.toSet()
            if (personen.size < 3) continue
            val summe = fenster.sumOf { it.betrag }
            val staerke = (0.4 + 0.1 * (personen.size - 3) + 0.15 * max(0.0, log10(max(summe, 1.0)) - 5.0)).coerceIn(0.4, 1.0)
            val s = Signal(symbol, Art.INSIDER_GRUPPE, fenster.maxOf { it.tag }, staerke, personen.size.toDouble(), summe)
            if (bestes == null || s.staerke > bestes.staerke || (s.staerke == bestes.staerke && s.tag > bestes.tag)) bestes = s
        }
        if (bestes != null) return listOf(bestes)
        return sortiert.filter { it.betrag >= 100_000 && (it.rolle == InsiderTrend.Rolle.SPITZE || it.rolle == InsiderTrend.Rolle.FUEHRUNG) }
            .map { k -> Signal(symbol, Art.INSIDER_KAUF, k.tag, (0.3 + 0.2 * (log10(k.betrag) - 5.0)).coerceIn(0.3, 0.9), 1.0, k.betrag, k.person) }
            .sortedByDescending { it.tag }.take(2)
    }

    // Kaeufe von Abgeordneten oder Regierungsmitgliedern (Betrag als Mitte der gemeldeten Spanne in Dollar, tag =
    // Meldetag): ein Signal je Wert mit Zahl der Personen und Summe, datiert auf die juengste Meldung.
    fun politik(symbol: String, art: Art, kaeufe: List<Triple<String, Double, Long>>): Signal? {
        if (kaeufe.isEmpty()) return null
        val personen = kaeufe.map { it.first }.toSet()
        val summe = kaeufe.sumOf { it.second }
        val staerke = (0.35 + 0.15 * (personen.size - 1) + 0.12 * max(0.0, log10(max(summe, 1.0)) - 4.0)).coerceIn(0.35, 1.0)
        return Signal(symbol, art, kaeufe.maxOf { it.third }, staerke, personen.size.toDouble(), summe, personen.singleOrNull().orEmpty())
    }

    // Neue Beteiligung ueber 5 % mit Absicht, Einfluss zu nehmen (Schedule 13D); Anteil in Prozent, wenn bekannt.
    fun grossaktionaer(symbol: String, tag: Long, anteil: Double?, name: String): Signal =
        Signal(symbol, Art.GROSSAKTIONAER, tag, (0.6 + (anteil ?: 5.0) / 50.0).coerceIn(0.6, 1.0), anteil ?: 0.0, 0.0, name)

    // Auffaellige Call-Kaeufe aus der Optionskette (Optionsanalyse): Praemie in Dollar, Umsatz gegen offene Kontrakte.
    fun optionen(symbol: String, tag: Long, auffaellig: List<Optionsanalyse.Auffaellig>): Signal? {
        val calls = auffaellig.filter { it.kontrakt.call && it.seite == Optionsanalyse.Seite.KAUF }
        if (calls.isEmpty()) return null
        val praemie = calls.sumOf { it.praemie }
        val staerke = (0.3 + 0.2 * (log10(max(praemie, 1.0)) - 5.0) + 0.05 * calls.maxOf { it.verhaeltnis }.coerceAtMost(6.0)).coerceIn(0.3, 1.0)
        return Signal(symbol, Art.OPTIONEN, tag, staerke, calls.size.toDouble(), praemie)
    }

    // Grosse Wette auf einem Prognosemarkt, deren Titel den Wert nennt (Einsatz in Dollar).
    fun prognose(symbol: String, tag: Long, betrag: Double, titel: String): Signal? {
        if (betrag < 10_000) return null
        return Signal(symbol, Art.PROGNOSE, tag, (0.3 + 0.25 * (log10(betrag) - 4.0)).coerceIn(0.3, 0.9), betrag, 0.0, titel)
    }

    // Nennt der Titel das Unternehmen? Ganzes Wort, Gross- und Kleinschreibung egal; der Name ohne Rechtsform muss
    // mindestens vier Zeichen haben, damit kurze Namen nicht in anderen Woertern auftauchen.
    fun nennt(titel: String, name: String): Boolean {
        val n = name.lowercase().replace(Regex("\\b(inc|corp|corporation|plc|ag|se|sa|nv|co|ltd|holdings?|group|the|class [a-c])\\b\\.?"), " ")
            .replace(de.troetenrepublik.app.engine.Muster.re("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")
        if (n.length < 4) return false
        return Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(n) + "($|[^\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(titel)
    }

    // Arten, die Aufmerksamkeit messen (nicht Insider, Politik usw.).
    val GESPRAECH = setOf(Art.ERWAEHNUNGEN, Art.WIKIPEDIA)

    // Im Gespraech: Hinweis der Art Erwaehnungen oder Wikipedia, oder schon breit bekannt.
    fun imGespraech(t: Treffer): Boolean = t.bekannt || t.signale.any { it.art in GESPRAECH }

    // Treffer nach Arten (null = alle) und Symbolen (null = alle) filtern; Reihenfolge bleibt.
    fun filtern(treffer: List<Treffer>, arten: Set<Art>?, nur: Set<String>?): List<Treffer> =
        treffer.filter { t -> (arten == null || t.signale.any { it.art in arten }) && (nur == null || t.symbol in nur) }
}
