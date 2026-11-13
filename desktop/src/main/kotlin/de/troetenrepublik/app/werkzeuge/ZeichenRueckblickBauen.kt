package de.troetenrepublik.app.werkzeuge

import de.troetenrepublik.app.engine.WarnRueckblick
import de.troetenrepublik.app.engine.WarnRueckblick.Reihe
import de.troetenrepublik.app.engine.ZeichenRueckblick
import java.io.File
import java.time.LocalDate

// Baut assets/indikatoren/zeichen_rueckblick.json (Bodenzeichen an den grossen Tiefs des S&P 500, Messgroessen des
// Blasen-Detektors an den Hochs vor Einbruechen) aus den Rohreihen, die tools/warnrueckblick/holen.py in einen Ordner
// legt (Zeilen „JJJJ-MM-TT,Wert“). Rechnung in engine/ZeichenRueckblick.kt; die Datei traegt nur die Werte an den
// Hochs und Tiefs und die Zaehlung der Phasen, keine Reihen.
// Aufruf: gradlew :desktop:zeichenRueckblick -Pdaten=<Ordner> [-Pziel=<Datei>]

private fun reihe(ordner: File, name: String, verzug: Int = 0, maxAlter: Int = 10): Reihe? {
    val f = File(ordner, "$name.csv")
    if (!f.exists()) { System.err.println("fehlt: $name"); return null }
    val z = f.readLines().mapNotNull { l ->
        val p = l.split(',')
        if (p.size < 2) return@mapNotNull null
        val d = runCatching { LocalDate.parse(p[0]).toEpochDay() }.getOrNull() ?: return@mapNotNull null
        val w = p[1].toDoubleOrNull() ?: return@mapNotNull null
        d to w
    }.sortedBy { it.first }.distinctBy { it.first }
    return Reihe(z.map { it.first }.toLongArray(), z.map { it.second }.toDoubleArray(), verzug, maxAlter)
}

fun main(args: Array<String>) {
    val ordner = File(args[0])
    val ziel = File(args[1])
    val gspc = reihe(ordner, "y_gspc")!!
    val wilshire = reihe(ordner, "y_w5000")
    // Dieselben Reihen, Verzuege und Hoechstalter wie im Warnzeichen-Rueckblick (WarnzeichenRueckblickBauen.kt)
    val reihen = listOf(
        WarnRueckblick.R_SP to gspc,
        WarnRueckblick.R_VIX to reihe(ordner, "y_vix"),
        WarnRueckblick.R_ZINS10 to reihe(ordner, "y_tnx"),
        WarnRueckblick.R_CAPE to reihe(ordner, "shiller_cape", WarnRueckblick.VERZUG_CAPE, 70),
        WarnRueckblick.R_GEWINN to reihe(ordner, "shiller_gewinn", WarnRueckblick.VERZUG_GEWINN, 200),
        WarnRueckblick.R_MARGIN to reihe(ordner, "z1_margin", WarnRueckblick.VERZUG_Z1, 120),
        WarnRueckblick.R_AKTIEN_WERT to reihe(ordner, "z1_aktien", WarnRueckblick.VERZUG_Z1, 120)?.let { WarnRueckblick.angeschlossen(it, wilshire) },
        WarnRueckblick.R_WILSHIRE to wilshire,
        WarnRueckblick.R_BIP to reihe(ordner, "bea_bip", WarnRueckblick.VERZUG_BIP, 120),
        ZeichenRueckblick.R_ZINS_LANG to reihe(ordner, "shiller_zins", ZeichenRueckblick.VERZUG_ZINS_LANG, 70),
        ZeichenRueckblick.R_SPY to reihe(ordner, "y_spy"),
        ZeichenRueckblick.R_RSP to reihe(ordner, "y_rsp"),
        ZeichenRueckblick.R_IPO to reihe(ordner, "y_ipo"),
    ).mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    val d = ZeichenRueckblick.rechnen(gspc.tage, gspc.werte, WarnRueckblick.Quellen(reihen), LocalDate.ofEpochDay(gspc.tage.last()).toString())
    ziel.parentFile?.mkdirs()
    ziel.writeText(ZeichenRueckblick.schreiben(d))
    val aus = StringBuilder()
    fun tag(t: Int) = LocalDate.ofEpochDay(t.toLong()).toString()
    aus.appendLine("geschrieben: ${ziel.name} (${ziel.length()} Bytes), Stand ${d.stand}")
    aus.appendLine("== ${d.boeden.size} Boeden")
    for (b in d.boeden) {
        val (e, n) = ZeichenRueckblick.zaehlung(b)
        aus.appendLine("  ${tag(b.hoch)} -> ${tag(b.tief)} -${b.rueckgang}% danach ${b.danach} | $e von $n | " + b.werte.joinToString(" ") { "${it.id}=${it.wert}${it.zusatz?.let { z -> "/$z" } ?: ""}:${ZeichenRueckblick.lage(it)}" })
    }
    aus.appendLine("== Phasen")
    for (a in d.alarme) aus.appendLine("  ${a.id} ab ${tag(a.ab)} phasen ${a.phasen} mitBoden ${a.mitBoden} fehl ${a.fehlalarme} offen ${a.offen} | Boeden ${a.erfuellt}/${a.boeden} | Zeit ${a.zeit}")
    aus.appendLine("== offen ab ${ZeichenRueckblick.wenden(gspc.werte).offenAb?.let { LocalDate.ofEpochDay(gspc.tage[it]) }}")
    aus.appendLine("== ${d.blasen.size} Hochs")
    for (h in d.blasen) {
        val (r, n) = ZeichenRueckblick.zaehlung(h)
        aus.appendLine("  ${tag(h.hoch)} -> ${tag(h.tief)} -${h.rueckgang}% | $r von $n | " + h.werte.joinToString(" ") { "${it.id}=${it.wert}${it.zusatz?.let { z -> "/$z" } ?: ""}:${ZeichenRueckblick.ampel(it)}" })
    }
    print(aus)
    args.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { File(it).writeText(aus.toString()) }
}
