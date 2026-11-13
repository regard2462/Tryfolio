package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.engine.Halbjahr
import de.troetenrepublik.app.engine.Kursmuster
import java.io.File
import java.time.LocalDate

// Baut assets/indikatoren/kursmuster.json (Praesidentschaftszyklus und Saisonmuster) aus den Tagesreihen, die
// tools/warnrueckblick/holen.py in einen Ordner legt (y_gspc.csv, y_gdaxi.csv, Zeilen „JJJJ-MM-TT,Wert“).
// Rechnung in engine/Kursmuster.kt; die Datei traegt nur die Ergebnisse, keine Kurse.
// Aufruf: gradlew :desktop:kursmuster -Pdaten=<Ordner> [-Pziel=<Datei>] [-Phalbjahr=<Datei>]
// Aus denselben Reihen entsteht assets/indikatoren/halbjahr.json (Winter- gegen Sommerhalbjahr, engine/Halbjahr.kt).

private fun tagesreihe(ordner: File, name: String): Pair<LongArray, DoubleArray> {
    val z = File(ordner, "$name.csv").readLines().mapNotNull { l ->
        val p = l.split(',')
        if (p.size < 2) return@mapNotNull null
        val d = runCatching { LocalDate.parse(p[0]).toEpochDay() }.getOrNull() ?: return@mapNotNull null
        val w = p[1].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        d to w
    }.sortedBy { it.first }.distinctBy { it.first }
    return z.map { it.first }.toLongArray() to z.map { it.second }.toDoubleArray()
}

fun main(args: Array<String>) {
    val ordner = File(args[0])
    val ziel = File(args[1])
    val (spTage, spKurs) = tagesreihe(ordner, "y_gspc")
    val (daxTage, daxKurs) = tagesreihe(ordner, "y_gdaxi")
    val indizes = listOfNotNull(
        Kursmuster.rechnen("sp500", spTage, spKurs, mitZyklus = true),
        Kursmuster.rechnen("dax", daxTage, daxKurs, mitZyklus = false),
    )
    require(indizes.size == 2) { "Reihen zu kurz" }
    // Stand: letzter voller Monat der kuerzeren Auswertung
    val stand = indizes.minOf { it.monatBis }
    ziel.writeText(Kursmuster.schreiben(Kursmuster.Datei(stand, indizes)))
    for (i in indizes) {
        println("${i.id}: Jahre ${i.vonJahr} bis ${i.bisJahr}, Monate ${i.monatVon} bis ${i.monatBis}")
        if (i.zyklus.isNotEmpty()) println("  Zyklus " + i.zyklus.joinToString { "${it.mittel} (${it.positiv}/${it.anzahl})" })
        println("  Monate " + i.monate.joinToString { "${it.mittel} (${it.positiv}/${it.anzahl})" })
    }
    val halb = listOfNotNull(Halbjahr.rechnen("sp500", spTage, spKurs), Halbjahr.rechnen("dax", daxTage, daxKurs))
    require(halb.size == 2) { "Reihen zu kurz fuer die Halbjahre" }
    args.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { File(it).writeText(Halbjahr.schreiben(Halbjahr.Datei(stand, halb))) }
    for (h in halb) println("${h.id}: Halbjahre ${h.vonJahr} bis ${h.bisJahr}, Winter ${h.winter.mittel}, Sommer ${h.sommer.mittel}, vorn ${h.winterVorn}/${h.jahre}, Regel ${h.regelJahr}, Halten ${h.haltenJahr}")
}
