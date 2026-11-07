package de.troetenrepublik.app.engine

// Zinserwartung fuer die USA (seit 5.92) aus den 30-Tage-Fed-Funds-Futures der CME, wie Yahoo sie zeigt. Ein Kontrakt
// rechnet mit dem durchschnittlichen Tagesgeldsatz (Fed Funds) seines Monats ab; 100 minus Kurs ist also der Satz,
// den der Markt fuer diesen Monat erwartet. Ohne Laufzeitpraemie gerechnet, damit nur eine Naeherung.
object Zinserwartung {

    // Monatskennbuchstaben der Terminboersen, Januar bis Dezember.
    private const val MONATE = "FGHJKMNQUVXZ"

    data class Kontrakt(val jahr: Int, val monat: Int, val symbol: String)

    // Kuerzel bei Yahoo fuer den laufenden und die folgenden Monate, etwa ZQZ26.CBT fuer Dezember 2026.
    fun kontrakte(jahr: Int, monat: Int, anzahl: Int = 14): List<Kontrakt> = (0 until anzahl).map { i ->
        val m0 = monat - 1 + i
        val j = jahr + m0 / 12
        val m = m0 % 12 + 1
        Kontrakt(j, m, "ZQ" + MONATE[m - 1] + (j % 100).toString().padStart(2, '0') + ".CBT")
    }

    data class Monat(val jahr: Int, val monat: Int, val zins: Double)

    // Erwarteter Satz je Monat aus den Kursen; Kurse ausserhalb 80 bis 100 gelten als Fehler.
    fun pfad(kontrakte: List<Kontrakt>, kurse: Map<String, Double>): List<Monat> = kontrakte.mapNotNull { k ->
        kurse[k.symbol]?.takeIf { it.isFinite() && it in 80.0..100.0 }?.let { Monat(k.jahr, k.monat, 100 - it) }
    }

    // heute: Satz des ersten bekannten Monats; dazu der erwartete Satz nach drei, sechs und zwoelf Monaten (null, wenn
    // der Kontrakt fehlt) und die Zahl der Schritte um einen Viertelpunkt bis in zwoelf Monaten (negativ: Senkungen).
    data class Erwartung(val heute: Monat, val in3: Monat?, val in6: Monat?, val in12: Monat?, val schritte: Double?, val pfad: List<Monat>)

    fun erwartung(pfad: List<Monat>): Erwartung? {
        val erster = pfad.firstOrNull() ?: return null
        if (pfad.size < 2) return null
        fun nach(n: Int): Monat? {
            val ziel = erster.jahr * 12 + erster.monat - 1 + n
            return pfad.firstOrNull { it.jahr * 12 + it.monat - 1 == ziel }
        }
        val in12 = nach(12) ?: nach(11)
        return Erwartung(erster, nach(3), nach(6), in12, in12?.let { (it.zins - erster.zins) / 0.25 }, pfad)
    }
}
