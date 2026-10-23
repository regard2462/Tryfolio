package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

// Meldungen deutscher Emittenten aus den Datenbanken der BaFin (seit 5.5), ohne Anmeldung abrufbar:
// - Eigengeschaefte von Fuehrungskraeften nach Art. 19 MAR (Directors' Dealings): Vorstaende, Aufsichtsraete und
//   Personen in enger Beziehung zu ihnen melden Kaeufe und Verkaeufe von Aktien ihres Unternehmens. Die Datenbank
//   zeigt die Meldungen der letzten zwoelf Monate.
// - Bedeutende Stimmrechtsanteile nach §§ 33, 38, 39 WpHG: wer ab 3 % der Stimmrechte einer Gesellschaft haelt.
// Der App liegt ein Stand bei (tools/bafin/holen.py, assets/copytrading/bafin.json); neue Eigengeschaefte des
// letzten Monats holt die App selbst nach. Die Nutzungsbedingungen der BaFin verlangen die Quellenangabe QUELLE.
object Bafin {
    const val QUELLE = "© Bundesanstalt für Finanzdienstleistungsaufsicht / www.bafin.de"
    const val SEITE = "https://portal.mvp.bafin.de/database/DealingsInfo/"
    private const val BASIS = "https://portal.mvp.bafin.de/database/"

    // Eine Meldung. Felder kurz wie in der mitgelieferten Datei.
    @Serializable
    data class Geschaeft(
        // Kennung der Meldung bei der BaFin und Kennung im Suchergebnis (die BaFin vergibt sie je Meldung).
        val id: String,
        val pid: String = "",
        val isin: String = "",
        // Emittent, meldepflichtige Person, Position/Status, Art des Instruments, Art des Geschaefts.
        val e: String,
        val p: String,
        val r: String = "",
        val i: String = "",
        val art: String = "",
        // Datum des Geschaefts und der Veroeffentlichung, JJJJ-MM-TT.
        val d: String,
        val o: String = "",
        val v: String = "",
        // Boersenkuerzel (Yahoo), wenn bekannt.
        val k: String? = null,
        // Durchschnittspreis und Volumen laut aggregierten Angaben, Waehrung.
        val preis: Double? = null,
        val vol: Double? = null,
        val w: String = "",
    ) {
        val kauf: Boolean get() = art.equals("Kauf", true)
        val verkauf: Boolean get() = art.equals("Verkauf", true)
    }

    // Stimmrechtsanteil: Emittent mit Sitz, Meldepflichtiger mit Sitz und Land, Anteile in Prozent (Stimmrechte nach
    // §§ 33, 34, Instrumente nach §§ 38, 39, Summe) und Tag der Veroeffentlichung.
    @Serializable
    data class Anteil(
        val e: String,
        val es: String = "",
        val m: String,
        val ms: String = "",
        val ml: String = "",
        val s: Double? = null,
        val inst: Double? = null,
        val summe: Double? = null,
        val d: String = "",
    ) {
        // Gesamter Anteil: die Summe, sonst die Stimmrechte.
        val gesamt: Double get() = summe?.takeIf { it > 0 } ?: s ?: 0.0
    }

    @Serializable
    data class Datei(
        val stand: String = "",
        val quelle: String = QUELLE,
        val eigengeschaefte: List<Geschaeft> = emptyList(),
        val stimmrechte: List<Anteil> = emptyList(),
        // Zeitpunkt des letzten eigenen Abrufs (nur in der Datei der App).
        val abgerufen: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // ------------------------------------------------------------ Seiten der Datenbank

    // Suchergebnis des letzten Monats, Seite seite (20 Meldungen je Seite).
    fun suchAdresse(seite: Int): String = BASIS + "DealingsInfo/sucheForm.do?meldepflichtigerName=&zeitraum=2&emittentName=&zeitraumVon=" +
        "&emittentIsin=&zeitraumBis=&d-5010980-p=$seite"

    fun personAdresse(id: String): String = BASIS + "DealingsInfo/ergebnisListe.do?cmd=loadEmittentenAction&meldepflichtigerId=" +
        URLEncoder.encode(id, "UTF-8")

    fun detailAdresse(emittent: String, meldung: String): String = BASIS + "DealingsInfo/transaktionListe.do?cmd=loadTransaktionenAction" +
        "&emittentBafinId=" + URLEncoder.encode(emittent, "UTF-8") + "&meldungId=" + URLEncoder.encode(meldung, "UTF-8")

    private val ZAHL = Regex("^\\d{1,12}$")

    // Kennungen auf einer Suchseite (je Meldung eine, verweisen auf die Seite der meldepflichtigen Person).
    fun personen(html: String): List<String> =
        Regex("meldepflichtigerId=(\\d+)").findAll(html).map { it.groupValues[1] }.filter { ZAHL.matches(it) }.distinct().toList()

    // Hoechste Seitenzahl des Suchergebnisses.
    fun letzteSeite(html: String): Int =
        Regex("d-5010980-p=(\\d+)").findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()?.coerceAtMost(50) ?: 1

    private fun text(s: String): String =
        s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    private fun zellen(zeile: String): List<String> =
        Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL).findAll(zeile).map { text(it.groupValues[1]) }.toList()

    // TT.MM.JJJJ (auch mit Uhrzeit) als JJJJ-MM-TT; leer, wenn kein Datum.
    fun datum(s: String): String = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})").find(s)?.let { "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }.orEmpty()

    // „174.549,99 EUR“ als Zahl und Waehrung.
    fun zahl(s: String): Pair<Double, String>? {
        val m = Regex("(-?[\\d.]+(?:,\\d+)?)\\s*([A-Z]{3})?").find(s.trim()) ?: return null
        val x = m.groupValues[1].replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null
        return x to m.groupValues[2]
    }

    private val MELDUNG = Regex("transaktionListe\\.do\\?cmd=loadTransaktionenAction&amp;emittentBafinId=(\\d+)&amp;meldungId=(\\d+)")

    // Meldungen auf der Seite zur Kennung pid, mit der Kennung des Emittenten bei der BaFin (fuer die Einzelheiten).
    fun meldungen(html: String, pid: String = ""): List<Pair<String, Geschaeft>> =
        Regex("<tr class=\"(?:odd|even)\">.*?</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { m ->
            val zeile = m.value
            val link = MELDUNG.find(zeile) ?: return@mapNotNull null
            val z = zellen(zeile)
            if (z.size < 10) return@mapNotNull null
            link.groupValues[1] to Geschaeft(
                id = link.groupValues[2], pid = pid, isin = z[2], e = z[0], p = z[3], r = z[4], i = z[5], art = z[6],
                d = datum(z[7]), o = z[8], v = datum(z[9]),
            )
        }.toList()

    // Preis und Volumen aus den aggregierten Angaben einer Meldung; mehrere Geschaefte werden addiert.
    fun einzelheiten(html: String, g: Geschaeft): Geschaeft {
        val preise = Regex("<td>Preis:</td>\\s*<td>([^<]*)</td>").findAll(html).mapNotNull { zahl(it.groupValues[1]) }.toList()
        val volumen = Regex("<td>Aggregiertes Volumen:</td>\\s*<td>([^<]*)</td>").findAll(html).mapNotNull { zahl(it.groupValues[1]) }.toList()
        val w = volumen.firstOrNull()?.second?.ifBlank { null } ?: preise.firstOrNull()?.second.orEmpty()
        return g.copy(preis = preise.firstOrNull()?.first, vol = volumen.takeIf { it.isNotEmpty() }?.sumOf { it.first }, w = w)
    }

    // ------------------------------------------------------------ Auswertung

    // Neue und bekannte Meldungen vereinen: je Kennung die neuere Fassung, nur ab abTag, neueste zuerst.
    fun vereinen(alt: List<Geschaeft>, neu: List<Geschaeft>, abTag: String): List<Geschaeft> {
        val m = LinkedHashMap<String, Geschaeft>()
        alt.forEach { m[it.id] = it }
        neu.forEach { g ->
            m[g.id] = m[g.id]?.let { a -> g.copy(pid = g.pid.ifBlank { a.pid }, k = g.k ?: a.k, preis = g.preis ?: a.preis, vol = g.vol ?: a.vol, w = g.w.ifBlank { a.w }) } ?: g
        }
        return m.values.filter { it.d >= abTag }.sortedWith(compareByDescending<Geschaeft> { it.d }.thenByDescending { it.v }.thenByDescending { it.id })
    }

    // Kaeufe und Verkaeufe der Fuehrung einer Firma.
    data class Firma(
        val isin: String, val name: String, val kuerzel: String?, val kaeufe: Double, val verkaeufe: Double,
        val anzahlKaeufe: Int, val anzahlVerkaeufe: Int, val letztes: String, val waehrung: String,
    ) {
        val netto: Double get() = kaeufe - verkaeufe
    }

    // Je Firma (ISIN) ab abTag, nur Aktien mit bekanntem Volumen in einer Waehrung; staerkste Nettokaeufe zuerst.
    fun firmen(liste: List<Geschaeft>, abTag: String): List<Firma> =
        liste.filter { it.d >= abTag && it.isin.isNotBlank() && it.vol != null && (it.kauf || it.verkauf) && it.i.equals("Aktie", true) }
            .groupBy { it.isin }
            .map { (isin, g) ->
                val w = g.groupBy { it.w }.maxByOrNull { it.value.size }?.key.orEmpty()
                val gleich = g.filter { it.w == w }
                Firma(
                    isin, g.first().e, g.firstNotNullOfOrNull { it.k },
                    gleich.filter { it.kauf }.sumOf { it.vol ?: 0.0 }, gleich.filter { it.verkauf }.sumOf { it.vol ?: 0.0 },
                    g.count { it.kauf }, g.count { it.verkauf }, g.maxOf { it.d }, w,
                )
            }
            .sortedByDescending { it.netto }

    // Insider-Trend aus den Aktiengeschaeften ab abTag (Kauf und Verkauf, Gewicht nach Position/Status).
    fun trend(liste: List<Geschaeft>, abTag: String): de.troetenrepublik.app.engine.InsiderTrend.Ergebnis? =
        de.troetenrepublik.app.engine.InsiderTrend.auswerten(
            liste.filter { (it.kauf || it.verkauf) && it.i.equals("Aktie", true) && (it.vol ?: 0.0) > 0 }.map {
                de.troetenrepublik.app.engine.InsiderTrend.Geschaeft(
                    it.p, de.troetenrepublik.app.engine.InsiderTrend.rolleBafin(it.r), it.kauf, it.vol ?: 0.0, it.d,
                )
            },
            abTag,
        )

    // Stimmrechtsanteile an einer Gesellschaft, erkannt am Namen (ohne Rechtsform); groesste zuerst.
    fun anteileAn(name: String, liste: List<Anteil>): List<Anteil> {
        if (name.isBlank()) return emptyList()
        // Gleicher Namensstamm (auch kurze Namen wie SAP) oder abgekuerzt gleich
        val stamm = Gleichwerte.stamm(name)
        val emittenten = liste.map { it.e }.distinct().filter { (stamm.isNotEmpty() && Gleichwerte.stamm(it) == stamm) || Gleichwerte.namenGleich(it, name) }
        if (emittenten.size != 1) return emptyList()
        return liste.filter { it.e == emittenten[0] && it.gesamt > 0 }.sortedByDescending { it.gesamt }
    }
}
