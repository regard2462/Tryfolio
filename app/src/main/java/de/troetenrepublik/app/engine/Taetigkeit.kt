package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable

// [Globus Depot 0.70] Sitz, Boerse und Taetigkeitsland einer Firma. Fuer die Laenderstreuung des Depots zaehlt, wo die
// Firma wirtschaftet: Holding-Sitze (Mauritius, Kaimaninseln, Bermuda ...) mit Taetigkeit anderswo gehen ins Taetigkeitsland.
// Quelle des Taetigkeitslands ist die Unternehmensbeschreibung (Yahoo, englisch); Regeln siehe recherche-e/herkunft.md.
object Taetigkeit {
    // Sitz, Taetigkeitsland (null: wie Sitz oder unbekannt) und Land der Boerse (alpha-2). land ist das Land fuer die Streuung.
    // weltweit: kein Land ueberwiegt; taetig ist dann das Land der operativen Zentrale
    data class Herkunft(val sitz: String?, val taetig: String?, val boerse: String?, val weltweit: Boolean = false) {
        val land: String? get() = taetig ?: sitz
        // Sitz und Taetigkeitsland fallen auseinander
        val abweichend: Boolean get() = taetig != null && sitz != null && taetig != sitz
    }

    // Eintrag im Zwischenspeicher (herkunft.json): z = Zeitpunkt der Berechnung in ms
    @Serializable
    data class Eintrag(val s: String? = null, val t: String? = null, val z: Long = 0L)

    // Laender, in denen Holdings sitzen, deren Geschaefte anderswo liegen (Steuer- und Finanzplaetze)
    val HOLDING_SITZE: Set<String> = setOf("MU", "KY", "BM", "VG", "JE", "GG", "LU", "IM", "GI", "BS", "PA", "MH", "LI", "CY", "CW", "AI", "SC", "LR")

    // Bei Rohstofffirmen liegt der Wert an der Lagerstaette, nicht am Sitz: Yahoo-Sektor oder Katalogbranche
    private val RESSOURCEN_SEKTOREN = setOf("Basic Materials", "Energy")
    private val RESSOURCEN_BRANCHEN = setOf("BERGBAU", "OEL_GAS", "ROHSTOFFE")
    fun ressource(sektor: String?, katalogBranche: String?): Boolean =
        sektor in RESSOURCEN_SEKTOREN || katalogBranche in RESSOURCEN_BRANCHEN

    // Englische Laendernamen (und gebraeuchliche Schreibweisen) zu alpha-2
    class Namen(namen: Map<String, String>) {
        private val karte: Map<String, String> = (namen + ALIASE).filterKeys { it !in AUSGESCHLOSSEN }
        private val muster: Regex? = karte.keys.sortedByDescending { it.length }.takeIf { it.isNotEmpty() }?.let { k ->
            Regex("(?:^|[^\\p{L}])(" + k.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}])")
        }

        fun code(name: String): String? = karte[name.trim()] ?: karte.entries.firstOrNull { it.key.equals(name.trim(), ignoreCase = true) }?.value

        // Alle Nennungen in der Reihenfolge ihres Auftretens: Land und Ort im Text. Laengere Namen gehen vor
        // (Democratic Republic of the Congo vor Congo); Gulf of Mexico, New Mexico, New Jersey und Niger Delta zaehlen nicht.
        fun nennungen(text: String): List<Pair<String, Int>> {
            val m = muster ?: return emptyList()
            return m.findAll(text).mapNotNull { tr ->
                val r = tr.groups[1]!!
                val name = r.value
                val von = r.range.first
                val vor = text.substring(maxOf(0, von - 8), von)
                val nach = text.substring(r.range.last + 1, minOf(text.length, r.range.last + 8))
                val unecht = (name == "Mexico" && (vor.endsWith("Gulf of ") || vor.endsWith("New "))) ||
                    (name == "Jersey" && vor.endsWith("New ")) || (name == "Niger" && nach.startsWith(" Delta"))
                if (unecht) null else karte.getValue(name) to von
            }.toList()
        }

        companion object {
            // Namen, die als Wort meist etwas anderes sind
            private val AUSGESCHLOSSEN = setOf("Georgia", "Chad", "Guinea")
            private val ALIASE = mapOf(
                "United States" to "US", "United States of America" to "US", "U.S." to "US", "USA" to "US",
                "UK" to "GB", "U.K." to "GB", "Great Britain" to "GB", "England" to "GB", "Scotland" to "GB",
                "Democratic Republic of Congo" to "CD", "Democratic Republic of the Congo" to "CD", "DR Congo" to "CD", "DRC" to "CD",
                "Congo-Kinshasa" to "CD", "Congo" to "CD", "Republic of Congo" to "CG", "Congo-Brazzaville" to "CG",
                "Republic of the Congo" to "CG", "Czechia" to "CZ", "Türkiye" to "TR", "Korea" to "KR", "Republic of Korea" to "KR",
                "People's Republic of China" to "CN", "PRC" to "CN", "Mainland China" to "CN", "Russian Federation" to "RU",
                "Côte d'Ivoire" to "CI", "Cote d'Ivoire" to "CI", "Netherlands" to "NL", "The Netherlands" to "NL",
                "Hong Kong SAR" to "HK", "Macao" to "MO", "UAE" to "AE", "Burma" to "MM", "Swaziland" to "SZ",
            )
        }
    }

    // Wortanfaenge, die eine Nennung als Geschaeftsort kennzeichnen (Mine, Projekt, Werk ...) und die einen Sitz oder eine
    // Notierung kennzeichnen (dann zaehlt die Nennung nicht als Taetigkeit)
    private val BETRIEB = Regex("(?i)(operat|\\bmine|mining|project|located|assets|produc|explor|concession|licen|deposit|\\bfield|plant|facilit|refiner|subsidiar|\\bsite|\\brig\\b|offshore|\\bblock|farm|plantation|smelter|pipeline|develop|manufactur|stores|branches|customers|clients)")
    private val SITZ = Regex("(?i)(listed on|listed in|incorporated|headquartered|registered|domiciled|based in|exchange in|stock exchange|formerly|originally|founded in|established in)")

    // Gewicht je Land: Nennungen, Betriebsnennungen zaehlen doppelt
    private class Gewicht(var summe: Int = 0, var betrieb: Int = 0)

    private fun gewichte(beschreibung: String, namen: Namen): Map<String, Gewicht> {
        val aus = LinkedHashMap<String, Gewicht>()
        for ((code, pos) in namen.nennungen(beschreibung)) {
            val vor = beschreibung.substring(maxOf(0, pos - 70), pos)
            // Satzanfang abschneiden: nur der Satz der Nennung zaehlt fuer die Einordnung
            val satz = vor.substringAfterLast(". ")
            if (SITZ.containsMatchIn(satz.takeLast(40))) continue
            val g = aus.getOrPut(code) { Gewicht() }
            g.summe++
            if (BETRIEB.containsMatchIn(satz)) g.betrieb++
        }
        return aus
    }

    // Taetigkeitsland nach der Beschreibung; null, wenn der Sitz gilt.
    // Holding-Sitz: das Land, das die Beschreibung klar am staerksten nennt (mindestens doppelt so stark wie das zweite).
    // Rohstofffirma mit Sitz anderswo: nur bei einem einzigen Land mit Betriebsnennung, wenn das Sitzland nirgends genannt ist.
    fun taetigkeitsland(sitz: String?, beschreibung: String, ressource: Boolean, namen: Namen): String? {
        if (beschreibung.isBlank()) return null
        val alle = gewichte(beschreibung, namen)
        val fremd = alle.filterKeys { it != sitz && it !in HOLDING_SITZE }
        if (fremd.isEmpty()) return null
        fun punkte(g: Gewicht) = g.summe + g.betrieb
        val reihe = fremd.entries.sortedByDescending { punkte(it.value) }
        val bester = reihe.first()
        val zweiter = reihe.getOrNull(1)
        if (zweiter != null && punkte(bester.value) < 2 * punkte(zweiter.value)) return null
        if (sitz != null && sitz in HOLDING_SITZE) return bester.key
        if (sitz != null && ressource && bester.value.betrieb >= 1 && sitz !in alle) return bester.key
        return null
    }

    // Herkunft: Sitz (Katalog oder Profil), Taetigkeitsland und Boerse
    fun herkunft(sitz: String?, symbol: String, beschreibung: String, ressource: Boolean, namen: Namen): Herkunft =
        Herkunft(sitz, taetigkeitsland(sitz, beschreibung, ressource, namen), boerse(symbol))

    // [0.73] Belegtes Tätigkeitsland je Kürzel aus assets/globus/taetigkeit.json: {"stand":"2026-11","w":{"CRH":"US"}}
    fun lesenDatei(text: String): Map<String, String> = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(text).let { e ->
            (e as kotlinx.serialization.json.JsonObject)["w"]?.let { w ->
                (w as kotlinx.serialization.json.JsonObject).mapNotNull { (k, v) ->
                    (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.length == 2 }?.let { k to it }
                }.toMap()
            }
        }
    }.getOrNull().orEmpty()

    // Zentralen weltweit verteilter Firmen: "z" in taetigkeit.json, Kuerzel zu Land der operativen Zentrale
    fun lesenZentralen(text: String): Map<String, String> = runCatching {
        (kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject)["z"]?.let { w ->
            (w as kotlinx.serialization.json.JsonObject).mapNotNull { (k, v) ->
                (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.length == 2 }?.let { k to it }
            }.toMap()
        }
    }.getOrNull().orEmpty()

    // Reihenfolge: eigene Wahl der Person, belegtes Land der Datendatei, Beschreibung (Textanalyse). Steht der Sitz in einem
    // Holding- oder Registerland und ergibt sich sonst nichts, gilt das Land der Hauptbörse.
    fun veredeln(
        h: Herkunft, symbol: String, datei: Map<String, String> = emptyMap(), wahl: String? = null, zentralen: Map<String, String> = emptyMap(),
    ): Herkunft {
        if (wahl != null) return Herkunft(h.sitz, wahl, h.boerse)
        datei[symbol]?.let { return Herkunft(h.sitz, it, h.boerse) }
        zentralen[symbol]?.let { return Herkunft(h.sitz, it, h.boerse, weltweit = true) }
        val b = h.boerse
        if (h.taetig == null && h.sitz != null && h.sitz in HOLDING_SITZE && b != null && b != h.sitz) return Herkunft(h.sitz, b, b)
        return h
    }

    fun herkunftErweitert(
        sitz: String?, symbol: String, beschreibung: String, ressource: Boolean, namen: Namen,
        datei: Map<String, String> = emptyMap(), wahl: String? = null,
    ): Herkunft = veredeln(herkunft(sitz, symbol, beschreibung, ressource, namen), symbol, datei, wahl)

    private val BOERSEN = mapOf(
        "V" to "CA", "TO" to "CA", "CN" to "CA", "NE" to "CA", "DE" to "DE", "F" to "DE", "SG" to "DE", "BE" to "DE", "DU" to "DE", "HM" to "DE",
        "MU" to "DE", "HA" to "DE", "L" to "GB", "IL" to "GB", "PA" to "FR", "AS" to "NL", "MI" to "IT", "MC" to "ES", "SW" to "CH", "ST" to "SE",
        "HE" to "FI", "CO" to "DK", "OL" to "NO", "VI" to "AT", "BR" to "BE", "LS" to "PT", "IR" to "IE", "AX" to "AU", "NZ" to "NZ", "HK" to "HK",
        "T" to "JP", "KS" to "KR", "KQ" to "KR", "TW" to "TW", "TWO" to "TW", "SS" to "CN", "SZ" to "CN", "NS" to "IN", "BO" to "IN", "SI" to "SG",
        "JO" to "ZA", "SA" to "BR", "MX" to "MX", "TA" to "IL", "IS" to "TR", "WA" to "PL", "PR" to "CZ", "AT" to "GR", "KL" to "MY", "BK" to "TH",
        "JK" to "ID", "SR" to "SA", "CR" to "VE",
    )

    // Land der Boerse nach der Kuerzelendung (AFM.V = Toronto Venture); ohne Endung die US-Boersen
    fun boerse(symbol: String): String? {
        if (symbol.startsWith("^") || symbol.contains("=")) return null
        if (symbol.endsWith("-USD") || symbol.endsWith("-EUR")) return null
        // Werte weiterer Kursquellen: Moskauer Boerse
        if (symbol.startsWith("MOEX:")) return "RU"
        val ende = symbol.substringAfterLast('.', "")
        if (ende.isEmpty()) return "US"
        return BOERSEN[ende.uppercase()]
    }
}
