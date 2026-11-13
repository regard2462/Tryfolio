package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle

// [0.78] Kurs und Umsatz nach Kostolany: Richtung eines Index ueber drei Monate gegen die Richtung des Boersenumsatzes.
//   faellt bei hohem Umsatz            Kaufzone (Verkaeufe in Eile, die Stuecke wechseln in feste Haende)
//   steigt bei niedrigem Umsatz        frueher Aufschwung
//   steigt seit langem, hoher Umsatz   Verkaufszone (breite Beteiligung nach langem Anstieg)
//   faellt bei niedrigem Umsatz        frueher Abschwung
// Kurs: Aenderung ueber KURZ Handelstage, ab SCHWELLE_KURS Prozent zaehlt eine Richtung; "seit langem" heisst zusaetzlich
// ueber LANG Handelstage im Plus. Umsatz: Mittel der letzten KURZ Handelstage gegen das Mittel der LANG Tage davor, ab
// SCHWELLE_UMSATZ Prozent Abstand hoch oder niedrig. Alles andere ergibt keine Zone.
object KursUmsatz {
    enum class Zone { KAUFZONE, FRUEHER_AUFSCHWUNG, VERKAUFSZONE, FRUEHER_ABSCHWUNG, KEINE }

    // kurs und kursLang: Aenderung in Prozent; umsatz: Abstand zum Mittel des Jahres davor in Prozent; tag: letzter Handelstag (ms).
    data class Ergebnis(val zone: Zone, val kurs: Double, val kursLang: Double, val umsatz: Double, val tag: Long)

    const val KURZ = 63
    const val LANG = 252
    const val SCHWELLE_KURS = 2.0
    const val SCHWELLE_UMSATZ = 10.0

    fun zone(kurs: Double, kursLang: Double, umsatz: Double): Zone {
        val steigt = kurs >= SCHWELLE_KURS
        val faellt = kurs <= -SCHWELLE_KURS
        val hoch = umsatz >= SCHWELLE_UMSATZ
        val niedrig = umsatz <= -SCHWELLE_UMSATZ
        return when {
            faellt && hoch -> Zone.KAUFZONE
            steigt && niedrig -> Zone.FRUEHER_AUFSCHWUNG
            steigt && hoch && kursLang > 0 -> Zone.VERKAUFSZONE
            faellt && niedrig -> Zone.FRUEHER_ABSCHWUNG
            else -> Zone.KEINE
        }
    }

    // kerzen: Tageskerzen mit Umsatz, aufsteigend. heute (Tag seit 1970): der laufende Tag zaehlt nicht mit, sein Umsatz
    // ist noch unvollstaendig. null ohne genug Tage mit Umsatz.
    fun auswerten(kerzen: List<Candle>, heute: Long): Ergebnis? {
        val k = kerzen.filter { it.close > 0 && it.close.isFinite() && it.volume > 0 && Math.floorDiv(it.t, 86_400_000L) < heute }
        if (k.size < KURZ + LANG + 1) return null
        val n = k.size
        val kurs = (k[n - 1].close / k[n - 1 - KURZ].close - 1) * 100
        val kursLang = (k[n - 1].close / k[n - 1 - LANG].close - 1) * 100
        val jetzt = k.subList(n - KURZ, n).map { it.volume }.average()
        val davor = k.subList(n - KURZ - LANG, n - KURZ).map { it.volume }.average()
        if (!(davor > 0)) return null
        val umsatz = (jetzt / davor - 1) * 100
        return Ergebnis(zone(kurs, kursLang, umsatz), kurs, kursLang, umsatz, k[n - 1].t)
    }
}
