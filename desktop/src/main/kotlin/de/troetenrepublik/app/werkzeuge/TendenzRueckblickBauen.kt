package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.engine.Eingepreist
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.TendenzRueckblick
import de.troetenrepublik.app.engine.TendenzRueckblick.Messung
import de.troetenrepublik.app.engine.TendenzRueckblick.Zeitraum
import de.troetenrepublik.app.engine.XlsLeser
import de.troetenrepublik.app.market.HausDaten
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.ui.CopyRouten
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

// Baut den Rueckblick auf die Tendenz unter „Im Preis?“ (assets/indikatoren/tendenz_rueckblick.json): je Signal,
// Gruppe und Zeitraum Trefferquote, Grundquote, Rendite in Tendenzrichtung und t-Wert, ohne Rohdaten. Rechnung in
// engine/TendenzRueckblick.kt. Aufruf: gradlew :desktop:tendenzRueckblick -Pziel=<Datei>. Die Rohdaten (EIA, FRED,
// Drought Monitor, Yahoo) liegen zwischengespeichert unter build/werkzeugdaten/tendenz, ein zweiter Lauf laedt nichts
// neu. Daneben entsteht bericht_roh.md mit allen Tabellen. Zwischen den Abrufen wartet es, um die Quellen zu schonen.

private val TAG = 86_400_000L
private lateinit var ordner: File
private val api = MarketApi()

// Datei aus dem Zwischenspeicher oder aus dem Netz (dann gespeichert).
private suspend fun roh(name: String, laden: suspend () -> ByteArray?): ByteArray? {
    val f = File(ordner, name)
    if (f.exists() && f.length() > 0) return f.readBytes()
    val b = runCatching { laden() }.getOrNull() ?: return null.also { System.err.println("fehlt: $name") }
    f.writeBytes(b)
    delay(600)
    return b
}

private suspend fun text(name: String, url: String, kopf: Map<String, String> = emptyMap(), max: Int = 4_000_000): String? =
    roh(name) { api.anfrage(url, kopf, max = max).takeIf { it.ok }?.text?.toByteArray(Charsets.UTF_8) }?.toString(Charsets.UTF_8)

// Kurse von Yahoo als Zeilen „ms;Schluss“ im Zwischenspeicher.
private suspend fun kerzen(symbol: String, von: LocalDate, intervall: String = "1d"): List<Candle> {
    val name = "y_" + symbol.replace(Regex("[^A-Za-z0-9]"), "_") + "_" + intervall + "_" + von + ".txt"
    val b = roh(name) {
        val s = api.candlesBetween(symbol, von.toEpochDay() * 86_400L, System.currentTimeMillis() / 1000 + 86_400L, intervall)
        val k = s.candles.filter { it.close.isFinite() && it.close > 0 }
        if (k.isEmpty()) null else k.joinToString("\n") { "${it.t};${it.close}" }.toByteArray()
    } ?: return emptyList()
    return b.toString(Charsets.UTF_8).lineSequence().mapNotNull { z ->
        val (t, c) = z.split(';').takeIf { it.size == 2 } ?: return@mapNotNull null
        val ms = t.toLongOrNull() ?: return@mapNotNull null
        val w = c.toDoubleOrNull() ?: return@mapNotNull null
        Candle(ms, w, w, w, w)
    }.toList()
}

private fun tage(k: List<Candle>): List<Pair<Long, Double>> = k.map { Math.floorDiv(it.t, TAG) to it.close }

private fun monat(tag: Long) = LocalDate.ofEpochDay(tag).toString().take(7)

// ------------------------------------------------------------ Bericht

private val bericht = StringBuilder()

private fun f1(v: Double) = String.format(java.util.Locale.GERMANY, "%.1f", v)
private fun f2(v: Double) = String.format(java.util.Locale.GERMANY, "%.2f", v)

private fun tabelle(titel: String, einheit: String, l: List<Zeitraum>) {
    bericht.append("\n**$titel**\n\n| Zeitraum | Fälle | unabh. | Treffer | Grundquote | Mittel | Drift | t (NW) | t (unabh.) | 1. Hälfte | 2. Hälfte | belegt |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n")
    for (z in l) {
        val m = if (einheit == TendenzRueckblick.PUNKTE) f2(z.mittel) + " Pkt." else f1(z.mittel * 100) + " %"
        val g = if (einheit == TendenzRueckblick.PUNKTE) f2(z.grundMittel) + " Pkt." else f1(z.grundMittel * 100) + " %"
        val h = z.haelften.map { if (einheit == TendenzRueckblick.PUNKTE) f2(it) else f1(it * 100) }
        bericht.append("| ${z.wochen} W. | ${z.n} | ${z.unabhaengig} | ${f1(z.treffer * 100)} % | ${f1(z.grund * 100)} % | $m | $g | ${f2(z.t)} | ${f2(z.tUnabhaengig)} | ${h.getOrElse(0) { "" }} | ${h.getOrElse(1) { "" }} | ${if (z.belegt) "ja" else "nein"} |\n")
    }
}

// Ein fruehes Signal pruefen: Faelle walk-forward, Messung gegen das Instrument, Gruppen alle, gelb und rot.
private fun pruefen(
    name: String, faelle: List<TendenzRueckblick.Fall>, kurse: List<Pair<Long, Double>>, art: TendenzRueckblick.Art, instrument: String,
): TendenzRueckblick.Signal {
    val einheit = if (art == TendenzRueckblick.Art.ZINS) TendenzRueckblick.PUNKTE else TendenzRueckblick.PROZENT
    if (faelle.isEmpty() || kurse.isEmpty()) return TendenzRueckblick.Signal(TendenzRueckblick.OHNE_DATEN, einheit, instrument)
    val gruppen = linkedMapOf<String, MutableList<Zeitraum>>("alle" to mutableListOf(), "gelb" to mutableListOf(), "rot" to mutableListOf())
    var bis = faelle.first().tag
    for (w in TendenzRueckblick.WOCHEN) {
        val m = faelle.mapNotNull { f -> TendenzRueckblick.rendite(kurse, f.tag + TendenzRueckblick.VERZUG, w, art)?.let { f to Messung(f.tag, f.tendenz, it) } }
        if (m.isEmpty()) continue
        bis = maxOf(bis, m.last().first.tag)
        val basis = m.map { it.second }
        TendenzRueckblick.auswerten(basis, w)?.let { gruppen["alle"]!! += it }
        TendenzRueckblick.auswerten(m.filter { it.first.ampel == Fruehwarnung.Ampel.GELB }.map { it.second }, w, basis = basis)?.let { gruppen["gelb"]!! += it }
        TendenzRueckblick.auswerten(m.filter { it.first.ampel == Fruehwarnung.Ampel.ROT }.map { it.second }, w, basis = basis)?.let { gruppen["rot"]!! += it }
    }
    val lang = faelle.count { it.tendenz > 0 }
    val kurz = faelle.count { it.tendenz < 0 }
    bericht.append("\n### $name\n\nGemessen an: $instrument. Stichtage ${monat(faelle.first().tag)} bis ${monat(faelle.last().tag)} (${faelle.size} Wochen), " +
        "davon eher long $lang, eher short $kurz, ohne Richtung ${faelle.size - lang - kurz}. Letzter Stichtag mit Folgerendite: ${monat(bis)}.\n")
    gruppen.forEach { (g, l) -> tabelle(g, einheit, l) }
    return TendenzRueckblick.Signal(TendenzRueckblick.GEPRUEFT, einheit, instrument, monat(faelle.first().tag), monat(bis), gruppen.filterValues { it.isNotEmpty() })
}

// Datierte Signale: je Ereignis Stichtage 5, 21 und 63 Handelstage nach dem Signal (so lange zeigt die App die Zeile
// etwa), Messung gegen den S&P 500 nach 1, 3, 6 und 12 Monaten. Gruppen alle, teil und wenig (Lauf).
private data class Ereignis(val symbol: String, val zeit: Long, val richtung: Int, val frueher: List<Long>)

private val NACH = listOf(5, 21, 63)

private fun datiertPruefen(name: String, ereignisse: List<Ereignis>, kurse: Map<String, List<Candle>>, index: List<Candle>): TendenzRueckblick.Signal {
    data class F(val stichtag: Long, val g: Eingepreist.Gelaufen, val symbol: String)
    val faelle = ArrayList<F>()
    for (e in ereignisse) {
        val k = kurse[e.symbol]?.takeIf { it.size > 30 } ?: continue
        for (n in NACH) TendenzRueckblick.datiert(k, index, e.zeit, e.richtung, e.frueher, n)?.let { (s, g) -> faelle += F(s, g, e.symbol) }
    }
    if (faelle.isEmpty()) return TendenzRueckblick.Signal(TendenzRueckblick.OHNE_DATEN, instrument = "S&P 500")
    val gruppen = linkedMapOf<String, MutableList<Zeitraum>>("alle" to mutableListOf(), "teil" to mutableListOf(), "wenig" to mutableListOf())
    var bis = 0L
    for (w in TendenzRueckblick.WOCHEN_DATIERT) {
        val m = faelle.mapNotNull { f ->
            Analystenbilanz.mehrRendite(kurse[f.symbol]!!, index, f.stichtag, TendenzRueckblick.handelstage(w))
                ?.let { f to Messung(Math.floorDiv(f.stichtag, TAG), Eingepreist.tendenz(f.g), it, f.symbol) }
        }
        if (m.isEmpty()) continue
        bis = maxOf(bis, m.maxOf { it.second.tag })
        val basis = m.map { it.second }
        TendenzRueckblick.auswerten(basis, w)?.let { gruppen["alle"]!! += it }
        TendenzRueckblick.auswerten(m.filter { it.first.g.lauf == Eingepreist.Lauf.TEIL }.map { it.second }, w, basis = basis)?.let { gruppen["teil"]!! += it }
        TendenzRueckblick.auswerten(m.filter { it.first.g.lauf == Eingepreist.Lauf.WENIG }.map { it.second }, w, basis = basis)?.let { gruppen["wenig"]!! += it }
    }
    val von = Math.floorDiv(faelle.minOf { it.stichtag }, TAG)
    val mitTendenz = faelle.count { Eingepreist.tendenz(it.g) != 0 }
    bericht.append("\n### $name\n\nGemessen gegen den S&P 500. ${ereignisse.size} Signale in ${ereignisse.map { it.symbol }.distinct().size} Werten, " +
        "${faelle.size} Stichtage (${monat(von)} bis ${monat(Math.floorDiv(faelle.maxOf { it.stichtag }, TAG))}), davon mit Tendenz $mitTendenz. " +
        "Lauf: " + Eingepreist.Lauf.entries.joinToString(", ") { l -> "${l.name} ${faelle.count { it.g.lauf == l }}" } + ".\n")
    gruppen.forEach { (g, l) -> tabelle(g, TendenzRueckblick.PROZENT, l) }
    return TendenzRueckblick.Signal(TendenzRueckblick.GEPRUEFT, TendenzRueckblick.PROZENT, "S&P 500", monat(von), if (bis > 0) monat(bis) else "", gruppen.filterValues { it.isNotEmpty() })
}

fun main(args: Array<String>) {
    val ziel = File(args.getOrNull(0)?.takeIf { it.isNotBlank() } ?: error("Ziel fehlt"))
    val katalog = File(args.getOrNull(1) ?: error("Katalog fehlt"))
    val assets = File(args.getOrNull(2) ?: error("Assets fehlen"))
    val nurFrueh = args.getOrNull(3) == "frueh"
    ordner = File(System.getProperty("troete.daten") ?: "build/werkzeugdaten", "tendenz").apply { mkdirs() }
    val heute = LocalDate.now(ZoneOffset.UTC)
    val signale = linkedMapOf<String, TendenzRueckblick.Signal>()
    bericht.append("# Rückblick Tendenz (Rohfassung, ${heute})\n")
    runBlocking {
        // EIA: Lager, Futures woechentlich (wie die App) und taeglich (fuer die Rendite mit Roll)
        val futW = mapOf(
            Fruehindikatoren.EIA_FUTURES_OEL to roh("eia_fut_oel_w.xls") { api.datei(Fruehindikatoren.EIA_FUTURES_OEL, 6_000_000) },
            Fruehindikatoren.EIA_FUTURES_GAS to roh("eia_fut_gas_w.xls") { api.datei(Fruehindikatoren.EIA_FUTURES_GAS, 6_000_000) },
        )
        val futD = mapOf(
            Fruehindikatoren.EIA_FUTURES_OEL to roh("eia_fut_oel_d.xls") { api.datei(Fruehindikatoren.EIA_FUTURES_OEL.replace("_W.xls", "_D.xls"), 8_000_000) },
            Fruehindikatoren.EIA_FUTURES_GAS to roh("eia_fut_gas_d.xls") { api.datei(Fruehindikatoren.EIA_FUTURES_GAS.replace("_W.xls", "_D.xls"), 8_000_000) },
        )
        bericht.append("\n## Lager und Wirtschaftsdaten\n")
        for (k in listOf(Fruehindikatoren.ROHOEL, Fruehindikatoren.CUSHING, Fruehindikatoren.BENZIN, Fruehindikatoren.DESTILLAT, Fruehindikatoren.GAS_US)) {
            val t = Fruehindikatoren.TERMIN[k]!!
            val lager = roh("eia_${k}.xls") { api.datei(Fruehindikatoren.eiaAdresse(Fruehindikatoren.EIA[k]!!), 4_000_000) }?.let { XlsLeser.reihe(it) }.orEmpty()
            val wo = futW[t.datei]?.let { XlsLeser.tabelle(it, t.blatt) }.orEmpty()
            val ta = futD[t.datei]?.let { XlsLeser.tabelle(it, t.blatt) }.orEmpty()
            if (lager.isEmpty() || wo.isEmpty() || ta.isEmpty() || !Fruehindikatoren.terminPlausibel(t, wo)) {
                signale[k] = TendenzRueckblick.Signal(TendenzRueckblick.OHNE_DATEN); continue
            }
            val faelle = TendenzRueckblick.faelle(Eingepreist.abweichungen(lager), Eingepreist.spannen(wo), -1, saison = true)
            val mitte = TendenzRueckblick.rollMitte(t.wurzel)
            val verlauf = TendenzRueckblick.rollVerlauf(ta, mitte = mitte)
            val tageImMonat = TendenzRueckblick.rollTageImMonat(ta, mitte).groupingBy { it }.eachCount().toSortedMap()
            System.err.println("$k: ${faelle.size} Stichtage, ${TendenzRueckblick.rollen(ta, mitte)} Rollen in ${ta.size} Tagen, Tag im Monat: $tageImMonat")
            signale[k] = pruefen("${k} (Kontrakt ${t.wurzel}, mit Roll)", faelle, verlauf, TendenzRueckblick.Art.PROZENT, "${t.wurzel} Kontrakt 1, gerollt in Kontrakt 2")
            // Zum Vergleich ohne Roll (wie die fortlaufende Reihe CL=F), nur im Bericht
            pruefen("${k} (Kontrakt ${t.wurzel}, ohne Roll, nur Vergleich)", faelle, TendenzRueckblick.rollVerlauf(ta, bereinigt = false), TendenzRueckblick.Art.PROZENT, "${t.wurzel} erster Kontrakt ohne Roll")
        }

        // Duerre gegen Getreide (Abstand zum Jahresschnitt), Rendite der fortlaufenden Kontrakte bei Yahoo
        val von2000 = LocalDate.of(2000, 1, 1)
        val duerre = text("duerre.csv", Fruehindikatoren.duerreAdresse(von2000, heute), mapOf("Accept" to "text/csv"), 3_000_000)?.let(Fruehindikatoren::duerreLesen).orEmpty()
        for (korn in Fruehindikatoren.GETREIDE) {
            val wochen = tage(kerzen(korn, von2000, "1wk"))
            val taeglich = tage(kerzen(korn, von2000))
            val sk = TendenzRueckblick.schluessel(Fruehindikatoren.DUERRE, korn)
            if (duerre.isEmpty() || wochen.isEmpty()) { signale[sk] = TendenzRueckblick.Signal(TendenzRueckblick.OHNE_DATEN); continue }
            val faelle = TendenzRueckblick.faelle(Eingepreist.abweichungen(duerre, punkte = true), Eingepreist.gegenSchnitt(wochen), 1, saison = true)
            signale[sk] = pruefen("Dürre gegen $korn", faelle, taeglich, TendenzRueckblick.Art.PROZENT, "$korn fortlaufend (Yahoo, ohne Roll)")
        }

        // Erstantraege gegen die zweijaehrige Rendite und gegen zyklisch/defensiv
        val okhttp = mapOf("Accept" to "text/csv", "User-Agent" to de.troetenrepublik.app.market.Ersatzabrufe.KENNUNG)
        val icsa = text("fred_icsa.csv", Fruehindikatoren.fredAdresse(LocalDate.of(1967, 1, 1), "ICSA"), okhttp, 2_000_000)?.let(Fruehindikatoren::fredLesen).orEmpty()
        val dgs2 = text("fred_dgs2.csv", Fruehindikatoren.fredAdresse(LocalDate.of(1976, 1, 1), Fruehindikatoren.ZWEI_JAHRE), okhttp, 2_000_000)?.let(Fruehindikatoren::fredLesen).orEmpty()
        val anstiege = Eingepreist.anstiege(icsa)
        signale[Fruehindikatoren.ERSTANTRAEGE] = if (anstiege.isEmpty() || dgs2.isEmpty()) TendenzRueckblick.Signal(TendenzRueckblick.OHNE_DATEN) else {
            val faelle = TendenzRueckblick.faelle(anstiege, Eingepreist.unterHoch(dgs2), -1, saison = false, umgekehrt = true)
            pruefen("Erstanträge gegen DGS2 (Tendenz für Anleihen)", faelle, dgs2, TendenzRueckblick.Art.ZINS, "DGS2, Änderung mit umgekehrtem Vorzeichen")
        }
        val von1999 = LocalDate.of(1999, 1, 1)
        val zykW = tage(kerzen(Fruehindikatoren.ZYKLISCH, von1999, "1wk"))
        val defW = tage(kerzen(Fruehindikatoren.DEFENSIV, von1999, "1wk"))
        val zykD = tage(kerzen(Fruehindikatoren.ZYKLISCH, von1999)).toMap()
        val defD = tage(kerzen(Fruehindikatoren.DEFENSIV, von1999)).toMap()
        val verhaeltnis = zykD.keys.sorted().mapNotNull { t -> defD[t]?.let { t to zykD[t]!! / it } }
        signale[TendenzRueckblick.ZYKLISCH] = if (anstiege.isEmpty() || zykW.isEmpty() || defW.isEmpty()) TendenzRueckblick.Signal(TendenzRueckblick.OHNE_DATEN) else {
            val faelle = TendenzRueckblick.faelle(anstiege, Eingepreist.relativ(zykW, defW, 182), -1, saison = false)
            pruefen("Erstanträge gegen XLY/XLP", faelle, verhaeltnis, TendenzRueckblick.Art.PROZENT, "XLY gegen XLP (Kurse ohne Dividenden)")
        }

        // Nicht pruefbar: Fluggaeste (erst ab Mitte 2022 geschaetzt, keine fuenf Jahre Lernzeit), EU-Gas (eigener Schluessel)
        signale[Fruehindikatoren.FLUGGAESTE] = TendenzRueckblick.Signal(TendenzRueckblick.ZU_KURZ, instrument = "JETS gegen SPY")
        signale[Fruehindikatoren.GAS_EU] = TendenzRueckblick.Signal(TendenzRueckblick.OHNE_ZUGANG, instrument = Fruehindikatoren.TTF)

        if (!nurFrueh) {
            bericht.append("\n## Datierte Signale\n")
            val index = kerzen("^GSPC", LocalDate.of(2009, 1, 1))
            val zufall = Random(592)

            // Insiderkaeufe: mitgelieferter SEC-Stand, je Wert alle Kaufmeldungen (Tag des Geschaefts wie die App)
            val sec = Json.parseToJsonElement(File(assets, "copytrading/sec.json").readText()).jsonObject["geschaefte"]!!.jsonArray
            val kaeufe = sec.mapNotNull { e ->
                val o = e.jsonObject
                val kauf = o["kauf"]?.jsonPrimitive?.content == "true"
                val wert = o["wert"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val k = o["k"]?.jsonPrimitive?.content?.trim().orEmpty()
                val d = runCatching { LocalDate.parse(o["d"]!!.jsonPrimitive.content.take(10)).toEpochDay() }.getOrNull()
                if (kauf && wert > 0 && k.isNotBlank() && d != null) CopyRouten.symbol(k.uppercase()) to d else null
            }.distinct()
            val secWerte = kaeufe.map { it.first }.distinct().shuffled(zufall).take(200).toSet()
            val secKurse = HashMap<String, List<Candle>>()
            for (s in secWerte) { kerzen(s, LocalDate.of(2024, 1, 1)).takeIf { it.isNotEmpty() }?.let { secKurse[s] = it } }
            val proWert = kaeufe.filter { it.first in secKurse }.groupBy({ it.first }, { it.second })
            val insider = proWert.flatMap { (s, l) -> l.sorted().map { d -> Ereignis(s, d * TAG + TAG / 2, 1, l.filter { it < d }.map { it * TAG + TAG / 2 }) } }
            signale[TendenzRueckblick.INSIDER] = datiertPruefen("Insiderkäufe (SEC)", insider, secKurse, index)

            // Kaeufe von Abgeordneten: mitgelieferter Stand des Repraesentantenhauses, ab dem Tag der Meldung
            val haus = HausDaten.lesen(File(assets, "copytrading/haus.json").readText())
            val hk = haus?.geschaefte?.values?.flatten().orEmpty()
                .filter { it.kauf && !it.kuerzel.isNullOrBlank() && it.gemeldet > 0 && (it.typ == "ST" || it.typ.isBlank()) }
                .map { CopyRouten.symbol(it.kuerzel!!) to Math.floorDiv(it.gemeldet, TAG) }.distinct()
            val hausWerte = hk.map { it.first }.distinct().shuffled(zufall).take(200).toSet()
            val hausKurse = HashMap<String, List<Candle>>()
            for (s in hausWerte) { (secKurse[s] ?: kerzen(s, LocalDate.of(2024, 1, 1))).takeIf { it.isNotEmpty() }?.let { hausKurse[s] = it } }
            val meldungen = hk.filter { it.first in hausKurse }.map { (s, d) -> Ereignis(s, d * TAG + TAG / 2, 1, emptyList()) }
            signale[TendenzRueckblick.MELDUNG] = datiertPruefen("Käufe von Abgeordneten (Repräsentantenhaus)", meldungen, hausKurse, index)

            // Hoch- und Abstufungen: die beliebtesten US-Aktien des Katalogs, Urteile von Yahoo
            val us = Json.parseToJsonElement(katalog.readText()).jsonObject["aktien"]!!.jsonArray.map { it.jsonObject }
                .filter { it["c"]?.jsonPrimitive?.content == "US" }.mapNotNull { it["s"]?.jsonPrimitive?.content }.take(120)
            val urteile = HashMap<String, List<Analystenbilanz.Urteil>>()
            for (s in us) {
                val b = roh("analysten_${s.replace(Regex("[^A-Za-z0-9]"), "_")}.txt") {
                    api.analysten(s)?.urteile?.takeIf { it.isNotEmpty() }?.joinToString("\n") { u -> listOf(u.haus, u.zeit, u.nach, u.von, u.aktion).joinToString("\t") }?.toByteArray()
                } ?: continue
                urteile[s] = b.toString(Charsets.UTF_8).lineSequence().mapNotNull { z ->
                    val f = z.split('\t').takeIf { it.size == 5 } ?: return@mapNotNull null
                    Analystenbilanz.Urteil(f[0], f[1].toLongOrNull() ?: return@mapNotNull null, f[2], f[3], f[4])
                }.toList()
            }
            val anaKurse = HashMap<String, List<Candle>>()
            for (s in urteile.keys) kerzen(s, LocalDate.of(2009, 1, 1)).takeIf { it.isNotEmpty() }?.let { anaKurse[s] = it }
            for ((art, richtung) in listOf(TendenzRueckblick.HOCH to 1, TendenzRueckblick.AB to -1)) {
                val ev = urteile.filterKeys { it in anaKurse }.flatMap { (s, l) ->
                    val mit = l.mapNotNull { u -> Analystenbilanz.richtung(u)?.let { u to it } }
                    mit.filter { it.second == richtung && it.first.zeit >= LocalDate.of(2012, 1, 1).toEpochDay() * TAG }.map { (u, _) ->
                        Ereignis(s, u.zeit, richtung, mit.filter { it.second == richtung && it.first.zeit < u.zeit }.map { it.first.zeit })
                    }
                }.distinctBy { it.symbol to Math.floorDiv(it.zeit, TAG) }
                signale[art] = datiertPruefen(if (richtung > 0) "Hochstufungen" else "Abstufungen", ev, anaKurse, index)
            }
        }
    }
    // Kennzahlen auf vier Stellen gerundet, damit die Datei klein bleibt
    fun r(v: Double) = Math.round(v * 10_000) / 10_000.0
    val gerundet = signale.mapValues { (_, s) ->
        s.copy(gruppen = s.gruppen.mapValues { (_, l) ->
            l.map { z -> z.copy(treffer = r(z.treffer), grund = r(z.grund), mittel = r(z.mittel), t = r(z.t), tUnabhaengig = r(z.tUnabhaengig), haelften = z.haelften.map(::r), grundMittel = r(z.grundMittel)) }
        })
    }
    val d = TendenzRueckblick.Datei(heute.toString(), TendenzRueckblick.T_SCHWELLE, gerundet)
    ziel.parentFile?.mkdirs()
    ziel.writeText(TendenzRueckblick.schreiben(d))
    File(ordner, "bericht_roh.md").writeText(bericht.toString())
    System.err.println("fertig: ${signale.size} Signale, belegt: " + signale.filterValues { s -> s.gruppen.values.flatten().any { it.belegt } }.keys)
}
