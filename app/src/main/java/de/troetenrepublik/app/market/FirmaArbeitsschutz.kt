package de.troetenrepublik.app.market

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Arbeitsschutz-Verstoesse US-amerikanischer Firmen aus den OSHA-Daten des Open Data Portal des US-Arbeitsministeriums
// (API v4, nur mit eigenem Schluessel, siehe Zugaenge). Zwei Datensaetze des Katalogs (apiprod.dol.gov/v4/datasets):
//  - OSHA/inspection (ID 10334): activity_nr, estab_name, site_city, site_state, open_date; hier steht der Betriebsname,
//  - OSHA/violation (ID 10338): activity_nr, viol_type, standard, initial_penalty, current_penalty, issuance_date,
//    delete_flag; Strafen stehen nur hier und tragen keinen Namen, sie haengen ueber activity_nr an der Inspektion.
// Ablauf: 1. Inspektionen der letzten fuenf Jahre, deren Betriebsname mit dem Firmennamen beginnt (Muster mit like),
// 2. Namensabgleich im Geraet (Verfahren.betriebPasst), 3. die Verstoesse dieser Inspektionen in Paketen ueber den in-Filter,
// 4. Summe der aktuellen Strafen und die groessten Faelle. Antwortaufbau laut Handbuch: {"data":[{...}]}.
object Arbeitsschutz {

    const val BASIS = "https://apiprod.dol.gov/v4/get/OSHA"

    // Katalog-IDs (apiprod.dol.gov/v4/datasets/{id}) zur Nachvollziehbarkeit
    const val ID_INSPEKTION = 10334
    const val ID_VERSTOSS = 10338

    // Strafen der letzten fuenf Jahre, gerechnet ab dem Beginn der Inspektion
    const val JAHRE = 5

    // Hoechste Zahl Datensaetze je Abruf laut Handbuch
    const val LIMIT = 10_000

    // Inspektionen je Paket des in-Filters und Zahl der Pakete (neueste Inspektionen zuerst). Bleibt ein Rest, gilt die
    // Summe als Mindestwert.
    const val PAKET = 400
    const val PAKETE = 6

    // So viele der groessten Faelle stehen in der Liste
    const val FAELLE = 8

    // Seite der Bedingungen des Portals und Einstieg in die OSHA-Daten (Anzeige als Quelle)
    const val BEDINGUNGEN = "https://www.dataportal.dol.gov/terms-of-use"
    const val QUELLE = "https://enforcedata.dol.gov"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun u(s: String) = URLEncoder.encode(s, "UTF-8")

    // Suchmuster fuer den Betriebsnamen: Woerter des Firmennamens ohne Rechtsform in Grossbuchstaben, dazwischen und am
    // Ende %, damit Schreibweisen wie „WAL-MART“ und „WALMART“ beide treffen. Null, wenn der Name zu allgemein ist (er muss
    // sich selbst eindeutig zuordnen lassen, Verfahren.betriebPasst). Nur A bis Z und Ziffern bleiben, kein Zeichen kann den
    // Filter aufbrechen.
    fun muster(firma: String): String? {
        val k = Verfahren.kurzname(firma)
        if (!Verfahren.betriebPasst(firma, k)) return null
        val alle = k.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        val w = if (alle.size > 1 && alle[0] == "THE") alle.drop(1) else alle
        if (w.isEmpty()) return null
        return w.joinToString("%") + "%"
    }

    fun inspektionAdresse(muster: String, ab: LocalDate, schluessel: String): String {
        val filter = "{\"and\":[{\"field\":\"estab_name\",\"operator\":\"like\",\"value\":\"$muster\"}," +
            "{\"field\":\"open_date\",\"operator\":\"gt\",\"value\":\"$ab\"}]}"
        return "$BASIS/inspection/json?limit=$LIMIT&sort=desc&sort_by=open_date" +
            "&fields=" + u("activity_nr,estab_name,site_city,site_state,open_date") +
            "&filter_object=" + u(filter) + "&X-API-KEY=" + u(schluessel.trim())
    }

    fun verstossAdresse(nummern: List<String>, schluessel: String): String {
        val filter = "{\"field\":\"activity_nr\",\"operator\":\"in\",\"value\":[" + nummern.joinToString(",") { "\"" + it.filter(Char::isDigit) + "\"" } + "]}"
        return "$BASIS/violation/json?limit=$LIMIT" +
            "&fields=" + u("activity_nr,viol_type,standard,current_penalty,delete_flag,issuance_date") +
            "&filter_object=" + u(filter) + "&X-API-KEY=" + u(schluessel.trim())
    }

    class Inspektion(val nummer: String, val betrieb: String, val ort: String, val staat: String, val datum: String)

    class Verstoss(val nummer: String, val art: String, val norm: String, val strafe: Double)

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun daten(text: String): List<JsonObject> = runCatching {
        ((json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }.getOrDefault(emptyList())

    // Nummern kommen als Zahl oder Text, auch als „306953084.0“
    private fun nummer(s: String?): String? = s?.substringBefore('.')?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }

    fun inspektionenLesen(text: String): List<Inspektion> = daten(text).mapNotNull { o ->
        val n = nummer(o.text("activity_nr")) ?: return@mapNotNull null
        val name = o.text("estab_name") ?: return@mapNotNull null
        val datum = o.text("open_date")?.take(10)?.takeIf { it.length == 10 } ?: return@mapNotNull null
        Inspektion(n, name, o.text("site_city").orEmpty(), o.text("site_state").orEmpty(), datum)
    }

    // Inspektionen der Firma seit dem Stichtag, neueste zuerst, ohne Doppelte
    fun passende(firma: String, liste: List<Inspektion>, ab: LocalDate): List<Inspektion> =
        liste.filter { it.datum > ab.toString() && Verfahren.betriebPasst(firma, it.betrieb) }.distinctBy { it.nummer }
            .sortedByDescending { it.datum }

    // Strafe als Dollarbetrag aus Zahl oder Text („450.0“, „1,250.00“)
    private fun dollar(s: String?): Double = s?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    // Verstoesse mit Strafe; geloeschte Verstoesse (delete_flag gesetzt) zaehlen nicht
    fun verstoesseLesen(text: String): List<Verstoss> = daten(text).mapNotNull { o ->
        if (o.text("delete_flag") != null) return@mapNotNull null
        val n = nummer(o.text("activity_nr")) ?: return@mapNotNull null
        val strafe = dollar(o.text("current_penalty"))
        if (strafe <= 0) return@mapNotNull null
        Verstoss(n, o.text("viol_type").orEmpty().uppercase(), o.text("standard").orEmpty(), strafe)
    }

    // „19101030 G02   IIA“ wird „29 CFR 1910.1030“; Nummern der allgemeinen Pflicht und andere Formen bleiben leer
    fun norm(standard: String): String {
        val z = standard.trim().substringBefore(' ')
        if (z.length != 8 || !z.all(Char::isDigit)) return ""
        return "29 CFR " + z.take(4) + "." + z.drop(4).trimStart('0').ifEmpty { "0" }
    }

    // Gewicht der Art: vorsaetzlich (W), wiederholt (R), schwerwiegend (S), nicht schwerwiegend (O), sonst unbekannt
    private fun gewicht(art: String): Int = when (art) { "W" -> 4; "R" -> 3; "S" -> 2; "O" -> 1; else -> 0 }

    class Fall(
        val nummer: String, val betrieb: String, val ort: String, val staat: String, val datum: String,
        val strafe: Double, val verstoesse: Int, val art: String, val norm: String,
    ) {
        // Seite der Inspektion bei OSHA
        val link: String get() = "https://www.osha.gov/ords/imis/establishment.inspection_detail?id=$nummer"
    }

    // summe: aktuelle Strafen aller zugeordneten Inspektionen; inspektionen: Zahl der Inspektionen mit Strafe; faelle: die
    // groessten; mindestens: nicht alle Inspektionen konnten abgefragt werden
    class Ergebnis(val summe: Double, val inspektionen: Int, val faelle: List<Fall>, val mindestens: Boolean)

    fun zusammenfassen(inspektionen: List<Inspektion>, verstoesse: List<Verstoss>, mindestens: Boolean): Ergebnis? {
        val je = verstoesse.groupBy { it.nummer }
        val faelle = inspektionen.mapNotNull { i ->
            val v = je[i.nummer] ?: return@mapNotNull null
            // Art und Norm vom gewichtigsten Verstoss, bei Gleichstand vom hoechsten Betrag
            val haupt = v.maxWith(compareBy<Verstoss>({ gewicht(it.art) }, { it.strafe }))
            Fall(i.nummer, i.betrieb, i.ort, i.staat, i.datum, v.sumOf { it.strafe }, v.size, haupt.art, norm(haupt.norm))
        }
        if (faelle.isEmpty()) return null
        return Ergebnis(faelle.sumOf { it.strafe }, faelle.size, faelle.sortedByDescending { it.strafe }.take(FAELLE), mindestens)
    }

    // Kuerzel, die gross bleiben, dazu Woerter ohne Vokal („LLC“, „CVS“) und mit Ziffern
    private val KUERZEL = setOf("USA", "US", "UK", "IBM", "UPS", "AT&T")

    // Gross geschriebene Betriebsnamen der Behoerde lesbar machen („WAL-MART STORES INC“ wird „Wal-Mart Stores Inc“)
    fun lesbar(name: String): String = name.trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w ->
        if (w in KUERZEL || w.any { it.isDigit() } || w.none { it in "AEIOUaeiou" }) w
        else w.lowercase().replaceFirstChar { it.uppercase() }.replace(Regex("(?<=[-/])[a-z]")) { it.value.uppercase() }
    }
}

// Abruf mit Schluessel und Kontingent (Gruppe dol); Ergebnis zwoelf Stunden im Speicher. Null ohne Schluessel, bei
// zu allgemeinem Namen, bei Fehlern des ersten Abrufs und ohne Strafen.
object ArbeitsschutzAbruf {
    private val api by lazy { MarketApi() }
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Arbeitsschutz.Ergebnis?>>()
    private const val GUELTIG_MS = 12 * 3_600_000L

    private suspend fun abruf(url: String, kontingent: Abrufkontingent, max: Int): MarketApi.Antwort? {
        // Erst den Mindestabstand abwarten, sonst meldet erlaubt() ihn als erschoepft
        val warte = kontingent.warten(Abrufkontingent.DOL)
        if (warte > 0) delay(warte)
        if (!kontingent.erlaubt(Abrufkontingent.DOL)) return null
        kontingent.buchen(Abrufkontingent.DOL)
        val a = api.anfrage(url, mapOf("Accept" to "application/json"), max = max)
        if (a.code == 429) kontingent.erschoepft(Abrufkontingent.DOL)
        return a
    }

    suspend fun laden(
        firma: String, schluessel: String, kontingent: Abrufkontingent,
        jetzt: Long = System.currentTimeMillis(), heute: LocalDate = LocalDate.now(java.time.ZoneOffset.UTC),
    ): Arbeitsschutz.Ergebnis? {
        if (schluessel.isBlank()) return null
        val muster = Arbeitsschutz.muster(firma) ?: return null
        val kennung = muster + "|" + schluessel.trim().hashCode()
        return sperre.withLock {
            speicher[kennung]?.takeIf { jetzt - it.first < GUELTIG_MS }?.let { return@withLock it.second }
            val ab = heute.minusYears(Arbeitsschutz.JAHRE.toLong())
            val a = runCatching { abruf(Arbeitsschutz.inspektionAdresse(muster, ab, schluessel), kontingent, 6_000_000) }.getOrNull()
            if (a == null || !a.ok) return@withLock null
            val roh = Arbeitsschutz.inspektionenLesen(a.text.orEmpty())
            val alle = Arbeitsschutz.passende(firma, roh, ab)
            val begrenzt = alle.take(Arbeitsschutz.PAKET * Arbeitsschutz.PAKETE)
            var mindestens = alle.size > begrenzt.size || roh.size >= Arbeitsschutz.LIMIT
            var ausgefallen = false
            val verstoesse = ArrayList<Arbeitsschutz.Verstoss>()
            for (paket in begrenzt.chunked(Arbeitsschutz.PAKET)) {
                val v = runCatching { abruf(Arbeitsschutz.verstossAdresse(paket.map { it.nummer }, schluessel), kontingent, 6_000_000) }.getOrNull()
                if (v == null || !v.ok) { mindestens = true; ausgefallen = true; continue }
                verstoesse += Arbeitsschutz.verstoesseLesen(v.text.orEmpty())
            }
            val e = Arbeitsschutz.zusammenfassen(begrenzt, verstoesse, mindestens)
            // Ausgefallene Pakete (Limit, Netz) werden nicht gemerkt, damit der naechste Versuch die Luecke fuellt
            if (!ausgefallen) speicher[kennung] = jetzt to e
            e
        }
    }
}
