package de.troetenrepublik.app.market

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

// Wertpapiergeschaefte von Abgeordneten des US-Repraesentantenhauses.
// Grundlage sind die Meldungen nach dem STOCK Act, die der Clerk of the House oeffentlich bereitstellt: ein
// Jahresverzeichnis aller Offenlegungen (ZIP mit XML) und je Transaktionsmeldung (Periodic Transaction Report, PTR)
// ein PDF. Abgeordnete muessen Geschaefte ueber 1.000 Dollar spaetestens 45 Tage nach dem Geschaeft melden, mit
// Betragsspannen statt genauer Summen. Die App liest die PDFs selbst (siehe PdfText) und zeigt nur die Angaben;
// eingescannte Papiermeldungen enthalten kein Textlayer und lassen sich nicht auslesen.
// Nutzung: Nach 5 U.S.C. § 13107 duerfen die Berichte nicht fuer kommerzielle Zwecke verwendet werden (ausser von
// Medien zur Verbreitung an die Oeffentlichkeit); die App ist nicht kommerziell und zeigt den Hinweis.
object Kongress {
    private val DOC_ID = Regex("^\\d{5,12}$")


    const val QUELLE = "https://disclosures-clerk.house.gov/FinancialDisclosure"

    fun indexAdresse(jahr: Int) = "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/${jahr}FD.zip"
    fun meldungAdresse(jahr: Int, docId: String) = "https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/$jahr/$docId.pdf"

    // Eine Transaktionsmeldung im Jahresverzeichnis.
    data class Meldung(
        val docId: String,
        val nachname: String,
        val vorname: String,
        val titel: String,
        // Bundesstaat und Wahlkreis, etwa „CA11“.
        val wahlkreis: String,
        // Tag der Einreichung (Mitternacht UTC).
        val datum: Long,
        val jahr: Int,
    ) {
        // Elektronisch eingereicht (mit Text); Papiermeldungen haben andere Nummernkreise.
        val elektronisch: Boolean get() = docId.startsWith("2") && docId.length >= 8
        val name: String get() = listOf(vorname.substringBefore(' ').ifBlank { vorname }, nachname).joinToString(" ").trim()
        // Schluessel einer Person: Nachname, Vorname und Wahlkreis (gleich lautende Namen kommen vor).
        val person: String get() = "$nachname|$vorname|${wahlkreis.take(2)}"
        val staat: String get() = wahlkreis.take(2)
    }

    // Das XML aus dem Jahres-ZIP; null, wenn es fehlt.
    fun xmlAusZip(zip: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                // Hoechstens 64 MB entpacken: ein praepariertes ZIP soll nicht den Speicher fuellen
                if (e.name.endsWith(".xml", true)) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(65_536)
                    while (true) {
                        val n = z.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > 64_000_000) return@use null
                    }
                    return@use out.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
                }
            }
            null
        }
    }.getOrNull()

    private fun feld(b: String, n: String) = Regex("<$n>(.*?)</$n>", RegexOption.DOT_MATCHES_ALL).find(b)?.groupValues?.get(1)?.trim().orEmpty()

    private val US_DATUM = DateTimeFormatter.ofPattern("M/d/yyyy")

    private fun tag(s: String, f: DateTimeFormatter): Long =
        runCatching { LocalDate.parse(s.trim(), f).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

    // Transaktionsmeldungen (Typ P) aus dem Jahresverzeichnis, neueste zuerst.
    fun verzeichnis(xml: String): List<Meldung> =
        Regex("<Member>(.*?)</Member>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val b = m.groupValues[1]
            if (feld(b, "FilingType") != "P") return@mapNotNull null
            // Nur Ziffern: die Kennung wird Teil eines Dateinamens und einer Adresse
            val doc = feld(b, "DocID").trim().takeIf { DOC_ID.matches(it) } ?: return@mapNotNull null
            Meldung(
                docId = doc, nachname = xmlText(feld(b, "Last")), vorname = xmlText(feld(b, "First")),
                titel = xmlText(feld(b, "Prefix")), wahlkreis = feld(b, "StateDst"),
                datum = tag(feld(b, "FilingDate"), US_DATUM), jahr = feld(b, "Year").toIntOrNull() ?: 0,
            )
        }.sortedByDescending { it.datum }.toList()

    private fun xmlText(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    // Geschaefte aus der Meldung

    enum class Art { KAUF, VERKAUF, TEILVERKAUF, TAUSCH }

    // Ein gemeldetes Geschaeft. Betraege in US-Dollar als Spanne; bis fehlt, wenn die Spanne nach oben offen ist.
    data class Geschaeft(
        // SP = Ehepartner, JT = gemeinsam, DC = abhaengiges Kind, leer = selbst.
        val inhaber: String,
        val wert: String,
        val kuerzel: String?,
        // Art des Vermoegenswerts laut Meldung, etwa ST Aktie, OP Option, EF Fonds oder ETF, GS Staatsanleihe.
        val typ: String,
        val art: Art,
        val datum: Long,
        val gemeldet: Long,
        val von: Long,
        val bis: Long?,
        val beschreibung: String = "",
    ) {
        // Mitte der Spanne als Schaetzwert, bei offener Spanne die Untergrenze.
        val mitte: Double get() = if (bis == null) von.toDouble() else (von + bis) / 2.0
        val kauf: Boolean get() = art == Art.KAUF
        val verkauf: Boolean get() = art == Art.VERKAUF || art == Art.TEILVERKAUF
    }

    private val TYP_RX = Regex("\\[([A-Z0-9]{2})]\\s*$")
    private val ART_ZEILE = Regex("^(P|S \\(partial\\)|S|E)$")
    private val DATUM_RX = Regex("\\d{2}/\\d{2}/\\d{4}")
    private val NUR_DATEN = Regex("\\d{2}/\\d{2}/\\d{4}(?:\\s+\\d{2}/\\d{2}/\\d{4})*")
    private val BUCHSTABE = Regex("[A-Za-z]")
    private val BETRAG_TEIL = Regex("^(?:Spouse/DC\\s+)?(?:Over\\s+)?\\$[\\d,]+(?:\\.\\d+)?\\s*(?:-\\s*(?:\\$[\\d,]+)?)?\\+?$|^-$")
    private val BETRAG_RX = Regex("(?:Spouse/DC\\s+)?(Over\\s+\\$[\\d,]+|\\$[\\d,]+\\s*-\\s*\\$[\\d,]+|\\$[\\d,]+(?:\\.\\d+)?\\s*\\+?)")
    private val TICKER_RX = Regex("\\(([A-Z][A-Z0-9.\\-/]{0,9})\\)")
    private val INHABER = setOf("SP", "JT", "DC")
    // Die Beschriftung steht in Kapitaelchen-Zeichen („Dʇʕʅʔʋʒʖʋʑʐ:“), daher elf beliebige Zeichen.
    private val BESCHREIBUNG = Regex("^(?:Description|D|\\S{11}):\\s*(.+)$")

    // Beschreibung in den Zeilen nach ab bis zum naechsten Geschaeft, mit dem Index ihrer letzten Zeile. Der eigene
    // PDF-Leser liefert sie als Blocksatz-Schnipsel ohne Leerzeichen; sie werden ohne Trennzeichen verbunden und enden
    // vor der ersten Zeile mit Leerzeichen (Name des naechsten Werts).
    private fun beschreibung(zeilen: List<String>, ab: Int): Pair<String, Int>? {
        for (n in (ab + 1) until minOf(zeilen.size, ab + 12)) {
            val z = zeilen[n]
            if (z in INHABER || TYP_RX.containsMatchIn(z) || z.startsWith("Filing ID")) return null
            if (z == "D" && zeilen.getOrNull(n + 1) == ":") {
                val sb = StringBuilder()
                var e = n + 1
                while (e + 1 < zeilen.size && sb.length < 400) {
                    val s = zeilen[e + 1]
                    if (s.contains(' ') || s in INHABER || TYP_RX.containsMatchIn(s) || s.startsWith("Filing ID")) break
                    sb.append(s)
                    e++
                }
                return sb.toString() to e
            }
            val m = BESCHREIBUNG.matchEntire(z) ?: continue
            val text = m.groupValues[1].trim()
            val weiter = zeilen.getOrNull(n + 1)
            if (!text.endsWith(".") && weiter != null && weiter.length < 80 && weiter !in INHABER && !TYP_RX.containsMatchIn(weiter) && !weiter.contains(':')) {
                return "$text ${weiter.trim()}" to n + 1
            }
            return text to n
        }
        return null
    }

    private val MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    // Ganze Zeile einer Tabellenzelle (keine Blocksatz-Schnipsel, keine Ueberschriften). Ein Datum darf im Namen stehen
    // (bei Anleihen etwa „5.000% MAT 09/01/2028“); nur Zeilen aus reinen Daten gehoeren zur Spalte daneben.
    private fun zellzeile(z: String): Boolean {
        if (z in INHABER) return false
        if (Regex("^\\(.{1,12}\\)$").matches(z)) return true
        return z.length >= 6 && !z.endsWith(":") && !z.startsWith("Filing ID") && !z.contains("$200?") && !ART_ZEILE.matches(z) &&
            !NUR_DATEN.matches(z) && !BETRAG_TEIL.matches(z)
    }

    // Kurze letzte Zeile eines Namens („Bonds“, „Inc.“): zwei bis fuenf Zeichen mit Buchstaben, direkt nach einer
    // ganzen Zellzeile vorher. Blocksatz-Schnipsel stehen nach anderen Schnipseln und zaehlen deshalb nicht.
    private fun kurzeFortsetzung(z: String, vorher: String?): Boolean =
        z.length in 2..5 && BUCHSTABE.containsMatchIn(z) && z !in INHABER && !ART_ZEILE.matches(z) && !z.endsWith(":") &&
            !BETRAG_TEIL.matches(z) && vorher != null && zellzeile(vorher)

    // Geschaefte aus den Textzeilen einer Meldung (PdfText.zeilen). Je Geschaeft stehen Inhaber, Name des Werts,
    // die Art des Werts in eckigen Klammern, dann Art des Geschaefts, zwei Daten und die Betragsspanne. Teilt ein
    // Seitenumbruch die Zeile, stehen Art, Daten und Betragsanfang vor dem Kopf der neuen Seite und die Art des Werts
    // erst danach; dann wird von dort aus rueckwaerts gesucht.
    fun geschaefte(zeilen: List<String>): List<Geschaeft> {
        val aus = ArrayList<Geschaeft>()
        var verbraucht = -1   // Zeilen bis hier gehoeren schon zu einem Geschaeft
        for (i in zeilen.indices) {
            val typ = TYP_RX.find(zeilen[i])?.groupValues?.get(1) ?: continue
            // Zeile mit der Art des Geschaefts: direkt danach, sonst (Seitenumbruch) davor
            val vorwaerts = i + 1 < zeilen.size && ART_ZEILE.matches(zeilen[i + 1])
            val k = if (vorwaerts) i + 1 else ((i - 1) downTo maxOf(verbraucht + 1, i - 90)).firstOrNull { j ->
                ART_ZEILE.matches(zeilen[j]) && j + 1 < zeilen.size && DATUM_RX.containsMatchIn(zeilen[j + 1])
            } ?: continue
            // Zwei Daten in den folgenden Zeilen
            val daten = ArrayList<String>()
            var d = k + 1
            while (d < minOf(zeilen.size, k + 5) && daten.size < 2) {
                DATUM_RX.findAll(zeilen[d]).forEach { if (daten.size < 2) daten += it.value }
                d++
            }
            if (daten.size < 2) continue
            // Betrag aus seinen Teilzeilen, auch ueber Kopfzeilen einer neuen Seite hinweg
            val betragText = StringBuilder()
            var b = d
            while (b < minOf(zeilen.size, d + 90)) {
                val z = zeilen[b]
                if (b != i && BETRAG_TEIL.matches(z)) {
                    if (betragText.isNotEmpty()) betragText.append(' ')
                    betragText.append(z)
                    val t = betragText.toString()
                    if (t.contains("Over") || Regex("\\$[\\d,]+\\s*-\\s*\\$[\\d,]+").containsMatchIn(t) || !t.trimEnd().endsWith("-")) break
                } else if (betragText.isEmpty() && b > d + 2 && vorwaerts) break
                b++
            }
            val betrag = BETRAG_RX.find(betragText)?.groupValues?.get(1) ?: continue
            // Name des Werts: ganze Zeilen vor der Zeile mit der Art oder vor der Art des Geschaefts, dazu der Rest der Typzeile
            // bis zum Inhaber oder zu Schnipseln
            val teile = ArrayList<String>()
            var j = (if (vorwaerts) i else k) - 1
            var inhaber = ""
            while (j > verbraucht && teile.size < 6) {
                val z = zeilen[j]
                if (z in INHABER) { inhaber = z; break }
                if (!zellzeile(z) && !kurzeFortsetzung(z, zeilen.getOrNull(j - 1)?.takeIf { j - 1 > verbraucht })) break
                teile.add(0, z)
                j--
            }
            // Nach einem Seitenumbruch: ganze Zeilen zwischen dem Kopf der neuen Seite und der Typzeile („Stock (TEM)“)
            if (!vorwaerts) {
                val nach = ArrayList<String>()
                var n = i - 1
                while (n > k && (zellzeile(zeilen[n]) || kurzeFortsetzung(zeilen[n], zeilen[n - 1].takeIf { n - 1 > k })) && nach.size < 4) {
                    nach.add(0, zeilen[n]); n--
                }
                teile += nach
            }
            zeilen[i].replace(TYP_RX, "").trim().takeIf { it.isNotEmpty() }?.let { teile += it }
            verbraucht = maxOf(i, b)
            val besch = beschreibung(zeilen, verbraucht)
            if (besch != null) verbraucht = besch.second
            val name = teile.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            val kuerzel = TICKER_RX.findAll(name).lastOrNull()?.groupValues?.get(1)
            val zahlen = Regex("[\\d,]+(?:\\.\\d+)?").findAll(betrag).map { it.value.replace(",", "").toDoubleOrNull()?.toLong() ?: 0L }.toList()
            aus += Geschaeft(
                inhaber = inhaber,
                wert = name.replace(TICKER_RX, "").replace(Regex("\\s+"), " ").trim().trimEnd('-', ' ').ifBlank { kuerzel ?: name },
                kuerzel = kuerzel,
                typ = typ,
                art = when (zeilen[k]) { "P" -> Art.KAUF; "S" -> Art.VERKAUF; "E" -> Art.TAUSCH; else -> Art.TEILVERKAUF },
                datum = tag(daten[0], MDY),
                gemeldet = tag(daten[1], MDY),
                von = zahlen.firstOrNull() ?: 0L,
                bis = if (betrag.startsWith("Over") || betrag.contains("Spouse") || zahlen.size < 2) null else zahlen[1],
                beschreibung = besch?.first.orEmpty(),
            )
        }
        return aus
    }

    // Auswertung

    // Eine Person mit ihren Meldungen.
    data class Person(val schluessel: String, val name: String, val titel: String, val wahlkreis: String, val meldungen: List<Meldung>) {
        val letzte: Long get() = meldungen.maxOfOrNull { it.datum } ?: 0L
    }

    // Meldungen nach Personen, aktivste zuerst (Zahl der Meldungen, dann die juengste).
    fun personen(meldungen: List<Meldung>): List<Person> =
        meldungen.groupBy { it.person }.map { (k, l) ->
            val m = l.maxBy { it.datum }
            Person(k, m.name, m.titel, m.wahlkreis, l.sortedByDescending { it.datum })
        }.sortedWith(compareByDescending<Person> { it.meldungen.size }.thenByDescending { it.letzte })

    // Ein Wert mit Kaeufen und Verkaeufen einer Person (Schaetzwerte aus den Spannen).
    data class Posten(val kuerzel: String, val name: String, val kaeufe: Double, val verkaeufe: Double, val anzahl: Int, val letztes: Long, val optionen: Boolean) {
        val netto: Double get() = kaeufe - verkaeufe
    }

    // Geschaefte je Wert zusammengefasst: Aktien und Optionen auf denselben Wert zaehlen zusammen (die Option wird
    // dem Basiswert zugerechnet), Werte ohne Kuerzel (Anleihen, Beteiligungen) fallen weg. Groesste Kaeufe zuerst.
    fun posten(geschaefte: List<Geschaeft>): List<Posten> =
        geschaefte.filter { it.kuerzel != null && it.typ in setOf("ST", "OP", "EF", "OT") && it.art != Art.TAUSCH }
            .groupBy { it.kuerzel!! }
            .map { (k, l) ->
                Posten(
                    kuerzel = k,
                    name = l.first().wert,
                    kaeufe = l.filter { it.kauf }.sumOf { it.mitte },
                    verkaeufe = l.filter { it.verkauf }.sumOf { it.mitte },
                    anzahl = l.size,
                    letztes = l.maxOf { it.datum },
                    optionen = l.any { it.typ == "OP" },
                )
            }
            .sortedWith(compareByDescending<Posten> { it.kaeufe }.thenByDescending { it.verkaeufe })

    // Gewichte zum Nachbilden: Werte mit mehr Kaeufen als Verkaeufen, nach dem geschaetzten Nettokauf gewichtet,
    // hoechstens anzahl Werte. Summe 1.
    fun gewichte(posten: List<Posten>, anzahl: Int): List<Pair<String, Double>> {
        val netto = posten.filter { it.netto > 0 }.sortedByDescending { it.netto }.take(anzahl)
        val summe = netto.sumOf { it.netto }
        if (summe <= 0) return emptyList()
        return netto.map { it.kuerzel to it.netto / summe }
    }
}
