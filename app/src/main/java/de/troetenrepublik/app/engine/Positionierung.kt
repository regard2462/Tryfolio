package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Positionierung an den Terminmaerkten (seit 5.8): Commitments of Traders der U.S. Commodity Futures Trading Commission
// (CFTC), woechentlich, gemeinfrei, ohne Anmeldung. Gezeigt wird die Nettoposition grosser Spekulanten (Non-Commercials)
// in Prozent der offenen Kontrakte und wo sie im Vergleich zu den letzten drei Jahren steht (COT-Index 0 bis 100).
// Seit 0.78 mit dem ganzen Archiv ab 1986 (assets/indikatoren/cot_archiv.json, tools/historie/holen.py cot) und allen
// Maerkten, die die App sonst zeigt (Indizes, Anleihen, Waehrungen, Metalle, Energie, Agrar, Bitcoin): Rang in der ganzen
// Geschichte und „so stark netto long wie seit …“. Beim Nutzer kommen nur die Wochen nach dem Archiv dazu.
object Positionierung {
    // Schluessel und CFTC-Code des Kontrakts (Reihenfolge der Anzeige; dieselbe Liste steht in tools/historie/holen.py).
    val MAERKTE = listOf(
        "sp500" to "13874A", "nasdaq" to "209742", "gold" to "088691", "oel" to "067651",
        "euro" to "099741", "anleihen" to "043602", "bitcoin" to "133741",
        "anleihen2" to "042601", "anleihen30" to "020601",
        "yen" to "097741", "pfund" to "096742", "franken" to "092741", "kanada" to "090741", "australien" to "232741",
        "silber" to "084691", "platin" to "076651", "palladium" to "075651", "kupfer" to "085692",
        "gas" to "023651", "weizen" to "001602", "mais" to "002602", "kaffee" to "083731", "kakao" to "073732", "zucker" to "080732",
    )

    // Die ersten Maerkte stehen offen, die uebrigen eingeklappt
    const val OFFEN = 7

    const val ARCHIV = "indikatoren/cot_archiv.json"
    const val SEITE = "https://www.cftc.gov/MarketReports/CommitmentsofTraders/index.htm"

    // Wochen fuer den Vergleich mit drei Jahren (Index 0 bis 100 wie bisher)
    const val DREI_JAHRE = 160

    // Ohne neue Wochen aus dem Netz gilt das Archiv nur so lange (Tage nach seiner letzten Woche)
    const val ARCHIV_GUELTIG_TAGE = 60

    const val QUELLE = "U.S. Commodity Futures Trading Commission, Commitments of Traders (publicreporting.cftc.gov)"

    private const val TAG_MS = 86_400_000L

    // Alle Wochen nach dem Tag seit (JJJJ-MM-TT) fuer mehrere Maerkte in einer Abfrage, aelteste zuerst
    fun adresseSeit(codes: List<String>, seit: java.time.LocalDate): String =
        "https://publicreporting.cftc.gov/resource/6dca-aqww.json?%24where=cftc_contract_market_code%20in(" +
            codes.joinToString(",") { "%27$it%27" } + ")%20AND%20report_date_as_yyyy_mm_dd%20%3E%20%27$seit%27" +
            "&%24order=report_date_as_yyyy_mm_dd%20ASC&%24limit=5000" +
            "&%24select=cftc_contract_market_code,report_date_as_yyyy_mm_dd,open_interest_all,noncomm_positions_long_all,noncomm_positions_short_all"

    // Antwort von adresseSeit je CFTC-Code, aelteste zuerst
    fun lesenMehrere(text: String): Map<String, List<Woche>> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val code = e.jsonObject["cftc_contract_market_code"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            val w = lesen("[$e]").singleOrNull() ?: return@mapNotNull null
            code to w
        }.groupBy({ it.first }, { it.second }).mapValues { (_, l) -> l.distinctBy { it.tag }.sortedBy { it.tag } }
    }.getOrDefault(emptyMap())

    // Archiv: je Schluessel (Zeit in ms, Netto in Prozent der offenen Kontrakte), aelteste zuerst. Die Datei fuehrt je Markt
    // den ersten Tag (ab), Ausnahmen vom Wochenabstand ([Platz, Tage seit dem Vorwert], sonst 7 Tage) und den Anteil in Promille.
    fun archivLesen(text: String): Map<String, List<Pair<Long, Double>>> = runCatching {
        val m = (Json.parseToJsonElement(text).jsonObject["maerkte"] ?: return emptyMap()).jsonObject
        m.mapNotNull { (k, v) ->
            val o = v.jsonObject
            val ab = o["ab"]?.jsonPrimitive?.contentOrNull?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: return@mapNotNull null
            val a = o["a"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null } ?: return@mapNotNull null
            val ausnahmen = o["x"]?.jsonArray.orEmpty().mapNotNull { p ->
                val paar = p.jsonArray
                val i = paar.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                val d = paar.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                i to d
            }.toMap()
            var tag = ab
            val reihe = a.mapIndexed { i, w ->
                if (i > 0) tag += ausnahmen[i] ?: 7
                tag * TAG_MS to w / 10.0
            }
            if (reihe.isEmpty()) null else k to reihe
        }.toMap()
    }.getOrDefault(emptyMap())

    // Mitgeliefertes Archiv, einmal gelesen
    val archiv: Map<String, List<Pair<Long, Double>>> by lazy {
        runCatching { archivLesen(de.troetenrepublik.app.market.Daten.text(ARCHIV)) }.getOrDefault(emptyMap())
    }

    // Archiv und neue Wochen zusammen; null, wenn zu wenig da ist oder das Archiv ohne neue Wochen zu alt ist.
    fun lageLang(schluessel: String, alt: List<Pair<Long, Double>>, neu: List<Woche>, jetzt: Long): Lage? {
        val ende = alt.lastOrNull()?.first ?: Long.MIN_VALUE
        val reihe = alt + neu.filter { it.tag > ende && it.offen > 0 }.map { it.tag to it.anteil }
        if (reihe.size < 10) return null
        if (neu.isEmpty() && jetzt - reihe.last().first > ARCHIV_GUELTIG_TAGE * TAG_MS) return null
        val drei = reihe.takeLast(DREI_JAHRE)
        val werte = drei.map { it.second }
        val jetztWert = werte.last()
        val min = werte.min()
        val max = werte.max()
        val index = if (max > min) Math.round((jetztWert - min) / (max - min) * 100).toInt() else 50
        // Ganzes Archiv: vollstaendig, wenn es der Datei entstammt (sonst nur ein Ausschnitt)
        val voll = alt.isNotEmpty()
        return Lage(
            schluessel, jetztWert, werte[werte.size - 2], index, drei, reihe.last().first,
            rang = if (voll) StandSeit.rang(reihe) else null, ab = reihe.first().first,
            seit = StandSeit.pruefen(reihe, vollstaendig = voll), lang = if (voll) reihe else emptyList(),
        )
    }

    fun adresse(code: String): String =
        "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code=$code" +
            "&%24order=report_date_as_yyyy_mm_dd%20DESC&%24limit=160" +
            "&%24select=report_date_as_yyyy_mm_dd,open_interest_all,noncomm_positions_long_all,noncomm_positions_short_all"

    data class Woche(val tag: Long, val offen: Double, val lang: Double, val kurz: Double) {
        // Nettoposition der Spekulanten in Prozent der offenen Kontrakte.
        val anteil: Double get() = if (offen > 0) (lang - kurz) / offen * 100 else 0.0
    }

    // Wochen aus der Antwort, aelteste zuerst.
    fun lesen(text: String): List<Woche> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            fun z(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val tag = o["report_date_as_yyyy_mm_dd"]?.jsonPrimitive?.contentOrNull?.take(10)
                ?.let { runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            Woche(tag, z("open_interest_all") ?: return@mapNotNull null, z("noncomm_positions_long_all") ?: 0.0, z("noncomm_positions_short_all") ?: 0.0)
        }.sortedBy { it.tag }
    }.getOrDefault(emptyList())

    data class Lage(
        val schluessel: String,
        val anteil: Double,
        val vorwoche: Double,
        // 0: so pessimistisch wie nie in drei Jahren, 100: so optimistisch wie nie.
        val index: Int,
        val verlauf: List<Pair<Long, Double>>,
        val stand: Long,
        // Seit 0.78 aus dem Archiv: Anteil aller Wochen seit ab (Prozent), die hoechstens so hoch lagen, „so stark wie seit …“
        // und die ganze Reihe fuer den Verlauf (leer ohne Archiv)
        val rang: Int? = null,
        val ab: Long = 0L,
        val seit: StandSeit.Ergebnis? = null,
        val lang: List<Pair<Long, Double>> = emptyList(),
    )

    fun lage(schluessel: String, wochen: List<Woche>): Lage? {
        if (wochen.size < 10) return null
        val werte = wochen.map { it.anteil }
        val jetzt = werte.last()
        val min = werte.min()
        val max = werte.max()
        val index = if (max > min) Math.round((jetzt - min) / (max - min) * 100).toInt() else 50
        return Lage(schluessel, jetzt, werte[werte.size - 2], index, wochen.map { it.tag to it.anteil }, wochen.last().tag)
    }
}
