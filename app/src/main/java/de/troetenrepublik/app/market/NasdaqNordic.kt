package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Nasdaq Nordic und Baltic (seit 5.95): Pflichtmeldungen zu Eigengeschaeften der Fuehrungskraefte (Managers
// Transactions) ueber die Nachrichtenschnittstelle von Nasdaq. Daenemark, Finnland und Island laufen ueber
// eine Gruppe, die baltischen Boersen ueber die andere. Nur Adressen und reine Leser, die Abrufe laufen
// spaeter ueber MarketApi.anfrage; releaseTime gilt in Europe/Berlin, die Abfrage in CET.
object NasdaqNordic {

    val LAENDER = setOf("DK", "FI", "IS", "EE", "LV", "LT")

    // Island laeuft ueber dieselbe Gruppe wie Daenemark und Finnland.
    fun gruppe(land: String): String? = when (land.trim().uppercase()) {
        "DK", "FI", "IS" -> "NordicMainMarkets"
        "EE", "LV", "LT" -> "BalticMainMarkets"
        else -> null
    }

    private val RECHTSFORMEN = setOf("a/s", "as", "oyj", "abp", "oy", "hf", "ab", "asa", "plc", "a", "b", "(publ)")
    private val PAARE = setOf("class a", "class b", "ser. a", "ser. b")

    // Firmenname ohne Rechtsform und Aktiengattung am Ende, wiederholt entfernt und getrimmt; nie leer (dann
    // der getrimmte Originalname).
    fun suchname(name: String): String {
        val worte = name.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        var weiter = true
        while (weiter && worte.isNotEmpty()) {
            weiter = false
            val letzte = worte.last().trimEnd(',', '.')
            if (letzte.isEmpty()) { worte.removeAt(worte.size - 1); weiter = true; continue }
            worte[worte.size - 1] = letzte
            if (letzte.lowercase() in RECHTSFORMEN && worte.size > 1) {
                worte.removeAt(worte.size - 1); weiter = true; continue
            }
            if (worte.size > 2) {
                val paar = (worte[worte.size - 2] + " " + letzte).lowercase()
                if (paar in PAARE) { worte.removeAt(worte.size - 1); worte.removeAt(worte.size - 1); weiter = true }
            }
        }
        val aus = worte.joinToString(" ").trim()
        return aus.ifEmpty { name.trim() }
    }

    // Prozentkodierung UTF-8 mit Leerzeichen als %20; unreservierte Zeichen bleiben.
    private fun kodiere(s: String): String {
        val hex = "0123456789ABCDEF"
        val aus = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 0x30..0x39 || c in 0x41..0x5A || c in 0x61..0x7A || c == 0x2D || c == 0x2E || c == 0x5F || c == 0x7E)
                aus.append(c.toChar())
            else aus.append('%').append(hex[c ushr 4]).append(hex[c and 15])
        }
        return aus.toString()
    }

    // Abfrage eines Zeitraums mit Freitextsuche nach dem Firmennamen; null, wenn das Land keine Gruppe hat.
    fun adresse(land: String, name: String, von: LocalDate, bis: LocalDate, anzahl: Int = 20): String? {
        val g = gruppe(land) ?: return null
        val suche = kodiere(suchname(name))
        return "https://api.news.eu.nasdaq.com/news/query.action?type=json&showAttachments=true&showCnsSpecific=true" +
            "&showCompany=true&countResults=false&market=&cnscategory=Managers%27%20Transactions&fromDate=$von&toDate=$bis" +
            "&globalGroup=exchangeNotice&globalName=$g&displayLanguage=en&language=&timeZone=CET" +
            "&dateMask=yyyy-MM-dd+HH%3Amm%3Ass&dir=DESC&limit=$anzahl&start=0&freeText=$suche&company="
    }

    // Eine Meldung mit Zeit in Millisekunden und Anhaengen als Paare aus Dateiname und URL.
    data class Meldung(val zeit: Long, val firma: String, val titel: String, val markt: String, val link: String, val anhaenge: List<Pair<String, String>>)

    // JSON der Schnittstelle: results.item[] oder eine reine Liste; null bei Parserfehler oder fehlender
    // Liste, nur ein gueltiges leeres Array ergibt eine leere Liste. Uebernommen werden nur https-Links.
    fun lesen(jsonText: String): List<Meldung>? = try {
        val wurzel = Json.parseToJsonElement(jsonText)
        val items = wurzel as? JsonArray
            ?: ((wurzel as? JsonObject)?.get("results") as? JsonObject)?.get("item") as? JsonArray
            ?: return null
        items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val titel = (o["headline"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val firma = (o["company"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (titel.isEmpty() || firma.isEmpty()) return@mapNotNull null
            Meldung(
                zeit = zeitLesen((o["releaseTime"] as? JsonPrimitive)?.content),
                firma = firma,
                titel = titel,
                markt = (o["market"] as? JsonPrimitive)?.content.orEmpty(),
                link = (o["messageUrl"] as? JsonPrimitive)?.content.orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
                anhaenge = (o["attachment"] as? JsonArray).orEmpty().mapNotNull { a ->
                    val x = a as? JsonObject ?: return@mapNotNull null
                    val datei = (x["fileName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    val url = (x["attachmentUrl"] as? JsonPrimitive)?.content.orEmpty()
                    if (datei.isNotEmpty() && url.startsWith("https://")) datei to url else null
                },
            )
        }
    } catch (_: Exception) { null }

    // releaseTime wie "2026-11-03 14:30:00" gilt in Europe/Berlin; 0 bei unlesbarem Wert.
    private fun zeitLesen(s: String?): Long {
        val m = s?.trim()?.let { Regex("(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})").matchEntire(it) } ?: return 0L
        return runCatching {
            LocalDateTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())
                .atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    // Gehoert die Meldung zu dieser Firma: Suchnamen gleich oder eines beginnt mit dem anderen plus Leerzeichen.
    fun passt(m: Meldung, name: String): Boolean {
        val a = InsiderWelt.name(suchname(m.firma))
        val b = InsiderWelt.name(suchname(name))
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.startsWith("$b ") || b.startsWith("$a ")
    }
}
