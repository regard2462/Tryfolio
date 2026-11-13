package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.SecInsider
import java.time.LocalDate

// Seltene Insiderkaeufe (0.77): jeder juengere Kauf einer US-Firma gegen die Kaeufe derselben Firma in den vorhandenen
// Daten (Form 345, 18 Monate). Drei Aussagen, jede nur, wenn sie stimmt: erster Kauf nach mindestens LUECKE_TAGE ohne
// Kauf (die Firma hatte davor Insidermeldungen), mindestens MIN_KAEUFER Kaeufer in einer Woche wie nie in den Daten
// (oder seit LUECKE_TAGE), groesster Kauf mit mindestens FAKTOR mal dem bisher groessten. Kaeufe unter MIN_BETRAG zaehlen
// nicht, Investmentfonds und aehnliche Emittenten fallen weg (istFonds). Gab es keinen frueheren Fall, gilt die Aussage
// seit Beginn der Daten (seitBeginn). Mit InsiderHistorie (Kaufhistorie ab 2016, davor liegend) zaehlt die Luecke ab dem
// letzten Kauf dort, "seit Beginn" heisst dann seit Beginn der Historie (seitJahr); ohne Eintrag des Tickers gilt die
// Rechnung nur mit den vorhandenen Daten. Reine Rechnung ohne Netz.
object InsiderSeltenheit {

    enum class Art { WOCHE, GROESSTER, ERSTER }

    // Eine Aussage zu einer Firma. tag: Tag des Kaufs (Meldetag), seit: Tag des letzten vergleichbaren Falls oder Beginn der
    // Daten, luecke: Tage dazwischen (Mass der Seltenheit), zahl: Kaeufer in der Woche (nur bei WOCHE).
    class Fund(
        val symbol: String, val name: String, val art: Art, val tag: String, val betrag: Double,
        val seit: String, val seitBeginn: Boolean, val luecke: Int, val zahl: Int = 0,
        // Verhaeltnis zum bisher groessten Kauf (nur bei GROESSTER)
        val faktor: Double = 0.0,
        // Jahr, ab dem die Historie reicht, wenn seitBeginn auf ihr beruht (sonst 0)
        val seitJahr: Int = 0,
    ) {
        // Tage der Luecke in ganzen Monaten
        val monate: Int get() = Math.round(luecke / 30.4).toInt()
    }

    // Kleinster Betrag eines Kaufs, damit er zaehlt (kleine Zukaeufe sind selten, aber ohne Aussage).
    const val MIN_BETRAG = 500_000.0

    // Kleinste Luecke zum letzten Kauf (erster Kauf) und zur letzten gleich starken Woche.
    const val LUECKE_TAGE = 365

    // Kaeufer in einer Woche, ab denen die Aussage zaehlt.
    const val MIN_KAEUFER = 3

    // Der groesste Kauf muss so viel mal den bisher groessten uebertreffen.
    const val FAKTOR = 3.0

    private val FONDS_NAME = Regex("\\bfunds?\\b", RegexOption.IGNORE_CASE)
    private val BDC_NAME = Regex("\\b(BDC|lending|specialty finance|capital corp)\\b", RegexOption.IGNORE_CASE)
    private val TRUST_NAME = Regex("\\btrust\\b", RegexOption.IGNORE_CASE)
    private val TRUST_ART = Regex("income|credit|municipal|private|term|dividend|convertible|equity|growth|global|yield|science|technology|opportunit|micro|small-cap|series|investment", RegexOption.IGNORE_CASE)
    private val IMMO_NAME = Regex("realty|propert|mortgage|residential|real estate investment|healthcare|bancorp|hotel", RegexOption.IGNORE_CASE)
    private val SONST_FONDS = Regex("interval|private real estate|private equity|private credit|municipal|\\bETF\\b|tender offer|portfolio, inc", RegexOption.IGNORE_CASE)
    private val FONDS_KUERZEL = Regex("[A-Z]{4}X")

    // Investmentfonds und geschlossene Fonds: Kuerzel mit fuenf Buchstaben und X am Ende (US-Investmentfonds), Namen mit
    // Fund (ausser boersennotierte BDCs wie Ares Capital oder Blackstone Secured Lending Fund), Trusts mit Einkommen,
    // Kredit, Kommunal und aehnlichem (ohne Immobilien-Trusts), Intervall- und Privatmarktfonds. Kaeufe ihrer Verwalter
    // sind kein Signal fuer eine Firma.
    fun istFonds(kuerzel: String, name: String): Boolean = when {
        FONDS_KUERZEL.matches(kuerzel.trim().uppercase()) -> true
        SONST_FONDS.containsMatchIn(name) -> true
        FONDS_NAME.containsMatchIn(name) && !BDC_NAME.containsMatchIn(name) -> true
        TRUST_NAME.containsMatchIn(name) && TRUST_ART.containsMatchIn(name) && !IMMO_NAME.containsMatchIn(name) -> true
        else -> false
    }

    // Zeitraum der "juengeren" Kaeufe vor dem Ende der Daten.
    const val NEU_TAGE = 30

    private class Kauf(val person: String, val tag: String, val tagNr: Long, val wert: Double)

    private fun nr(tag: String): Long = LocalDate.parse(tag).toEpochDay()

    // Tag der Meldung, sonst des Geschaefts.
    private fun meldetag(g: SecInsider.Geschaeft): String = g.f.ifBlank { g.d }

    // Erster Tag der Daten (kleinster Tag eines Geschaefts), null ohne Geschaefte.
    fun beginn(liste: List<SecInsider.Geschaeft>): String? = liste.minOfOrNull { it.d }

    // Aussagen zu allen Firmen der Liste, seltenste zuerst. ende: letzter Tag, den die Daten abdecken (JJJJ-MM-TT).
    fun funde(liste: List<SecInsider.Geschaeft>, ende: String, beginn: String, neuTage: Int = NEU_TAGE, historie: InsiderHistorie? = null): List<Fund> =
        liste.groupBy { it.k }
            .flatMap { (_, g) -> fuerFirma(g, ende, beginn, neuTage, historie) }
            .sortedWith(compareByDescending<Fund> { it.luecke }.thenBy { it.art.ordinal }.thenByDescending { it.betrag })

    // Je Firma nur die seltenste Aussage: fuer die Liste ueber alle Werte.
    fun besteJeFirma(funde: List<Fund>): List<Fund> = funde.groupBy { it.symbol }.map { (_, l) -> l.first() }
        .sortedWith(compareByDescending<Fund> { it.luecke }.thenBy { it.art.ordinal }.thenByDescending { it.betrag })

    // Aussagen zu einer Firma, seltenste zuerst; geschaefte sind alle ihre Meldungen (Kaeufe und Verkaeufe).
    fun fuerFirma(geschaefte0: List<SecInsider.Geschaeft>, ende: String, beginn: String, neuTage: Int = NEU_TAGE, historie: InsiderHistorie? = null): List<Fund> {
        if (geschaefte0.isEmpty() || istFonds(geschaefte0.first().k, geschaefte0.first().e)) return emptyList()
        val geschaefte = geschaefte0.filter { it.kauf && it.wert > 0 }
        val alleTage = geschaefte0.map { meldetag(it) }
        val alle = geschaefte.filter { it.kauf && it.wert > 0 }.map { Kauf(it.p.trim().lowercase(), meldetag(it), nr(meldetag(it)), it.wert) }.sortedBy { it.tagNr }
        if (alle.isEmpty()) return emptyList()
        val symbol = geschaefte.first { it.kauf && it.wert > 0 }.k
        val name = geschaefte.first { it.kauf && it.wert > 0 }.e
        val endeNr = nr(ende)
        val beginnNr = nr(beginn)
        // Historie dieser Firma (davor liegend); ohne Eintrag gilt die Rechnung ohne Historie
        val h = historie?.firmen?.get(symbol)?.takeIf { historie.von < beginn }
        val vonNr = if (h != null) nr(historie.von) else beginnNr
        val vonTag = if (h != null) historie.von else beginn
        val vonJahr = if (h != null) historie.jahr else 0
        fun tagText(n: Long) = LocalDate.ofEpochDay(n).toString()
        val neu = alle.filter { it.tagNr in (endeNr - neuTage + 1)..endeNr }
        if (neu.isEmpty()) return emptyList()
        val aus = ArrayList<Fund>()

        // Erster Kauf seit: der fruehste juengere Kauf, davor nichts oder lange nichts
        run {
            val erster = neu.first()
            val vorher = alle.lastOrNull { it.tagNr < erster.tagNr }
            val hLetzter = if (vorher == null) h?.letzter else null
            val luecke = (erster.tagNr - (vorher?.tagNr ?: hLetzter?.tag ?: vonNr)).toInt()
            val betrag = alle.filter { it.tagNr == erster.tagNr }.sumOf { it.wert }
            // Ohne fruehere Meldung der Firma fehlt nur die Abdeckung der Daten; die Historie kennt die Firma
            val frueheresDa = h != null || alleTage.any { it < erster.tag }
            if (luecke >= LUECKE_TAGE && betrag >= MIN_BETRAG && frueheresDa) {
                val seit = vorher?.tag ?: hLetzter?.let { tagText(it.tag) } ?: vonTag
                val ohne = vorher == null && hLetzter == null
                aus += Fund(symbol, name, Art.ERSTER, erster.tag, betrag, seit, ohne, luecke, seitJahr = if (ohne) vonJahr else 0)
            }
        }

        // So viele Kaeufer in einer Woche wie seit: die groesste Zahl verschiedener Kaeufer in sieben Tagen unter den juengeren Kaeufen
        run {
            fun kaeufer(bis: Long) = alle.filter { it.tagNr in (bis - 6)..bis }.map { it.person }.toSet().size
            val spitze = neu.map { it.tagNr }.distinct().map { it to kaeufer(it) }.maxWithOrNull(compareBy<Pair<Long, Int>> { it.second }.thenBy { it.first }) ?: return@run
            val (bis, zahl) = spitze
            if (zahl < MIN_KAEUFER) return@run
            // Letzter Tag davor (ausserhalb dieser Woche), an dem eine Woche mindestens so viele Kaeufer hatte
            val vorher = alle.filter { it.tagNr < bis - 6 }.map { it.tagNr }.distinct().lastOrNull { kaeufer(it) >= zahl }
            val hWoche = if (vorher == null) h?.letzteWoche(zahl) else null
            val luecke = (bis - 6 - (vorher ?: hWoche?.tag ?: vonNr)).toInt()
            val betrag = alle.filter { it.tagNr in (bis - 6)..bis }.sumOf { it.wert }
            if (luecke >= LUECKE_TAGE && betrag >= MIN_BETRAG) {
                val tag = LocalDate.ofEpochDay(bis).toString()
                val ohne = vorher == null && hWoche == null
                aus += Fund(symbol, name, Art.WOCHE, tag, betrag, (vorher ?: hWoche?.tag)?.let { tagText(it) } ?: vonTag, ohne, luecke, zahl, seitJahr = if (ohne) vonJahr else 0)
            }
        }

        // Groesster Kauf seit: der groesste juengere Kauf gegen die frueheren; mindestens zwei fruehere Kaeufe als Vergleich
        run {
            val gross = neu.maxWithOrNull(compareBy<Kauf> { it.wert }.thenBy { it.tagNr }) ?: return@run
            val frueher = alle.filter { it.tagNr < gross.tagNr }
            if (frueher.size + (h?.n ?: 0) < 2 || gross.wert < MIN_BETRAG) return@run
            val bisher = maxOf(frueher.maxOfOrNull { it.wert } ?: 0.0, h?.groesster ?: 0.0)
            if (gross.wert < FAKTOR * bisher) return@run
            val vorher = frueher.lastOrNull { it.wert >= gross.wert }
            val hVorher = if (vorher == null) h?.letzterMit(gross.wert) else null
            val luecke = (gross.tagNr - (vorher?.tagNr ?: hVorher?.tag ?: vonNr)).toInt()
            val ohne = vorher == null && hVorher == null
            aus += Fund(symbol, name, Art.GROESSTER, gross.tag, gross.wert, vorher?.tag ?: hVorher?.let { tagText(it.tag) } ?: vonTag, ohne, luecke, faktor = gross.wert / bisher, seitJahr = if (ohne) vonJahr else 0)
        }
        return aus.sortedWith(compareByDescending<Fund> { it.luecke }.thenBy { it.art.ordinal })
    }
}
