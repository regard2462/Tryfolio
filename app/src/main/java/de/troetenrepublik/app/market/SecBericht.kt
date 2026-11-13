package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Rohstoffprofil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Jahresberichte bei der SEC (10-K, 20-F, 40-F) als Quelle fuer den Umsatz nach Segment, Region und Grosskunde (seit 0.74):
// Kuerzel zu CIK, Liste der Einreichungen, Einreichungsordner mit der XBRL-Instanz. Gelesen wird die Instanz in Xbrl.kt.
object SecBericht {
    const val QUELLE = "U.S. Securities and Exchange Commission (EDGAR, XBRL)"

    // Groesste Instanz in entpackten Bytes
    const val MAX_BYTES = 25_000_000L

    private val JAHRESFORMEN = setOf("10-K", "20-F", "40-F")

    // k: 0 Name, 1 Kunde (nr: A, 1 ...), 2 Kundengruppe (nr: Anzahl), 3 Land (l: Kuerzel), 4 Sitzland, 5 ausserhalb der USA.
    // w: Anteil an der Summe der Gruppe, bei Grosskunden am Umsatz.
    @Serializable
    class Posten(val n: String, val w: Double, val l: String = "", val k: Int = 0, val nr: String = "")

    // Status der Gruppen: 0 vorhanden, 1 nicht eindeutig gemeldet, 2 nicht gemeldet
    @Serializable
    class Auswertung(
        val form: String = "",
        val stichtag: String = "",
        val jahr: Int = 0,
        val gesamt: Double = 0.0,
        val segmente: List<Posten> = emptyList(),
        val regionen: List<Posten> = emptyList(),
        val kunden: List<Posten> = emptyList(),
        val segmenteStatus: Int = 2,
        val regionenStatus: Int = 2,
    )

    // Stand je Wert auf dem Geraet. abgerufen: letzte Nachfrage bei der SEC (ms); acc: Einreichung der Auswertung.
    @Serializable
    class Eintrag(
        val abgerufen: Long = 0L,
        val cik: String = "",
        val acc: String = "",
        val status: Int = Status.OK.ordinal,
        val bericht: Auswertung? = null,
        val eingang: String = "",
        // [0.76] sd: Datum der juengsten Form SD (Konfliktmineralien) der letzten zwei Jahre, "" ohne; roh: im Rohstoffabschnitt
        // des Jahresberichts genannte Rohstoffe, gelesen fuer die Einreichung rohAcc
        val sd: String = "",
        val roh: List<String> = emptyList(),
        val rohAcc: String = "",
    )

    enum class Status { OK, KEIN_SEC, KEIN_BERICHT, ZU_GROSS, NICHT_LESBAR, FEHLER, OHNE_KONTAKT }

    @Serializable
    class Datei(val werte: Map<String, Eintrag> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // ------------------------------------------------------------ Zuordnung Kuerzel zu CIK

    private val FIRMENWORTE = setOf(
        "inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited", "plc", "nv", "se", "ag", "sa", "adr", "ads",
        "holding", "holdings", "group", "the", "de", "cl", "class", "spa", "oyj", "ab", "asa", "llc", "lp",
        "as", "bv", "kk", "oy", "publ", "pcl", "sab", "cv", "koninklijke", "telefonaktiebolaget", "aktiengesellschaft", "sponsored",
    )

    internal fun nameKern(s: String): String =
        s.lowercase().replace(Regex("\\ba/s\\b"), "as").substringBefore('/').replace(".", "").replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length > 1 && it !in FIRMENWORTE }.joinToString(" ")

    internal fun namenPassen(a: String, b: String): Boolean {
        val x = a.split(' ').filter { it.isNotEmpty() }.toSet(); val y = b.split(' ').filter { it.isNotEmpty() }.toSet()
        return x.isNotEmpty() && y.isNotEmpty() && (x.containsAll(y) || y.containsAll(x))
    }

    // CIK aus der Kuerzelliste der SEC. Ein Kuerzel ohne Boersenzusatz gilt direkt (auch mit Klassenzusatz wie BRK-B). Mit
    // Boersenzusatz (SAP.DE) zaehlt das Basiskuerzel nur, wenn der Firmenname passt; sonst entscheidet der Name allein, wenn er
    // genau einer Firma gehoert.
    fun cikAusListe(text: String, symbol: String, name: String = ""): String? {
        class E(val cik: String, val kuerzel: String, val titel: String)
        val liste = runCatching {
            json.parseToJsonElement(text).jsonObject.values.mapNotNull { e ->
                val o = e.jsonObject
                val c = o["cik_str"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val t = o["ticker"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                E(c, t.uppercase(), o["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
            }
        }.getOrNull().orEmpty()
        if (liste.isEmpty()) return null
        val s = symbol.uppercase()
        listOf(s, s.replace('.', '-'), s.replace('-', '.')).forEach { k -> liste.firstOrNull { it.kuerzel == k }?.let { return it.cik } }
        val n = nameKern(name)
        if (n.isEmpty()) return null
        if (s.contains('.')) {
            // Basiskuerzel (mit und ohne Klassenzusatz wie ERIC-B) zaehlt, wenn die Namensworte der einen Seite in der anderen stehen
            val basis = s.substringBefore('.')
            listOf(basis, basis.substringBefore('-')).distinct().forEach { b ->
                liste.firstOrNull { it.kuerzel == b && namenPassen(nameKern(it.titel), n) }?.let { return it.cik }
            }
        }
        val gleich = liste.filter { nameKern(it.titel) == n }.map { it.cik }.distinct()
        return gleich.singleOrNull()
    }

    // ------------------------------------------------------------ Einreichungen

    fun submissionsAdresse(cik: String) = "https://data.sec.gov/submissions/CIK" + cik.trimStart('0').padStart(10, '0') + ".json"

    fun verzeichnisAdresse(cik: String, acc: String) = SecInsider.verzeichnisAdresse(cik, acc)

    fun archivAdresse(cik: String, acc: String, datei: String) = SecInsider.archivAdresse(cik, acc, datei)

    class Jahresbericht(val acc: String, val form: String, val eingang: String, val stichtag: String)

    // Juengster Jahresbericht (10-K, 20-F, 40-F, ohne Nachtraege) mit XBRL aus der Liste der letzten Einreichungen
    fun juengsterJahresbericht(text: String): Jahresbericht? = runCatching {
        val r = json.parseToJsonElement(text).jsonObject["filings"]?.jsonObject?.get("recent")?.jsonObject ?: return null
        fun spalte(k: String) = r[k]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val formen = spalte("form"); val nummern = spalte("accessionNumber"); val tage = spalte("filingDate")
        val berichtstage = spalte("reportDate"); val xbrl = spalte("isXBRL")
        formen.indices.filter { formen[it] in JAHRESFORMEN && (xbrl.getOrNull(it).let { x -> x == null || x == "1" }) }
            .maxByOrNull { tage.getOrNull(it).orEmpty() }
            ?.let { i -> Jahresbericht(nummern.getOrNull(i) ?: return null, formen[i], tage.getOrNull(i).orEmpty(), berichtstage.getOrNull(i).orEmpty()) }
    }.getOrNull()

    // [0.76] Juengste Form SD (Konfliktmineralien) aus der Liste der letzten Einreichungen, hoechstens zwei Jahre alt;
    // jahr: laufendes Jahr. "" ohne.
    fun sdDatum(text: String, jahr: Int): String = runCatching {
        val r = json.parseToJsonElement(text).jsonObject["filings"]?.jsonObject?.get("recent")?.jsonObject ?: return ""
        fun spalte(k: String) = r[k]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val formen = spalte("form"); val tage = spalte("filingDate")
        formen.indices.filter { formen[it] == "SD" || formen[it] == "SD/A" }.map { tage.getOrNull(it).orEmpty() }
            .filter { it >= (jahr - 2).toString() }.maxOrNull().orEmpty()
    }.getOrDefault("")

    class Hauptdokument(val datei: String, val groesse: Long)

    // Hauptdokument (HTML) einer Einreichung aus der Liste der letzten Einreichungen
    fun hauptdokument(text: String, acc: String): Hauptdokument? = runCatching {
        val r = json.parseToJsonElement(text).jsonObject["filings"]?.jsonObject?.get("recent")?.jsonObject ?: return null
        fun spalte(k: String) = r[k]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val nummern = spalte("accessionNumber"); val dateien = spalte("primaryDocument"); val groessen = spalte("size")
        val i = nummern.indexOf(acc).takeIf { it >= 0 } ?: return null
        val datei = dateien.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return null
        Hauptdokument(datei, groessen.getOrNull(i)?.toLongOrNull() ?: 0L)
    }.getOrNull()

    // Harte Obergrenze fuer das, was vom Jahresbericht als Text gelesen wird (entpackt, Rohstoffabschnitt). Gelesen wird nur bis
    // "Item 1A. Risk Factors"; die Antwort kommt komprimiert (gzip), der Abruf endet, sobald der Abschnitt gelesen ist.
    const val MAX_TEXT_BYTES = 1_500_000L

    // Schritt, in dem beim Lesen geprueft wird, ob "Item 1A" erreicht ist (entpackte Bytes)
    private const val PRUEFSCHRITT = 192 * 1024

    // Liest den Jahresbericht (HTML) aus dem Strom, bis "Item 1A. Risk Factors" erreicht ist oder max Bytes gelesen sind, und
    // gibt das Gelesene als Text zurueck. Der Rest des Stroms wird nicht abgenommen.
    fun lesenAbschnitt(ein: java.io.InputStream, max: Long = MAX_TEXT_BYTES): String {
        val aus = java.io.ByteArrayOutputStream()
        val puffer = ByteArray(16_384)
        var naechste = PRUEFSCHRITT
        while (aus.size() < max) {
            val n = ein.read(puffer, 0, minOf(puffer.size.toLong(), max - aus.size()).toInt())
            if (n < 0) break
            aus.write(puffer, 0, n)
            if (aus.size() >= naechste) {
                naechste = aus.size() + PRUEFSCHRITT
                if (Rohstoffprofil.risikoBeginn(Rohstoffprofil.klartext(aus.toByteArray().decodeToString())) >= 0) break
            }
        }
        return aus.toByteArray().decodeToString()
    }

    class Instanz(val datei: String, val groesse: Long)

    // Instanzdatei im Einreichungsordner (index.json): bei Inline-XBRL die Datei „…_htm.xml“, sonst die groesste XML-Datei
    // ausser Gliederungen, Beschriftungen und Berichtsuebersicht
    fun instanzDatei(indexJson: String): Instanz? = runCatching {
        val items = json.parseToJsonElement(indexJson).jsonObject["directory"]?.jsonObject?.get("item")?.jsonArray.orEmpty().mapNotNull {
            val o = it.jsonObject
            val n = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Instanz(n, o["size"]?.jsonPrimitive?.contentOrNull?.trim()?.toLongOrNull() ?: 0L)
        }
        items.firstOrNull { it.datei.endsWith("_htm.xml", true) } ?: items.filter {
            val n = it.datei.lowercase()
            n.endsWith(".xml") && n != "filingsummary.xml" && !Regex("_(cal|def|lab|pre|ref)\\.xml$").containsMatchIn(n) &&
                !Regex("^r\\d+\\.xml$").matches(n) && !n.contains("index") && !n.startsWith("financial_report")
        }.maxByOrNull { it.groesse }
    }.getOrNull()
}
