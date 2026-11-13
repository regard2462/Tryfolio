package de.troetenrepublik.app.engine

import kotlin.math.abs

// Gemeldeter Gewinn je Aktie gegen den erwarteten (0.78): eine Formel, eine Schwelle und eine Reihe fuer alle Stellen,
// die Geschaeftszahlen einordnen (Ereignisse, Bewegung zu den Zahlen, Finanzen, Chancen und Risiken, Quartalsueberblick).
object Gewinnueberraschung {

    // Bis zu dieser Abweichung in Prozent gilt eine Zahl als wie erwartet.
    const val SCHWELLE = 2.0

    enum class Urteil { UEBERTROFFEN, WIE_ERWARTET, VERFEHLT }

    // Abweichung in Prozent der Erwartung, bezogen auf ihren Betrag (von -0,10 auf -0,05 sind +50 %); null ohne beide
    // Werte oder bei Erwartung 0.
    fun abweichung(ist: Double?, erwartet: Double?): Double? {
        if (ist == null || erwartet == null || erwartet == 0.0 || !erwartet.isFinite() || !ist.isFinite()) return null
        return (ist - erwartet) / abs(erwartet) * 100.0
    }

    fun urteil(abweichung: Double): Urteil = when {
        abweichung > SCHWELLE -> Urteil.UEBERTROFFEN
        abweichung < -SCHWELLE -> Urteil.VERFEHLT
        else -> Urteil.WIE_ERWARTET
    }

    fun urteil(ist: Double?, erwartet: Double?): Urteil? = abweichung(ist, erwartet)?.let(::urteil)

    // Ein gemeldetes Quartal: ende als "yyyy-MM-dd"; fehlende Werte sind null, nie 0.
    data class Quartal(val ende: String, val ist: Double?, val erwartet: Double?) {
        val abweichung: Double? get() = abweichung(ist, erwartet)
    }

    // Quartale mit beiden Werten, wie sie Tabellen und Zaehlungen brauchen.
    fun vollstaendig(q: List<Quartal>): List<Quartal> = q.filter { it.ist != null && it.erwartet != null }

    // uebertroffen und verfehlt zaehlen erst jenseits der Schwelle; gezaehlt: Quartale mit brauchbarer Erwartung.
    data class Bilanz(val uebertroffen: Int, val verfehlt: Int, val gezaehlt: Int)

    // paare: gemeldet und erwartet je Quartal, aelteste zuerst; gewertet werden die letzten anzahl.
    fun bilanz(paare: List<Pair<Double, Double>>, anzahl: Int = 4): Bilanz {
        val urteile = paare.takeLast(anzahl).mapNotNull { urteil(it.first, it.second) }
        return Bilanz(urteile.count { it == Urteil.UEBERTROFFEN }, urteile.count { it == Urteil.VERFEHLT }, urteile.size)
    }
}

// Termine der Geschaeftszahlen auf der Wertpapierseite (0.78).
object Zahlentermine {

    private const val TAG_MS = 86_400_000L

    // Ein Termin steht unter den anstehenden, solange er nicht laenger als einen Tag vorbei ist (wie Ex-Tag und Zahltag).
    fun anstehend(termin: Long, jetzt: Long): Boolean = termin > jetzt - TAG_MS

    // Tag der Veroeffentlichung zu einem Quartalsende (beides Tage seit 1970): der erste bekannte Termin, der bis zu
    // 120 Tage nach dem Quartalsende liegt; null, wenn keiner passt.
    fun veroeffentlicht(quartalsende: Long, termine: List<Long>): Long? =
        termine.filter { it - quartalsende in 1..120 }.minOrNull()
}
