package de.troetenrepublik.app.engine

import java.util.Locale
import kotlin.math.abs

// Kurszeile unter einer Mitteilung zu einem Wert: "262,30 € · +1,2 % heute". Rein, ohne Zugriff auf Anzeige und Netz.
object Kursmeldung {
    private const val TAG_MS = 86_400_000L

    // Kurs und Vortagesschluss in derselben (Anzeige-)Waehrung; null, wenn eines von beiden fehlt.
    fun zeile(kurs: Double, vortag: Double, symbol: String, de: Boolean, heute: String, bezeichnung: String? = null): String? {
        if (!(vortag > 0.0) || !vortag.isFinite() || !(kurs > 0.0) || !kurs.isFinite()) return null
        return zeileProzent(kurs, (kurs / vortag - 1.0) * 100.0, symbol, de, heute, bezeichnung)
    }

    // Wie zeile, mit fertiger Veraenderung in Prozent (die Zahl der Nachrichtenliste).
    fun zeileProzent(kurs: Double, prozent: Double?, symbol: String, de: Boolean, heute: String, bezeichnung: String? = null): String? {
        if (prozent == null || !prozent.isFinite() || !(kurs > 0.0) || !kurs.isFinite()) return null
        val teile = listOfNotNull(
            listOfNotNull(bezeichnung?.takeIf { it.isNotBlank() }, preis(kurs, symbol, de)).joinToString(" "),
            "${vorzeichenProzent(prozent, de)} $heute",
        )
        return teile.joinToString(" · ")
    }

    // Nur die Veraenderung, wenn der Kurs schon im Text steht.
    fun nurVeraenderung(prozent: Double?, de: Boolean, heute: String): String? =
        if (prozent == null || !prozent.isFinite()) null else "${vorzeichenProzent(prozent, de)} $heute"

    // Mehrere Werte in einer Zeile, ohne Kurs: "NVDA +1,2 % · AMD −0,8 %"; Werte ohne Veraenderung entfallen.
    fun mehrere(werte: List<Pair<String, Double?>>, de: Boolean, max: Int = 3): String? =
        werte.mapNotNull { (k, p) -> if (p == null || !p.isFinite()) null else "$k ${vorzeichenProzent(p, de)}" }
            .take(max).joinToString(" · ").ifEmpty { null }

    // Gilt der Kurs noch? Hoechstens ein Handelstag (Werktag) zwischen Kurszeit und jetzt; Wochenende zaehlt nicht.
    fun aktuell(kursZeit: Long, jetzt: Long): Boolean {
        if (kursZeit <= 0L || jetzt < kursZeit) return false
        val von = Math.floorDiv(kursZeit, TAG_MS)
        val bis = Math.floorDiv(jetzt, TAG_MS)
        var werktage = 0
        for (t in von + 1..bis) if (Math.floorMod(t + 3, 7L) < 5) werktage++
        return werktage <= 1
    }

    // +1,2 % / −0,8 % / 0,0 %: eine Nachkommastelle, was auf null rundet bekommt kein Vorzeichen.
    fun vorzeichenProzent(p: Double, de: Boolean): String {
        val zehntel = Math.round(abs(p) * 10.0)
        val zahl = String.format(if (de) Locale.GERMANY else Locale.US, "%,.1f", zehntel / 10.0)
        val v = if (zehntel == 0L) "" else if (p > 0) "+" else "−"
        return v + zahl + if (de) " %" else "%"
    }

    private fun preis(kurs: Double, symbol: String, de: Boolean): String {
        val stellen = if (kurs >= 1.0) 2 else if (kurs >= 0.01) 4 else 6
        val zahl = String.format(if (de) Locale.GERMANY else Locale.US, "%,.${stellen}f", kurs)
        return if (de) "$zahl $symbol" else if (symbol.length == 1) "$symbol$zahl" else "$zahl $symbol"
    }
}
