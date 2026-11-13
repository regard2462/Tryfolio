package de.troetenrepublik.app.engine

// Optionen und Terminkurve bei Rohstoffen (0.78). Optionen auf den Rohstoff selbst (Optionen auf Terminkontrakte) liefert
// die Optionsquelle der App nicht; sie fuehrt aber die Optionen auf die grossen Rohstoff-ETFs der US-Boersen. Jeder
// Rohstoff bekommt darum den ETF, der seinen Preis abbildet. Rohstoffe ohne solchen ETF haben keinen Abschnitt.
object RohstoffHandel {

    // ETF mit Optionen an den US-Boersen je Rohstoff; art: was der ETF haelt (PHYSISCH: Barren im Tresor, TERMIN:
    // Terminkontrakte, mit Rollkosten)
    enum class Art { PHYSISCH, TERMIN }

    class Etf(val kuerzel: String, val art: Art)

    private val ETF = mapOf(
        "GOLD" to Etf("GLD", Art.PHYSISCH), "SILBER" to Etf("SLV", Art.PHYSISCH),
        "PLATIN" to Etf("PPLT", Art.PHYSISCH), "PALLADIUM" to Etf("PALL", Art.PHYSISCH),
        "OEL" to Etf("USO", Art.TERMIN), "GAS" to Etf("UNG", Art.TERMIN), "KUPFER" to Etf("CPER", Art.TERMIN),
        "WEIZEN" to Etf("WEAT", Art.TERMIN), "MAIS" to Etf("CORN", Art.TERMIN), "ZUCKER" to Etf("CANE", Art.TERMIN),
        "KORB" to Etf("DBC", Art.TERMIN),
    )

    // Brent hat einen eigenen ETF
    private val ETF_SYMBOL = mapOf("BZ=F" to Etf("BNO", Art.TERMIN))

    // ETF zum Wert: zuerst nach dem Kuerzel (Brent), sonst nach dem Rohstoff; null ohne ETF mit Optionen (Kaffee, Kakao,
    // Fonds auf Minenaktien).
    fun etf(symbol: String): Etf? = ETF_SYMBOL[symbol.uppercase()] ?: Rohstoffe.schluessel(symbol)?.let { ETF[it] }

    // Wurzel und Boerse der Terminkontrakte bei der Kursquelle, etwa GC und CMX fuer GCZ26.CMX
    class Termin(val wurzel: String, val boerse: String)

    private val TERMIN = mapOf(
        "GC=F" to Termin("GC", "CMX"), "SI=F" to Termin("SI", "CMX"), "HG=F" to Termin("HG", "CMX"),
        "PL=F" to Termin("PL", "NYM"), "PA=F" to Termin("PA", "NYM"),
        "CL=F" to Termin("CL", "NYM"), "BZ=F" to Termin("BZ", "NYM"), "NG=F" to Termin("NG", "NYM"),
        "ZW=F" to Termin("ZW", "CBT"), "ZC=F" to Termin("ZC", "CBT"),
        "KC=F" to Termin("KC", "NYB"), "CC=F" to Termin("CC", "NYB"), "SB=F" to Termin("SB", "NYB"),
    )

    // Terminkontrakte nur fuer Werte, deren Kurs selbst ein Terminkontrakt ist
    fun termin(symbol: String): Termin? = TERMIN[symbol.uppercase()]

    // Kontrakt des Rohstoffs, dessen Kurve auch fuer ETCs und ETFs auf ihn gilt (Waage)
    fun terminFuer(schluessel: String): Termin? = when (schluessel) {
        "OEL" -> TERMIN["CL=F"]; "GAS" -> TERMIN["NG=F"]; "KUPFER" -> TERMIN["HG=F"]
        else -> null
    }

    // Nicht jeder Monat wird gehandelt (Getreide, Kaffee, Edelmetalle): Kandidaten ueber so viele Monate, dass mindestens
    // vier gehandelte Kontrakte darunter sind.
    const val KANDIDATEN = 16

    // Eine Zeile der Terminkurve: Kontrakt, Preis, Abstand zum Kurs der Seite in Prozent und auf ein Jahr gerechnet
    // (Eingepreist.spanne mit umgekehrtem Vorzeichen: positiv heisst, die spaetere Lieferung ist teurer).
    class Zeile(val jahr: Int, val monat: Int, val preis: Double, val abstand: Double, val aufsJahr: Double?)

    // kurve: gehandelte Kontrakte mit Schlusskurs, naechster zuerst; kurs: Kurs der Seite (naechstfaelliger Kontrakt).
    // Der erste Kontrakt ist meist der Kurs der Seite selbst; er steht als Bezug in der Liste.
    fun zeilen(kurve: List<Pair<Eingepreist.Kontrakt, Double>>, kurs: Double): List<Zeile> {
        if (kurs <= 0 || kurve.isEmpty()) return emptyList()
        val erster = kurve.first().first.nummer
        return kurve.map { (k, p) ->
            val monate = k.nummer - erster
            Zeile(k.jahr, k.monat, p, (p / kurs - 1) * 100, if (monate > 0) Eingepreist.spanne(kurs, p, monate)?.let { -it } else null)
        }
    }

    // Spanne der Kurve fuer die Waage: erster gegen letzten von bis zu vier Kontrakten (positiv: Backwardation); null bei
    // weniger als zwei Kontrakten.
    fun spanne(kurve: List<Pair<Eingepreist.Kontrakt, Double>>): Double? {
        if (kurve.size < 2) return null
        val a = kurve.first()
        val b = kurve.last()
        return Eingepreist.spanne(a.second, b.second, b.first.nummer - a.first.nummer)
    }
}
