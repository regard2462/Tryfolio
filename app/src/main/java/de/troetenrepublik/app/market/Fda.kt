package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Zulassungen und Rueckrufe der US-Arzneimittelbehoerde FDA (seit 5.9) bei Pharma-, Biotech- und Medizintechnik-Aktien,
// ueber openFDA (api.fda.gov, CC0, ohne Schluessel, 1000 Abrufe am Tag je IP). Zulassungen aus Drugs@FDA (neue
// Arzneimittel und Biologika, Erweiterungen der Anwendung), Rueckrufe aus den Enforcement Reports fuer Arzneimittel und
// Medizinprodukte. Die FDA nennt Firmen mit eigenen Namen und Kuerzeln; die App gleicht sie mit dem Firmennamen ab.
object Fda {
    const val QUELLE = "U.S. Food and Drug Administration, openFDA (api.fda.gov)"

    data class Zulassung(val nummer: String, val marke: String, val wirkstoff: String, val art: Art, val tag: String, val vorrang: Boolean)

    // NEU: neuer Wirkstoff; ORIGINAL: andere Erstzulassung; ANWENDUNG: neue Anwendung eines zugelassenen Mittels.
    enum class Art { NEU, ORIGINAL, ANWENDUNG }

    // klasse 1 bis 3 (I: ernste Gesundheitsgefahr); geraet: Medizinprodukt statt Arzneimittel.
    data class Rueckruf(val nummer: String, val klasse: Int, val produkt: String, val grund: String, val tag: String, val laufend: Boolean, val geraet: Boolean)

    data class Ergebnis(val zulassungen: List<Zulassung>, val rueckrufe: List<Rueckruf>, val abgerufen: Long)

    private val branche = Regex("drug|biotech|medical|diagnostic|pharma|health ?care", RegexOption.IGNORE_CASE)

    // Ob die Branche einer Aktie zur FDA passt (Yahoo-Branche oder -Sektor).
    fun betroffen(industrie: String, sektor: String): Boolean =
        branche.containsMatchIn(industrie) || (industrie.isBlank() && sektor.equals("Healthcare", true))

    private val endungen = setOf(
        "INC", "INCORPORATED", "CORP", "CORPORATION", "CO", "COMPANY", "LTD", "LIMITED", "LLC", "LP", "LLP", "PLC", "AG",
        "SA", "SE", "NV", "BV", "AB", "GMBH", "HOLDINGS", "HOLDING", "GROUP", "THE", "US", "USA", "PHARMACEUTICALS",
        "PHARMACEUTICAL", "PHARMS", "PHARMA", "LABS", "LABORATORIES", "THERAPEUTICS", "BIOSCIENCES", "BIOTECHNOLOGY",
        "AS", "A", "S", "ASA", "OYJ", "SPA", "AND", "ADR", "ORD", "CLASS", "INDUSTRIES", "INTERNATIONAL",
    )

    // Grossbuchstaben ohne Akzente, & als AND, ohne Satzzeichen, ohne "DBA ..." und ohne Rechtsform und Fuellwoerter am Ende.
    fun normal(name: String): String {
        var s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}"), "")
            .uppercase().replace("&", " AND ")
        s = s.replace(Regex("\\b(D/B/A|DBA)\\b.*"), " ").replace(Regex("[^A-Z0-9 ]"), " ")
        val worte = s.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (worte.size > 1 && worte.last() in endungen) worte.removeAt(worte.lastIndex)
        if (worte.size > 1 && worte.first() == "THE") worte.removeAt(0)
        return worte.joinToString(" ")
    }

    // Tochterfirmen und Kuerzel der FDA je normalisiertem Firmennamen.
    private val ALIAS = mapOf(
        "PFIZER" to listOf("HOSPIRA", "WYETH", "PARKE DAVIS", "KING", "MERIDIAN MEDICAL TECHNOLOGIES"),
        "JOHNSON AND JOHNSON" to listOf("JANSSEN", "J AND J", "ETHICON", "DEPUY", "JOHNSON AND JOHNSON"),
        "ELI LILLY" to listOf("LILLY"),
        "MERCK" to listOf("MERCK SHARP DOHME", "MERCK SHARP AND DOHME"),
        "ABBVIE" to listOf("ALLERGAN"),
        "BRISTOL MYERS SQUIBB" to listOf("BRISTOL MYERS", "CELGENE"),
        "AMGEN" to listOf("AMGEN"),
        "GILEAD SCIENCES" to listOf("GILEAD"),
        "MEDTRONIC" to listOf("MEDTRONIC"),
        "BAXTER" to listOf("BAXTER"),
        "BECTON DICKINSON" to listOf("BECTON DICKINSON", "CAREFUSION", "BARD"),
        "MERCK KGAA" to listOf("EMD SERONO", "MERCK KGAA"),
        "ABBOTT" to listOf("ABBOTT"),
        "TEVA" to listOf("TEVA"),
        "VIATRIS" to listOf("MYLAN"),
        "NOVO NORDISK" to listOf("NOVO NORDISK"),
        "GLAXOSMITHKLINE" to listOf("GLAXOSMITHKLINE", "GSK"),
        "GSK" to listOf("GLAXOSMITHKLINE", "GSK"),
        "ASTRAZENECA" to listOf("ASTRAZENECA"),
        "NOVARTIS" to listOf("NOVARTIS", "SANDOZ"),
        "SANOFI" to listOf("SANOFI", "GENZYME"),
        "BAYER" to listOf("BAYER"),
        "ROCHE" to listOf("GENENTECH", "HOFFMANN LA ROCHE", "ROCHE"),
        "FRESENIUS MEDICAL CARE" to listOf("FRESENIUS MEDCL", "FRESENIUS MEDICAL"),
    )

    // Namen, unter denen die FDA die Firma fuehren kann: der normalisierte Name und bekannte Toechter.
    fun namen(firma: String): List<String> {
        val n = normal(firma)
        val kurz = n.split(' ').take(2).joinToString(" ")
        return (listOf(n) + (ALIAS[n] ?: ALIAS[kurz] ?: ALIAS[n.substringBefore(' ')]).orEmpty()).distinct()
    }

    // Ob ein Firmenname der FDA zu einem der Namen passt: gleich, als Wortanfang (mindestens fuenf Buchstaben im ersten
    // Wort, damit kurze Namen nicht fremde Firmen treffen) oder als „A <NAME> COMPANY“ und „SUBSIDIARY OF <NAME>“.
    fun passt(fdaName: String, namen: List<String>): Boolean {
        val f = normal(fdaName)
        val roh = " " + fdaName.uppercase().replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex(" +"), " ") + " "
        return namen.any { n ->
            f == n || (n.substringBefore(' ').length >= 5 && (f.startsWith("$n ") || " $f ".contains(" $n "))) ||
                roh.contains(" A $n COMPANY ") || roh.contains(" SUBSIDIARY OF $n ") || roh.contains(" OF $n ")
        }
    }

    private fun tagApi(iso: String) = iso.replace("-", "")
    private fun tagIso(api: String) = if (api.length == 8) "${api.take(4)}-${api.substring(4, 6)}-${api.substring(6)}" else api

    // Drugs@FDA: Antraege (NDA, BLA) mit Einreichungen im Zeitraum, Sponsor beginnt mit dem ersten Wort des Namens.
    fun zulassungAdresse(name: String, von: String, bis: String): String {
        val wort = name.substringBefore(' ')
        return "https://api.fda.gov/drug/drugsfda.json?search=sponsor_name:$wort*+AND+(application_number:NDA*+OR+application_number:BLA*)" +
            "+AND+submissions.submission_status_date:[${tagApi(von)}+TO+${tagApi(bis)}]&limit=100"
    }

    fun rueckrufAdresse(geraet: Boolean, name: String, von: String, bis: String): String {
        val art = if (geraet) "device" else "drug"
        val q = java.net.URLEncoder.encode("\"$name\"", "UTF-8")
        return "https://api.fda.gov/$art/enforcement.json?search=recalling_firm:$q+AND+report_date:[${tagApi(von)}+TO+${tagApi(bis)}]&sort=report_date:desc&limit=100"
    }

    private fun JsonObject.text(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    private fun ergebnisse(text: String): JsonArray =
        runCatching { Json.parseToJsonElement(text).jsonObject["results"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())

    // Zulassungen der Firma im Zeitraum: Erstzulassungen (ORIG) und neue Anwendungen (Ergaenzung EFFICACY), nur genehmigt.
    fun zulassungenLesen(text: String, namen: List<String>, von: String, bis: String): List<Zulassung> {
        val aus = mutableListOf<Zulassung>()
        for (e in ergebnisse(text)) {
            val o = e.jsonObject
            if (!passt(o.text("sponsor_name"), namen)) continue
            val produkt = o["products"]?.jsonArray?.firstOrNull()?.jsonObject
            val marke = produkt?.text("brand_name").orEmpty()
            val wirkstoff = produkt?.get("active_ingredients")?.jsonArray?.joinToString(", ") { it.jsonObject.text("name") }.orEmpty()
            for (s in o["submissions"]?.jsonArray.orEmpty()) {
                val su = s.jsonObject
                if (su.text("submission_status") != "AP") continue
                val tag = tagIso(su.text("submission_status_date"))
                if (tag < von || tag > bis) continue
                val typ = su.text("submission_type")
                val klasse = su.text("submission_class_code")
                val art = when {
                    typ == "ORIG" && (klasse.startsWith("TYPE 1") || o.text("application_number").startsWith("BLA")) -> Art.NEU
                    typ == "ORIG" -> Art.ORIGINAL
                    typ == "SUPPL" && klasse == "EFFICACY" -> Art.ANWENDUNG
                    else -> continue
                }
                aus.add(Zulassung(o.text("application_number"), marke, wirkstoff, art, tag, su.text("review_priority") == "PRIORITY"))
            }
        }
        return aus.distinctBy { it.marke.ifBlank { it.nummer } + it.tag + it.art }.sortedByDescending { it.tag }
    }

    fun rueckrufeLesen(text: String, namen: List<String>, geraet: Boolean): List<Rueckruf> =
        ergebnisse(text).mapNotNull { e ->
            val o = e.jsonObject
            if (!passt(o.text("recalling_firm"), namen)) return@mapNotNull null
            val klasse = when (o.text("classification").substringAfter("Class ").trim()) { "I" -> 1; "II" -> 2; "III" -> 3; else -> 0 }
            Rueckruf(
                o.text("recall_number"), klasse, o.text("product_description").replace(Regex("\\s+"), " ").trim(),
                o.text("reason_for_recall").replace(Regex("\\s+"), " ").trim(), tagIso(o.text("report_date")),
                o.text("status").equals("Ongoing", true), geraet,
            )
        }
}
