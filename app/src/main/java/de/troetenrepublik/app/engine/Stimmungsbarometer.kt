package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Stimmungsbarometer auf einer Skala von 0 (extreme Angst) bis 100 (extreme Gier). Keine Empfehlung.
// Aktien: Fear & Greed Index von CNN Business, sieben Teilindikatoren, US-Markt; daneben ein eigener
// Angst-Gier-Index aus fuenf Teilen (angstGier), der auch einspringt, wenn CNN nicht antwortet. Er hat keine eigene
// Kachel: Momentum und Breite gehen in die Gesamtstimmung ein (Gesamtstimmung.kt), der Blasen-Detektor nutzt ihn.
// Krypto: Crypto Fear & Greed Index von alternative.me. Die Quellenangabe direkt bei den Daten ist Bedingung.
// Reddit: Stimmung auf r/wallstreetbets laut Tradestie, nach Kommentaren gewichtet.
// Sichere Haefen (seit 5.93): Aktien gegen Staatsanleihen ueber 20 Handelstage.
object Stimmungsbarometer {

    const val CNN = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata"
    // Seite von CNN als Referer der Anfrage.
    const val CNN_SEITE = "https://www.cnn.com/markets/fear-and-greed"
    const val KRYPTO = "https://api.alternative.me/fng/?limit=31"
    // Mit so vielen Tagen, dass die Antwort an das mitgelieferte Archiv anschliesst (KryptoArchiv.limit)
    fun kryptoAdresse(tage: Int): String = "https://api.alternative.me/fng/?limit=$tage"
    const val KRYPTO_SEITE = "https://alternative.me/crypto/fear-and-greed-index/"
    const val WSB = "https://tradestie.com/api/v1/apps/reddit"
    const val WSB_SEITE = "https://tradestie.com/apps/reddit/top-reddit-stocks/"

    // Einteilung in fuenf Stufen, Grenzen 25, 45, 55 und 75.
    enum class Stufe { EXTREME_ANGST, ANGST, NEUTRAL, GIER, EXTREME_GIER }

    fun stufe(wert: Double): Stufe = when {
        wert < 25 -> Stufe.EXTREME_ANGST
        wert < 45 -> Stufe.ANGST
        wert <= 55 -> Stufe.NEUTRAL
        wert <= 75 -> Stufe.GIER
        else -> Stufe.EXTREME_GIER
    }

    // Aus der Bezeichnung des Anbieters, etwa „extreme fear“. Null, wenn unbekannt.
    fun stufe(text: String): Stufe? = when (text.trim().lowercase()) {
        "extreme fear" -> Stufe.EXTREME_ANGST
        "fear" -> Stufe.ANGST
        "neutral" -> Stufe.NEUTRAL
        "greed" -> Stufe.GIER
        "extreme greed" -> Stufe.EXTREME_GIER
        else -> null
    }

    // Teilindikator eines Index, etwa Momentum (Schluessel aus INDEX_TEILE).
    data class Teil(val schluessel: String, val wert: Double, val stufe: Stufe)

    // Vergleichswerte: Vortag, vor einer Woche, einem Monat, einem Jahr, null wenn unbekannt.
    // Verlauf: Zeit in Millisekunden und Wert, aelteste zuerst.
    data class Barometer(
        val wert: Double,
        val stufe: Stufe,
        val vortag: Double? = null,
        val woche: Double? = null,
        val monat: Double? = null,
        val jahr: Double? = null,
        val verlauf: List<Pair<Long, Double>> = emptyList(),
        val teile: List<Teil> = emptyList(),
        val stand: Long = 0L,
    )

    data class WsbWert(val ticker: String, val steigend: Boolean, val punkte: Double, val kommentare: Int)

    // Ab diesem Betrag des Tradestie-Scores gilt der Ton eines Werts als klar (seit 5.93), und nur mit genug
    // Kommentaren (5.94): Bei wenigen kippt ein einzelner Beitrag die Einordnung.
    const val WSB_KLAR = 0.1
    const val WSB_MIN_KOMMENTARE = 10
    fun wsbKlar(w: WsbWert) = kotlin.math.abs(w.punkte) >= WSB_KLAR && w.kommentare >= WSB_MIN_KOMMENTARE
    fun wsbSteigendKlar(w: WsbWert) = w.punkte >= WSB_KLAR && w.kommentare >= WSB_MIN_KOMMENTARE
    fun wsbFallendKlar(w: WsbWert) = w.punkte <= -WSB_KLAR && w.kommentare >= WSB_MIN_KOMMENTARE

    // Kuerzel, die auf r/wallstreetbets fast immer als gewoehnliches Wort vorkommen (5.94): „AI“ steht fuer kuenstliche
    // Intelligenz und kaum fuer C3.ai, „YOU“ oder „UP“ sind Woerter. Sie verzerren Barometer und Listen.
    val WSB_WOERTER = setOf(
        "AI", "YOU", "GOOD", "VERY", "ON", "UP", "ES", "SK", "NK", "HR", "API", "AR", "SI", "WAR", "RAM", "TACO", "PUMP", "AGI",
        "MSM", "ARR", "CEO", "DD", "ATH", "IMO", "YOLO", "EV", "IT", "ALL", "ARE", "FOR", "NOW", "OR", "SO", "BE", "GO", "CAN", "OUT",
    )

    // Liste bereinigen (5.94): Woerter heraus, Gattungen derselben Firma (Alphabet A und C, Fox A und B ...) zu einem
    // Eintrag, Kommentare summiert, Score nach Kommentaren gewichtet. name: Firmenname laut Katalog oder null; ohne
    // Namen bleibt ein Kuerzel allein. Die Regel ist dieselbe wie bei doppelten Kacheln (GleicheFirma), alle Werte
    // gelten als US-Notierung. Der Ton heisst positiv, sobald der Score ueber null liegt.
    fun wsbBereinigt(werte: List<WsbWert>, name: (String) -> String?): List<WsbWert> {
        val rest = werte.filter { it.ticker !in WSB_WOERTER }
        val eintraege = rest.map { GleicheFirma.Eintrag(it.ticker, name(it.ticker) ?: "#" + it.ticker, "US", null) }
        return GleicheFirma.gruppen(eintraege).map { idx ->
            val g = idx.map { rest[it] }
            if (g.size == 1) return@map g[0]
            val k = g.sumOf { it.kommentare }
            val punkte = if (k > 0) g.sumOf { it.punkte * it.kommentare } / k else g.map { it.punkte }.average()
            // Angezeigt wird die meistbesprochene Gattung
            WsbWert(g.maxBy { it.kommentare }.ticker, punkte > 0, punkte, k)
        }.sortedByDescending { it.kommentare }
    }

    // Anlass: meistbesprochener Wert mit starker Kursbewegung (aenderung in Prozent ueber fuenf Handelstage).
    data class WsbAnlass(val ticker: String, val aenderung: Double)

    // klarAnteil: Anteil der Kommentare zu Werten mit klarer Richtung, 0 bis 1.
    data class WsbLage(val klarAnteil: Double, val unklar: Boolean, val anlass: WsbAnlass?)

    // Anteil der Kommentare mit steigender Stimmung, 0 bis 100.
    // aenderungen (5.94): Kursaenderung in Prozent ueber fuenf Handelstage je Ticker, fuer die Anzeige neben dem Ton.
    data class RedditBarometer(
        val barometer: Barometer, val werte: List<WsbWert>, val lage: WsbLage? = null, val aenderungen: Map<String, Double> = emptyMap(),
    )

    // Teile des CNN-Index, Reihenfolge wie bei CNN.
    val TEILE = listOf(
        "market_momentum_sp500", "stock_price_strength", "stock_price_breadth", "put_call_options",
        "market_volatility_vix", "junk_bond_demand", "safe_haven_demand",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.zahl(): Double? = text()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    // CNN liefert die Zeit in UTC (+00:00).
    private fun zeitIso(s: String?): Long = s?.let { BrokerExport.zeit(it) } ?: 0L

    // Null, wenn die Antwort keinen Indexwert enthaelt.
    fun cnnLesen(text: String): Barometer? = runCatching {
        val w = json.parseToJsonElement(text).obj() ?: return null
        val fg = w["fear_and_greed"].obj() ?: return null
        val wert = fg["score"].zahl() ?: return null
        val verlauf = (w["fear_and_greed_historical"].obj()?.get("data") as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val x = o["x"].zahl()?.toLong() ?: return@mapNotNull null
            val y = o["y"].zahl() ?: return@mapNotNull null
            x to y
        }.sortedBy { it.first }
        val teile = TEILE.mapNotNull { k ->
            val o = w[k].obj() ?: return@mapNotNull null
            val s = o["score"].zahl() ?: return@mapNotNull null
            Teil(k, s, o["rating"].text()?.let(::stufe) ?: stufe(s))
        }
        Barometer(
            wert = wert, stufe = fg["rating"].text()?.let(::stufe) ?: stufe(wert),
            vortag = fg["previous_close"].zahl(), woche = fg["previous_1_week"].zahl(), monat = fg["previous_1_month"].zahl(),
            jahr = fg["previous_1_year"].zahl(), verlauf = verlauf, teile = teile, stand = zeitIso(fg["timestamp"].text()),
        )
    }.getOrNull()

    // ------------------------------------------------------------ Eigener Angst-Gier-Index (5.94)

    // Fuenf Teile nach dem Vorbild bekannter Stimmungsindizes, jeder als Rang des heutigen Werts unter den Werten des
    // letzten Jahres (0 bis 100, hoch heisst Gier). Der Index ist ihr Mittel, sobald mindestens drei Teile Werte haben.
    // momentum: Abstand des S&P 500 zu seiner 125-Tage-Linie; breite: gleichgewichteter S&P 500 (RSP) gegen den
    // gewichteten (SPY) ueber 20 Handelstage; vix: Hoehe des VIX (niedrig ist Gier); stress: Finanzstress der St. Louis
    // Fed (STLFSI4, Wochenwerte, niedrig ist Gier); sicher: Aktien gegen Staatsanleihen ueber 20 Handelstage. Das
    // Put/Call-Verhaeltnis steht als eigenes Barometer daneben, Hochzinsanleihen fehlen.
    val INDEX_TEILE = listOf("momentum", "breite", "vix", "stress", "sicher")

    private fun tagVon(t: Long) = Math.floorDiv(t, 86_400_000L)

    // Rang je Punkt ab Punkt mindestens: Anteil der Werte im Fenster (hoechstens fenster Punkte bis heute), die kleiner
    // oder gleich (gier) bzw. groesser oder gleich dem heutigen sind, mal 100. Leer bei zu wenigen Punkten.
    fun rangReihe(reihe: List<Pair<Long, Double>>, gier: Boolean, fenster: Int = 260, mindestens: Int = 100): List<Pair<Long, Double>> {
        if (reihe.size < mindestens) return emptyList()
        return (mindestens - 1 until reihe.size).map { i ->
            val f = reihe.subList(maxOf(0, i - fenster + 1), i + 1)
            val x = reihe[i].second
            reihe[i].first to 100.0 * f.count { if (gier) it.second <= x else it.second >= x } / f.size
        }
    }

    // Abstand zur gleitenden Linie ueber tage Kurse in Prozent, ab dem Kurs Nummer tage.
    fun abstandZurLinie(kurse: List<Pair<Long, Double>>, tage: Int = 125): List<Pair<Long, Double>> {
        if (kurse.size < tage) return emptyList()
        var summe = kurse.take(tage).sumOf { it.second }
        val aus = ArrayList<Pair<Long, Double>>()
        for (i in tage - 1 until kurse.size) {
            if (i >= tage) summe += kurse[i].second - kurse[i - tage].second
            val schnitt = summe / tage
            if (schnitt > 0) aus += kurse[i].first to (kurse[i].second / schnitt - 1) * 100
        }
        return aus
    }

    // Vorsprung von a vor b ueber tage Handelstage in Prozentpunkten, nur an Tagen, an denen beide einen Kurs haben.
    fun vorsprung(a: List<Pair<Long, Double>>, b: List<Pair<Long, Double>>, tage: Int = 20): List<Pair<Long, Double>> {
        val bt = b.filter { it.second > 0 }.associate { tagVon(it.first) to it.second }
        val gem = a.filter { it.second > 0 }.mapNotNull { (t, k) -> bt[tagVon(t)]?.let { Triple(t, k, it) } }
            .sortedBy { it.first }.distinctBy { tagVon(it.first) }
        return (tage until gem.size).map { i ->
            gem[i].first to ((gem[i].second / gem[i - tage].second - 1) - (gem[i].third / gem[i - tage].third - 1)) * 100
        }
    }

    // Index aus den Rangreihen der Teile (Schluessel aus INDEX_TEILE, aelteste zuerst). Ein Wert je Tag der taeglichen
    // Teile; jeder Teil zaehlt mit seinem letzten Wert der vergangenen zehn Tage, so tragen die Wochenwerte des Stresses
    // bis zur naechsten Woche. Verlauf ein Jahr. Null, wenn kein Tag drei Teile hat.
    fun angstGier(teile: Map<String, List<Pair<Long, Double>>>): Barometer? {
        val karten = teile.mapValues { (_, r) -> java.util.TreeMap<Long, Double>().apply { r.forEach { put(tagVon(it.first), it.second) } } }
        fun werte(tag: Long) = INDEX_TEILE.mapNotNull { k -> karten[k]?.floorEntry(tag)?.takeIf { tag - it.key <= 10 }?.let { k to it.value } }
        val tage = teile.filterKeys { it != "stress" }.values.flatten().map { it.first }.distinctBy(::tagVon).sorted()
        val verlauf = tage.mapNotNull { t -> werte(tagVon(t)).takeIf { it.size >= 3 }?.let { w -> t to w.sumOf { it.second } / w.size } }
        val (stand, wert) = verlauf.lastOrNull() ?: return null
        val n = verlauf.size
        fun vor(k: Int) = verlauf.getOrNull(n - 1 - k)?.second
        return Barometer(
            wert, stufe(wert), vortag = vor(1), woche = vor(5), monat = vor(21), jahr = vor(252),
            verlauf = verlauf.takeLast(260), teile = werte(tagVon(stand)).map { (k, v) -> Teil(k, v, stufe(v)) }, stand = stand,
        )
    }

    // alternative.me liefert die neuesten zuerst, Zeit in Sekunden.
    fun kryptoLesen(text: String): Barometer? = runCatching {
        val daten = (json.parseToJsonElement(text).obj()?.get("data") as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val t = o["timestamp"].zahl()?.toLong() ?: return@mapNotNull null
            val v = o["value"].zahl() ?: return@mapNotNull null
            Triple(t * 1000, v, o["value_classification"].text())
        }.sortedByDescending { it.first }
        val jetzt = daten.firstOrNull() ?: return null
        Barometer(
            wert = jetzt.second, stufe = jetzt.third?.let(::stufe) ?: stufe(jetzt.second),
            vortag = daten.getOrNull(1)?.second, woche = daten.getOrNull(7)?.second, monat = daten.getOrNull(30)?.second,
            verlauf = daten.map { it.first to it.second }.reversed(), stand = jetzt.first,
        )
    }.getOrNull()

    // Die meistbesprochenen Werte auf r/wallstreetbets mit Stimmung.
    fun wsbLesen(text: String): List<WsbWert> = runCatching {
        (json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val t = o["ticker"].text()?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val s = o["sentiment"].text()?.lowercase() ?: return@mapNotNull null
            // Richtung aus dem Score (5.94): Tradestie nennt auch 0,0 „Bearish“, null ist aber neutral
            val punkte = o["sentiment_score"].zahl()
            WsbWert(t, punkte?.let { it > 0 } ?: s.startsWith("bull"), punkte ?: 0.0, o["no_of_comments"].zahl()?.toInt() ?: 0)
        }
    }.getOrDefault(emptyList())

    // Anteil der Kommentare zu Werten mit steigender Stimmung, 0 bis 100. Null ohne Kommentare.
    fun wsbBarometer(werte: List<WsbWert>, jetzt: Long): RedditBarometer? {
        val alle = werte.sumOf { it.kommentare }
        if (alle <= 0) return null
        val wert = 100.0 * werte.filter { it.steigend }.sumOf { it.kommentare } / alle
        return RedditBarometer(Barometer(wert, stufe(wert), stand = jetzt), werte.sortedByDescending { it.kommentare })
    }

    // Prozentaenderung ueber tage Handelstage, aelteste zuerst. Null bei zu wenig Kursen oder Basis <= 0.
    fun aenderung(kurse: List<Pair<Long, Double>>, tage: Int = 5): Double? {
        if (kurse.size <= tage) return null
        val alt = kurse[kurse.size - 1 - tage].second
        if (alt <= 0) return null
        return (kurse.last().second / alt - 1) * 100
    }

    // Stimmung auf r/wallstreetbets mit Lage (seit 5.93): Nur Werte mit klarer Richtung zaehlen fuer steigend oder fallend.
    // Bei zu wenigen klaren Kommentaren bleibt die Richtung offen (Wert 50). aenderungen: Prozent ueber fuenf Handelstage je
    // Ticker; der meistbesprochene Wert mit einer Bewegung ab 10 % wird als Anlass genannt. Null ohne Kommentare.
    fun wsbMitLage(werte: List<WsbWert>, aenderungen: Map<String, Double>, jetzt: Long): RedditBarometer? {
        val alle = werte.sumOf { it.kommentare }
        if (alle <= 0) return null
        val klar = werte.filter(::wsbKlar)
        val klarK = klar.sumOf { it.kommentare }
        val anteil = klarK.toDouble() / alle
        val unklar = anteil < 0.3 || klar.size < 5
        val steigK = werte.filter(::wsbSteigendKlar).sumOf { it.kommentare }
        val wert = if (unklar) 50.0 else 100.0 * steigK / klarK
        val sortiert = werte.sortedByDescending { it.kommentare }
        // Die Liste ist schon nach Kommentaren sortiert, der erste Treffer ist der meistbesprochene Anlass
        val anlass = sortiert.take(5).firstOrNull { aenderungen[it.ticker.uppercase()]?.let { c -> kotlin.math.abs(c) >= 10.0 } == true }
            ?.let { WsbAnlass(it.ticker, aenderungen.getValue(it.ticker.uppercase())) }
        return RedditBarometer(Barometer(wert, stufe(wert), stand = jetzt), sortiert, WsbLage(anteil, unklar, anlass), aenderungen)
    }

    // Put/Call-Verhaeltnis der Aktienoptionen an der Cboe als achtes Barometer (5.93): das uebliche Band von 0,5 bis 0,8
    // (WeitereQuellen.putCallLage) liegt zwischen 75 und 25, also von Gier bis Angst; unter 0,5 (sorglos) steht extreme
    // Gier, ab 0,8 (vorsichtig) extreme Angst, jeweils gerade fortgesetzt. Ohne Aktienwert gilt das Gesamtverhaeltnis.
    fun putCallBarometer(p: WeitereQuellen.PutCall): Barometer? {
        val r = p.aktien ?: p.gesamt ?: return null
        val wert = (Math.round((25.0 + (0.8 - r) / 0.3 * 50.0) * 1e6) / 1e6).coerceIn(0.0, 100.0)
        return Barometer(wert, stufe(wert), teile = listOf(Teil("putcall", r, stufe(wert))), stand = p.tag * 86_400_000L)
    }

    // Schwankungsbarometer (seit 5.93) aus den VIX-Schlusskursen (aelteste zuerst): 100 * Anteil der Tage der letzten 260
    // (hoechstens), an denen der VIX mindestens so hoch war wie jetzt. Niedriger VIX gibt viel Gier. Null bei unter 100 Kursen.
    fun vixBarometer(kurse: List<Pair<Long, Double>>): Barometer? {
        if (kurse.size < 100) return null
        fun wertBis(i: Int) = kurse.subList(maxOf(0, i - 259), i + 1).let { f -> 100.0 * f.count { it.second >= kurse[i].second } / f.size }
        val n = kurse.size
        val wert = wertBis(n - 1)
        return Barometer(
            wert, stufe(wert), vortag = wertBis(n - 2), woche = wertBis(n - 6), monat = wertBis(n - 22),
            verlauf = (maxOf(99, n - 120) until n).map { kurse[it].first to wertBis(it) },
            teile = listOf(Teil("vix", kurse.last().second, stufe(wert))), stand = kurse.last().first,
        )
    }

    // Sichere Haefen (seit 5.93): Rendite des Aktienindex minus Rendite der Staatsanleihen ueber 20 Handelstage, in Prozentpunkten
    // (wie im CNN-Index). Beide Reihen aelteste zuerst, (Zeit in ms, Schlusskurs). Nur Tage, an denen beide einen Kurs haben,
    // zaehlen (Tag = floorDiv(t, 86_400_000)). Wert = 100 * Anteil der Tage der letzten 260 (hoechstens), an denen der Unterschied
    // kleiner oder gleich dem heutigen war. Liegen Aktien weit vorn, ist das viel Gier. Null bei unter 120 gemeinsamen Tagen.
    fun sicherBarometer(aktien: List<Pair<Long, Double>>, anleihen: List<Pair<Long, Double>>): Barometer? {
        val tag = { t: Long -> Math.floorDiv(t, 86_400_000L) }
        val an = anleihen.filter { it.second > 0 }.associate { tag(it.first) to it.second }
        val gem = aktien.filter { it.second > 0 }.mapNotNull { (t, k) -> an[tag(t)]?.let { Triple(t, k, it) } }
            .sortedBy { it.first }.distinctBy { tag(it.first) }
        if (gem.size < 120) return null
        val d = (20 until gem.size).map { i ->
            (gem[i].second / gem[i - 20].second - 1) * 100 - (gem[i].third / gem[i - 20].third - 1) * 100
        }
        fun wertBis(j: Int) = d.subList(maxOf(0, j - 259), j + 1).let { f -> 100.0 * f.count { it <= d[j] } / f.size }
        val n = d.size
        val wert = wertBis(n - 1)
        return Barometer(
            wert, stufe(wert), vortag = wertBis(n - 2), woche = wertBis(n - 6), monat = wertBis(n - 22),
            verlauf = (maxOf(0, n - 120) until n).map { gem[it + 20].first to wertBis(it) },
            teile = listOf(Teil("sicher", d.last(), stufe(wert))), stand = gem.last().first,
        )
    }

    // Anteil der Bullen unter Bullen und Baeren, 0 bis 100.
    data class AaiiBarometer(val barometer: Barometer, val woche: Fruehwarnung.Umfrage)

    // wochen: neueste zuerst.
    fun aaiiBarometer(wochen: List<Fruehwarnung.Umfrage>): AaiiBarometer? {
        fun anteil(u: Fruehwarnung.Umfrage) = (u.bullisch + u.baerisch).takeIf { it > 0 }?.let { 100.0 * u.bullisch / it }
        val neu = wochen.firstOrNull() ?: return null
        val wert = anteil(neu) ?: return null
        return AaiiBarometer(
            Barometer(wert, stufe(wert), vortag = wochen.getOrNull(1)?.let(::anteil), monat = wochen.getOrNull(4)?.let(::anteil),
                verlauf = wochen.mapNotNull { u -> anteil(u)?.let { u.zeit to it } }.reversed(), stand = neu.zeit),
            neu,
        )
    }

    // Aus deutschen Subreddits. Null bei weniger als mindestens Beitraegen mit Richtung.
    fun beitragsBarometer(s: RedditAuswertung.Stimmung, jetzt: Long, mindestens: Int = 5): Barometer? {
        if (s.mitRichtung < mindestens) return null
        val wert = 100.0 * s.steigend / s.mitRichtung
        return Barometer(wert, stufe(wert), stand = jetzt)
    }

    // Sorgen auf Wikipedia (seit 5.92): taegliche Aufrufe der englischen Artikel ueber Rezession und Boersencrash als
    // frei nutzbarer Ersatz fuer Suchtrends. Werden sie mehr gelesen als sonst im Jahr, ist die Sorge gross.
    val SORGEN_ARTIKEL = listOf("Recession", "Stock_market_crash")

    // Barometer aus den Aufrufen (je Artikel aelteste zuerst, letzter Eintrag am Tag letzterTag in Millisekunden):
    // 100 minus der Rang des Siebentagesschnitts im ganzen Zeitraum (siehe Blasen.rang), also 0 bei so viel Sorge wie
    // nie. Verlauf der letzten 120 Tage. Null bei weniger als 60 gemeinsamen Tagen.
    fun sorgen(reihen: List<List<Int>>, letzterTag: Long): Barometer? {
        val gut = reihen.filter { it.size >= 60 }
        if (gut.isEmpty()) return null
        val n = gut.minOf { it.size }
        val summe = (0 until n).map { i -> gut.sumOf { r -> r[r.size - n + i] } }
        fun wertBis(ende: Int): Double? = Blasen.rang(summe.subList(0, ende + 1))?.let { 100 - it }
        val wert = wertBis(n - 1) ?: return null
        val tag = 86_400_000L
        val verlauf = (maxOf(59, n - 120) until n).mapNotNull { i -> wertBis(i)?.let { (letzterTag - (n - 1 - i) * tag) to it } }
        return Barometer(
            wert, stufe(wert), vortag = wertBis(n - 2), woche = if (n > 66) wertBis(n - 8) else null,
            monat = if (n > 89) wertBis(n - 31) else null, verlauf = verlauf, stand = letzterTag,
        )
    }
}
