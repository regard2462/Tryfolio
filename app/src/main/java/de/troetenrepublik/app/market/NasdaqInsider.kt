package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Luecke
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

// Insidergeschaefte je US-Aktie aus den Meldungen, die die Nasdaq auf ihrer Seite fuehrt: schluesselfreier Rueckfall,
// wenn der mitgelieferte Stand der SEC (SecInsider) veraltet ist und keine Kontaktadresse fuer die SEC eingetragen
// ist. Gezaehlt werden wie bei der SEC nur Kaeufe und Verkaeufe an der Boerse (Buy, Sell und die Plan-Fassungen
// Automatic Buy und Automatic Sell); Zuteilungen, Ausuebungen, Schenkungen und Abgaben ausserhalb der Boerse zaehlen
// nicht. Reine Lese- und Vereinigungslogik, ohne Netz pruefbar.
object NasdaqInsider {
    // Zeilen je Abruf; die Antwort nennt ausserdem die Gesamtzahl.
    const val LIMIT = 100

    // Drei Stunden gespeichert
    const val HALTBAR_MS = 3 * 3_600_000L

    // Tage vor dem Stand der SEC-Daten, ab denen Zeilen der Nasdaq gelten: Der Stand ist der Tag der Meldung, das Geschaeft
    // liegt ein bis zwei Handelstage davor.
    private const val PUFFER_TAGE = 3L

    // Praefix der Kennung (statt einer Accession Number), damit eine spaeter eintreffende SEC-Meldung die Zeile ersetzt.
    const val KENNUNG = "nq-"

    fun adresse(symbol: String) =
        "https://api.nasdaq.com/api/company/" + symbol.trim().lowercase() + "/insider-trades?limit=$LIMIT&type=ALL&sortColumn=lastDate&sortOrder=DESC"

    // Die Nasdaq fuehrt nur reine Buchstaben und Ziffern als Kuerzel dieses Endpunkts (keine Klassen wie BRK-B, keine Boersenendungen).
    fun abrufbar(symbol: String): Boolean = symbol.isNotBlank() && symbol.length <= 6 && symbol.all { it in 'A'..'Z' || it in 'a'..'z' }

    // Eine Zeile der Tabelle (Kauf oder Verkauf an der Boerse).
    data class Zeile(
        val person: String, val beziehung: String, val tag: String, val kauf: Boolean, val automatisch: Boolean, val direkt: Boolean,
        val stueck: Double, val preis: Double, val gehalten: Double?,
    )

    // zeilen: nur Kaeufe und Verkaeufe an der Boerse mit Preis; gelesen: Zeilen der Antwort insgesamt; gesamt: Zahl der
    // Meldungen laut Antwort; aeltester: Tag der aeltesten gelesenen Zeile (JJJJ-MM-TT, leer ohne Zeilen).
    class Antwort(val zeilen: List<Zeile>, val gelesen: Int, val gesamt: Int, val aeltester: String) {
        val gekuerzt: Boolean get() = gesamt > gelesen
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.content?.trim().orEmpty()

    // Zahl wie "1,366,000" oder "$219.72"; null bei Text ohne Ziffern
    internal fun zahl(s: String): Double? = s.replace(",", "").replace("$", "").trim().toDoubleOrNull()

    // Tag "9/21/2026" oder "11/04/2026" als JJJJ-MM-TT
    internal fun tag(s: String): String = NasdaqKalender.datum(s)?.toString().orEmpty()

    // Null bei unlesbarer Antwort oder fehlender Tabelle; eine Tabelle ohne Zeilen ist eine gueltige, leere Antwort.
    fun lesen(text: String): Antwort? = runCatching {
        val wurzel = json.parseToJsonElement(text).obj() ?: return null
        val tabelle = wurzel["data"].obj()?.get("transactionTable").obj() ?: return null
        val zeilen = (tabelle["table"].obj()?.get("rows") as? JsonArray) ?: return null
        val roh = zeilen.mapNotNull { it.obj() }
        val gesamt = zahl(tabelle["totalRecords"].text())?.toInt() ?: roh.size
        val aeltester = roh.map { tag(it["lastDate"].text()) }.filter { it.isNotEmpty() }.minOrNull().orEmpty()
        val gelesen = roh.mapNotNull { z ->
            val art = z["transactionType"].text().lowercase()
            val kauf = art == "buy" || art == "automatic buy"
            if (!kauf && art != "sell" && art != "automatic sell") return@mapNotNull null
            val stueck = zahl(z["sharesTraded"].text())?.takeIf { it > 0 } ?: return@mapNotNull null
            // Ohne Preis in der Meldung (Mischpreise, Fussnote) laesst sich der Betrag nicht angeben: keine Zeile
            val preis = zahl(z["lastPrice"].text())?.takeIf { it > 0 } ?: return@mapNotNull null
            val t = tag(z["lastDate"].text()).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val person = z["insider"].text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Zeile(
                person, z["relation"].text(), t, kauf, art.startsWith("automatic"), !z["ownType"].text().equals("Indirect", true),
                stueck, preis, zahl(z["sharesHeld"].text()),
            )
        }
        Antwort(gelesen, roh.size, gesamt, aeltester)
    }.getOrNull()

    // "STEVENS MARK A" als "Stevens Mark A" (wie die Namen der SEC-Datensaetze); Zahlzeichen der Namensendungen bleiben gross.
    internal fun name(roh: String): String = roh.trim().split(Regex("\\s+")).joinToString(" ") { w ->
        if (w.uppercase() in setOf("II", "III", "IV")) w.uppercase()
        else w.split("-").joinToString("-") { t -> t.split("'").joinToString("'") { u -> u.lowercase().replaceFirstChar { it.uppercase() } } }
    }

    // Schluessel zum Vergleichen von Personen: Kleinschrift ohne Satzzeichen und Mehrfachleerzeichen.
    fun personSchluessel(name: String): String = name.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex("\\s+"), " ")

    // Ab diesem Tag (JJJJ-MM-TT) gelten Zeilen der Nasdaq: ein paar Tage vor dem Stand der SEC-Daten, bei einer Luecke vor deren
    // Beginn. Leer ohne lesbaren Stand (dann zaehlt alles).
    fun abTag(stand: String, luecke: Luecke.Spanne?): String =
        runCatching { LocalDate.parse(luecke?.takeIf { !it.leer }?.von ?: stand).minusDays(PUFFER_TAGE).toString() }.getOrDefault("")

    class Ergebnis(val geschaefte: List<SecInsider.Geschaeft>, val lueckeVon: String, val lueckeBis: String)

    // Zeilen je Person, Tag und Richtung zu einem Geschaeft zusammenfassen (wie SecInsider.form4 je Meldung):
    // Stueck und Betrag ueber alle Zeilen, Bestand danach und Stueck fuer den Anteil nur aus direkt gehaltenen Zeilen. Bei
    // mehreren direkten Zeilen eines Tages gilt der kleinste Bestand nach Verkaeufen, der groesste nach Kaeufen.
    // name/cik: Firma aus den vorhandenen Meldungen. Nur Tage nach ab. Reicht die Antwort nicht bis ab zurueck (mehr
    // Meldungen als Zeilen), bleibt die Spanne von ab bis zur aeltesten Zeile als Luecke.
    fun auswerten(a: Antwort, symbol: String, firma: String, cik: String, ab: String): Ergebnis {
        val k = symbol.trim().uppercase()
        val liste = a.zeilen.filter { it.tag > ab }.groupBy { Triple(personSchluessel(it.person), it.tag, it.kauf) }.map { (_, z) ->
            val erste = z.first()
            val n = z.sumOf { it.stueck }
            val wert = z.sumOf { it.stueck * it.preis }
            val direkt = z.filter { it.direkt }
            val bestaende = direkt.mapNotNull { it.gehalten }
            val beziehung = z.map { it.beziehung }.firstOrNull { it.isNotBlank() }.orEmpty()
            val rolle = SecInsider.rolle(beziehung.contains("director", true), beziehung.contains("officer", true), beziehung)
            SecInsider.Geschaeft(
                a = KENNUNG + k + "-" + erste.tag + "-" + personSchluessel(erste.person).replace(' ', '_'),
                k = k, e = firma.ifBlank { k }, c = cik, p = name(erste.person), r = rolle, t = "", kauf = erste.kauf, d = erste.tag,
                n = n, preis = if (n > 0) wert / n else 0.0, wert = wert,
                nach = if (erste.kauf) bestaende.maxOrNull() else bestaende.minOrNull(),
                plan = !erste.kauf && z.any { it.automatisch }, nd = direkt.sumOf { it.stueck },
            )
        }.sortedByDescending { it.d }
        val luecke = a.gekuerzt && a.aeltester.isNotEmpty() && a.aeltester > ab && ab.isNotEmpty()
        return Ergebnis(liste, if (luecke) ab else "", if (luecke) a.aeltester else "")
    }
}
