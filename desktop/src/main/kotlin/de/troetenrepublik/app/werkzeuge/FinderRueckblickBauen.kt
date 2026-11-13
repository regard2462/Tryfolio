package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.engine.FinderRueckblick
import de.troetenrepublik.app.engine.FinderRueckblickMessung
import de.troetenrepublik.app.engine.Splits
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Rueckblick-Test des Aktienfinders (Werkzeug, nicht Teil der App): bewertet an jedem Monatsende seit 2012 die
// damaligen Mitglieder des S&P 500 mit der echten Engine, nur mit Daten, die damals bekannt waren, und misst die
// spaetere Rendite (FinderRueckblickMessung). Ausgeschiedene Werte zaehlen mit, solange Yahoo Kurse zu ihnen hat; je
// Stichtag haelt die Datei fest, wie viele der damaligen Mitglieder gemessen wurden. Schreibt nur Kennzahlen nach
// assets/indikatoren/finder_rueckblick.json und Tabellen fuer den Bericht.
// Aufruf: gradlew :desktop:finderRueckblick [-Pfacts=<companyfacts.zip>] [-Pbericht=<Datei>]
// Quellen: Mitglieder je Tag aus assets/indizes/sp500_mitglieder.json (fja05680/sp500, MIT, gebaut mit
// tools/indizes/sp500_mitglieder.py), Yahoo (Kurse, Splits, Analystenurteile, Jahreswerte der letzten vier Jahre) und
// Wikipedia (heutige Mitglieder mit CIK, Namen und Aenderungen als Gegenprobe bei Umbenennungen, CC BY-SA). Bei der SEC
// ruft das Werkzeug nichts ab: Abschluesse von dort nur aus einer lokal liegenden companyfacts.zip. Alle Antworten
// liegen im Zwischenspeicher unter werkzeugdaten/rueckblick, ein zweiter Lauf fragt nichts neu, jedes Kuerzel wird
// hoechstens einmal abgerufen. Zwischen den Abrufen wartet es.

private const val Q1 = "https://query1.finance.yahoo.com"
private const val PAUSE_MS = 600L
private const val START = "2012-01-01"
private const val MSCI = "URTH"
private const val SPX = "^GSPC"

private val DATUM_EN = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

private data class Mitglied(val symbol: String, val name: String, val ab: Long?, val cik: Long?, val branche: String)

fun main(args: Array<String>) {
    val ziel = File(args.getOrNull(0)?.ifBlank { null } ?: error("Ziel fehlt"))
    val bericht = args.getOrNull(1)?.ifBlank { null }?.let { File(it) }
    val fakten = args.getOrNull(2)?.ifBlank { null }?.let { File(it) }?.takeIf { it.isFile }
    val katalog = File(args.getOrNull(3) ?: error("Katalog fehlt"))
    val secDatei = args.getOrNull(4)?.let { File(it) }
    val liste = FinderRueckblick.mitgliederLesen(File(args.getOrNull(5) ?: error("Mitgliederliste fehlt")).readText())
        ?: error("Mitgliederliste unlesbar")
    // Universum: leer fuer den S&P 500, sonst der Name der Mitgliederliste (ndx100). Fuer andere Indizes entfaellt die
    // Gegenprobe mit Wikipedia; Namen kommen dann aus dem Katalog.
    val universum = args.getOrNull(6).orEmpty()
    val ordner = File(System.getProperty("troete.daten") ?: "build/werkzeugdaten", "rueckblick").apply { mkdirs() }
    val api = MarketApi()

    // Katalog: Name und Branche der heutigen US-Werte
    val kat = Json.parseToJsonElement(katalog.readText()).jsonObject["aktien"]!!.jsonArray.map { it.jsonObject }
        .filter { it["c"]?.jsonPrimitive?.content == "US" }
        .associate { o -> o["s"]!!.jsonPrimitive.content to (o["n"]!!.jsonPrimitive.content to o["i"]!!.jsonPrimitive.content) }

    runBlocking {
        // Wikipedia: heutige Mitglieder und Aenderungen
        suspend fun wiki(titel: String, datei: String): String {
            val f = File(ordner, "wiki/$datei")
            if (f.isFile && f.length() > 10_000) return f.readText()
            val a = api.anfrage("https://en.wikipedia.org/api/rest_v1/page/html/$titel", kopf = mapOf("Accept" to "text/html"), max = 8_000_000)
            delay(PAUSE_MS)
            val text = a.text?.takeIf { a.ok } ?: error("Wikipedia antwortet nicht (${a.code})")
            f.parentFile.mkdirs()
            f.writeText(text)
            return text
        }
        val heute = if (universum.isEmpty()) lesenMitglieder(wiki("List_of_S%26P_500_companies", "sp500.html"), kat) else emptyList<Mitglied>()
        val (aenderungen, frueher) =
            if (universum.isEmpty()) lesenAenderungen(wiki("Historical_components_of_the_S%26P_500", "historie.html")) else Pair(emptyList<FinderRueckblick.Aenderung>(), emptyMap<String, String>())
        System.err.println("Wikipedia: ${heute.size} Mitglieder, ${aenderungen.size} Aenderungen")
        // Kuerzel bei Yahoo je Abschnitt der Mitgliederliste (Umbenennungen aufgeloest)
        val yahoo = FinderRueckblick.yahooJeAbschnitt(liste, aenderungen)
        System.err.println("Mitgliederliste ${liste.stand}: ${liste.abschnitte.size} Abschnitte, ${yahoo.count { (a, y) -> y != FinderRueckblick.yahooKuerzel(a.kuerzel) }} unter neuerem Kuerzel")

        // Vergleiche
        val spx = kurse(api, ordner, SPX)
        val msci = kurse(api, ordner, MSCI)
        val letzterTag = FinderRueckblick.tag(spx.last().t)
        // Stichtage: letzter Handelstag jedes Monats, solange zwoelf Monate danach verstrichen sind
        val stichtage = spx.map { FinderRueckblick.tag(it.t) }.filter { it >= LocalDate.parse(START).toEpochDay() }
            .groupBy { LocalDate.ofEpochDay(it).withDayOfMonth(1) }.values.map { it.max() }.sorted()
            .filter { LocalDate.ofEpochDay(it).plusMonths(12).toEpochDay() <= letzterTag && FinderRueckblick.kursZum(msci, it) != null }
        // Je Abschnitt der erste Stichtag, an dem er im Index war; Abschnitte ohne Stichtag bleiben aussen vor
        val ersterStichtag = liste.abschnitte.mapNotNull { a -> stichtage.firstOrNull { a.imIndex(it) }?.let { a to it } }.toMap()
        val alle = ersterStichtag.keys.map { yahoo.getValue(it) }.toSortedSet()
        System.err.println("${stichtage.size} Stichtage, ${ersterStichtag.size} Abschnitte mit ${alle.size} Kuerzeln waren in der Zeit Mitglied")

        // Je Wert: Kurse, Splits, Urteile, Jahreswerte von Yahoo (nur, wenn Kurse zu einem Abschnitt passen)
        val namen = heute.associateBy { it.symbol }
        val eingaben = HashMap<String, FinderRueckblick.Eingaben>()
        val kurseJe = HashMap<String, List<Candle>>()
        var ohneKurse = 0
        var umbenannt = 0
        for ((n, s) in alle.withIndex()) {
            val k = kurse(api, ordner, s)
            if (k.size < 60) { ohneKurse++; continue }
            kurseJe[s] = k
            // Kuerzel, das heute eine andere Firma traegt: Kurse beginnen erst nach dem ersten Stichtag jedes Abschnitts
            if (ersterStichtag.none { (a, e) -> yahoo[a] == s && FinderRueckblick.kurseFuer(k, e) }) { umbenannt++; continue }
            val sp = splits(api, ordner, s)
            val ur = urteile(api, ordner, s)
            val ab = if (FinderRueckblick.tag(k.last().t) >= LocalDate.parse("2023-01-01").toEpochDay()) yahooAbschluesse(api, ordner, s) else emptyList()
            val frueherName = frueher[s] ?: ersterStichtag.keys.firstNotNullOfOrNull { a -> if (yahoo[a] == s) frueher[FinderRueckblick.yahooKuerzel(a.kuerzel)] else null }
            val (name, branche) = kat[s] ?: ((namen[s]?.name ?: frueherName ?: s) to (namen[s]?.branche ?: ""))
            eingaben[s] = FinderRueckblick.Eingaben(s, name, branche.ifBlank { "?$s" }, k, ab, ur, sp)
            if (n % 50 == 0) System.err.println("${n + 1}/${alle.size} $s")
        }
        System.err.println("Mit Kursen: ${eingaben.size}, ohne Kurse bei Yahoo: $ohneKurse, Kuerzel heute anders belegt: $umbenannt")

        // Abschluesse der SEC aus der lokalen companyfacts.zip, wenn vorhanden (ersetzen die Jahreswerte von Yahoo)
        var mitFakten = false
        if (fakten != null) {
            val cik = HashMap<Long, String>()
            heute.forEach { m -> if (m.cik != null && m.symbol in eingaben) cik[m.cik] = m.symbol }
            secDatei?.takeIf { it.isFile }?.let { d ->
                runCatching {
                    Json.parseToJsonElement(d.readText()).jsonObject["geschaefte"]!!.jsonArray.forEach { g ->
                        val o = g.jsonObject
                        val k = o["k"]?.jsonPrimitive?.content?.let { FinderRueckblick.yahooKuerzel(it) } ?: return@forEach
                        val c = o["c"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@forEach
                        if (k in eingaben && c !in cik) cik[c] = k
                    }
                }
            }
            val vergeben = cik.values.toHashSet()
            val nachName = eingaben.values.filter { it.symbol !in vergeben }
            val gelesen = FinderRueckblick.ausZip(fakten, { c, n ->
                cik[c] ?: nachName.firstOrNull { Gleichwerte.namenGleich(n, it.name) }?.symbol
            }, { eingaben[it]?.splits.orEmpty() })
            gelesen.forEach { (s, l) -> eingaben[s] = eingaben.getValue(s).copy(abschluesse = l) }
            mitFakten = gelesen.isNotEmpty()
            System.err.println("companyfacts: ${gelesen.size} Werte mit Abschluessen")
        }

        // Bewerten je Stichtag mit der echten Engine, nebenlaeufig
        val gewichte = FinderRueckblick.testGewichte()
        val horizonte = listOf(FinderRueckblickMessung.MONAT) + FinderRueckblickMessung.HORIZONTE + FinderRueckblickMessung.HALTEN + FinderRueckblick.BIS_HEUTE
        // Rendite zum Horizont oder, bei BIS_HEUTE, vom Stichtag bis zum letzten Kurstag
        fun rend(k: List<Candle>, t: Long, h: Int) =
            if (h == FinderRueckblick.BIS_HEUTE) FinderRueckblick.renditeBis(k, t, letzterTag)
            else FinderRueckblick.rendite(k, t, h, letzterTag)
        // Ein Abschnitt ist messbar, wenn die Kurse unter seinem Yahoo-Kuerzel zur Firma von damals passen
        val messbar = { a: FinderRueckblick.Abschnitt ->
            val s = yahoo.getValue(a)
            val e = ersterStichtag[a]
            s in eingaben && e != null && FinderRueckblick.kurseFuer(kurseJe.getValue(s), e)
        }
        val ergebnis = stichtage.parallelStream().map { t ->
            val (soll, mitglieder) = FinderRueckblick.zumStichtag(liste, yahoo, messbar, t)
            val daten = mitglieder.mapNotNull { s -> eingaben[s]?.let { FinderRueckblick.datenZum(it, t, spx) } }
            val bewertet = Aktienfinder.bewerten(daten, gewichte)
            val werte = bewertet.map { e ->
                FinderRueckblickMessung.Wert(e.symbol, e.bereiche, horizonte.associateWith { h -> rend(eingaben.getValue(e.symbol).kurse, t, h) }, e.kennzahlen)
            }
            FinderRueckblickMessung.Stichtag(
                t, werte, horizonte.associateWith { rend(msci, t, it) },
                horizonte.associateWith { rend(spx, t, it) }, soll,
            )
        }.toList().sortedBy { it.tag }

        val datei = FinderRueckblickMessung.zusammenfassen(ergebnis, gewichte, LocalDate.now().toString(), mitFakten, letzterTag, liste.stand, universum)
        ziel.parentFile?.mkdirs()
        ziel.writeText(FinderRueckblickMessung.schreiben(datei) + "\n")
        val umbenennungen = ersterStichtag.keys.sortedBy { it.ab }.mapNotNull { a ->
            yahoo.getValue(a).takeIf { it != FinderRueckblick.yahooKuerzel(a.kuerzel) }?.let { "${a.kuerzel} (${FinderRueckblick.datum(a.ab)} bis ${a.bis?.let { b -> FinderRueckblick.datum(b) }}) als $it" }
        }
        val tabellen = tabellen(datei, ergebnis, eingaben, ohneKurse, umbenannt, alle.size, umbenennungen)
        (bericht ?: File(ordner, "tabellen.md")).apply { parentFile?.mkdirs() }.writeText(tabellen)
        System.err.println(tabellen)
    }
}

// Heutige Mitglieder aus der Tabelle „constituents“: Kuerzel, Name, Aufnahmetag, CIK; Branche aus dem Katalog.
private fun lesenMitglieder(html: String, kat: Map<String, Pair<String, String>>): List<Mitglied> {
    val tabelle = Jsoup.parse(html).getElementById("constituents") ?: error("Tabelle der Mitglieder fehlt")
    return tabelle.select("tr").mapNotNull { tr ->
        val z = tr.select("td").map { it.text().trim() }
        if (z.size < 7) return@mapNotNull null
        val s = FinderRueckblick.yahooKuerzel(z[0])
        Mitglied(s, z[1], FinderRueckblick.tag(z[5]), z[6].toLongOrNull(), kat[s]?.second.orEmpty())
    }
}

// Aenderungen aus der Tabelle „changes“: Datum, aufgenommen (Kuerzel, Name), entfernt (Kuerzel, Name), Grund.
// Dazu die Namen je Kuerzel (fuer die Zuordnung der SEC-Abschluesse frueherer Mitglieder).
private fun lesenAenderungen(html: String): Pair<List<FinderRueckblick.Aenderung>, Map<String, String>> {
    val tabelle = Jsoup.parse(html).getElementById("changes") ?: error("Tabelle der Aenderungen fehlt")
    val aus = ArrayList<FinderRueckblick.Aenderung>()
    val namen = HashMap<String, String>()
    var letzter: Long? = null
    for (tr in tabelle.select("tr")) {
        val z = tr.select("td").map { it.text().trim() }
        if (z.size < 4) continue
        val tag = runCatching { LocalDate.parse(z[0], DATUM_EN).toEpochDay() }.getOrNull()
        // Zeilen ohne eigenes Datum (zusammengefasste Zelle) gehoeren zum Datum davor
        val zellen = if (tag != null) z.drop(1) else z
        val t = tag ?: letzter ?: continue
        letzter = t
        val hinzu = zellen.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { FinderRueckblick.yahooKuerzel(it) }
        val weg = zellen.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { FinderRueckblick.yahooKuerzel(it) }
        hinzu?.let { k -> zellen.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { namen.putIfAbsent(k, it) } }
        weg?.let { k -> zellen.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { namen.putIfAbsent(k, it) } }
        if (hinzu != null || weg != null) aus += FinderRueckblick.Aenderung(t, hinzu, weg)
    }
    return aus to namen
}

// Antwortet die Quelle mit 403 oder 429, bricht der Lauf ab (bereits Gespeichertes bleibt im Zwischenspeicher).
private fun abbruchBeiSperre(code: Int) {
    if (code == 403 || code == 429) error("Quelle antwortet mit $code, Abbruch ohne Wiederholung")
}

private fun dateiname(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

// Tageskurse (Zeit in ms und Schlusskurs), zwischengespeichert. Leere Datei: Yahoo hat keine Kurse.
private suspend fun kurse(api: MarketApi, ordner: File, s: String): List<Candle> {
    val f = File(ordner, "kurse/${dateiname(s)}.csv")
    if (!f.isFile) {
        val k = runCatching { api.candlesBetween(s, 0L, System.currentTimeMillis() / 1000).candles }.getOrDefault(emptyList())
        delay(PAUSE_MS)
        // Ohne Kerzen einmal nachsehen, ob die Quelle sperrt (403, 429): dann Abbruch, nichts speichern, nicht wiederholen
        if (k.isEmpty()) {
            val a = api.anfrage("$Q1/v8/finance/chart/${java.net.URLEncoder.encode(s, "UTF-8")}?interval=1mo&range=1mo")
            delay(PAUSE_MS)
            abbruchBeiSperre(a.code)
        }
        f.parentFile.mkdirs()
        f.writeText(k.joinToString("\n") { "${it.t};${it.close}" })
    }
    return f.readLines().mapNotNull { z ->
        val p = z.split(';')
        val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val c = p.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return@mapNotNull null
        Candle(t, c, c, c, c)
    }.sortedBy { it.t }
}

// Splits der ganzen Geschichte aus dem Chart-Endpunkt (Monatskerzen halten die Antwort klein).
private suspend fun splits(api: MarketApi, ordner: File, s: String): List<Splits.Split> {
    val f = File(ordner, "splits/${dateiname(s)}.csv")
    if (!f.isFile) {
        val bis = System.currentTimeMillis() / 1000
        val a = api.anfrage("$Q1/v8/finance/chart/${java.net.URLEncoder.encode(s, "UTF-8")}?interval=1mo&period1=0&period2=$bis&events=split")
        delay(PAUSE_MS)
        abbruchBeiSperre(a.code)
        if (!a.ok) return emptyList()
        val l = runCatching {
            val r = (Json.parseToJsonElement(a.text!!).jsonObject["chart"]!!.jsonObject["result"] as JsonArray).first().jsonObject
            ((r["events"] as? JsonObject)?.get("splits") as? JsonObject)?.values.orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun z(k: String) = (o[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
                val t = z("date") ?: return@mapNotNull null
                val zaehler = z("numerator")?.takeIf { it > 0 } ?: return@mapNotNull null
                val nenner = z("denominator")?.takeIf { it > 0 } ?: return@mapNotNull null
                "${t.toLong() * 1000L};$zaehler;$nenner"
            }
        }.getOrDefault(emptyList())
        f.parentFile.mkdirs()
        f.writeText(l.joinToString("\n"))
    }
    return f.readLines().mapNotNull { z ->
        val p = z.split(';')
        Splits.Split(p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null, p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null, p.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null)
    }.sortedBy { it.t }
}

// Hoch- und Abstufungen (upgradeDowngradeHistory), aufsteigend nach Zeit. Ohne Antwort nichts gespeichert.
private suspend fun urteile(api: MarketApi, ordner: File, s: String): List<Analystenbilanz.Urteil> {
    val f = File(ordner, "analysten/${dateiname(s)}.tsv")
    if (!f.isFile) {
        val v = api.analysten(s)
        delay(PAUSE_MS)
        if (v == null) return emptyList()
        f.parentFile.mkdirs()
        f.writeText(v.urteile.joinToString("\n") { u ->
            listOf(u.zeit.toString(), u.haus, u.nach, u.von, u.aktion, u.ziel.toString(), u.zielVorher.toString()).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
        })
    }
    return f.readLines().mapNotNull { z ->
        val p = z.split('\t')
        if (p.size < 7) return@mapNotNull null
        Analystenbilanz.Urteil(p[1], p[0].toLongOrNull() ?: return@mapNotNull null, p[2], p[3], p[4], p[5].toDoubleOrNull() ?: 0.0, p[6].toDoubleOrNull() ?: 0.0)
    }.sortedBy { it.zeit }
}

// Jahreswerte von Yahoo (ws/fundamentals-timeseries), Rohtext zwischengespeichert.
private suspend fun yahooAbschluesse(api: MarketApi, ordner: File, s: String): List<FinderRueckblick.Abschluss> {
    val f = File(ordner, "yahoo/${dateiname(s)}.json")
    if (!f.isFile) {
        val text = api.jahresabschluesse(s, FinderRueckblick.YAHOO_FELDER.map { it.first }.distinct())
        delay(PAUSE_MS)
        if (text == null) return emptyList()
        f.parentFile.mkdirs()
        f.writeText(text)
    }
    return FinderRueckblick.ausYahoo(f.readText())
}

private fun p(x: Double, stellen: Int = 1): String = String.format(Locale.GERMAN, "%+.${stellen}f %%", x * 100)
private fun q(x: Double, stellen: Int = 0): String = String.format(Locale.GERMAN, "%.${stellen}f %%", x * 100)
private fun z(x: Double, stellen: Int = 3): String = String.format(Locale.GERMAN, "%.${stellen}f", x)

// Tabellen fuer den Bericht (Markdown).
private fun tabellen(
    d: FinderRueckblickMessung.Datei, st: List<FinderRueckblickMessung.Stichtag>, eingaben: Map<String, FinderRueckblick.Eingaben>,
    ohneKurse: Int, umbenannt: Int, alle: Int, umbenennungen: List<String>,
): String = buildString {
    appendLine("Zeitraum ${d.von} bis ${d.bis}, ${d.stichtage} Stichtage, im Schnitt ${d.werte} bewertete Werte, fehlend ${q(d.fehlend, 1)}")
    appendLine("Werte je Mitgliedschaft: $alle, ohne Kurse $ohneKurse, Kuerzel anders belegt $umbenannt, mit Urteilen ${eingaben.values.count { it.urteile.isNotEmpty() }}, mit Abschluessen ${eingaben.values.count { it.abschluesse.isNotEmpty() }}")
    val ersteUrteile = eingaben.values.mapNotNull { e -> e.urteile.firstOrNull()?.zeit }.sorted()
    if (ersteUrteile.isNotEmpty()) appendLine("Erste Urteile: Median ${FinderRueckblick.datum(FinderRueckblick.tag(ersteUrteile[ersteUrteile.size / 2]))}, frueheste ${FinderRueckblick.datum(FinderRueckblick.tag(ersteUrteile.first()))}")
    appendLine("Gewichte im Test: ${d.gewichte}")
    appendLine("Datenlage (Anteil Stichtage mit Daten bei mind. der Haelfte): ${d.datenlage.mapValues { q(it.value) }}")
    appendLine()
    fun hz(titel: String, l: List<FinderRueckblickMessung.Horizont>) {
        appendLine("### $titel")
        appendLine("| Monate | Stichtage | Top 10 | Top 20 | MSCI World | S&P 500 | Universum | Top 20 minus MSCI (SE) | Trefferquote | IC (SE) | Fuenftel-Spanne (SE) |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        l.forEach { h ->
            appendLine("| ${h.monate} | ${h.stichtage} | ${p(h.top10)} | ${p(h.top20)} | ${p(h.msci)} | ${p(h.spx)} | ${p(h.universum)} | ${p(h.mehr)} (${p(h.mehrSe)}) | ${q(h.treffer)} | ${z(h.ic)} (${z(h.icSe)}) | ${p(h.spanne)} (${p(h.spanneSe)}) |")
        }
        appendLine()
    }
    hz("Gesamter Zeitraum", d.horizonte)
    hz("Pruefzeitraum ab ${FinderRueckblickMessung.PRUEF_AB}", d.pruefzeit)
    hz("Kaufen und halten (Monate)", d.halten)
    d.haltenHeute?.let { h ->
        appendLine("### Erster Stichtag bis heute")
        appendLine("Von ${h.von} bis ${h.bis} (${z(h.jahre, 1)} Jahre): Top 20 ${p(h.top20)}, MSCI World ${p(h.msci)}, S&P 500 ${p(h.spx)}, Universum ${p(h.universum)}; ${h.anzahl} Werte mit Rendite")
        appendLine()
    }
    appendLine("### Rang-Korrelation je Bereich")
    appendLine("| Bereich | Monate | IC | SE | t | Stichtage | ab |")
    appendLine("|---|---|---|---|---|---|---|")
    d.ics.forEach { appendLine("| ${it.bereich} | ${it.monate} | ${z(it.ic)} | ${z(it.se)} | ${z(if (it.se > 0) it.ic / it.se else 0.0, 1)} | ${it.stichtage} | ${it.ab} |") }
    appendLine()
    val pruef = st.filter { it.jahr >= FinderRueckblickMessung.PRUEF_AB }
    appendLine("### Rang-Korrelation je Bereich ab ${FinderRueckblickMessung.PRUEF_AB}")
    appendLine("| Bereich | Monate | IC | SE | Stichtage |")
    appendLine("|---|---|---|---|---|")
    FinderRueckblick.IM_TEST.forEach { b -> FinderRueckblickMessung.HORIZONTE.forEach { h ->
        FinderRueckblickMessung.bereichWert(pruef, b, h)?.let { appendLine("| ${b.name} | $h | ${z(it.ic)} | ${z(it.se)} | ${it.stichtage} |") }
    } }
    appendLine()
    appendLine("### Jahr fuer Jahr (monatlich umgeschichtet, Top 20)")
    appendLine("| Jahr | Monate | Top 20 | Top 20 nach Kosten | MSCI World | S&P 500 | Universum | Top 20 minus MSCI |")
    appendLine("|---|---|---|---|---|---|---|---|")
    d.jahre.forEach { appendLine("| ${it.jahr} | ${it.monate} | ${p(it.top20)} | ${p(it.top20Netto)} | ${p(it.msci)} | ${p(it.spx)} | ${p(it.universum)} | ${p(it.top20Netto - it.msci)} |") }
    appendLine()
    d.strategie?.let { s ->
        appendLine("### Monatliches Umschichten")
        appendLine("Pro Jahr: Top 20 ${p(s.top20)}, nach Kosten ${p(s.top20Netto)}, MSCI World ${p(s.msci)}, S&P 500 ${p(s.spx)}, Universum ${p(s.universum)}")
        appendLine("Umschlag je Monat ${q(s.umschlag)}, Kosten pro Jahr ${q(s.kostenJahr, 2)}; groesster Rueckstand gegen MSCI World ${p(-s.rueckstand)} (${s.rueckstandVon} bis ${s.rueckstandBis}); groesster Verlust der Top 20 ${p(-s.tiefstand)}")
        appendLine()
    }
    // Andere Gewichte zum Vergleich (im Nachhinein gewaehlt, deshalb nur als Einordnung)
    val standard = FinderRueckblick.testGewichte()
    val varianten = listOf(
        "Standard" to standard,
        "nur Trend" to standard.mapValues { (b, _) -> if (b == Aktienfinder.Bereich.TREND) 1.0 else 0.0 },
        "nur Analysten" to standard.mapValues { (b, _) -> if (b == Aktienfinder.Bereich.ANALYSTEN) 1.0 else 0.0 },
        "nur Bewertung" to standard.mapValues { (b, _) -> if (b == Aktienfinder.Bereich.BEWERTUNG) 1.0 else 0.0 },
        "nur Qualitaet" to standard.mapValues { (b, _) -> if (b == Aktienfinder.Bereich.QUALITAET) 1.0 else 0.0 },
        "Analysten 10, Trend 20" to standard + mapOf(Aktienfinder.Bereich.ANALYSTEN to 10.0, Aktienfinder.Bereich.TREND to 20.0),
        "Analysten 10, Qualitaet 25" to standard + mapOf(Aktienfinder.Bereich.ANALYSTEN to 10.0, Aktienfinder.Bereich.QUALITAET to 25.0),
        "Analysten 10, Bewertung 35" to standard + mapOf(Aktienfinder.Bereich.ANALYSTEN to 10.0, Aktienfinder.Bereich.BEWERTUNG to 35.0),
    )
    appendLine("### Andere Gewichte (Top 20 minus MSCI, IC; gesamt und ab ${FinderRueckblickMessung.PRUEF_AB})")
    appendLine("| Gewichte | 6 M gesamt | 12 M gesamt | IC 6 M | 6 M ab 2019 | 12 M ab 2019 | IC 6 M ab 2019 | 6 M ab 2023 | IC 6 M ab 2023 |")
    appendLine("|---|---|---|---|---|---|---|---|---|")
    val ab2023 = st.filter { it.jahr >= 2023 }
    varianten.forEach { (name, g) ->
        val a6 = FinderRueckblickMessung.horizont(st, g, 6)
        val a12 = FinderRueckblickMessung.horizont(st, g, 12)
        val p6 = FinderRueckblickMessung.horizont(pruef, g, 6)
        val p12 = FinderRueckblickMessung.horizont(pruef, g, 12)
        val n6 = FinderRueckblickMessung.horizont(ab2023, g, 6)
        appendLine("| $name | ${a6?.let { p(it.mehr) }} | ${a12?.let { p(it.mehr) }} | ${a6?.let { z(it.ic) }} | ${p6?.let { p(it.mehr) }} | ${p12?.let { p(it.mehr) }} | ${p6?.let { z(it.ic) }} | ${n6?.let { p(it.mehr) }} | ${n6?.let { z(it.ic) }} |")
    }
    appendLine()
    // Anteil der damaligen Mitglieder, die gemessen wurden, je Jahr und der schwaechste Stichtag (Survivorship)
    appendLine("### Abdeckung (gemessen je Stichtag, Mitgliederliste Stand ${d.mitgliederStand})")
    appendLine("Schnitt ${d.abdeckungSchnitt()?.let { q(it, 1) }}; je Jahr: " + d.abdeckungJeJahr().joinToString(", ") { (j, a) -> "$j: ${q(a)}" })
    d.abdeckung.minByOrNull { it.anteil }?.let { appendLine("Niedrigste: ${it.tag} ${it.gemessen} von ${it.mitglieder} (${q(it.anteil, 1)})") }
    appendLine()
    appendLine("### Abschnitte unter neuerem Kuerzel")
    appendLine(umbenennungen.joinToString(", "))
    appendLine()
    d.lernen?.let { l ->
        appendLine("### Walk-forward (${l.monate} Monate, Pruefung ab ${l.ab})")
        appendLine("Stichtage ${l.stichtage}, Jahre ${l.jahre}, Jahre besser ${l.jahreBesser}")
        appendLine("IC Standard ${z(l.icStandard)}, gelernt ${z(l.icGelernt)}, Differenz ${z(l.icDiff)} (SE ${z(l.icDiffSe)})")
        appendLine("Top 20 minus MSCI: Standard ${p(l.mehrStandard)}, gelernt ${p(l.mehrGelernt)}, Differenz ${p(l.mehrDiff)} (SE ${p(l.mehrDiffSe)})")
        appendLine("Gelernte Gewichte (alle Daten): ${l.gewichte.mapValues { z(it.value, 1) }}")
        l.jeJahr.forEach { (j, g) -> appendLine("- $j: ${g.mapValues { z(it.value, 1) }}") }
        appendLine("Klar und stabil besser: ${l.klarBesser}")
    }
}
