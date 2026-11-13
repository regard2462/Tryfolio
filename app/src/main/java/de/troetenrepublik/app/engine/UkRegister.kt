package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Register der finanziellen Interessen des britischen Unterhauses (0.78): gemeldete Beteiligungen an Unternehmen
// (Kategorie „Shareholdings“) aus der Schnittstelle des Parlaments, Open Parliament Licence v3.0. Das Register nennt
// Bestaende ueber einer Meldeschwelle, keine einzelnen Geschaefte. Hier stehen Adressen und Leser, der Abruf in
// market/UkRegisterAbruf.kt.
object UkRegister {

    private const val API = "https://interests-api.parliament.uk/api/v1/Interests"
    const val KATEGORIE = 8
    const val SEITEN_GROESSE = 20
    const val LIZENZ = "https://www.parliament.uk/site-information/copyright-parliament/open-parliament-licence/"
    // Wortlaut, den die Lizenz fuer die Nennung vorgibt
    const val NENNUNG = "Contains Parliamentary information licensed under the Open Parliament Licence v3.0"

    fun adresse(skip: Int) = "$API?CategoryId=$KATEGORIE&Take=$SEITEN_GROESSE&Skip=$skip"

    // Seite des Parlaments mit allen gemeldeten Interessen eines Mitglieds.
    fun personSeite(personId: Long) = "https://members.parliament.uk/member/$personId/registeredinterests"

    // Eine gemeldete Beteiligung. Schwelle: Anteil am Kapital in % oder Wert in Pfund, wie der Eintrag sie nennt.
    // Daten als JJJJ-MM-TT oder leer; ende gesetzt: die Beteiligung besteht nicht mehr.
    data class Beteiligung(
        val id: Long, val personId: Long, val person: String, val partei: String, val firma: String,
        val schwelleProzent: Int?, val schwellePfund: Long?, val gemeldet: String, val seit: String, val ende: String,
    ) {
        val aktuell: Boolean get() = ende.isEmpty()
    }

    // Eine Seite der Schnittstelle: Zahl aller Eintraege, Zahl der Eintraege dieser Seite und die lesbaren davon.
    data class Seite(val gesamt: Int, val gelesen: Int, val eintraege: List<Beteiligung>)

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun datum(s: String?): String = s?.trim()?.take(10)?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }.orEmpty()

    // Schwelle aus dem Wortlaut des Eintrags: „over 15% of issued share capital“ oder „valued at more than £70,000“.
    fun schwelle(text: String): Pair<Int?, Long?> {
        val prozent = Regex("(\\d{1,3})\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val pfund = Regex("£\\s*([\\d,]+)").find(text)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        return prozent to pfund
    }

    fun seiteLesen(json: String): Seite? = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val gesamt = o.text("totalResults")?.toIntOrNull() ?: return null
        val items = o["items"] as? JsonArray ?: return null
        val liste = items.mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            val m = x["member"] as? JsonObject ?: return@mapNotNull null
            val felder = (x["fields"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .mapNotNull { f -> f.text("name")?.let { n -> n to f.text("value").orEmpty().trim() } }.toMap()
            val person = m.text("nameDisplayAs")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // Firma aus dem Feld; fehlt es, aus der Zusammenfassung („Shares in …“)
            val firma = (felder["OrganisationName"]?.takeIf { it.isNotEmpty() }
                ?: x.text("summary")?.trim()?.removePrefix("Shares in ")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null).replace(Regex("\\s+"), " ")
            val (prozent, pfund) = schwelle(felder["ShareholdingThreshold"].orEmpty())
            Beteiligung(
                id = x.text("id")?.toLongOrNull() ?: return@mapNotNull null,
                personId = m.text("id")?.toLongOrNull() ?: 0L, person = person, partei = m.text("party")?.trim().orEmpty(), firma = firma,
                schwelleProzent = prozent, schwellePfund = pfund,
                gemeldet = datum(x.text("registrationDate")).ifEmpty { datum(x.text("publishedDate")) },
                seit = datum(felder["RegistrableDate"]), ende = datum(felder["EndDate"]),
            )
        }
        Seite(gesamt, items.size, liste)
    }.getOrNull()

    // Neueste Meldung zuerst.
    fun sortiert(liste: List<Beteiligung>): List<Beteiligung> = liste.sortedWith(compareByDescending<Beteiligung> { it.gemeldet }.thenBy { it.person })

    // Suche nach Person oder Firma: jedes Wort der Eingabe muss in Name oder Firma vorkommen. Leere Eingabe: alles.
    fun suche(liste: List<Beteiligung>, eingabe: String): List<Beteiligung> {
        val worte = eingabe.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (worte.isEmpty()) return liste
        return liste.filter { b -> val t = (b.person + " " + b.firma).lowercase(); worte.all { it in t } }
    }

    // Zahl der Personen mit mindestens einer bestehenden Beteiligung.
    fun personen(liste: List<Beteiligung>): Int = liste.filter { it.aktuell }.map { if (it.personId > 0) it.personId.toString() else it.person }.distinct().size

    // ------------------------------------------------------------ Zuordnung zu einer Aktie

    // Rechtsformen nach Familie: Nur Namen mit Rechtsform derselben Familie gelten als dieselbe Firma („Muster Ltd“ ist
    // eine andere Gesellschaft als „Muster plc“).
    private val FORMEN = mapOf(
        "ltd" to "ltd", "limited" to "ltd", "plc" to "plc", "inc" to "inc", "incorporated" to "inc", "corp" to "inc", "corporation" to "inc",
        "se" to "se", "sa" to "sa", "ag" to "ag", "nv" to "nv", "llc" to "llc", "lp" to "lp", "asa" to "asa", "ab" to "ab", "oyj" to "oyj", "spa" to "spa",
    )

    // Kern des Namens und Familie der Rechtsform; null ohne Rechtsform am Ende oder mit zu kurzem Kern.
    fun firmenSchluessel(name: String): Pair<String, String>? {
        val worte = name.lowercase().replace(".", "").replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\bpublic limited company\\b"), "plc").trim()
            .split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (worte.firstOrNull() == "the") worte.removeAt(0)
        val form = worte.lastOrNull()?.let { FORMEN[it] } ?: return null
        worte.removeAt(worte.size - 1)
        // „Muster Holdings Co Ltd“: ein weiteres Formwort vor der Rechtsform gehoert nicht zum Kern
        while (worte.isNotEmpty() && worte.last() in setOf("co", "company")) worte.removeAt(worte.size - 1)
        val kern = worte.joinToString(" ")
        return if (kern.length >= 3) kern to form else null
    }

    // Bestehende Beteiligungen an genau dieser Firma: gleicher Namenskern und gleiche Familie der Rechtsform. Ohne
    // Rechtsform auf einer der beiden Seiten gibt es keine Zuordnung (dann bleibt die Suche nach dem Namen).
    fun zuFirma(liste: List<Beteiligung>, firmenname: String): List<Beteiligung> {
        val ziel = firmenSchluessel(firmenname) ?: return emptyList()
        return sortiert(liste.filter { it.aktuell && firmenSchluessel(it.firma) == ziel })
    }
}
