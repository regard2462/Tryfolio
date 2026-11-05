package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TreeMap
import kotlin.math.pow
import kotlin.math.sqrt

// Wertreihe: Zeit in ms und Wert.
typealias Reihe = List<Pair<Long, Double>>

// Portfolioverlaeufe fuer Copytrading: Entwicklung des Portfolios eines Abgeordneten, Investors oder einer Strategie,
// mit Kaeufen und Verkaeufen und im Vergleich zu anderen Portfolios. Alle Reihen sind Wertindizes (Zeit in ms, Wert),
// erst fuer die Anzeige auf 100 am Beginn des Zeitraums gesetzt. Zum Vergleich wird mit dem Wechselkurs des jeweiligen
// Tages umgerechnet (EURUSD, US-Dollar je Euro).
// Investoren: mitgelieferte Wochenreihe aus allen 13F-Meldungen der letzten fuenf Jahre (assets/copytrading/verlauf.json).
// Bestand zum Stichtag, bis zum naechsten Stichtag gehalten; Kaeufe und Verkaeufe je Quartal aus der Veraenderung der
// Stueckzahl. Nach dem letzten Stand der Datei fuehrt fortschreiben die Reihe mit den groessten Positionen weiter.
// Abgeordnete: Portfolio aus den gemeldeten Geschaeften (kongress), Betraege als Mitte der Spanne, Optionen als
// Basiswert. Bestaende von vor dem Zeitraum sind unbekannt; Verkaeufe solcher Werte bleiben aussen vor.
// Strategien: aus Strategien.kennzahlen (Euro-ETFs, taeglich gleiche Gewichte).
object Portfolioverlauf {

    enum class Waehrung { EUR, USD }

    // Vergleichsportfolios aus dem Katalog (Euro-ETFs bzw. Index an der Xetra, thesaurierend oder Performanceindex).
    data class Massstab(val id: String, val symbol: String, val name: String)

    val MASSSTAEBE = listOf(
        Massstab("msci", "EUNL.DE", "MSCI World"),
        Massstab("acwi", "IUSQ.DE", "MSCI ACWI"),
        Massstab("sp500", "SXR8.DE", "S&P 500"),
        Massstab("nasdaq", "SXRV.DE", "Nasdaq 100"),
        Massstab("dax", "^GDAXI", "DAX"),
    )

    const val EURUSD = "EURUSD=X"

    // Zeit

    fun tag(t: Long): Long = Math.floorDiv(t, 86_400_000L)
    fun ms(tag: Long): Long = tag * 86_400_000L
    fun iso(s: String): Long = runCatching { LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

    // Investoren aus verlauf.json

    // Eine Kauf- oder Verkaufsbewegung eines Quartals: art neu, mehr, weniger, raus; prozent Stueckzahl; usd Gegenwert.
    data class Bewegung(val kuerzel: String, val name: String, val art: String, val prozent: Double, val usd: Double) {
        val kauf: Boolean get() = art == "neu" || art == "mehr"
    }

    data class Quartal(
        val stichtag: Long,
        val eingereicht: Long,
        val wertUsd: Double,
        val positionen: Int,
        val abgedeckt: Double,
        val bewegungen: List<Bewegung>,
    )

    data class Investor(val quartale: List<Quartal>, val usd: Reihe, val eur: Reihe) {
        fun reihe(w: Waehrung): Reihe = if (w == Waehrung.USD) usd else eur
    }

    data class Datei(val stand: Long, val investoren: Map<String, Investor>)

    private val json = Json { ignoreUnknownKeys = true }

    // verlauf.json lesen; Fehler ergeben eine leere Datei.
    fun lesen(text: String): Datei = runCatching {
        val o = json.parseToJsonElement(text) as JsonObject
        val stand = iso((o["stand"] as? JsonPrimitive)?.content.orEmpty())
        val inv = (o["investoren"] as JsonObject).mapValues { (_, v) ->
            v as JsonObject
            val quartale = (v["quartale"] as JsonArray).map { q ->
                q as JsonObject
                fun d(n: String) = (q[n] as? JsonPrimitive)?.doubleOrNull ?: 0.0
                Quartal(
                    stichtag = iso((q["stichtag"] as JsonPrimitive).content),
                    eingereicht = iso((q["eingereicht"] as? JsonPrimitive)?.content.orEmpty()),
                    wertUsd = d("wertUsd"),
                    positionen = (q["positionen"] as? JsonPrimitive)?.intOrNull ?: 0,
                    abgedeckt = d("abgedeckt"),
                    bewegungen = (q["bewegungen"] as? JsonArray).orEmpty().mapNotNull { b ->
                        val a = b as? JsonArray ?: return@mapNotNull null
                        fun s(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.content.orEmpty()
                        fun z(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: 0.0
                        Bewegung(s(0), s(1), s(2), z(3), z(4))
                    },
                )
            }.sortedBy { it.stichtag }
            val w = v["woche"] as JsonObject
            val t = (w["t"] as JsonArray).map { iso((it as JsonPrimitive).content) }
            fun reihe(n: String) = (w[n] as JsonArray).mapIndexedNotNull { i, x -> (x as JsonPrimitive).doubleOrNull?.let { t.getOrNull(i)?.let { tt -> tt to it } } }
            Investor(quartale, reihe("usd"), reihe("eur"))
        }
        Datei(stand, inv)
    }.getOrDefault(Datei(0L, emptyMap()))

    // Marken der Kaeufe und Verkaeufe je Quartal (am Stichtag), mit den groessten Bewegungen im Text.
    fun quartalsMarken(inv: Investor, text: (Quartal, List<Bewegung>, Boolean) -> String): List<Triple<Long, String, Boolean>> =
        inv.quartale.drop(1).flatMap { q ->
            listOf(true, false).mapNotNull { kauf ->
                val b = q.bewegungen.filter { it.kauf == kauf }.sortedByDescending { kotlin.math.abs(it.usd) }
                if (b.isEmpty()) null else Triple(q.stichtag, text(q, b, kauf), kauf)
            }
        }

    // Reihe nach ihrem letzten Punkt fortschreiben: gewichte (Kuerzel, Anteil am Stichtag stichtag) treiben mit
    // den Tagesschlusskursen; ab dem letzten Punkt der Reihe wird deren Wert mit der Veraenderung dieses Bestands
    // weitergefuehrt. Kurse in Handelswaehrung (US-Dollar), Ergebnis in derselben Waehrung wie reihe in USD.
    fun fortschreiben(reihe: Reihe, stichtag: Long, gewichte: List<Pair<String, Double>>, kurse: Map<String, List<Candle>>): Reihe {
        if (reihe.isEmpty()) return reihe
        val ende = tag(reihe.last().first)
        val nutzbar = gewichte.mapNotNull { (k, w) ->
            val m = karte(kurse[k] ?: return@mapNotNull null)
            val p0 = kursAm(m, tag(stichtag)) ?: return@mapNotNull null
            Triple(m, w / p0, k)
        }
        if (nutzbar.isEmpty()) return reihe
        fun wert(d: Long) = nutzbar.sumOf { (m, f, _) -> (kursAm(m, d) ?: 0.0) * f }
        val basis = wert(ende).takeIf { it > 0 } ?: return reihe
        val tage = nutzbar.flatMap { it.first.keys.filter { d -> d > ende } }.toSortedSet()
        if (tage.isEmpty()) return reihe
        val letzter = reihe.last().second
        // Woechentlich wie die Datei, dazu der letzte Tag
        val aus = ArrayList(reihe)
        var zuletzt = ende
        for (d in tage) {
            if (d - zuletzt >= 7 || d == tage.last()) {
                aus += ms(d) to letzter * wert(d) / basis
                zuletzt = d
            }
        }
        return aus
    }

    // Abgeordnete aus Einzelgeschaeften

    // Ein Geschaeft fuer den Verlauf: Betrag in US-Dollar, ganz = vollstaendiger Verkauf.
    data class Handel(
        val t: Long, val kuerzel: String, val name: String, val kauf: Boolean, val betrag: Double, val ganz: Boolean, val option: Boolean,
        // Option mit Basispreis und Verfall; sie hat im Verlauf einen eigenen Wert.
        val derivat: Derivate.Derivat? = null,
    ) {
        // Position im Verlauf: bewertbare Optionen fuer sich, alles andere (auch Optionen ohne Angaben) beim Kuerzel.
        val position: String get() = derivat?.takeIf { it.bewertbar }?.schluessel ?: kuerzel
    }

    data class KongressVerlauf(
        // Wertindex in US-Dollar von 100 an, je Handelstag ein Punkt.
        val reihe: Reihe,
        // Geschaefte, die eingerechnet sind (mit Kurs).
        val handel: List<Handel>,
        // Geschaefte ohne Kurse (unbekanntes Kuerzel).
        val ohneKurs: Int,
        // Verkaeufe von Werten, die im Zeitraum nicht gekauft wurden.
        val ohneBestand: Int,
        // Bestand am Ende: Kuerzel und Anteil.
        val bestand: List<Pair<String, Double>>,
        // Eigener Hinweis unter dem Graphen.
        val hinweis: String? = null,
        // Bestand am Ende mit Wert und Einstand in US-Dollar, je Basiswert.
        val werte: List<Wert> = emptyList(),
    )

    // Geschaetzte Position am Ende: Wert und Einstand (Kaufbetraege, bei Teilverkaeufen anteilig gemindert) in US-Dollar.
    data class Wert(
        val kuerzel: String, val name: String, val usd: Double, val einstand: Double, val optionUsd: Double = 0.0,
        // Aufteilung nach Aktien und einzelnen Optionen, groesste zuerst (fuer die Unterzweige der Aktuellen Positionen).
        val teile: List<Teil> = emptyList(),
    )

    // Teil einer Position: derivat null fuer die Aktie selbst (auch Optionen ohne Basispreis und Verfall).
    data class Teil(val derivat: Derivate.Derivat?, val usd: Double, val einstand: Double)

    fun handelAus(g: Kongress.Geschaeft): Handel? {
        val d = Derivate.aus(g)
        // Strukturierte Produkte haben keinen Kurs; Optionen aus Papiermeldungen nennen den Basiswert nur im Namen
        if (d?.art == Derivate.Art.STRUKTUR) return null
        val k = g.kuerzel ?: d?.takeIf { it.bewertbar }?.basis ?: return null
        if (g.art == Kongress.Art.TAUSCH || g.typ !in setOf("ST", "OP", "EF", "OT")) return null
        if (g.datum <= 0L || g.mitte <= 0) return null
        return Handel(g.datum, k, g.wert, g.kauf, g.mitte, g.art == Kongress.Art.VERKAUF, g.typ == "OP" || d != null, d?.takeIf { it.bewertbar })
    }

    // Geschaefte fuer den Verlauf; ein Aktienkauf durch Ausuebung schliesst zusaetzlich die Option.
    fun handelAlle(g: Kongress.Geschaeft): List<Handel> {
        val h = handelAus(g) ?: return emptyList()
        val o = Derivate.ausuebung(g) ?: return listOf(h)
        return listOf(Handel(g.datum, h.kuerzel, g.wert, kauf = false, betrag = g.mitte, ganz = true, option = true, derivat = o), h)
    }

    // Zeitgewichteter Verlauf wie bei Fonds: Kaeufe und Verkaeufe veraendern den Index nicht, nur die Kursentwicklung
    // der gehaltenen Werte. Ein Kauf erwirbt Betrag / Schlusskurs des Tages (oder des letzten Handelstags davor) Stueck,
    // ein Teilverkauf gibt Betrag / Kurs Stueck ab (hoechstens den Bestand), ein Verkauf alles. Ohne Bestand bleibt der
    // Index stehen. kurse je Kuerzel in Handelswaehrung. Optionen mit Basispreis und Verfall bekommen eine eigene
    // Kursreihe aus dem Basiswert (Derivate.reihe).
    fun kongress(handel: List<Handel>, kurse: Map<String, List<Candle>>, bis: Long): KongressVerlauf? {
        val karten = HashMap<String, TreeMap<Long, Double>>()
        for (h in handel.map { it.kuerzel }.distinct()) kurse[h]?.takeIf { it.isNotEmpty() }?.let { karten[h] = karte(it) }
        for (h in handel) {
            val d = h.derivat ?: continue
            if (h.position !in karten) karten[h.kuerzel]?.let { basis -> Derivate.reihe(d, basis).takeIf { it.isNotEmpty() }?.let { karten[h.position] = it } }
        }
        val mitKurs = handel.filter { h -> karten[h.position]?.let { kursAm(it, tag(h.t)) ?: it.ceilingEntry(tag(h.t))?.value } != null }
            .sortedBy { it.t }
        val ohneKurs = handel.size - mitKurs.size
        val ersterKauf = mitKurs.firstOrNull { it.kauf } ?: return KongressVerlauf(emptyList(), mitKurs, ohneKurs, 0, emptyList())
        val start = tag(ersterKauf.t)
        val ende = tag(bis)
        val tage = karten.values.flatMap { m -> m.subMap(start, true, ende, true).keys }.toSortedSet().toList()
        if (tage.isEmpty()) return null
        val stueck = HashMap<String, Double>()
        val kosten = HashMap<String, Double>()
        val letzter = HashMap<String, Double>()
        var index = 100.0
        var vorher = 0.0
        var ohneBestand = 0
        var i = mitKurs.indexOf(ersterKauf)
        val reihe = ArrayList<Pair<Long, Double>>()
        fun wert() = stueck.entries.sumOf { (k, n) -> n * (letzter[k] ?: 0.0) }
        for (d in tage) {
            for ((k, m) in karten) m[d]?.let { letzter[k] = it }
            val vor = wert()
            if (vorher > 0) index *= vor / vorher
            while (i < mitKurs.size && tag(mitKurs[i].t) <= d) {
                val h = mitKurs[i++]
                val p = h.position
                val m = karten.getValue(p)
                val kurs = kursAm(m, tag(h.t)) ?: m.ceilingEntry(tag(h.t))!!.value
                if (letzter[p] == null) letzter[p] = kurs
                val hat = stueck[p] ?: 0.0
                if (h.kauf) { stueck[p] = hat + h.betrag / kurs; kosten[p] = (kosten[p] ?: 0.0) + h.betrag }
                else if (hat <= 1e-9) ohneBestand++
                else {
                    val rest = if (h.ganz) 0.0 else (hat - h.betrag / kurs).coerceAtLeast(0.0)
                    stueck[p] = rest
                    kosten[p] = (kosten[p] ?: 0.0) * (rest / hat)
                }
            }
            vorher = wert()
            reihe += ms(d) to index
        }
        val summe = wert()
        // Optionen zaehlen im Bestand bei ihrem Basiswert
        val basisVon = mitKurs.associate { it.position to it.kuerzel }
        val bestand = if (summe <= 0) emptyList() else stueck.filterValues { it > 1e-9 }
            .map { (k, n) -> (basisVon[k] ?: k) to n * (letzter[k] ?: 0.0) / summe }
            .groupBy({ it.first }, { it.second }).map { (k, l) -> k to l.sum() }
            .filter { it.second > 0 }.sortedByDescending { it.second }
        val namen = mitKurs.associate { it.kuerzel to it.name }
        val derivatVon = mitKurs.filter { it.derivat?.bewertbar == true }.associate { it.position to it.derivat!!.copy(kontrakte = null) }
        val werte = stueck.filterValues { it > 1e-9 }.entries
            .groupBy({ basisVon[it.key] ?: it.key }, { Teil(derivatVon[it.key], it.value * (letzter[it.key] ?: 0.0), kosten[it.key] ?: 0.0) })
            .map { (k, l) ->
                Wert(
                    k, namen[k] ?: k, l.sumOf { it.usd }, l.sumOf { it.einstand }, l.filter { it.derivat != null }.sumOf { it.usd },
                    l.filter { it.usd > 0 }.sortedByDescending { it.usd },
                )
            }
            .filter { it.usd > 0 }.sortedByDescending { it.usd }
        return KongressVerlauf(reihe, mitKurs, ohneKurs, ohneBestand, bestand, werte = werte)
    }

    // Reihen umrechnen und vergleichen

    // Kurse als Karte Tag -> Schlusskurs.
    fun karte(kerzen: List<Candle>): TreeMap<Long, Double> = TreeMap<Long, Double>().apply {
        kerzen.forEach { if (it.close.isFinite() && it.close > 0) put(tag(it.t), it.close) }
    }

    // Schlusskurs am Tag d oder am letzten Handelstag davor.
    fun kursAm(m: TreeMap<Long, Double>, d: Long): Double? = m.floorEntry(d)?.value

    // Reihe aus Tagesschlusskursen, ein Punkt je Handelstag, etwa fuer Vergleichsindizes.
    fun ausKursen(kerzen: List<Candle>): Reihe = karte(kerzen).map { (d, v) -> ms(d) to v }

    // Reihe von von nach nach umrechnen, mit dem Kurs fx (US-Dollar je Euro) des jeweiligen Tages; vor dem
    // ersten bekannten Kurs gilt der erste. Ohne Kurse bleibt die Reihe, wie sie ist.
    fun umrechnen(reihe: Reihe, von: Waehrung, nach: Waehrung, fx: List<Candle>): Reihe {
        if (von == nach || reihe.isEmpty()) return reihe
        val m = karte(fx)
        if (m.isEmpty()) return reihe
        return reihe.map { (t, v) ->
            val k = kursAm(m, tag(t)) ?: m.firstEntry().value
            t to if (nach == Waehrung.EUR) v / k else v * k
        }
    }

    // Ausschnitt ab von (ms), auf 100 am ersten Punkt gesetzt; liegt von vor der Reihe, ab ihrem Anfang.
    fun fenster(reihe: Reihe, von: Long): Reihe {
        val ab = reihe.dropWhile { it.first < von }
        // Wert am Anfang des Zeitraums: letzter Punkt davor, damit alle Linien am selben Tag bei 100 beginnen
        val vorher = reihe.lastOrNull { it.first <= von }
        val basis = (vorher?.second ?: ab.firstOrNull()?.second)?.takeIf { it > 0 } ?: return emptyList()
        val start = if (vorher != null && (ab.isEmpty() || ab.first().first > von)) listOf(von to vorher.second) else emptyList()
        return (start + ab).map { (t, v) -> t to v / basis * 100.0 }
    }

    data class Kennzahlen(val gesamt: Double, val proJahr: Double?, val maxVerlust: Double, val schwankung: Double?)

    // Gesamtrendite, Rendite pro Jahr (ab etwa einem Jahr), groesster Rueckgang vom Hoch und Schwankung pro Jahr.
    fun kennzahlen(reihe: Reihe): Kennzahlen? {
        if (reihe.size < 2) return null
        val a = reihe.first().second
        val e = reihe.last().second
        if (a <= 0) return null
        val tage = (reihe.last().first - reihe.first().first) / 86_400_000.0
        var hoch = a
        var verlust = 0.0
        val renditen = ArrayList<Double>()
        for (i in reihe.indices) {
            val v = reihe[i].second
            hoch = maxOf(hoch, v)
            verlust = minOf(verlust, v / hoch - 1)
            if (i > 0 && reihe[i - 1].second > 0) renditen += v / reihe[i - 1].second - 1
        }
        val abstand = tage / (reihe.size - 1).coerceAtLeast(1)
        val schwankung = if (renditen.size >= 8 && abstand > 0) {
            val m = renditen.average()
            sqrt(renditen.sumOf { (it - m) * (it - m) } / (renditen.size - 1)) * sqrt(365.25 / abstand)
        } else null
        return Kennzahlen(
            gesamt = e / a - 1,
            proJahr = if (tage >= 330) (e / a).pow(365.25 / tage) - 1 else null,
            maxVerlust = verlust,
            schwankung = schwankung,
        )
    }
}
