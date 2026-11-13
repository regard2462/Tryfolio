package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.ZoneId

// Vergleich mit anderen Wertreihen (5.96) in „Was waere, wenn“ und „Depots vergleichen“: Reihen bereinigen
// und untersuchen, den gemeinsamen Zeitraum finden und fremde Reihen mit den Rechnungen von Analysis durchrechnen.
object Vergleichsrechnung {

    const val TAG = 86_400_000L

    // Weniger Tage Verlauf gelten als zu kurz fuer einen Vergleich.
    const val MIN_TAGE = 30

    // Bei Politikern: weniger eingerechnete Geschaefte ergeben keinen sinnvollen Depotverlauf.
    const val MIN_GESCHAEFTE = 3

    // Endet eine Reihe hoechstens so viele Tage vor jetzt, gilt sie als aktuell (Wochenende, Feiertage, Wochenreihen der Investoren).
    const val AKTUELL_TAGE = 8

    // Obergrenze in „Was waere, wenn“: Aktie, vier Vergleiche und beim Sparplan die graue Einzahlungslinie
    // sind sechs Linien im 170 dp hohen Chart, darueber wird es unlesbar, und jeder Politiker-Verlauf braucht
    // bis zu 80 Kursabrufe.
    const val MAX_ZIELE = 4

    enum class Status { OK, KEINE_DATEN, ZU_KURZ, ZU_WENIG_GESCHAEFTE }

    data class Spanne(val von: Long, val bis: Long)

    // Nach Zeit sortiert, nur endliche Werte > 0, je Zeitpunkt der letzte Wert.
    fun bereinigt(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val sauber = reihe.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val out = ArrayList<Pair<Long, Double>>(sauber.size)
        for (p in sauber) {
            if (out.isNotEmpty() && out.last().first == p.first) out[out.size - 1] = p else out.add(p)
        }
        return out
    }

    // In dieser Reihenfolge geprueft: weniger als zwei gueltige Punkte, weniger als MIN_GESCHAEFTE
    // Geschaefte, kuerzer als minTage Tage.
    fun status(reihe: List<Pair<Long, Double>>?, geschaefte: Int? = null, minTage: Int = MIN_TAGE): Status {
        val sauber = bereinigt(reihe ?: emptyList())
        if (sauber.size < 2) return Status.KEINE_DATEN
        if (geschaefte != null && geschaefte < MIN_GESCHAEFTE) return Status.ZU_WENIG_GESCHAEFTE
        if (sauber.last().first - sauber.first().first < minTage * TAG) return Status.ZU_KURZ
        return Status.OK
    }

    // Gemeinsamer Zeitraum: von = max(gewuenscht, Anfang jeder Reihe); bis = min(Ende jeder Reihe), wobei
    // ein Ende ab jetzt - AKTUELL_TAGE als jetzt zaehlt. Null bei leerer Liste, leerer Reihe oder wenn
    // bis - von kuerzer als MIN_TAGE Tage ist.
    fun gemeinsam(reihen: List<List<Pair<Long, Double>>>, gewuenscht: Long, jetzt: Long): Spanne? {
        val sauber = reihen.map { bereinigt(it) }
        if (sauber.isEmpty() || sauber.any { it.isEmpty() }) return null
        val von = sauber.maxOf { maxOf(gewuenscht, it.first().first) }
        val bis = sauber.minOf { if (it.last().first >= jetzt - AKTUELL_TAGE * TAG) jetzt else it.last().first }
        if (bis - von < MIN_TAGE * TAG) return null
        return Spanne(von, bis)
    }

    // Index der Reihe, die den Beginn nach hinten schiebt (spaetester Anfang), wenn dieser mehr als
    // 3 Tage nach gewuenscht liegt; sonst null.
    fun begrenzer(reihen: List<List<Pair<Long, Double>>>, gewuenscht: Long): Int? {
        val anfaenge = reihen.map { bereinigt(it).firstOrNull()?.first }
        val spaetester = anfaenge.filterNotNull().maxOrNull() ?: return null
        if (spaetester <= gewuenscht + 3L * TAG) return null
        return anfaenge.indexOf(spaetester)
    }

    // Ausschnitt von „von“ bis „bis“ (beide einschliesslich). Liegt ein Punkt vor von, beginnt der
    // Ausschnitt mit (von, Wert dieses letzten Punkts davor), sofern es nicht schon genau einen Punkt
    // bei von gibt. Bereinigt vorher.
    fun schneiden(reihe: List<Pair<Long, Double>>, von: Long, bis: Long): List<Pair<Long, Double>> {
        val sauber = bereinigt(reihe)
        val drin = sauber.filter { it.first in von..bis }
        if (drin.firstOrNull()?.first == von) return drin
        val vorher = sauber.lastOrNull { it.first < von } ?: return drin
        return listOf(von to vorher.second) + drin
    }

    // Reihe als Tageskerzen (open = high = low = close = Wert), damit die Rechnungen von Analysis sie wie Kurse nehmen.
    fun alsKerzen(reihe: List<Pair<Long, Double>>): List<Candle> =
        reihe.map { Candle(it.first, it.second, it.second, it.second, it.second) }

    // Einmalanlage auf eine fremde Reihe; schneiden setzt einen Punkt auf spanne.von, den Analysis als Kauftag nimmt.
    fun einmal(reihe: List<Pair<Long, Double>>, betrag: Double, spanne: Spanne): Analysis.LumpSum? =
        Analysis.lumpSum(alsKerzen(schneiden(reihe, spanne.von, spanne.bis)), betrag, spanne.von, null, spanne.bis)

    // Sparplan auf eine fremde Reihe im selben Zeitraum.
    fun sparplan(reihe: List<Pair<Long, Double>>, monatlich: Double, spanne: Spanne, zone: ZoneId = ZoneId.systemDefault()): Analysis.Plan? =
        Analysis.savingsPlan(alsKerzen(schneiden(reihe, spanne.von, spanne.bis)), monatlich, spanne.von, null, spanne.bis, zone)

    // Schluessel einer Laborregel „ART-a-b-n“ und zurueck (null bei unbekannter Art oder falschem Aufbau).
    fun laborSchluessel(r: StrategieLabor.Regel): String = "${r.art.name}-${r.a}-${r.b}-${r.n}"

    fun laborRegel(s: String): StrategieLabor.Regel? {
        val teile = s.split('-')
        if (teile.size != 4) return null
        val art = StrategieLabor.Art.entries.firstOrNull { it.name == teile[0] } ?: return null
        val zahlen = teile.drop(1).map { it.toIntOrNull() ?: return null }
        return StrategieLabor.Regel(art, zahlen[0], zahlen[1], zahlen[2])
    }

    // Wertreihe einer Laborregel ueber alle Kerzen, nur Punkte mit Wert > 0; null bei weniger als 2 Kerzen
    // oder Punkten. Die Indikatoren laufen so ueber die ganze Reihe.
    fun laborReihe(kerzen: List<Candle>, regel: StrategieLabor.Regel, kosten: StrategieLabor.Kosten): List<Pair<Long, Double>>? {
        if (kerzen.size < 2) return null
        val d = StrategieLabor.Daten(kerzen)
        val verlauf = StrategieLabor.rechnen(d, regel, StrategieLabor.ziel(d, regel), 0, kerzen.size, kosten)?.verlauf ?: return null
        return verlauf.filter { it.second > 0 }.takeIf { it.size >= 2 }
    }
}
