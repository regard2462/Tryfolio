package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.InsiderTrend
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Insidergeschaefte weiterer europaeischer Laender (seit 5.8), zuerst Schweden: das oeffentliche Insynsregister der
// Finansinspektionen (Meldungen nach Art. 19 MAR), als CSV ohne Anmeldung. Der App liegt ein Stand bei
// (tools/schweden/holen.py, assets/copytrading/insider_se.json); neuere Meldungen holt sie ueber denselben Export.
object EuInsider {
    const val QUELLE_SE = "Finansinspektionen, Insynshandel (fi.se)"

    // Laender mit eigenem Register (seit 5.9 mehr als Schweden).
    val LAENDER = listOf("SE", "FR", "NL", "GB", "CH")

    // Land, dessen Register eine Aktie mit dieser ISIN wahrscheinlich fuehrt (zum Nachladen, bevor sie bekannt ist).
    fun landFuerIsin(isin: String): String? = when (isin.take(2)) {
        "SE" -> "SE"
        "FR" -> "FR"
        "NL" -> "NL"
        "GB", "JE", "GG", "IM" -> "GB"
        "CH" -> "CH"
        else -> null
    }

    @Serializable
    data class Geschaeft(
        // Emittent, ISIN, meldende Person und (bei Nahestehenden) die Fuehrungsperson, fuer die gemeldet wird.
        val e: String,
        val isin: String,
        val p: String,
        val fuer: String = "",
        // Rolle (S Spitze, F Fuehrung, A Aufsicht, N nahestehend) und Funktion im Wortlaut.
        val r: String = "N",
        val t: String = "",
        // Art des Instruments; Art des Geschaefts K Kauf, V Verkauf, S Sonstiges (Zeichnung, Zuteilung, Schenkung ...).
        val i: String = "",
        val art: String = "S",
        // Tag des Geschaefts und der Veroeffentlichung, JJJJ-MM-TT.
        val d: String,
        val v: String = "",
        val n: Double = 0.0,
        val preis: Double = 0.0,
        val wert: Double = 0.0,
        val w: String = "",
        val o: String = "",
        // Kennung der Meldung und Link zum Original (seit 5.9, Frankreich, Niederlande, Grossbritannien und weitere).
        val id: String = "",
        val q: String = "",
        // Kennung an der Heimatboerse (NSE-Symbol oder BSE-Code, HKEX-Code, ASX-Kuerzel) fuer Laender ohne ISIN in der Quelle.
        val c: String = "",
    ) {
        val kauf: Boolean get() = art == "K"
        val verkauf: Boolean get() = art == "V"
        val schluessel: String get() = if (id.isNotEmpty()) "$id|$isin|$d|$art|$n" else "$p|$isin|$d|$art"
        val rolle: InsiderTrend.Rolle
            get() = when (r) {
                "S" -> InsiderTrend.Rolle.SPITZE
                "F" -> InsiderTrend.Rolle.FUEHRUNG
                "A" -> InsiderTrend.Rolle.AUFSICHT
                else -> InsiderTrend.Rolle.NAHE
            }
    }

    @Serializable
    data class Datei(
        val stand: String = "",
        val quelle: String = QUELLE_SE,
        val geschaefte: List<Geschaeft> = emptyList(),
        val abgerufen: Long = 0L,
        // Land der Quelle (seit 5.9) und Kennungen schon gelesener Meldungen, auch solcher ohne Geschaeft.
        val land: String = "SE",
        val gelesen: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // ------------------------------------------------------------ Schweden: Export der FI

    fun exportAdresse(von: String, bis: String): String =
        "https://marknadssok.fi.se/Publiceringsklient/sv-SE/Search/Search?SearchFunctionType=Insyn&Publiceringsdatum.From=$von" +
            "&Publiceringsdatum.To=$bis&button=export"

    // Der Export ist UTF-16, teils ohne Byte-Order-Mark.
    fun text(roh: ByteArray): String = when {
        roh.size >= 2 && roh[0] == 0xFF.toByte() && roh[1] == 0xFE.toByte() -> String(roh, 2, roh.size - 2, Charsets.UTF_16LE)
        roh.size >= 2 && roh[0] == 0xFE.toByte() && roh[1] == 0xFF.toByte() -> String(roh, 2, roh.size - 2, Charsets.UTF_16BE)
        roh.take(200).any { it == 0.toByte() } -> String(roh, Charsets.UTF_16LE)
        else -> String(roh, Charsets.UTF_8)
    }

    fun rolleSchweden(befattning: String, naerstaende: String): String {
        if (naerstaende.trim().equals("ja", true)) return "N"
        val b = befattning.lowercase().replace('\u00A0', ' ')
        return when {
            ("verkställande direktör" in b && "vice" !in b) || "finanschef" in b || "ekonomichef" in b -> "S"
            "styrelse" in b || "arbetstagarrepresentant" in b || "revisor" in b -> "A"
            else -> "F"
        }
    }

    private fun zahl(s: String): Double? = s.replace('\u00A0', ' ').replace(" ", "").replace(",", ".").toDoubleOrNull()

    // Aktuelle Meldungen des Exports, zusammengefasst je Person, ISIN, Tag und Art.
    fun schwedenLesen(text: String): List<Geschaeft> {
        val zeilen = de.troetenrepublik.app.engine.TradeRepublicImport.csv(text.trimStart('\uFEFF'), ';')
        val kopf = zeilen.firstOrNull()?.map { it.trim() } ?: return emptyList()
        fun idx(name: String) = kopf.indexOf(name)
        val iStatus = idx("Status"); val iIsin = idx("ISIN"); val iVol = idx("Volym"); val iPris = idx("Pris")
        val iArt = idx("Karaktär"); val iTag = idx("Transaktionsdatum"); val iPerson = idx("Person i ledande ställning")
        val iMeldet = idx("Anmälningsskyldig"); val iEmittent = idx("Emittent"); val iBef = idx("Befattning"); val iNah = idx("Närstående")
        val iTyp = idx("Instrumenttyp"); val iPubl = idx("Publiceringsdatum"); val iWaehrung = idx("Valuta"); val iOrt = idx("Handelsplats")
        if (listOf(iStatus, iIsin, iVol, iArt, iTag, iMeldet, iEmittent).any { it < 0 }) return emptyList()
        val aus = LinkedHashMap<String, Geschaeft>()
        for (z in zeilen.drop(1)) {
            fun f(i: Int) = z.getOrNull(i)?.trim().orEmpty()
            if (f(iStatus) != "Aktuell") continue
            val isin = f(iIsin)
            val n = zahl(f(iVol)) ?: continue
            if (isin.isBlank() || n <= 0) continue
            val p = zahl(f(iPris)) ?: 0.0
            val art = when (f(iArt).lowercase()) { "förvärv" -> "K"; "avyttring" -> "V"; else -> "S" }
            val meldet = f(iMeldet)
            val person = f(iPerson)
            val g = Geschaeft(
                e = f(iEmittent), isin = isin, p = meldet, fuer = person.takeIf { it.isNotBlank() && it != meldet }.orEmpty(),
                r = rolleSchweden(f(iBef), f(iNah)), t = f(iBef).replace('\u00A0', ' '), i = f(iTyp), art = art,
                d = f(iTag).take(10), v = f(iPubl).take(10), n = n, wert = n * p, w = f(iWaehrung), o = f(iOrt),
            )
            val alt = aus[g.schluessel]
            aus[g.schluessel] = if (alt == null) g else alt.copy(n = alt.n + g.n, wert = alt.wert + g.wert)
        }
        return aus.values.map { it.copy(preis = if (it.n > 0) it.wert / it.n else 0.0) }
    }

    // ------------------------------------------------------------ Auswertung

    fun vereinen(alt: List<Geschaeft>, neu: List<Geschaeft>, abTag: String): List<Geschaeft> {
        val m = LinkedHashMap<String, Geschaeft>()
        (alt + neu).forEach { m[it.schluessel] = it }
        // Tippfehler im Jahr (etwa 2926) fallen weg
        val morgen = java.time.LocalDate.now().plusDays(1).toString()
        return m.values.filter { it.d >= abTag && it.d <= morgen }.sortedWith(compareByDescending<Geschaeft> { it.d }.thenByDescending { it.v })
    }

    // Grobe Umrechnung in Euro, nur fuer die Schwelle des Insider-Trends.
    private val KURS = mapOf("EUR" to 1.0, "USD" to 0.9, "GBP" to 1.15, "CHF" to 1.05, "SEK" to 0.09, "NOK" to 0.085, "DKK" to 0.134,
        "HKD" to 0.115, "CNY" to 0.125, "INR" to 0.011, "AUD" to 0.6, "CAD" to 0.66, "JPY" to 0.006)

    fun inEuro(betrag: Double, waehrung: String): Double = betrag * (KURS[waehrung.uppercase()] ?: 1.0)

    fun trend(liste: List<Geschaeft>, abTag: String): InsiderTrend.Ergebnis? =
        InsiderTrend.auswerten(
            // Kleinstbetraege (monatliche Mitarbeiterplaene, Bruchstuecke) zaehlen nicht als Entscheidung einer Person
            liste.filter { (it.kauf || it.verkauf) && it.i.equals("Aktie", true) && it.wert > 0 && it.n >= 10 && inEuro(it.wert, it.w) >= 2_000 }
                .map { InsiderTrend.Geschaeft(it.fuer.ifBlank { it.p }.ifBlank { it.id }, it.rolle, it.kauf, it.wert, it.d) },
            abTag,
        )
}
