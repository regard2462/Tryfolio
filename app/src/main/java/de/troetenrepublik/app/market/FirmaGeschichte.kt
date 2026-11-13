package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Splits
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

// Firmengeschichte als Zeitleiste: Ereignisse aus Wikidata (Gruendung, Boersengang, Vorgaenger und Nachfolger, Eigner,
// Toechter mit Zeit, Namen, Kuerzel, Sitz, CEO), aus der Liste der SEC (fruehere Namen), aus Aktiensplits und aus den
// 8-K (Uebernahmen, Fuehrungswechsel, Insolvenz). Reine Funktionen; der Abruf steht in FirmaHintergrundLader.kt.
// Ein Ereignis traegt nur Art und Werte, der Text entsteht in der Oberflaeche (zweisprachig).
object FirmaGeschichte {
    enum class Art {
        GRUENDUNG, BOERSENGANG, VORGAENGER, NACHFOLGER, EIGNER, MUTTER, MUTTER_ENDE, NEUE_TOCHTER, TOCHTER_ENDE, NAME, KUERZEL, SITZ,
        CEO, SPLIT, AUFLOESUNG, DEAL_ANGEKUENDIGT, DEAL_ANGEBOT, DEAL_ABGESCHLOSSEN, FUEHRUNG, INSOLVENZ,
    }

    // tag: JJJJ, JJJJ-MM oder JJJJ-MM-TT. a und b: Werte je nach Art (Name, Kuerzel, Verhaeltnis, Preis), link: Quelle.
    @Serializable
    class Ereignis(val art: Art, val tag: String, val a: String = "", val b: String = "", val link: String = "") {
        val jahr: Int get() = tag.take(4).toIntOrNull() ?: 0
        val taggenau: Boolean get() = tag.length == 10
    }

    @Serializable
    class Daten(val abgerufen: Long = 0L, val kennung: String = "", val ereignisse: List<Ereignis> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun schreiben(d: Daten): String = json.encodeToString(Daten.serializer(), d)

    fun lesen(text: String): Daten? = runCatching { json.decodeFromString(Daten.serializer(), text) }.getOrNull()

    // ------------------------------------------------------------ Wikidata lesen

    fun eintragAdresse(id: String) = "https://www.wikidata.org/wiki/Special:EntityData/$id.json"

    fun seite(id: String) = "https://www.wikidata.org/wiki/$id"

    // Eine Aussage: Wert (Kennung eines Eintrags, Text oder Zahl) mit den Zeitangaben und Zusaetzen
    class Aussage(
        val id: String?, val text: String?, val start: String?, val ende: String?, val punkt: String?, val anteil: Double?, val ticker: String?,
    ) {
        val zeit: String? get() = punkt ?: start ?: ende
    }

    fun anspruch(antwort: String, id: String): JsonObject? = runCatching {
        Json.parseToJsonElement(antwort).jsonObject["entities"]?.jsonObject?.get(id)?.jsonObject?.get("claims")?.jsonObject
    }.getOrNull()

    // "+1980-12-00T00:00:00Z" mit Genauigkeit 10 (Monat): "1980-12"; Genauigkeit 9 (Jahr): "1980". Vor Christus: null.
    internal fun zeit(wert: JsonObject?): String? {
        val z = wert?.get("time")?.jsonPrimitive?.contentOrNull ?: return null
        val m = Regex("^\\+?(\\d{1,4})-(\\d{2})-(\\d{2})").find(z) ?: return null
        val genau = wert["precision"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 9
        val j = m.groupValues[1].toInt()
        if (j <= 0) return null
        val mo = m.groupValues[2].toInt(); val t = m.groupValues[3].toInt()
        val jahr = j.toString().padStart(4, '0')
        return when {
            genau >= 11 && mo in 1..12 && t in 1..31 -> "$jahr-${m.groupValues[2]}-${m.groupValues[3]}"
            genau >= 10 && mo in 1..12 -> "$jahr-${m.groupValues[2]}"
            else -> jahr
        }
    }

    private fun wertVon(el: JsonElement?): JsonElement? = (el as? JsonObject)?.get("datavalue")?.let { (it as? JsonObject)?.get("value") }

    private fun qualZeit(q: JsonObject?, prop: String): String? =
        ((q?.get(prop) as? JsonArray)?.firstOrNull() as? JsonObject)?.let { zeit(wertVon(it) as? JsonObject) }

    fun aussagen(anspruch: JsonObject?, prop: String): List<Aussage> =
        ((anspruch?.get(prop) as? JsonArray).orEmpty()).mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            if ((o["rank"] as? JsonPrimitive)?.contentOrNull == "deprecated") return@mapNotNull null
            val main = o["mainsnak"] as? JsonObject
            val v = wertVon(main)
            val q = o["qualifiers"] as? JsonObject
            val zeitWert = (v as? JsonObject)?.takeIf { it.containsKey("time") }
            val id = (v as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            val text = when {
                zeitWert != null -> zeit(zeitWert)
                v is JsonObject -> v["text"]?.jsonPrimitive?.contentOrNull
                v is JsonPrimitive -> v.contentOrNull
                else -> null
            }
            val anteil = ((q?.get("P1107") as? JsonArray)?.firstOrNull() as? JsonObject)?.let { (wertVon(it) as? JsonObject)?.get("amount")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() }
            val ticker = ((q?.get("P249") as? JsonArray)?.firstOrNull() as? JsonObject)?.let { (wertVon(it) as? JsonPrimitive)?.contentOrNull }
            if (id == null && text == null) return@mapNotNull null
            Aussage(id, text, qualZeit(q, "P580"), qualZeit(q, "P582"), qualZeit(q, "P585"), anteil, ticker)
        }

    // Reihenfolge fuer die Zeitleiste: unvollstaendige Angaben liegen in der Mitte des Zeitraums
    internal fun sortTag(tag: String): String = when (tag.length) { 4 -> "$tag-07-01"; 7 -> "$tag-15"; else -> tag }

    // ------------------------------------------------------------ Ereignisse aus Wikidata

    // name liefert die Beschriftung eines Eintrags; null: das Ereignis entfaellt. Aufgenommen werden nur Aussagen mit Zeitangabe.
    fun ausWikidata(anspruch: JsonObject, kennung: String, name: (String) -> String?): List<Ereignis> {
        val aus = ArrayList<Ereignis>()
        val eigen = seite(kennung)
        val gegruendet = aussagen(anspruch, "P571").firstNotNullOfOrNull { it.text }
        gegruendet?.let { aus += Ereignis(Art.GRUENDUNG, it, link = eigen) }

        // Boersengang: fruehester Beginn einer Notierung; Kuerzelwechsel an derselben Boerse
        val notierungen = aussagen(anspruch, "P414").filter { it.start != null && it.id != null }
        notierungen.minByOrNull { sortTag(it.start.orEmpty()) }?.let { n ->
            name(n.id.orEmpty())?.let { aus += Ereignis(Art.BOERSENGANG, n.start.orEmpty(), a = it, link = eigen) }
        }
        for ((_, gruppe) in notierungen.filter { it.ticker != null }.groupBy { it.id }) {
            val s = gruppe.sortedBy { sortTag(it.start.orEmpty()) }
            for (i in 1 until s.size) {
                val alt = s[i - 1].ticker.orEmpty(); val neu = s[i].ticker.orEmpty()
                if (alt != neu) aus += Ereignis(Art.KUERZEL, s[i].start.orEmpty(), a = alt, b = neu, link = eigen)
            }
        }

        for (v in aussagen(anspruch, "P1365")) {
            val z = v.zeit ?: continue
            val i = v.id ?: continue
            val n = name(i) ?: continue
            aus += Ereignis(Art.VORGAENGER, z, a = n, link = seite(i))
        }
        for (v in aussagen(anspruch, "P1366")) {
            val z = v.ende ?: v.zeit ?: continue
            val i = v.id ?: continue
            val n = name(i) ?: continue
            aus += Ereignis(Art.NACHFOLGER, z, a = n, link = seite(i))
        }
        // Mehrheitseigner und Mutter mit Beginn
        for (v in aussagen(anspruch, "P127")) {
            if ((v.anteil ?: 0.0) < 0.5) continue
            val z = v.start ?: continue
            val i = v.id ?: continue
            val n = name(i) ?: continue
            aus += Ereignis(Art.EIGNER, z, a = n, link = seite(i))
        }
        for (v in aussagen(anspruch, "P749")) {
            val i = v.id ?: continue
            val n = name(i) ?: continue
            v.start?.let { aus += Ereignis(Art.MUTTER, it, a = n, link = seite(i)) }
            v.ende?.let { aus += Ereignis(Art.MUTTER_ENDE, it, a = n, link = seite(i)) }
        }
        // Toechter mit Beginn oder Ende
        for (v in aussagen(anspruch, "P355")) {
            if (v.start == null && v.ende == null) continue
            val i = v.id ?: continue
            val n = name(i) ?: continue
            v.start?.let { aus += Ereignis(Art.NEUE_TOCHTER, it, a = n, link = seite(i)) }
            v.ende?.let { aus += Ereignis(Art.TOCHTER_ENDE, it, a = n, link = seite(i)) }
        }
        // Namen mit Zeit: jeder Name ab dem zweiten
        val namen = aussagen(anspruch, "P1448").filter { it.start != null && it.text != null }
        val sortiert = namen.sortedBy { sortTag(it.start.orEmpty()) }
        for (i in 1 until sortiert.size) {
            val alt = sortiert[i - 1].text.orEmpty(); val neu = sortiert[i].text.orEmpty(); val z = sortiert[i].start.orEmpty()
            if (alt != neu && z.isNotEmpty()) aus += Ereignis(Art.NAME, z, a = alt, b = neu, link = eigen)
        }
        // Sitzverlegung nur, wenn es davor einen Sitz an einem anderen Ort gab: ein anderes Objekt, das frueher begann oder vor
        // dem Beginn des neuen endete. Ein Beginn-Datum allein an einem einzigen Sitz ist kein Umzug.
        val sitze = aussagen(anspruch, "P159").filter { it.id != null }
        for (s in sitze) {
            val z = s.start ?: continue
            val i = s.id ?: continue
            val frueher = sitze.any { o ->
                o.id != i && (
                    (o.start != null && sortTag(o.start) < sortTag(z)) ||
                        (o.start == null && o.ende != null && sortTag(o.ende) <= sortTag(z))
                    )
            }
            if (!frueher) continue
            name(i)?.let { aus += Ereignis(Art.SITZ, z, a = it, link = seite(i)) }
        }
        // CEO mit Beginn
        for (v in aussagen(anspruch, "P169")) {
            val z = v.start ?: continue
            val i = v.id ?: continue
            val n = name(i) ?: continue
            aus += Ereignis(Art.CEO, z, a = n, link = seite(i))
        }
        aussagen(anspruch, "P576").firstNotNullOfOrNull { it.text }?.let { aus += Ereignis(Art.AUFLOESUNG, it, link = eigen) }
        return aus
    }

    // Kennungen, deren Beschriftung ausWikidata braucht (zum Nachladen)
    fun benoetigteKennungen(anspruch: JsonObject, kennung: String): List<String> {
        val ids = LinkedHashSet<String>()
        ausWikidata(anspruch, kennung) { ids += it; "?" }
        return ids.toList()
    }

    // ------------------------------------------------------------ Liste der SEC: fruehere Namen

    fun secName(subs: String, cik: String): List<Ereignis> = runCatching {
        val o = Json.parseToJsonElement(subs).jsonObject
        val aktuell = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val alt = (o["formerNames"] as? JsonArray).orEmpty().mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            val n = x["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val bis = x["to"]?.jsonPrimitive?.contentOrNull?.take(10)?.takeIf { it.length == 10 } ?: return@mapNotNull null
            Triple(n, x["from"]?.jsonPrimitive?.contentOrNull?.take(10).orEmpty(), bis)
        }.sortedBy { it.third }
        alt.mapIndexed { i, (n, _, bis) ->
            val neu = alt.getOrNull(i + 1)?.first ?: aktuell
            Ereignis(Art.NAME, bis, a = n.trim(), b = neu.trim(), link = "https://www.sec.gov/edgar/browse/?CIK=" + cik.trimStart('0'))
        }.filter { it.a.isNotBlank() && it.b.isNotBlank() && !gleichName(it.a, it.b) }
    }.getOrDefault(emptyList())

    private fun gleichName(a: String, b: String) = SecBericht.nameKern(a) == SecBericht.nameKern(b) && a.lowercase().replace(Regex("[^a-z0-9]"), "") == b.lowercase().replace(Regex("[^a-z0-9]"), "")

    // ------------------------------------------------------------ Splits

    fun ausSplits(splits: List<Splits.Split>): List<Ereignis> = splits.map { s ->
        Ereignis(Art.SPLIT, LocalDate.ofEpochDay(Math.floorDiv(s.t, 86_400_000L)).toString(), a = s.text, b = if (s.verhaeltnis < 1.0) "REVERSE" else "")
    }

    // ------------------------------------------------------------ 8-K (24 Monate)

    // Uebernahmen, Fuehrungswechsel (CEO und CFO) und Insolvenzen aus dem Ergebnis der 8-K-Abfrage
    fun ausAchtK(erg: AchtKAbruf.Ergebnis): List<Ereignis> {
        fun adresse(accession: String, datei: String) = SecInsider.archivAdresse(erg.cik, accession, datei)
        val deals = erg.vorgaenge.map { v ->
            val art = when (v.art) {
                AchtK.VorgangsArt.ANGEKUENDIGT -> Art.DEAL_ANGEKUENDIGT
                AchtK.VorgangsArt.ANGEBOT -> Art.DEAL_ANGEBOT
                AchtK.VorgangsArt.ABGESCHLOSSEN -> Art.DEAL_ABGESCHLOSSEN
            }
            Ereignis(art, v.tag, a = v.gegenseite.orEmpty(), b = v.preis?.toString().orEmpty(), link = adresse(v.accession, v.datei))
        }
        val warn = erg.warnzeichen.mapNotNull { w ->
            when (w.art) {
                AchtK.Art.FUEHRUNG -> Ereignis(Art.FUEHRUNG, w.tag, a = w.detail?.person.orEmpty(), b = w.rolle.name, link = adresse(w.accession, w.datei))
                AchtK.Art.INSOLVENZ -> Ereignis(Art.INSOLVENZ, w.tag, a = (w.detail?.kapitel ?: 0).toString(), link = adresse(w.accession, w.datei))
                else -> null
            }
        }
        return deals + warn
    }

    // ------------------------------------------------------------ Zusammenfuehren

    // Doppelte entfallen (gleiche Art, Tag und Werte); ein Wikidata-Name neben einem SEC-Namen im selben oder
    // benachbarten Jahr entfaellt. Neueste zuerst.
    fun zusammenfuehren(liste: List<Ereignis>, secNamen: List<Ereignis>): List<Ereignis> {
        val wd = liste.filter { e -> e.art != Art.NAME || secNamen.none { s -> kotlin.math.abs(s.jahr - e.jahr) <= 1 } }
        return (wd + secNamen).distinctBy { listOf(it.art, it.tag, it.a, it.b) }
            .sortedWith(compareByDescending<Ereignis> { sortTag(it.tag) }.thenBy { it.art.ordinal })
    }

    class Kern(val gegruendet: Int?, val boersengang: Int?, val uebernahmen: Int)

    fun kern(liste: List<Ereignis>): Kern = Kern(
        liste.firstOrNull { it.art == Art.GRUENDUNG }?.jahr?.takeIf { it > 0 },
        liste.filter { it.art == Art.BOERSENGANG }.minByOrNull { sortTag(it.tag) }?.jahr?.takeIf { it > 0 },
        liste.count { it.art == Art.NEUE_TOCHTER || it.art == Art.DEAL_ABGESCHLOSSEN },
    )

    // So viele Eintraege stehen zuerst da, aeltere sind eingeklappt
    const val ZUERST = 6
}
