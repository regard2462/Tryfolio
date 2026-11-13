package de.troetenrepublik.app.engine

// Kennzahlen zu den Meldungen von Abgeordneten und Senatoren (0.76): Meldeverzug je Person und die Verteilung der
// Geschaefte nach Sektor, Kammer und Familie. Reine Funktionen ohne Netz.
object KongressKennzahlen {

    private const val TAG = 86_400_000L

    // Nach dem STOCK Act gilt eine Meldung ab 45 Tagen nach dem Geschaeft als verspaetet.
    const val SPAET_TAGE = 45

    const val FENSTER_TAGE = 90

    // Ein Geschaeft mit Handelstag, Tag der Veroeffentlichung der Meldung (beide in ms) und geschaetztem Betrag.
    data class Eintrag(val datum: Long, val gemeldet: Long, val betrag: Double)

    // median und spaet null ohne auswertbares Geschaeft; spaet ist der Anteil (0..1) der Meldungen ueber 45 Tage.
    data class Verzug(val median: Int?, val spaet: Double?, val anzahl: Int, val geschaefte90: Int, val volumen90: Double)

    // Tage vom Geschaeft bis zur Meldung; null, wenn eine Angabe fehlt, nie negativ.
    fun tage(datum: Long, gemeldet: Long): Int? =
        if (datum <= 0 || gemeldet <= 0) null else Math.floorDiv(gemeldet - datum, TAG).toInt().coerceAtLeast(0)

    // Mitte der sortierten Werte; bei gerader Zahl der Mittelwert der beiden mittleren, auf ganze Tage gerundet.
    fun median(werte: List<Int>): Int? {
        if (werte.isEmpty()) return null
        val s = werte.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else Math.round((s[n / 2 - 1] + s[n / 2]) / 2.0).toInt()
    }

    fun verzug(l: List<Eintrag>, jetzt: Long): Verzug {
        val t = l.mapNotNull { tage(it.datum, it.gemeldet) }
        val ab = jetzt - FENSTER_TAGE * TAG
        val neu = l.filter { it.datum >= ab && it.datum <= jetzt }
        return Verzug(
            median(t), if (t.isEmpty()) null else t.count { it > SPAET_TAGE }.toDouble() / t.size,
            t.size, neu.size, neu.sumOf { it.betrag },
        )
    }

    // ------------------------------------------------------------ Verteilung

    // Kauf- und Verkaufsseite einer Gruppe: Zahl der Geschaefte und geschaetzte Summe (Mitte der Spannen).
    data class Seite(val kaeufe: Int = 0, val verkaeufe: Int = 0, val kaufSumme: Double = 0.0, val verkaufSumme: Double = 0.0) {
        val summe: Double get() = kaufSumme + verkaufSumme
        val leer: Boolean get() = kaeufe + verkaeufe == 0

        fun plus(kauf: Boolean, betrag: Double) =
            if (kauf) copy(kaeufe = kaeufe + 1, kaufSumme = kaufSumme + betrag) else copy(verkaeufe = verkaeufe + 1, verkaufSumme = verkaufSumme + betrag)
    }

    data class Gruppe(val name: String, val seite: Seite)

    data class Fluss(
        // Sektoren nach Summe absteigend
        val sektoren: List<Gruppe>,
        val haus: Seite,
        val senat: Seite,
        // Geschaefte der Familie (Ehepartner, gemeinsam, Kinder) und eigene
        val eigene: Seite,
        val familie: Seite,
        val ohneSektor: Int,
        val gesamt: Int,
    )

    // Familie: SP Ehepartner, JT gemeinsam, DC abhaengiges Kind laut Meldung; leer heisst selbst.
    fun familie(inhaber: String): Boolean = inhaber.trim().uppercase() in setOf("SP", "JT", "DC")

    // Geschaefte der letzten tage Tage (nach Handelstag). sektor liefert den Namen zu einem Kuerzel oder null.
    fun fluss(handel: List<KongressAuswertung.Handel>, jetzt: Long, sektor: (String) -> String?, tage: Int = FENSTER_TAGE): Fluss {
        val ab = jetzt - tage * TAG
        val im = handel.filter { it.datum in ab..jetzt }
        val nach = LinkedHashMap<String, Seite>()
        var ohne = 0
        var haus = Seite()
        var senat = Seite()
        var eigene = Seite()
        var familie = Seite()
        for (h in im) {
            if (h.gruppe == 'S') senat = senat.plus(h.kauf, h.betrag) else haus = haus.plus(h.kauf, h.betrag)
            if (familie(h.inhaber)) familie = familie.plus(h.kauf, h.betrag) else eigene = eigene.plus(h.kauf, h.betrag)
            val s = sektor(h.symbol)
            if (s == null) ohne++ else nach[s] = (nach[s] ?: Seite()).plus(h.kauf, h.betrag)
        }
        return Fluss(
            nach.map { Gruppe(it.key, it.value) }.sortedByDescending { it.seite.summe },
            haus, senat, eigene, familie, ohne, im.size,
        )
    }
}
