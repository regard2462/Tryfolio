package de.troetenrepublik.app.engine

import java.time.LocalDate

// GDPNow der Federal Reserve Bank of Atlanta (0.78): laufende Schaetzung des US-Wachstums im aktuellen Quartal,
// aufs Jahr gerechnet. Die Reihe ist urheberrechtlich geschuetzt und verlangt die Nennung der Quelle; sie wird nur
// beim Nutzer abgerufen und nicht mitgeliefert. Die Reihe fuehrt je Quartal einen Wert (die juengste Schaetzung);
// den Verlauf innerhalb des Quartals ergeben die Staende der Reihe an frueheren Tagen (ALFRED).
object GdpNow {

    const val REIHE = "GDPNOW"
    const val SEITE = "https://fred.stlouisfed.org/series/GDPNOW"

    // So viele Staende je Abruf
    const val SPALTEN = 12

    // Staende der Reihe an den genannten Tagen in einer Datei: je Tag eine Spalte „GDPNOW_JJJJMMTT“.
    fun adresse(tage: List<LocalDate>): String =
        "https://alfred.stlouisfed.org/graph/alfredgraph.csv?id=" + tage.joinToString(",") { REIHE } + "&vintage_date=" + tage.joinToString(",")

    // Die letzten zwoelf Tage (fuer den Tag der juengsten Aenderung) und davor zwoelf Wochenstaende (ein Quartal).
    fun tageNah(heute: LocalDate): List<LocalDate> = (0 until SPALTEN).map { heute.minusDays(it.toLong()) }.reversed()
    fun tageFern(heute: LocalDate): List<LocalDate> = (2 until 2 + SPALTEN).map { heute.minusWeeks(it.toLong()) }.reversed()

    // quartal: erster Tag des geschaetzten Quartals (Tag seit 1970). wert: juengste Schaetzung in Prozent.
    // stand: frueheste abgerufene Tag, seit dem dieser Wert gilt. verlauf: Schaetzung je abgerufenem Tag.
    // vorquartal: letzte Schaetzung fuer das Quartal davor.
    data class Stand(
        val quartal: Long, val wert: Double, val stand: Long, val verlauf: List<Pair<Long, Double>>, val vorquartal: Double?,
        val quelle: Quelle = Quelle.FRED,
    ) {
        val jahr: Int get() = LocalDate.ofEpochDay(quartal).year
        val nummer: Int get() = (LocalDate.ofEpochDay(quartal).monthValue - 1) / 3 + 1
    }

    // Woher der Stand stammt: Tabelle der Atlanta Fed oder die Reihe bei FRED
    enum class Quelle { ATLANTA, FRED }

    private val SPALTE = Regex("^" + REIHE + "_(\\d{4})(\\d{2})(\\d{2})$")

    // Eine Datei: Quartal -> (Tag des Stands -> Wert). Leere Felder fehlen.
    private fun tabelle(csv: String): Map<Long, Map<Long, Double>> {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val kopf = zeilen.firstOrNull()?.split(',')?.map { it.trim() } ?: return emptyMap()
        val tage = kopf.map { k ->
            SPALTE.matchEntire(k)?.let { m -> runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toEpochDay() }.getOrNull() }
        }
        if (tage.none { it != null }) return emptyMap()
        val aus = HashMap<Long, Map<Long, Double>>()
        for (z in zeilen.drop(1)) {
            val f = z.split(',')
            val quartal = runCatching { LocalDate.parse(f[0].trim()).toEpochDay() }.getOrNull() ?: continue
            val werte = HashMap<Long, Double>()
            for (i in 1 until f.size) {
                val tag = tage.getOrNull(i) ?: continue
                val w = f[i].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > -100 && it < 100 } ?: continue
                werte[tag] = w
            }
            if (werte.isNotEmpty()) aus[quartal] = werte
        }
        return aus
    }

    // Aus einer oder mehreren Dateien: das juengste Quartal mit Wert und sein Verlauf. null ohne lesbaren Wert.
    fun lesen(dateien: List<String>): Stand? {
        val alle = HashMap<Long, HashMap<Long, Double>>()
        for (d in dateien) for ((q, w) in tabelle(d)) alle.getOrPut(q) { HashMap() }.putAll(w)
        val quartal = alle.keys.maxOrNull() ?: return null
        val verlauf = alle.getValue(quartal).toList().sortedBy { it.first }
        val wert = verlauf.last().second
        val stand = verlauf.asReversed().takeWhile { it.second == wert }.last().first
        val davor = LocalDate.ofEpochDay(quartal).minusMonths(3).toEpochDay()
        val vorquartal = alle[davor]?.maxByOrNull { it.key }?.value
        return Stand(quartal, wert, stand, verlauf, vorquartal)
    }

    // 0 schrumpft, 1 waechst kaum (unter 1 %), 2 waechst
    fun stufe(wert: Double): Int = if (wert < 0) 0 else if (wert < 1) 1 else 2
}
