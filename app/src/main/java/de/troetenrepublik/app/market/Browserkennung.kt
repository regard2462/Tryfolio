package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Uhr

// Browser-Kennung der App: eine gewoehnliche Chrome-Kennung, deren Hauptfassung mit der Zeit mitlaeuft, damit sie auch
// nach Jahren ohne neue Fassung nicht auffaellig alt ist. Ein gewoehnlicher Chrome erhoeht seine Hauptfassung etwa alle
// vier Wochen. Ausgangspunkt ist die eingebaute Fassung am Baudatum; ein signiertes Quellen-Buendel kann einen neueren
// Ausgangspunkt nennen (browser.chrome ab browser.ab). Es gilt die hoehere der beiden Rechnungen.
object Browserkennung {
    const val CHROME_BASIS = 154
    const val CHROME_AB = "2026-11-09"
    const val TAGE_JE_FASSUNG = 28

    // Hoechstens so viele Fassungen weiterzaehlen (rund fuenf Jahre), danach bleibt die Zahl stehen.
    const val MAX_ZUWACHS = 65

    // Zahl, die ab dem Tag ab (JJJJ-MM-TT) gilt, weitergezaehlt bis jetzt; 0 bei unlesbarem Tag.
    fun hauptAb(basis: Int, ab: String, jetzt: Long): Int {
        val teile = ab.split('-').map { it.toIntOrNull() ?: return 0 }
        if (teile.size != 3 || basis !in 1..999) return 0
        val tag0 = runCatching { java.time.LocalDate.of(teile[0], teile[1], teile[2]).toEpochDay() }.getOrNull() ?: return 0
        val tage = Math.floorDiv(jetzt, 86_400_000L) - tag0
        if (tage <= 0) return basis
        return basis + (tage / TAGE_JE_FASSUNG).toInt().coerceAtMost(MAX_ZUWACHS)
    }

    // Hauptfassung zum Zeitpunkt jetzt: die hoehere aus eingebautem und Buendel-Ausgangspunkt.
    fun chromeHaupt(jetzt: Long = Uhr.jetzt()): Int {
        val eingebaut = hauptAb(CHROME_BASIS, CHROME_AB, jetzt)
        val b = Quellen.aktiv
        val ausBuendel = if (b.browserChrome > 0 && b.browserAb.isNotEmpty()) hauptAb(b.browserChrome, b.browserAb, jetzt) else 0
        return maxOf(eingebaut, ausBuendel)
    }

    fun windows(jetzt: Long = Uhr.jetzt()): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeHaupt(jetzt)}.0.0.0 Safari/537.36"

    fun android(jetzt: Long = Uhr.jetzt()): String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeHaupt(jetzt)}.0.0.0 Mobile Safari/537.36"

    // Safari auf dem iPhone: Hauptfassung des Systems, jaehrlich im September eine mehr (26 im September 2025).
    fun ios(jetzt: Long = Uhr.jetzt()): String {
        val tag = Math.floorDiv(jetzt, 86_400_000L)
        val d = java.time.LocalDate.ofEpochDay(tag)
        val major = (26 + (d.year - 2025) - if (d.monthValue < 9) 1 else 0).coerceIn(26, 60)
        return "Mozilla/5.0 (iPhone; CPU iPhone OS ${major}_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/$major.0 Mobile/15E148 Safari/604.1"
    }
}
