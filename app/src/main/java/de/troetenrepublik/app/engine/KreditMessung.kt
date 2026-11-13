package de.troetenrepublik.app.engine

// Kreditanstieg und Finanzkrisen [0.78], gemessen an allen Laendern in HistorieJst: Wie oft folgte auf ein Jahr, in dem
// Kredit zu BIP binnen JAHRE Jahren um mindestens ANSTIEG Punkte gestiegen war, binnen JAHRE Jahren ein Krisenjahr, und wie
// oft war das ueber alle Laenderjahre so (Grundquote). Gezaehlt werden Laenderjahre; aufeinanderfolgende Jahre eines
// Landes ueberlappen sich und sind nicht unabhaengig.
object KreditMessung {

    const val JAHRE = 5
    const val ANSTIEG = 20.0

    // Aenderung von Kredit zu BIP in Punkten gegen den Wert JAHRE Jahre davor; null bei Luecke.
    fun anstieg(l: HistorieJst.Land, jahr: Int): Double? {
        val jetzt = l.kredit.getOrNull(jahr - l.von) ?: return null
        val vorher = l.kredit.getOrNull(jahr - JAHRE - l.von) ?: return null
        return jetzt - vorher
    }

    // faelle: Laenderjahre mit Anstieg ab der Schwelle, mitKrise: davon mit Krisenjahr in den JAHRE Jahren danach.
    // alle und alleMitKrise: dasselbe ueber alle Laenderjahre mit Wert. von und bis: erstes und letztes gezaehltes Jahr.
    data class Bilanz(val faelle: Int, val mitKrise: Int, val alle: Int, val alleMitKrise: Int, val laender: Int, val von: Int, val bis: Int) {
        val quote: Double get() = if (faelle > 0) mitKrise.toDouble() / faelle else 0.0
        val grund: Double get() = if (alle > 0) alleMitKrise.toDouble() / alle else 0.0
    }

    fun bilanz(d: HistorieJst.Datei, schwelle: Double = ANSTIEG): Bilanz? {
        var faelle = 0
        var mitKrise = 0
        var alle = 0
        var alleMitKrise = 0
        var von = Int.MAX_VALUE
        var bis = Int.MIN_VALUE
        val laender = HashSet<String>()
        for (l in d.laender.values) {
            val krisen = l.krisen.toHashSet()
            for (i in l.kredit.indices) {
                val jahr = l.von + i
                // Die Folgejahre muessen in der Datei liegen
                if (jahr + JAHRE > d.ende) continue
                val a = anstieg(l, jahr) ?: continue
                val krise = (1..JAHRE).any { (jahr + it) in krisen }
                alle++
                if (krise) alleMitKrise++
                laender += l.a3
                von = minOf(von, jahr)
                bis = maxOf(bis, jahr)
                if (a >= schwelle) {
                    faelle++
                    if (krise) mitKrise++
                }
            }
        }
        return if (alle == 0) null else Bilanz(faelle, mitKrise, alle, alleMitKrise, laender.size, von, bis)
    }
}
