package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.math.sqrt

// Copytrading: bekannte Investoren mit ihren Bestandsmeldungen (Form 13F der SEC) und Anlagestrategien
// als Musterportfolios aus ETFs. Die Abgeordneten des US-Repraesentantenhauses stehen in Kongress.
// 13F: Verwalter mit mehr als 100 Mio. Dollar in US-Aktien melden ihre Bestaende vierteljaehrlich, spaetestens 45 Tage
// nach Quartalsende. Die Meldungen zeigen nur US-boersennotierte Werte (keine Barmittel, keine Leerverkaeufe, keine
// Auslandsaktien); Optionen stehen mit dem Wert der zugrunde liegenden Aktien. Die App bringt die letzten zwei
// Quartale mit (assets/copytrading/investoren.json, aus den Meldungen bei der SEC) und prueft, ob die SEC eine
// neuere Meldung hat; die Bestandslisten selbst gibt die SEC nur an Abrufer mit Kontaktangabe heraus, die die App
// nicht mitschickt.
object Investoren {

    @Serializable
    data class Position(
        val name: String,
        val kuerzel: String? = null,
        val cusip: String,
        val wert: Double,
        val stueck: Double,
        // aktie, call, put, anleihe
        val art: String = "aktie",
    )

    @Serializable
    data class Quartal(val stichtag: String, val eingereicht: String, val summe: Double, val positionen: List<Position>)

    @Serializable
    data class Investor(
        val id: String,
        val name: String,
        val firma: String,
        val cik: String,
        val stilDe: String,
        val stilEn: String,
        val quartale: List<Quartal>,
    ) {
        val aktuell: Quartal? get() = quartale.firstOrNull()
        val vorher: Quartal? get() = quartale.getOrNull(1)
    }

    @Serializable
    data class Datei(val stand: String, val investoren: List<Investor>)

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): List<Investor> = runCatching { json.decodeFromString<Datei>(text).investoren }.getOrDefault(emptyList())

    enum class Wandel { NEU, ERHOEHT, VERRINGERT, GLEICH, VERKAUFT }

    data class Zeile(val position: Position, val anteil: Double, val wandel: Wandel, val stueckVorher: Double)

    // Positionen des aktuellen Quartals mit Anteil am Gesamtwert und der Veraenderung gegenueber dem Vorquartal
    // (nach Stueckzahl, gleiche CUSIP und Art); dahinter, was im Vorquartal noch da war und jetzt fehlt.
    fun zeilen(inv: Investor): List<Zeile> {
        val q = inv.aktuell ?: return emptyList()
        val alt = inv.vorher?.positionen?.associateBy { it.cusip + it.art }.orEmpty()
        val summe = q.summe.takeIf { it > 0 } ?: q.positionen.sumOf { it.wert }
        val jetzt = q.positionen.map { p ->
            val a = alt[p.cusip + p.art]
            val w = when {
                inv.vorher == null -> Wandel.GLEICH
                a == null -> Wandel.NEU
                p.stueck > a.stueck * 1.005 -> Wandel.ERHOEHT
                p.stueck < a.stueck * 0.995 -> Wandel.VERRINGERT
                else -> Wandel.GLEICH
            }
            Zeile(p, p.wert / summe, w, a?.stueck ?: 0.0)
        }.sortedByDescending { it.anteil }
        val neu = q.positionen.map { it.cusip + it.art }.toSet()
        val weg = inv.vorher?.positionen.orEmpty().filter { it.cusip + it.art !in neu }
            .sortedByDescending { it.wert }.map { Zeile(it, 0.0, Wandel.VERKAUFT, it.stueck) }
        return jetzt + weg
    }

    // Gewichte zum Nachbilden: Aktien mit Kuerzel, groesste zuerst, hoechstens anzahl; Summe 1.
    fun gewichte(inv: Investor, anzahl: Int): List<Pair<String, Double>> {
        val q = inv.aktuell ?: return emptyList()
        val aktien = q.positionen.filter { it.art == "aktie" && !it.kuerzel.isNullOrBlank() }
            .groupBy { it.kuerzel!! }.map { (k, l) -> k to l.sumOf { it.wert } }
            .sortedByDescending { it.second }.take(anzahl)
        val s = aktien.sumOf { it.second }
        return if (s <= 0) emptyList() else aktien.map { it.first to it.second / s }
    }

    // Neueste 13F-HR in der Liste der Meldungen der SEC (data.sec.gov/submissions): Stichtag und Tag der Meldung.
    fun neuesteMeldung(submissionsJson: String): Pair<String, String>? = runCatching {
        val o = json.parseToJsonElement(submissionsJson) as kotlinx.serialization.json.JsonObject
        val r = (o["filings"] as kotlinx.serialization.json.JsonObject)["recent"] as kotlinx.serialization.json.JsonObject
        fun liste(n: String) = (r[n] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        val form = liste("form")
        val stichtag = liste("reportDate")
        val tag = liste("filingDate")
        form.indices.filter { form[it] == "13F-HR" }.maxByOrNull { stichtag[it] }?.let { stichtag[it] to tag[it] }
    }.getOrNull()

    fun secAdresse(cik: String) = "https://data.sec.gov/submissions/CIK${cik.padStart(10, '0')}.json"
    fun secSeite(cik: String) = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${cik.trimStart('0')}&type=13F-HR"
}

// Anlagestrategien als Musterportfolios aus ETFs des Katalogs (Xetra, Euro). Die Kennzahlen rechnet die App aus den
// Tagesschlusskursen von Yahoo mit taeglich gleichen Gewichten (vereinfachte, laufende Umschichtung), ohne Kosten
// und Steuern. Sie beschreiben die Vergangenheit und sagen nichts ueber die Zukunft.
object Strategien {

    enum class Risiko { DEFENSIV, AUSGEWOGEN, OFFENSIV }

    data class Strategie(
        val id: String,
        val nameDe: String,
        val nameEn: String,
        val textDe: String,
        val textEn: String,
        val risiko: Risiko,
        val anteile: List<Pair<String, Double>>,
    )

    val alle: List<Strategie> = listOf(
        Strategie(
            "dauer", "Permanent Portfolio", "Permanent Portfolio",
            "Nach Harry Browne (1981): je ein Viertel Aktien, lange Staatsanleihen, Gold und Geldmarkt. Jeder Teil soll in einer anderen Wirtschaftslage tragen: Aufschwung, Deflation, Inflation, Rezession. Hier mit langen US-Staatsanleihen wie im Original.",
            "After Harry Browne (1981): one quarter each in shares, long-term government bonds, gold and money market. Each part is meant to hold up in a different economic climate: growth, deflation, inflation, recession. Here with long-term US Treasuries as in the original.",
            Risiko.DEFENSIV, listOf("EUNL.DE" to 0.25, "IS04.DE" to 0.25, "4GLD.DE" to 0.25, "XEON.DE" to 0.25),
        ),
        Strategie(
            "allwetter", "All-Weather (vereinfacht)", "All Weather (simplified)",
            "Vereinfachte, öffentlich beschriebene Fassung des Ansatzes von Ray Dalio: 30 % Aktien, 40 % lange und 15 % mittlere Anleihen, je 7,5 % Gold und Rohstoffe. Die Anteile richten sich danach, wie stark die Teile schwanken, nicht nach ihrem Wert.",
            "Simplified, publicly described version of Ray Dalio's approach: 30% shares, 40% long-term and 15% intermediate bonds, 7.5% each in gold and commodities. The weights follow how much the parts fluctuate, not their value.",
            Risiko.DEFENSIV, listOf("EUNL.DE" to 0.30, "IS04.DE" to 0.40, "IUSM.DE" to 0.15, "4GLD.DE" to 0.075, "SXRS.DE" to 0.075),
        ),
        Strategie(
            "rente", "Defensiv 30/70", "Defensive 30/70",
            "Für kurze Anlagedauer oder die Entnahme im Ruhestand: 30 % weltweite Aktien, 40 % Euro-Staatsanleihen, 20 % Euro-Unternehmensanleihen und 10 % Geldmarkt. Schwankt deutlich weniger als ein reines Aktienportfolio, bringt aber auch weniger.",
            "For a short horizon or withdrawals in retirement: 30% global shares, 40% euro government bonds, 20% euro corporate bonds and 10% money market. Fluctuates far less than an all-equity portfolio, but also earns less.",
            Risiko.DEFENSIV, listOf("EUNL.DE" to 0.30, "XGLE.DE" to 0.40, "D5BG.DE" to 0.20, "XEON.DE" to 0.10),
        ),
        Strategie(
            "minvol", "Schwankungsarme Aktien", "Minimum volatility shares",
            "Weltweite Aktien, bei denen der Index die Werte so auswählt, dass das Portfolio möglichst wenig schwankt. Fällt in Krisen meist weniger als der breite Markt und steigt in starken Jahren oft weniger.",
            "Global shares selected by the index so that the portfolio fluctuates as little as possible. Usually falls less than the broad market in crises and often rises less in strong years.",
            Risiko.DEFENSIV, listOf("IQQ0.DE" to 1.0),
        ),
        Strategie(
            "klassisch", "Klassisch 60/40", "Classic 60/40",
            "Der Klassiker: 60 % weltweite Aktien, 40 % Euro-Staatsanleihen. Die Anleihen dämpfen Rückgänge der Aktien, in Jahren mit steigenden Zinsen (2022) fielen aber beide.",
            "The classic: 60% global shares, 40% euro government bonds. The bonds cushion falls in shares, but in years of rising interest rates (2022) both fell.",
            Risiko.AUSGEWOGEN, listOf("EUNL.DE" to 0.60, "XGLE.DE" to 0.40),
        ),
        Strategie(
            "butterfly", "Golden Butterfly", "Golden Butterfly",
            "Fünf gleiche Teile: Aktien, Nebenwerte, lange und kurze Staatsanleihen, Gold. Eine Abwandlung des Permanent Portfolio mit mehr Aktien.",
            "Five equal parts: shares, small caps, long and short government bonds, gold. A variant of the Permanent Portfolio with more shares.",
            Risiko.AUSGEWOGEN, listOf("EUNL.DE" to 0.20, "ZPRS.DE" to 0.20, "IS04.DE" to 0.20, "IUSU.DE" to 0.20, "4GLD.DE" to 0.20),
        ),
        Strategie(
            "dividende", "Dividendenstrategie", "Dividend strategy",
            "Unternehmen mit hohen oder seit Jahren steigenden Ausschüttungen: weltweite Dividenden-Aristokraten, hohe Dividendenrendite weltweit und Europa. Mehr Banken, Versorger und Konsumwerte, weniger Technologie als der Weltmarkt.",
            "Companies with high or steadily rising payouts: global dividend aristocrats, global high dividend yield and Europe. More banks, utilities and consumer staples, less technology than the world market.",
            Risiko.AUSGEWOGEN, listOf("ZPRG.DE" to 0.40, "VGWD.DE" to 0.40, "SPYW.DE" to 0.20),
        ),
        Strategie(
            "welt", "Ein ETF für die Welt", "One ETF for the world",
            "Alles in einem ETF auf den MSCI ACWI: rund 2.500 Unternehmen aus Industrie- und Schwellenländern nach ihrem Börsenwert. Der einfachste Weg, breit gestreut am Aktienmarkt teilzunehmen.",
            "Everything in one ETF on the MSCI ACWI: around 2,500 companies from developed and emerging markets by market value. The simplest way to take part in the stock market with broad diversification.",
            Risiko.OFFENSIV, listOf("IUSQ.DE" to 1.0),
        ),
        Strategie(
            "faktoren", "Faktor-Mix", "Factor mix",
            "Vier Aktienfaktoren zu gleichen Teilen: Qualität, Value, Momentum und geringe Schwankung. Sie entwickeln sich zu verschiedenen Zeiten gut; zusammen sollen sie die Schwächen der einzelnen ausgleichen.",
            "Four equity factors in equal parts: quality, value, momentum and minimum volatility. They do well at different times; together they are meant to offset each one's weak spells.",
            Risiko.OFFENSIV, listOf("IS3Q.DE" to 0.25, "IS3S.DE" to 0.25, "IS3R.DE" to 0.25, "IQQ0.DE" to 0.25),
        ),
        Strategie(
            "breit", "Welt mit Schwellenländern und Nebenwerten", "World with emerging markets and small caps",
            "70 % Industrieländer, 20 % Schwellenländer, 10 % kleine Unternehmen. Mehr Gewicht auf Märkte und Größen, die im Weltindex wenig zählen.",
            "70% developed markets, 20% emerging markets, 10% small companies. More weight on markets and sizes that count for little in the world index.",
            Risiko.OFFENSIV, listOf("EUNL.DE" to 0.70, "XMME.DE" to 0.20, "ZPRS.DE" to 0.10),
        ),
    )

    fun finde(id: String): Strategie? = alle.firstOrNull { it.id == id }

    // Kennzahlen eines Musterportfolios ueber die gemeinsame Kursgeschichte aller ETFs (hoechstens jahre Jahre).
    data class Kennzahlen(
        val von: Long,
        val bis: Long,
        val renditePa: Double,
        val gesamt: Double,
        val schwankungPa: Double,
        val maxVerlust: Double,
        // Rendite je Kalenderjahr (volle Jahre und das laufende).
        val jahre: List<Pair<Int, Double>>,
        // Wertverlauf von 1,0 an, je Woche ein Punkt.
        val verlauf: List<Pair<Long, Double>>,
    )

    // Wertverlauf bei taeglich gleichen Gewichten: Tagesrenditen der ETFs gewichtet addiert, an Tagen ohne Kurs eines
    // ETFs zaehlt dessen Rendite als null. Beginnt am ersten Tag, an dem alle ETFs Kurse haben.
    fun kennzahlen(reihen: Map<String, List<Candle>>, anteile: List<Pair<String, Double>>, jetzt: Long, jahre: Int = 10): Kennzahlen? {
        if (anteile.any { reihen[it.first].isNullOrEmpty() }) return null
        val zone = java.time.ZoneOffset.UTC
        fun tag(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()
        val kurse = anteile.map { (s, _) -> reihen.getValue(s).filter { it.close > 0 }.associate { tag(it.t) to it.close } }
        val grenze = tag(jetzt) - jahre * 365L
        val start = maxOf(kurse.maxOf { it.keys.min() }, grenze)
        val tage = kurse.flatMap { it.keys }.filter { it >= start }.toSortedSet().toList()
        if (tage.size < 60) return null
        val letzte = DoubleArray(anteile.size) { i -> kurse[i].filterKeys { it <= start }.maxByOrNull { it.key }?.value ?: kurse[i].values.first() }
        var wert = 1.0
        var hoch = 1.0
        var maxVerlust = 0.0
        val renditen = ArrayList<Double>()
        val verlauf = ArrayList<Pair<Long, Double>>()
        val jahresStart = HashMap<Int, Double>()
        val jahresEnde = HashMap<Int, Double>()
        for ((n, t) in tage.withIndex()) {
            var r = 0.0
            for (i in anteile.indices) {
                val k = kurse[i][t]
                if (k != null) {
                    if (n > 0) r += anteile[i].second * (k / letzte[i] - 1)
                    letzte[i] = k
                }
            }
            if (n > 0) { wert *= 1 + r; renditen += r }
            hoch = maxOf(hoch, wert)
            maxVerlust = minOf(maxVerlust, wert / hoch - 1)
            val datum = java.time.LocalDate.ofEpochDay(t)
            jahresStart.putIfAbsent(datum.year, if (n == 0) wert else wert / (1 + r))
            jahresEnde[datum.year] = wert
            if (n % 5 == 0 || n == tage.lastIndex) verlauf += datum.atStartOfDay(zone).toInstant().toEpochMilli() to wert
        }
        val jahreZahl = (tage.last() - tage.first()) / 365.25
        val mittel = renditen.average()
        val sd = sqrt(renditen.sumOf { (it - mittel) * (it - mittel) } / (renditen.size - 1).coerceAtLeast(1))
        return Kennzahlen(
            von = java.time.LocalDate.ofEpochDay(tage.first()).atStartOfDay(zone).toInstant().toEpochMilli(),
            bis = java.time.LocalDate.ofEpochDay(tage.last()).atStartOfDay(zone).toInstant().toEpochMilli(),
            renditePa = if (jahreZahl > 0.5) wert.pow(1 / jahreZahl) - 1 else wert - 1,
            gesamt = wert - 1,
            schwankungPa = sd * sqrt(252.0),
            maxVerlust = maxVerlust,
            jahre = jahresEnde.keys.sorted().map { y -> y to jahresEnde.getValue(y) / jahresStart.getValue(y) - 1 },
            verlauf = verlauf,
        )
    }
}
