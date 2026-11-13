package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.engine.WarnRueckblick
import de.troetenrepublik.app.engine.WarnRueckblick.Reihe
import java.io.File
import java.time.LocalDate

// Baut den Rueckblick „Haette man es sehen koennen?“ (assets/indikatoren/warnzeichen_rueckblick.json) aus den Rohreihen,
// die tools/warnrueckblick/holen.py in einen Ordner legt (Zeilen „JJJJ-MM-TT,Wert“). Rechnung in engine/WarnRueckblick.kt.
// Aufruf: gradlew :desktop:warnzeichenRueckblick -Pdaten=<Ordner> -Pziel=<Datei> [-Pketten=<ketten.json>]
// Argumente: Ordner der Rohreihen, Zieldatei, Ketten-Datei (oder leer), Bericht (oder leer).

private fun reihe(ordner: File, name: String): Pair<LongArray, DoubleArray>? {
    val f = File(ordner, "$name.csv")
    if (!f.exists()) { System.err.println("fehlt: $name"); return null }
    val z = f.readLines().mapNotNull { l ->
        val p = l.split(',')
        if (p.size < 2) return@mapNotNull null
        val d = runCatching { LocalDate.parse(p[0]).toEpochDay() }.getOrNull() ?: return@mapNotNull null
        val w = p[1].toDoubleOrNull() ?: return@mapNotNull null
        d to w
    }.sortedBy { it.first }.distinctBy { it.first }
    return z.map { it.first }.toLongArray() to z.map { it.second }.toDoubleArray()
}

private fun r(ordner: File, name: String, verzug: Int = 0, maxAlter: Int = 10): Reihe? =
    reihe(ordner, name)?.let { Reihe(it.first, it.second, verzug, maxAlter) }

// Wert der Reihe am Tag oder davor, hoechstens `alter` Tage alt; sonst NaN
private fun ausgerichtet(r: Reihe?, tage: LongArray, alter: Int): DoubleArray = DoubleArray(tage.size) { i ->
    if (r == null) Double.NaN else {
        val n = r.anzahl(tage[i])
        if (n == 0 || tage[i] - r.tage[n - 1] > alter) Double.NaN else r.werte[n - 1]
    }
}

fun main(args: Array<String>) {
    val ordner = File(args[0])
    val ziel = File(args[1])
    val ketten = args.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { File(it).readText() }
    val gspc = r(ordner, "y_gspc")!!
    val irx = r(ordner, "y_irx")
    val tnx = r(ordner, "y_tnx")
    val gemeinsam = HashMap<String, Reihe?>()
    gemeinsam[WarnRueckblick.R_KURVE] = if (tnx != null && irx != null) WarnRueckblick.differenz(tnx, irx) else null
    val t10 = r(ordner, "t_10j")
    val t2 = r(ordner, "t_2j")
    gemeinsam[WarnRueckblick.R_KURVE_2J] = if (t10 != null && t2 != null) WarnRueckblick.differenz(t10, t2) else null
    gemeinsam[WarnRueckblick.R_ARBEIT] = r(ordner, "bls_unrate", verzug = WarnRueckblick.VERZUG_ARBEIT, maxAlter = 100)
    gemeinsam[WarnRueckblick.R_VIX] = r(ordner, "y_vix")
    gemeinsam[WarnRueckblick.R_VIX3M] = r(ordner, "y_vix3m")
    gemeinsam[WarnRueckblick.R_SP] = gspc
    gemeinsam[WarnRueckblick.R_NFCI] = r(ordner, "nfci", verzug = WarnRueckblick.VERZUG_NFCI, maxAlter = 21)
    gemeinsam[WarnRueckblick.R_CAPE] = r(ordner, "shiller_cape", verzug = WarnRueckblick.VERZUG_CAPE, maxAlter = 70)
    gemeinsam[WarnRueckblick.R_GEWINN] = r(ordner, "shiller_gewinn", verzug = WarnRueckblick.VERZUG_GEWINN, maxAlter = 200)
    gemeinsam[WarnRueckblick.R_ZINS10] = tnx
    gemeinsam[WarnRueckblick.R_OEL] = r(ordner, "eia_wti", verzug = WarnRueckblick.VERZUG_OEL, maxAlter = 10)
    gemeinsam[WarnRueckblick.R_ANLEIHEN] = r(ordner, "y_ief")
    gemeinsam[WarnRueckblick.R_KREDIT] = r(ordner, "fed_gz_spread", verzug = WarnRueckblick.VERZUG_KREDIT, maxAlter = 45)
    gemeinsam[WarnRueckblick.R_MARGIN] = r(ordner, "z1_margin", verzug = WarnRueckblick.VERZUG_Z1, maxAlter = 120)
    val wilshire = r(ordner, "y_w5000")
    gemeinsam[WarnRueckblick.R_WILSHIRE] = wilshire
    val z1Aktien = r(ordner, "z1_aktien", verzug = WarnRueckblick.VERZUG_Z1, maxAlter = 120)
    gemeinsam[WarnRueckblick.R_AKTIEN_WERT] = z1Aktien?.let { WarnRueckblick.angeschlossen(it, wilshire) }
    if (wilshire != null && z1Aktien != null) println("Anschluss Wilshire 5000 an die Finanzierungsrechnung: Faktor ${WarnRueckblick.anschlussFaktor(z1Aktien, wilshire)}")
    gemeinsam[WarnRueckblick.R_BIP] = r(ordner, "bea_bip", verzug = WarnRueckblick.VERZUG_BIP, maxAlter = 120)
    gemeinsam[WarnRueckblick.R_ANTRAEGE] = r(ordner, "dol_erstantraege_sa", verzug = WarnRueckblick.VERZUG_ANTRAEGE, maxAlter = 14)
    val cliUsa = r(ordner, "oecd_cli_usa", verzug = WarnRueckblick.VERZUG_CLI, maxAlter = 100)
    val cliDeu = r(ordner, "oecd_cli_deu", verzug = WarnRueckblick.VERZUG_CLI, maxAlter = 100)
    val kursD = reihe(ordner, "shiller_kurs")
    val divD = reihe(ordner, "shiller_dividende")
    val shillerRendite = if (kursD != null && divD != null) {
        val km = kursD.first.indices.associate { kursD.first[it] to kursD.second[it] }
        divD.first.indices.mapNotNull { i -> km[divD.first[i]]?.takeIf { it > 0 }?.let { divD.first[i] to divD.second[i] / it * 100 } }
            .let { z -> Reihe(z.map { it.first }.toLongArray(), z.map { it.second }.toDoubleArray(), 0, 60) }
    } else null

    fun eingabe(id: String, name: String, trend: String, cli: Reihe?, dividende: Boolean): WarnRueckblick.IndexEingabe? {
        val (tage, kurs) = reihe(ordner, name) ?: return null
        val reihen = gemeinsam.filterValues { it != null }.mapValues { it.value!! }.toMutableMap()
        reihen[WarnRueckblick.R_INDEX] = Reihe(tage, kurs, 0, 10)
        cli?.let { reihen[WarnRueckblick.R_CLI] = it }
        return WarnRueckblick.IndexEingabe(
            id, tage, kurs, ausgerichtet(irx, tage, 10),
            if (dividende) ausgerichtet(shillerRendite, tage, 60).map { if (it.isNaN()) 0.0 else it }.toDoubleArray() else null,
            reihen, trend,
        )
    }

    val indizes = listOfNotNull(
        eingabe("sp500", "y_gspc", "trend_sp500", cliUsa, true),
        eingabe("dax", "y_gdaxi", "trend_dax", cliDeu, false),
        eingabe("msciworld", "y_msciworld", "trend_msciworld", cliUsa, false),
    ).map { WarnRueckblick.indexErgebnis(it) }

    val liste = WarnRueckblick.zeichenListe()
    val info = liste.map { WarnRueckblick.ZeichenInfo(it.id, it.gruppe, it.warnAb, it.verzug, it.ersatz, it.revidiert, it.eigeneRegel) } +
        WarnRueckblick.ZUSAMMEN_AB.map { WarnRueckblick.ZeichenInfo("${WarnRueckblick.ZUSAMMEN}_$it", "zusammen", 3, 0) }
    val fehlend = listOf(
        "stlfsi4" to "FRED",
        "breite" to "KURZ", "ipo" to "KURZ", "konzentration" to "KEINE", "erwaehnung" to "KURZ", "gewinn" to "KEINE",
        "aaii" to "KEINE", "cnn" to "KURZ", "putcall" to "KEINE", "wetten" to "KEINE",
    ).map { WarnRueckblick.Fehlt(it.first, it.second) }
    val tageSp = gspc.tage
    val kettenListe = ketten?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString<List<WarnRueckblick.Kette>>(it) }.getOrNull() }.orEmpty()
    val datei = WarnRueckblick.Datei(
        LocalDate.now().toString(), LocalDate.ofEpochDay(tageSp.last()).toString(), WarnRueckblick.KOSTEN_WECHSEL, info, fehlend, indizes, kettenListe,
    )
    ziel.parentFile?.mkdirs()
    ziel.writeText(WarnRueckblick.schreiben(datei))
    println("geschrieben: $ziel (${ziel.length() / 1024} KB)")
    // Kurzbericht
    for (ix in indizes) {
        println("== ${ix.id}: ${ix.ereignisse.count { it.stufe == 20 }} Einbrueche, ${ix.ereignisse.count { it.stufe == 10 }} Rueckgaenge")
        for (e in ix.ereignisse) println("  ${LocalDate.ofEpochDay(e.hoch.toLong())} -> ${LocalDate.ofEpochDay(e.tief.toLong())} -${"%.1f".format(e.tiefe)}% [${e.stufe}] erholt=${e.erholt?.let { LocalDate.ofEpochDay(it.toLong()) }}")
        for (b in ix.zeichen) println(
            "  %-18s ab %s ausw %2d treffer %2d (%d-%d%%) phasen %2d tr %2d fehl %2d offen %d vorlauf %s zeit %.0f%% | folgen %s | halten %s".format(
                b.id, if (b.ab == 0) "-" else LocalDate.ofEpochDay(b.ab.toLong()).toString(), b.auswertbar, b.treffer, b.spanneVon, b.spanneBis,
                b.phasen, b.phasenTreffer, b.fehlalarme, b.offen, b.vorlaufMittel?.let { "%.1f/%.1f".format(it, b.vorlaufMin) } ?: "-", b.zeitAnteil * 100,
                b.folgen?.let { "${it.rendite}% ${it.maxRueck}% w${it.wechsel}" } ?: "-", b.halten?.let { "${it.rendite}% ${it.maxRueck}%" } ?: "-",
            ),
        )
        if (ix.id == "sp500") for (e in ix.ereignisse.filter { it.stufe == 20 }) println("  ${LocalDate.ofEpochDay(e.hoch.toLong())} " + e.stand.entries.joinToString(" ") { "${it.key}=${it.value}" })
    }
}
