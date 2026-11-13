package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Landesdaten aus Wikidata (CC0), gebuendelt in assets/globus/laenderdaten.json und von Hand geprueft: Hauptstadt,
// Staatsoberhaupt, Regierungschef, Regierungsform, Waehrung, Mitgliedschaften. Je Feld ein Text oder [englisch, deutsch].
// Dazu wiki-wirtschaft.json: je Land die Titel der Wikipedia-Artikel "Wirtschaft von ..." und des Laenderartikels.
object LaenderDaten {
    data class Eintrag(
        val hauptstadt: String?, val waehrung: String?, val oberhaupt: String?, val regierungschef: String?,
        // Grundform der Regierungsform (siehe Strings), ohne den Zusatz Bundesstaat
        val form: String?, val bundesstaat: Boolean, val mitglieder: List<String>,
    )

    data class Datei(val stand: String, val laender: Map<String, Eintrag>)

    // Mitgliedschaften, die das Blatt kennt, in dieser Reihenfolge
    val ORGANISATIONEN = listOf("EU", "EURO", "NATO", "G7", "G20", "OPEC", "BRICS", "OECD", "ASEAN", "MERCOSUR", "GOLF", "AU")

    private val json = Json { ignoreUnknownKeys = true }

    private fun text(e: JsonElement?, de: Boolean): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> (if (de) e.getOrNull(1) else e.getOrNull(0))?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: e.getOrNull(0)?.jsonPrimitive?.contentOrNull
        else -> null
    }?.takeIf { it.isNotBlank() }

    fun lesen(text: String, de: Boolean): Datei? = runCatching {
        val w = json.parseToJsonElement(text).jsonObject
        val aus = HashMap<String, Eintrag>()
        for ((a2, v) in w["l"]!!.jsonObject) {
            val o = v as? JsonObject ?: continue
            val f = o["f"]?.jsonPrimitive?.contentOrNull
            aus[a2] = Eintrag(
                text(o["h"], de), text(o["w"], de), text(o["s"], de), text(o["r"], de),
                f?.substringBefore('+'), f?.endsWith("+B") == true,
                o["m"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter { it in ORGANISATIONEN }.orEmpty(),
            )
        }
        Datei(w["stand"]?.jsonPrimitive?.contentOrNull.orEmpty(), aus)
    }.getOrNull()

    // Personen (Staatsoberhaupt, Regierungschef) altern schnell: ab sechs Monaten nach dem Stand der Datei erscheinen sie mit
    // Stand ("... (Stand 11/2026)") statt als Gegenwart.
    fun gealtert(d: Datei, de: Boolean, jetzt: Long): Datei {
        val marke = Alter.marke(d.stand)
        if (marke.isEmpty() || Alter.zustand(d.stand, Alter.LAENDER_PERSONEN, jetzt) == Alter.Zustand.FRISCH) return d
        val zusatz = " (" + (if (de) "Stand " else "as of ") + marke + ")"
        return Datei(d.stand, d.laender.mapValues { (_, e) -> e.copy(oberhaupt = e.oberhaupt?.plus(zusatz), regierungschef = e.regierungschef?.plus(zusatz)) })
    }

    // Wikipedia-Titel: Sprache und Titel des Wirtschaftsartikels (deutsch, sonst englisch) und des Laenderartikels.
    data class Artikel(val sprache: String, val titel: String)
    data class WikiTitel(val wirtschaft: Artikel?, val land: Artikel?)

    fun wikiTitel(text: String, a2: String, de: Boolean): WikiTitel? = runCatching {
        val a = json.parseToJsonElement(text).jsonObject["t"]!!.jsonObject[a2] as? JsonArray ?: return null
        fun s(i: Int) = a.getOrNull(i)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        fun wahl(d: String?, e: String?): Artikel? = when {
            de && d != null -> Artikel("de", d)
            e != null -> Artikel("en", e)
            d != null -> Artikel("de", d)
            else -> null
        }
        WikiTitel(wahl(s(0), s(1)), wahl(s(2), s(3)))
    }.getOrNull()

    // Zusammenfassung der Wikipedia-REST-Schnittstelle: Auszug und Adresse des Artikels.
    data class Auszug(val text: String, val adresse: String)

    fun auszug(antwort: String): Auszug? = runCatching {
        val o = json.parseToJsonElement(antwort).jsonObject
        val t = o["extract"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = o["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.contentOrNull.orEmpty()
        Auszug(Lautschrift.ohne(t), url)
    }.getOrNull()

    fun auszugAdresse(sprache: String, titel: String): String =
        "https://$sprache.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(titel.replace(' ', '_'), "UTF-8").replace("+", "%20")

    fun artikelAdresse(sprache: String, titel: String): String =
        "https://$sprache.wikipedia.org/wiki/" + java.net.URLEncoder.encode(titel.replace(' ', '_'), "UTF-8").replace("+", "%20").replace("%3A", ":").replace("%2C", ",")

    // Kuerzt am Satzende auf hoechstens max Zeichen. Ein Punkt nach Abkuerzung oder Zahl (z. B. "Mrd.", "5.") zaehlt nicht
    // als Satzende. Ist schon der erste Satz laenger, wird am letzten Wortende vor max gekuerzt.
    fun kuerzen(text: String, max: Int = 600): String {
        if (text.length <= max) return text
        var ende = -1
        var i = 0
        while (i < text.length && i < max) {
            val c = text[i]
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length && text[i + 1] == ' ' && i + 2 < text.length && text[i + 2].isUpperCase()) {
                val wort = text.substring(0, i).substringAfterLast(' ')
                val abkuerzung = c == '.' && (wort.length <= 3 || wort.all { it.isDigit() } || wort.endsWith("Mrd") || wort.endsWith("Mio") || wort.endsWith("Bio"))
                if (!abkuerzung) ende = i
            }
            i++
        }
        if (ende > 0) return text.substring(0, ende + 1)
        val schnitt = text.lastIndexOf(' ', max - 1).takeIf { it > 0 } ?: (max - 1)
        return text.substring(0, schnitt).trimEnd(',', ';', ':', ' ') + "\u2026"
    }

    // [0.78] Titel fuer den Wirtschaftsteil: der Wirtschaftsartikel nur in der Sprache der App, der Laenderartikel in der
    // Sprache der App und sonst in der anderen. Fehlt der eigene Wirtschaftsartikel, steht der Abschnitt des Laenderartikels.
    fun wikiTitelEigen(text: String, a2: String, de: Boolean): WikiTitel? = runCatching {
        val a = json.parseToJsonElement(text).jsonObject["t"]!!.jsonObject[a2] as? JsonArray ?: return null
        fun s(i: Int) = a.getOrNull(i)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val sprache = if (de) "de" else "en"
        val eigen = s(if (de) 0 else 1)?.let { Artikel(sprache, it) }
        val land = s(if (de) 2 else 3)?.let { Artikel(sprache, it) } ?: s(if (de) 3 else 2)?.let { Artikel(if (de) "en" else "de", it) }
        WikiTitel(eigen, land)
    }.getOrNull()

    // Titel und Art der Seite, die die Zusammenfassung wirklich liefert (nach einer Weiterleitung der Zielartikel).
    fun auszugTitel(antwort: String): String? = runCatching {
        val o = json.parseToJsonElement(antwort).jsonObject
        if (o["type"]?.jsonPrimitive?.contentOrNull == "disambiguation") return null
        o["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun gleich(a: String, b: String) = a.replace('_', ' ').trim().equals(b.replace('_', ' ').trim(), ignoreCase = true)

    // Ob die gelieferte Seite der Wirtschaftsartikel ist: der angefragte Titel selbst oder ein anderer Wirtschaftsartikel.
    // Eine Weiterleitung auf den Laenderartikel (dessen Einleitung nichts zur Wirtschaft sagt) zaehlt nicht.
    fun istWirtschaftsartikel(geliefert: String?, angefragt: String, land: String?): Boolean {
        if (geliefert == null) return false
        if (land != null && gleich(geliefert, land)) return false
        if (gleich(geliefert, angefragt)) return true
        val t = geliefert.lowercase()
        return "wirtschaft" in t || "economy" in t
    }

    // Ganzer Text eines Artikels ohne Auszeichnung, Ueberschriften als "== Titel ==" (Wikimedia-Schnittstelle, TextExtracts).
    fun textAdresse(sprache: String, titel: String): String =
        "https://$sprache.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles=" +
            java.net.URLEncoder.encode(titel.replace(' ', '_'), "UTF-8")

    // Antwort von textAdresse: Titel der Seite, ihr Text und der Abschnitt, auf den eine Weiterleitung zeigt.
    data class Seite(val titel: String, val text: String, val abschnitt: String?)

    fun seite(antwort: String): Seite? = runCatching {
        val q = json.parseToJsonElement(antwort).jsonObject["query"]!!.jsonObject
        val p = (q["pages"] as JsonArray).firstOrNull() as? JsonObject ?: return null
        val text = p["extract"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val ziel = (q["redirects"] as? JsonArray)?.lastOrNull()?.jsonObject?.get("tofragment")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        Seite(p["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), Lautschrift.ohne(text), ziel)
    }.getOrNull()

    // Ueberschriften des Wirtschaftsteils im Laenderartikel, je Sprache.
    fun wirtschaftsNamen(sprache: String): List<String> = if (sprache == "de") listOf("Wirtschaft") else listOf("Economy")

    private val KOPF = Regex("^(=+)\\s*(.*?)\\s*=+\\s*$")

    // Abschnitt eines Laenderartikels: Ueberschrift der obersten Ebene, die einem der Namen gleicht oder mit ihm beginnt
    // ("Wirtschaft und Infrastruktur"); die genaue Ueberschrift geht vor. Liefert Ueberschrift und Fliesstext ohne die
    // Zwischenueberschriften; null ohne solchen Abschnitt oder ohne Text darin.
    data class Abschnitt(val kopf: String, val text: String)

    fun abschnitt(text: String, namen: List<String>): Abschnitt? {
        val zeilen = text.split('\n')
        // Ebene-2-Ueberschriften mit Zeilennummer
        val koepfe = zeilen.mapIndexedNotNull { i, z -> KOPF.find(z.trim())?.takeIf { it.groupValues[1].length == 2 }?.let { i to it.groupValues[2] } }
        val genau = koepfe.firstOrNull { (_, k) -> namen.any { it.isNotBlank() && k.equals(it, ignoreCase = true) } }
        val treffer = genau ?: koepfe.firstOrNull { (_, k) -> namen.any { it.isNotBlank() && k.startsWith(it, ignoreCase = true) } } ?: return null
        val ende = koepfe.firstOrNull { it.first > treffer.first }?.first ?: zeilen.size
        val absaetze = zeilen.subList(treffer.first + 1, ende).map { it.trim() }.filter { it.isNotEmpty() && KOPF.find(it) == null }
        if (absaetze.isEmpty()) return null
        return Abschnitt(treffer.second, absaetze.joinToString(" "))
    }

    // Adresse des Abschnitts im Artikel (Sprungmarke wie auf der Seite).
    fun abschnittAdresse(sprache: String, titel: String, kopf: String): String =
        artikelAdresse(sprache, titel) + "#" + java.net.URLEncoder.encode(kopf.replace(' ', '_'), "UTF-8").replace("+", "_")

    // Wikipedia-Auszuege bleiben eine Woche gespeichert.
    const val GUELTIG_MS = 7 * 24 * 3_600_000L
}
