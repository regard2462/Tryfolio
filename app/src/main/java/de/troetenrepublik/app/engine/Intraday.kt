package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random

// Intraday-Rueckblick (0.78) auf 5-Minuten-Kerzen der letzten Wochen, je Wert am Hauptplatz.
// Teil 1: Ausbruch aus der Eroeffnungsspanne. Die Spanne ist Hoch und Tief der ersten Minuten der Hauptsitzung. Das
// Signal ist die erste abgeschlossene Kerze mit Schluss ausserhalb der Spanne, gehandelt wird zur Eroeffnung der
// naechsten Kerze, hoechstens einmal am Tag. Ausstieg am Stopp, am Ziel (Vielfaches des Risikos) oder zehn Minuten vor
// Handelsschluss. Trifft eine Kerze Stopp und Ziel, zaehlt der Stopp.
// Teil 2: der Rest des Tages als Wiederholung vergangener Kerzen derselben Tageszeit.
// Alle Ergebnisse sind Anteile des Einsatzes nach Spread und Ordergebuehr, die Waehrung spielt keine Rolle.
object Intraday {
    const val KERZE_MS = 300_000L
    private const val MIN_MS = 60_000L

    // Fester Einsatz je Geschaeft, an ihm bemisst sich die Ordergebuehr
    const val EINSATZ = StrategieLabor.START

    // Tage mit weniger Kerzen zaehlen nicht (Handelsaussetzung, Luecke im Abruf)
    const val MIN_KERZEN = 12

    // Ab so vielen Handelstagen wird gerechnet
    const val MIN_TAGE = 10

    const val LAEUFE = 8_000

    // Nachbarkerzen derselben Tageszeit, aus denen eine Rendite gezogen wird
    const val NACHBARN = 2

    // ------------------------------------------------------------ Handelsplatz und Handelstage

    // Hauptplatz eines Werts mit seinen Handelszeiten; land ist das Kuerzel fuer die Boersenfeiertage.
    class Platz(val land: String, val handel: Boersenzeiten.Handel)

    // Boersenland weiterer Kuerzel-Endungen, soweit Alarmarten.land sie nicht kennt
    private val ENDUNGEN = mapOf(
        "TO" to "CA", "V" to "CA", "MX" to "MX", "SA" to "BR", "BA" to "AR", "SN" to "CL", "IR" to "IE", "MI" to "IT", "MC" to "ES",
        "SW" to "CH", "VI" to "AT", "ST" to "SE", "CO" to "DK", "HE" to "FI", "OL" to "NO", "WA" to "PL", "PR" to "CZ", "BD" to "HU",
        "AT" to "GR", "IS" to "TR", "TA" to "IL", "SR" to "SA", "JO" to "ZA", "NS" to "IN", "BO" to "IN", "SS" to "CN", "SZ" to "CN",
        "HK" to "HK", "KS" to "KR", "KQ" to "KR", "TW" to "TW", "TWO" to "TW", "SI" to "SG", "KL" to "MY", "BK" to "TH", "JK" to "ID",
        "AX" to "AU", "NZ" to "NZ", "RO" to "RO", "TL" to "EE", "RG" to "LV", "VS" to "LT", "IC" to "IS", "QA" to "QA", "CA" to "EG",
    )

    // Hauptplatz nach der Endung des Kuerzels, ohne Endung die US-Boersen. null bei Indizes, Waehrungen, Terminkontrakten,
    // Werten anderer Kursquellen und unbekannten Endungen.
    fun platz(symbol: String): Platz? {
        if (symbol.isBlank() || symbol.any { it == ':' || it == '^' || it == '=' }) return null
        val land = Alarmarten.land(symbol).ifEmpty { ENDUNGEN[symbol.substringAfterLast('.').uppercase()] ?: return null }
        return Boersenzeiten.NACH_LAND[land]?.let { Platz(land, it) }
    }

    // Ein Handelstag: Beginn und Ende der Hauptsitzung und ihre Kerzen in zeitlicher Folge.
    class Tag(val offen: Long, val schluss: Long, val kerzen: List<Candle>) {
        val schritte: Int get() = ((schluss - offen) / KERZE_MS).toInt()
        fun schritt(k: Candle): Int = ((k.t - offen) / KERZE_MS).toInt()

        // Letzte Kerze, die zehn Minuten vor Handelsschluss beginnt; an ihrem Schluss wird glattgestellt. An einem
        // verkuerzten Tag ohne spaetere Kerzen die letzte vorhandene.
        val letzte: Int get() = kerzen.indexOfLast { it.t + 2 * KERZE_MS <= schluss }
    }

    // Abgeschlossene Handelstage aus den Kerzen: je Kalendertag am Platz die Kerzen der Hauptsitzung (bei einer
    // Mittagspause von der ersten Eroeffnung bis zum letzten Schluss). Wochenenden, Boersenfeiertage, der laufende Tag
    // und Tage mit zu wenigen Kerzen fallen weg. Sommerzeit folgt aus der Zeitzone des Platzes.
    fun tage(kerzen: List<Candle>, platz: Platz, jetzt: Long): List<Tag> {
        val zone = platz.handel.zone
        val gut = kerzen.filter { it.close.isFinite() && it.close > 0 && it.high >= it.low && it.low > 0 }
        return gut.groupBy { Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() }.entries.sortedBy { it.key }.mapNotNull { (datum, k) ->
            if (datum.dayOfWeek !in platz.handel.tage || Boersenzeiten.feiertag(platz.land, datum)) return@mapNotNull null
            val sitzungen = platz.handel.sitzungenAm(datum).ifEmpty { return@mapNotNull null }
            val offen = datum.atTime(sitzungen.first().first).atZone(zone).toInstant().toEpochMilli()
            val schluss = datum.atTime(sitzungen.last().second).atZone(zone).toInstant().toEpochMilli()
            if (schluss > jetzt) return@mapNotNull null
            val drin = k.filter { it.t >= offen && it.t < schluss }.sortedBy { it.t }.distinctBy { it.t }
            if (drin.size < MIN_KERZEN) null else Tag(offen, schluss, drin)
        }
    }

    // ------------------------------------------------------------ Teil 1: Ausbruch aus der Eroeffnungsspanne

    // GEGENSEITE: bei Long das Spannentief, bei Short das Spannenhoch
    enum class Stopp { MITTE, GEGENSEITE, PROZENT }

    data class Regel(
        val spanneMin: Int = 15,
        val stopp: Stopp = Stopp.MITTE,
        // Abstand des festen Stopps vom Einstieg als Anteil
        val stoppAnteil: Double = 0.005,
        // Ziel als Vielfaches des Risikos
        val ziel: Double = 2.0,
        val short: Boolean = false,
        // Signale nur bis so viele Minuten vor Handelsschluss
        val bisVorSchlussMin: Int = 150,
        // Kleinster Abstand von Einstieg und Stopp als Anteil des Einstiegs; darunter entfaellt das Geschaeft
        val mindestRisiko: Double = 0.001,
    )

    enum class Ende { ZIEL, STOPP, SCHLUSS }

    // r und ergebnis nach Kosten: r in Vielfachen des Risikos, ergebnis als Anteil des Einsatzes
    data class Geschaeft(
        val long: Boolean, val einstiegT: Long, val ausstiegT: Long, val einstieg: Double, val stopp: Double, val ziel: Double,
        val ausstieg: Double, val ende: Ende, val r: Double, val ergebnis: Double,
    )

    // Ergebnis eines Geschaefts als Anteil des Einsatzes: Kauf zum Brief, Verkauf zum Geld, je Order die Gebuehr.
    fun nachKosten(long: Boolean, einstieg: Double, ausstieg: Double, kosten: StrategieLabor.Kosten): Double {
        val geb = kosten.gebuehr / EINSATZ
        return if (long) (1 - geb) * Pricing.bid(ausstieg, kosten.spreadBps) / Pricing.ask(einstieg, kosten.spreadBps) - geb - 1
        else (1 - geb) * (2 - Pricing.ask(ausstieg, kosten.spreadBps) / Pricing.bid(einstieg, kosten.spreadBps)) - geb - 1
    }

    // Noetige Trefferquote, damit Gewinne von ziel Risiken die Verluste von je einem Risiko ausgleichen.
    fun noetigeQuote(ziel: Double): Double = 1.0 / (1.0 + ziel)

    // Das Geschaeft eines Tages oder null: kein Ausbruch im Zeitfenster, keine Kerze danach oder Risiko zu klein.
    fun ausbruch(tag: Tag, regel: Regel, kosten: StrategieLabor.Kosten): Geschaeft? {
        val k = tag.kerzen
        val endeSpanne = tag.offen + regel.spanneMin * MIN_MS
        val spanne = k.filter { it.t + KERZE_MS <= endeSpanne }.ifEmpty { return null }
        val hoch = spanne.maxOf { it.high }
        val tief = spanne.minOf { it.low }
        val letzte = tag.letzte
        val bis = tag.schluss - regel.bisVorSchlussMin * MIN_MS
        for (i in spanne.size until k.size) {
            if (k[i].t + KERZE_MS > bis) return null
            val long = when {
                k[i].close > hoch -> true
                regel.short && k[i].close < tief -> false
                else -> continue
            }
            // Gehandelt wird zur Eroeffnung der naechsten Kerze
            val e = i + 1
            if (e > letzte) return null
            val einstieg = k[e].open
            val stopp = when (regel.stopp) {
                Stopp.MITTE -> (hoch + tief) / 2
                Stopp.GEGENSEITE -> if (long) tief else hoch
                Stopp.PROZENT -> einstieg * (if (long) 1 - regel.stoppAnteil else 1 + regel.stoppAnteil)
            }
            val risiko = if (long) einstieg - stopp else stopp - einstieg
            if (risiko <= 0 || risiko / einstieg < regel.mindestRisiko) return null
            val ziel = if (long) einstieg + regel.ziel * risiko else einstieg - regel.ziel * risiko
            var ausstieg = k[letzte].close
            var ausstiegT = k[letzte].t + KERZE_MS
            var ende = Ende.SCHLUSS
            for (j in e..letzte) {
                val c = k[j]
                val amStopp = if (long) c.low <= stopp else c.high >= stopp
                val amZiel = if (long) c.high >= ziel else c.low <= ziel
                if (!amStopp && !amZiel) continue
                // Stopp vor Ziel. Eroeffnet die Kerze schon jenseits des Stopps, gilt die Eroeffnung.
                ende = if (amStopp) Ende.STOPP else Ende.ZIEL
                ausstieg = if (!amStopp) ziel else if (long) minOf(c.open, stopp) else maxOf(c.open, stopp)
                ausstiegT = c.t
                break
            }
            val ergebnis = nachKosten(long, einstieg, ausstieg, kosten)
            return Geschaeft(long, k[e].t, ausstiegT, einstieg, stopp, ziel, ausstieg, ende, ergebnis / (risiko / einstieg), ergebnis)
        }
        return null
    }

    class Ergebnis(
        val geschaefte: List<Geschaeft>, val tage: Int, val von: Long, val bis: Long,
        // Anteil der Geschaefte mit Gewinn nach Kosten
        val trefferquote: Double,
        val mittelR: Double, val mittel: Double, val noetig: Double,
        // Summen als Anteil des festen Einsatzes: die Regel, jeden Morgen kaufen und am Abend verkaufen, einmal kaufen und halten
        val summe: Double, val morgensAbends: Double, val halten: Double,
    ) {
        val jahre: Double get() = (bis - von) / (365.0 * 86_400_000.0)
        val aussage: StrategieLabor.Aussage get() = StrategieLabor.aussage(geschaefte.size, jahre)
    }

    // null bei weniger als MIN_TAGE Handelstagen.
    fun pruefen(tage: List<Tag>, regel: Regel, kosten: StrategieLabor.Kosten): Ergebnis? {
        if (tage.size < MIN_TAGE) return null
        val g = tage.mapNotNull { ausbruch(it, regel, kosten) }
        val morgens = tage.sumOf { nachKosten(true, it.kerzen.first().open, it.kerzen[it.letzte].close, kosten) }
        val halten = nachKosten(true, tage.first().kerzen.first().open, tage.last().let { it.kerzen[it.letzte].close }, kosten)
        return Ergebnis(
            g, tage.size, tage.first().offen, tage.last().schluss,
            if (g.isEmpty()) 0.0 else g.count { it.ergebnis > 0 } / g.size.toDouble(),
            if (g.isEmpty()) 0.0 else g.sumOf { it.r } / g.size, if (g.isEmpty()) 0.0 else g.sumOf { it.ergebnis } / g.size,
            noetigeQuote(regel.ziel), g.sumOf { it.ergebnis }, morgens, halten,
        )
    }

    // ------------------------------------------------------------ Teil 2: der Rest des Tages

    // Quantile des Faechers
    val QUANTILE = doubleArrayOf(0.05, 0.25, 0.5, 0.75, 0.95)

    class Rest(
        val laeufe: Int, val tage: Int, val schritte: Int,
        val ziel: Double, val stopp: Double, val offen: Double,
        // Mittleres Ergebnis nach Kosten als Anteil des Einsatzes und in Vielfachen des Risikos
        val mittel: Double, val mittelR: Double, val noetig: Double,
        // Je Quantil der Stand nach jedem Schritt als Anteil des Einstiegs (Einstieg = 1), beginnend mit dem Einstieg
        val faecher: List<DoubleArray>,
    )

    // Wiederholt den Rest des Handelstags ab der Kerze abSchritt (gezaehlt ab Eroeffnung) fuer einen Kauf mit Stopp
    // stoppAnteil unter dem Einstieg und Ziel beim zielVielfachen des Risikos. Jeder Schritt zieht eine vergangene
    // Kerze derselben Tageszeit (plus/minus NACHBARN Kerzen) mit Hoch, Tief und Schluss gegen den Schluss davor; der
    // Stopp wird vor dem Ziel geprueft. Glattgestellt wird wie in Teil 1 zehn Minuten vor Schluss. Der Startwert des
    // Zufallsgenerators ist fest, gleiche Eingaben geben gleiche Ergebnisse. null ohne genug Tage oder ohne Restzeit.
    fun rest(
        tage: List<Tag>, abSchritt: Int, stoppAnteil: Double, zielVielfach: Double, kosten: StrategieLabor.Kosten,
        laeufe: Int = LAEUFE, startwert: Long = 20_261_112L, abbruch: () -> Unit = {},
    ): Rest? {
        if (tage.size < MIN_TAGE || stoppAnteil <= 0 || zielVielfach <= 0 || laeufe < 1) return null
        // Die letzte Kerze des Tages bleibt aussen vor (glatt zehn Minuten vor Schluss)
        val ende = tage.maxOf { it.schritte } - 1
        val ab = abSchritt.coerceAtLeast(0)
        if (ende - ab < 2) return null
        // Je Tageszeit die vergangenen Kerzen als Hoch, Tief und Schluss gegen den Schluss davor
        val hoch = Array(ende) { ArrayList<Double>() }
        val tief = Array(ende) { ArrayList<Double>() }
        val schluss = Array(ende) { ArrayList<Double>() }
        for (t in tage) {
            var davor = t.kerzen.first().open
            for (c in t.kerzen) {
                val s = t.schritt(c)
                if (s in 0 until ende && davor > 0) { hoch[s] += c.high / davor; tief[s] += c.low / davor; schluss[s] += c.close / davor }
                davor = c.close
            }
        }
        // Auswahl je Schritt: Tageszeit und Nachbarn als Paare (Tageszeit, Stelle)
        val wahl = Array(ende) { s ->
            val von = (s - NACHBARN).coerceAtLeast(0)
            val bis = (s + NACHBARN).coerceAtMost(ende - 1)
            val n = (von..bis).sumOf { schluss[it].size }
            val zeit = IntArray(n)
            val stelle = IntArray(n)
            var i = 0
            for (z in von..bis) for (j in schluss[z].indices) { zeit[i] = z; stelle[i] = j; i++ }
            zeit to stelle
        }
        val n = ende - ab
        val stoppKurs = 1 - stoppAnteil
        val zielKurs = 1 + zielVielfach * stoppAnteil
        val pfade = Array(n + 1) { FloatArray(laeufe) }
        val zufall = Random(startwert)
        var ziele = 0
        var stopps = 0
        var summe = 0.0
        for (l in 0 until laeufe) {
            if (l % 500 == 0) abbruch()
            var kurs = 1.0
            var fertig = false
            pfade[0][l] = 1f
            for (i in 0 until n) {
                if (!fertig) {
                    val (zeit, stelle) = wahl[ab + i]
                    if (zeit.isNotEmpty()) {
                        val z = zufall.nextInt(zeit.size)
                        val s = zeit[z]
                        val j = stelle[z]
                        if (kurs * tief[s][j] <= stoppKurs) {
                            // Eroeffnungsluecken gibt es hier nicht, der Stopp gilt zum Stoppkurs
                            kurs = stoppKurs; fertig = true; stopps++
                        } else if (kurs * hoch[s][j] >= zielKurs) {
                            kurs = zielKurs; fertig = true; ziele++
                        } else kurs *= schluss[s][j]
                    }
                }
                pfade[i + 1][l] = kurs.toFloat()
            }
            summe += nachKosten(true, 1.0, kurs, kosten)
        }
        val faecher = QUANTILE.map { DoubleArray(n + 1) }
        for (i in 0..n) {
            val sortiert = pfade[i].also { it.sort() }
            QUANTILE.forEachIndexed { q, anteil -> faecher[q][i] = sortiert[((laeufe - 1) * anteil).toInt()].toDouble() }
        }
        val mittel = summe / laeufe
        return Rest(
            laeufe, tage.size, n, ziele / laeufe.toDouble(), stopps / laeufe.toDouble(), (laeufe - ziele - stopps) / laeufe.toDouble(),
            mittel, mittel / stoppAnteil, noetigeQuote(zielVielfach), faecher,
        )
    }

    // Kerze der Tageszeit jetzt am juengsten Handelsplan des Platzes, gezaehlt ab Eroeffnung; null ausserhalb der Sitzung.
    fun schrittJetzt(platz: Platz, jetzt: Long): Int? {
        val zone = platz.handel.zone
        val datum = Instant.ofEpochMilli(jetzt).atZone(zone).toLocalDate()
        if (datum.dayOfWeek !in platz.handel.tage || Boersenzeiten.feiertag(platz.land, datum)) return null
        val sitzungen = platz.handel.sitzungenAm(datum).ifEmpty { return null }
        val offen = datum.atTime(sitzungen.first().first).atZone(zone).toInstant().toEpochMilli()
        val schluss = datum.atTime(sitzungen.last().second).atZone(zone).toInstant().toEpochMilli()
        return if (jetzt in offen until schluss) ((jetzt - offen) / KERZE_MS).toInt() else null
    }

    // Betrag kleiner als ein Hundertstel Prozent gilt als null (fuer die Faerbung)
    fun vorzeichen(v: Double): Int = if (abs(v) < 0.00005) 0 else if (v > 0) 1 else -1
}
