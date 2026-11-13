package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Laenderrisiken
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Standorte einer Firma: wo sie Toechter hat und welche Laender ihre Jahresberichte nennen. Reine Funktionen zum Lesen
// der Quellen (SEC Exhibit 21 und Item 2, GLEIF Level 2, Wikidata) und zum Zusammenfuehren je Land. Der Abruf steht in
// FirmaHintergrundLader.kt.
object FirmaStandorte {

    // ------------------------------------------------------------ Laendernamen

    class Land(val a2: String, val en: String, val de: String)

    // Ordnet Rechtsordnungen und Laendernamen (englisch, deutsch, gaengige Schreibweisen, US-Staaten, kanadische
    // Provinzen, australische Staaten) einem Landeskuerzel (alpha-2) zu.
    class Aufloeser(laender: List<Land>) {
        private val karte = HashMap<String, String>()
        private val nennungen = ArrayList<Pair<String, String>>()

        init {
            for (l in laender) {
                if (l.a2.isBlank()) continue
                for (n in listOf(l.en, l.de)) {
                    karte.putIfAbsent(Laenderrisiken.norm(n), l.a2)
                    if (n.length >= 4 && n !in MEHRDEUTIG) nennungen += n to l.a2
                }
            }
            for ((a2, namen) in ALIAS) for (n in namen) {
                karte[Laenderrisiken.norm(n)] = a2
                if (n.length >= 4 && n !in MEHRDEUTIG) nennungen += n to a2
            }
            for (s in US_STAATEN) karte.putIfAbsent(Laenderrisiken.norm(s), "US")
            for (s in CA_PROVINZEN) karte.putIfAbsent(Laenderrisiken.norm(s), "CA")
            for (s in AU_STAATEN) karte.putIfAbsent(Laenderrisiken.norm(s), "AU")
        }

        private val nennungsMuster: Regex by lazy {
            val alt = nennungen.map { it.first }.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("\\b(?:$alt)\\b")
        }
        private val nennungsKarte: Map<String, String> by lazy { nennungen.toMap() }

        private fun einzeln(t: String): String? {
            val k = Laenderrisiken.norm(t)
            if (k.isEmpty()) return null
            karte[k]?.let { return it }
            val ohne = t.replace(VORSATZ, "").trim()
            if (ohne != t) karte[Laenderrisiken.norm(ohne)]?.let { return it }
            return null
        }

        // Kuerzel zu einer Rechtsordnung wie "Delaware", "Cayman Islands", "England & Wales" oder "Zug, Switzerland"
        fun a2(text: String): String? {
            val t = text.replace("&", " and ").replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', ';', ':')
            if (t.isEmpty() || t.length > 60) return null
            einzeln(t)?.let { return it }
            val teile = t.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (teile.size > 1) {
                einzeln(teile.last())?.let { return it }
                einzeln(teile.first())?.let { return it }
            }
            // "Hong Kong (SAR)": der Teil vor der Klammer zaehlt, nicht der in der Klammer ("Alpha Ltd (Japan)" ist ein Name)
            val vor = t.substringBefore('(').trim()
            if (vor.isNotEmpty() && vor != t) einzeln(vor)?.let { return it }
            return null
        }

        // Alle Laender, die ein Text mit vollem Namen nennt (Gross- und Kleinschreibung wie im Namen)
        fun genannt(text: String): Set<String> =
            nennungsMuster.findAll(text).mapNotNull { nennungsKarte[it.value] }.toSet()
    }

    private val VORSATZ = Regex("(?i)^(?:the\\s+)?(?:state|commonwealth|province|republic|kingdom|federal republic|territory|special administrative region|principality|grand duchy) of\\s+")

    // Namen, die ohne weiteren Zusammenhang auch Orte, Personen oder US-Staaten sind
    private val MEHRDEUTIG = setOf("Georgia", "Jersey", "Guinea", "Niger", "Chad", "Jordan", "Jordanien", "Georgien", "Tschad")

    private val ALIAS: Map<String, List<String>> = mapOf(
        "GB" to listOf("UK", "U.K.", "United Kingdom", "Great Britain", "England", "Scotland", "Wales", "Northern Ireland", "England and Wales", "Vereinigtes Königreich"),
        "US" to listOf("USA", "U.S.", "U.S.A.", "United States", "United States of America"),
        "CN" to listOf("PRC", "People's Republic of China", "China"), "KR" to listOf("Korea", "South Korea", "Republic of Korea", "Korea, Republic of"),
        "RU" to listOf("Russia", "Russian Federation"), "VN" to listOf("Vietnam", "Viet Nam"), "CZ" to listOf("Czech Republic", "Czechia"),
        "HK" to listOf("Hong Kong SAR", "Hong Kong", "Hong Kong, China"), "MO" to listOf("Macao", "Macau", "Macao SAR"),
        "TW" to listOf("Taiwan", "Republic of China", "Taiwan, Province of China", "Taiwan, R.O.C."),
        "AE" to listOf("UAE", "United Arab Emirates", "Dubai", "Abu Dhabi", "DIFC", "ADGM", "Ras Al Khaimah", "Sharjah"),
        "CW" to listOf("Netherlands Antilles", "Curacao", "Curaçao"), "TR" to listOf("Turkey", "Türkiye", "Turkiye"),
        "CI" to listOf("Ivory Coast", "Cote d'Ivoire", "Côte d'Ivoire"), "SK" to listOf("Slovak Republic", "Slovakia"),
        "BN" to listOf("Brunei", "Brunei Darussalam"), "LA" to listOf("Laos", "Lao PDR"), "SY" to listOf("Syria"),
        "IR" to listOf("Iran"), "MD" to listOf("Moldova"), "BO" to listOf("Bolivia"), "VE" to listOf("Venezuela"), "TZ" to listOf("Tanzania"),
        "PS" to listOf("Palestine", "West Bank"), "MK" to listOf("Macedonia", "North Macedonia"), "CV" to listOf("Cape Verde", "Cabo Verde"),
        "SZ" to listOf("Swaziland", "Eswatini"), "VG" to listOf("BVI", "British Virgin Islands", "Tortola"), "KY" to listOf("Cayman", "Cayman Islands"),
        "MY" to listOf("Labuan", "Malaysia"), "PT" to listOf("Madeira", "Azores"), "NL" to listOf("The Netherlands", "Holland"),
        "CD" to listOf("DR Congo", "Democratic Republic of the Congo", "Congo, Democratic Republic of the"), "MM" to listOf("Burma", "Myanmar"),
        "FM" to listOf("Micronesia"), "BS" to listOf("Bahamas", "The Bahamas"), "GM" to listOf("Gambia", "The Gambia"),
    )

    private val US_STAATEN = listOf(
        "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida", "Hawaii", "Idaho",
        "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota",
        "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina",
        "North Dakota", "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas",
        "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming", "District of Columbia", "Washington D.C.",
    )
    private val CA_PROVINZEN = listOf(
        "Ontario", "Quebec", "Québec", "British Columbia", "Alberta", "Manitoba", "Saskatchewan", "Nova Scotia", "New Brunswick",
        "Newfoundland and Labrador", "Prince Edward Island", "Yukon", "Northwest Territories", "Nunavut",
    )
    private val AU_STAATEN = listOf(
        "New South Wales", "Victoria", "Queensland", "Western Australia", "South Australia", "Tasmania", "Australian Capital Territory", "Northern Territory",
    )

    // ------------------------------------------------------------ Exhibit 21 (Liste der Toechter)

    @Serializable
    class Tochter(val name: String, val a2: String)

    private val SKRIPT = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val ZEILE = Regex("(?is)<tr[^>]*>(.*?)</tr>")
    private val ZELLE = Regex("(?is)<t[dh][^>]*>(.*?)</t[dh]>")
    private val TAG = Regex("(?s)<[^>]*>")
    private val KOPF = Regex("(?i)^(?:name|names|subsidiar|jurisdiction|state or|country|place of|exhibit|list of|legal entity|entity|incorporat|organized|\\d+\\s*$|page\\s|\\*|percent|ownership|%)")

    private fun entitaeten(s: String): String =
        s.replace("&nbsp;", " ").replace("&#160;", " ").replace("&#xa0;", " ").replace(" ", " ").replace("&amp;", "&")
            .replace("&#38;", "&").replace("&#8217;", "'").replace("&#8211;", "-").replace("&#8212;", "-").replace("&quot;", "\"")
            .replace("&lt;", "<").replace("&gt;", ">")

    private fun zelle(html: String): String = entitaeten(TAG.replace(html, " ")).replace(Regex("\\s+"), " ").trim()

    private fun nameOk(s: String): Boolean = s.length in 3..160 && s.any { it.isLetter() } && !KOPF.containsMatchIn(s)

    // Liest die Liste der Tochtergesellschaften: je Zeile Name und Rechtsordnung, auch als Abschnitte (Land als
    // Zwischenueberschrift) oder als "Name (Land)". Mehrfache Namen zaehlen einmal.
    fun tochterListe(dokument: String, a: Aufloeser): List<Tochter> {
        val roh = SKRIPT.replace(dokument, " ")
        val zeilen: List<List<String>> =
            if (Regex("(?i)<tr[\\s>]").containsMatchIn(roh)) {
                ZEILE.findAll(roh).map { r -> ZELLE.findAll(r.groupValues[1]).map { zelle(it.groupValues[1]) }.filter { it.isNotEmpty() && it != "$" }.toList() }.toList()
            } else {
                val text = roh.replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>"), "\n")
                entitaeten(TAG.replace(text, " ")).lines().map { l -> l.trim().split(Regex("\\s{2,}|\\t|\\.{3,}")).map { it.trim() }.filter { it.isNotEmpty() } }
            }
        val aus = ArrayList<Tochter>()
        var abschnitt: String? = null
        for (z in zeilen) {
            if (z.isEmpty()) continue
            val ordnung = (z.size - 1 downTo 1).firstOrNull { a.a2(z[it]) != null }
            if (ordnung != null) {
                val name = z.first()
                if (nameOk(name) && a.a2(name) == null) aus += Tochter(name, a.a2(z[ordnung])!!)
                continue
            }
            if (z.size == 1) {
                val t = z[0]
                val land = a.a2(t)
                if (land != null && t.length < 45) { abschnitt = land; continue }
                // "Name (Land)" oder "Name - Land"
                val m = Regex("^(.+?)\\s*(?:\\(([^()]+)\\)|\\s[-–—]\\s+([^-–—]+))\\s*$").find(t)
                val l2 = m?.let { a.a2(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
                if (m != null && l2 != null && nameOk(m.groupValues[1])) { aus += Tochter(m.groupValues[1].trim(), l2); continue }
                if (abschnitt != null && nameOk(t)) aus += Tochter(t, abschnitt)
            } else if (abschnitt != null && nameOk(z[0]) && z.drop(1).all { it.length < 12 }) {
                aus += Tochter(z[0], abschnitt)
            }
        }
        return aus.distinctBy { it.name.lowercase().replace(Regex("[^a-z0-9]"), "") + "|" + it.a2 }
    }

    // Datei der Liste im Einreichungsordner (index.json): form 10-K Exhibit 21, 20-F Exhibit 8
    fun listenDatei(indexJson: String, form: String): String? = runCatching {
        val namen = Json.parseToJsonElement(indexJson).jsonObject["directory"]?.jsonObject?.get("item")?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
            .filter { it.endsWith(".htm", true) || it.endsWith(".html", true) || it.endsWith(".txt", true) }
        val muster = if (form.startsWith("20-F")) Regex("(?i)ex(?:hibit)?[-_ ]?8(?:[-_.]?0?1)?(?!\\d)|exhibit[-_ ]?8") else Regex("(?i)ex(?:hibit)?[-_ ]?21(?!\\d{2})|exhibit[-_ ]?21")
        namen.firstOrNull { muster.containsMatchIn(it) && !Regex("(?i)ex[-_]?(?:23|31|32|101)").containsMatchIn(it) }
    }.getOrNull()

    // ------------------------------------------------------------ Item 2 (Properties)

    private const val ABSTAND = "(?:&#160;|&nbsp;|&#xa0;|\\s|<[^>]*>|[.:\\u2013\\u2014-])*"
    private val KOPF_ITEM2 = Regex("(?i)(?:>|^)\\s*item$ABSTAND" + "2$ABSTAND" + "properties")
    private val KOPF_ITEM3 = Regex("(?i)(?:>|^)\\s*item$ABSTAND" + "3$ABSTAND" + "legal$ABSTAND" + "proceedings")

    // Ist der Abschnitt Item 2 im bisher gelesenen HTML vollstaendig? Wahr, wenn nach einer echten Ueberschrift
    // "Item 2. Properties" (nicht im Inhaltsverzeichnis) die Ueberschrift "Item 3. Legal Proceedings" folgt.
    fun item2Text(html: String): String? {
        val zwei = KOPF_ITEM2.findAll(html).map { it.range.first }.toList()
        val drei = KOPF_ITEM3.findAll(html).map { it.range.first }.toList()
        for (z in zwei.reversed()) {
            val d = drei.firstOrNull { it > z } ?: continue
            val text = zelle(html.substring(z, d)).replace(Regex("(?i)^>?\\s*item\\s*2\\.?\\s*properties\\.?"), "").trim()
            if (text.length >= 120) return text.take(30_000)
        }
        return null
    }

    // ------------------------------------------------------------ GLEIF Level 2

    fun kinderAdresse(lei: String, seite: Int) = "${Gleif.BASIS}/lei-records/$lei/ultimate-children?page%5Bsize%5D=200&page%5Bnumber%5D=$seite"

    class Kinder(val laender: Map<String, Int>, val gesamt: Int, val letzteSeite: Int)

    fun kinderLesen(text: String): Kinder? = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val daten = o["data"] as? JsonArray ?: return null
        val je = HashMap<String, Int>()
        for (d in daten) {
            val e = d.jsonObject["attributes"]?.jsonObject?.get("entity")?.jsonObject ?: continue
            val land = e["legalAddress"]?.jsonObject?.get("country")?.jsonPrimitive?.contentOrNull?.uppercase()?.takeIf { it.length == 2 } ?: continue
            je[land] = (je[land] ?: 0) + 1
        }
        val p = o["meta"]?.jsonObject?.get("pagination")?.jsonObject
        Kinder(je, p?.get("total")?.jsonPrimitive?.intOrNull ?: daten.size, p?.get("lastPage")?.jsonPrimitive?.intOrNull ?: 1)
    }.getOrNull()

    // ------------------------------------------------------------ Zusammenfuehren

    @Serializable
    class Daten(
        val abgerufen: Long = 0L,
        // Sitzland (alpha-2), leer ohne Angabe
        val sitz: String = "",
        // Toechter je Land nach Quelle
        val ex21: Map<String, Int> = emptyMap(),
        val gleif: Map<String, Int> = emptyMap(),
        val wikidata: Map<String, Int> = emptyMap(),
        // Laender, die Item 2 des Jahresberichts nennt
        val item2: List<String> = emptyList(),
        // Berichtsart und Einreichung der Exhibit-Liste, leer ohne Bericht
        val bericht: String = "",
        val berichtTag: String = "",
        // Tatsaechliche Zahl der Toechter laut GLEIF (kann ueber den gelesenen Seiten liegen)
        val gleifGesamt: Int = 0,
    )

    // Toechter je Land: der hoechste Wert der Quellen, damit dieselben Firmen nicht doppelt zaehlen
    fun toechter(d: Daten): Map<String, Int> {
        val alle = HashSet<String>().apply { addAll(d.ex21.keys); addAll(d.gleif.keys); addAll(d.wikidata.keys) }
        return alle.associateWith { maxOf(d.ex21[it] ?: 0, d.gleif[it] ?: 0, d.wikidata[it] ?: 0) }.filterValues { it > 0 }
    }

    // Laender mit Standort: Toechter, Sitz und Laender aus Item 2
    // Item 2 zaehlt erst ab zwei genannten Laendern; ein einzelnes sagt nichts ueber die Standorte
    fun item2(d: Daten): List<String> = d.item2.takeIf { it.size >= 2 }.orEmpty()

    fun standorte(d: Daten): Set<String> = (toechter(d).keys + item2(d) + listOfNotNull(d.sitz.ifBlank { null })).toSet()

    fun leer(d: Daten): Boolean = standorte(d).isEmpty()

    // Laender, die der Rat der EU am 9. Oktober 2026 als nicht kooperative Steuergebiete fuehrt (Anhang I der Liste)
    const val STEUER_STAND = "2026-10-09"
    val STEUERLISTE: Set<String> = setOf("AS", "AI", "GU", "PW", "RU", "TC", "VI", "VU")

    // Hohes Risiko gilt hier nach LandRisiko.hoch (Regierungsfuehrung im untersten Viertel, Krieg bei unterdurchschnittlicher
    // Regierungsfuehrung, Reisehinweis Stufe 4 oder mindestens drei Sanktionsregime). Eine reine Punktegrenze traf auch grosse
    // Schwellenlaender (Indien, Brasilien, Mexiko, China, Israel), die nur mittlere Regierungsfuehrung haben.

    class Risiko(val tochterGesamt: Int, val tochterHoch: Int, val laenderHoch: List<String>, val tochterOhneWert: Int)

    // Anteil der Toechter in Laendern mit hohem Landrisiko. Laender ohne Wert (null) zaehlen nicht mit.
    fun risiko(t: Map<String, Int>, hoch: (String) -> Boolean?): Risiko {
        var gesamt = 0; var hochN = 0; var ohne = 0
        val laender = ArrayList<String>()
        for ((a2, n) in t) {
            val h = hoch(a2)
            if (h == null) { ohne += n; continue }
            gesamt += n
            if (h) { hochN += n; laender += a2 }
        }
        return Risiko(gesamt, hochN, laender.sortedByDescending { t[it] ?: 0 }, ohne)
    }


    // Eine Zeile je Land fuer Liste und Globus: Toechter, Sitz, Standort (Item 2), hohes Risiko, Steuergebiet
    class Zeile(val a2: String, val name: String, val toechter: Int, val standort: Boolean, val sitz: Boolean, val hoch: Boolean, val steuer: Boolean)

    // Laender aus Item 2 und der Sitz zusammen
    fun standortLaender(d: Daten): Set<String> = (item2(d) + listOfNotNull(d.sitz.ifBlank { null })).toSet()

    // Zuerst Laender mit Toechtern (meiste zuerst), dann reine Standorte nach Name
    fun zeilen(d: Daten, name: (String) -> String, hoch: (String) -> Boolean?): List<Zeile> {
        val t = toechter(d)
        return standorte(d).map { a2 ->
            Zeile(a2, name(a2), t[a2] ?: 0, a2 in item2(d), a2 == d.sitz, hoch(a2) == true, a2 in STEUERLISTE)
        }.sortedWith(compareByDescending<Zeile> { it.toechter }.thenBy { it.name.lowercase() })
    }

    fun steuerToechter(t: Map<String, Int>): Int = t.filterKeys { it in STEUERLISTE }.values.sum()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun schreiben(d: Daten): String = json.encodeToString(Daten.serializer(), d)

    fun lesen(text: String): Daten? = runCatching { json.decodeFromString(Daten.serializer(), text) }.getOrNull()
}
