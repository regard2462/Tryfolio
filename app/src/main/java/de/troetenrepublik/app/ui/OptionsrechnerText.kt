package de.troetenrepublik.app.ui

import de.troetenrepublik.app.engine.Optionsrechner
import de.troetenrepublik.app.engine.Optionsrechner.Bein
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import de.troetenrepublik.app.engine.OptionsrechnerPlan
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Gewinn
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Satzbausteine des Optionsrechners aus den echten Zahlen: Kernaussage und Klartextzeile zur Strategie. Zahlen und
// Datum haengen ueber geschuetzte Leerzeichen zusammen und brechen nie um.
private const val USD = "USD"

private fun fest(t: String) = t.replace(' ', ' ')
private fun kurs(v: Double) = fest(Fmt.price(v, USD))
private fun geld(v: Double) = fest(Fmt.moneyIn(abs(v), USD, 0))

// Ein Satz: Einsatz oder Einnahme, Gewinnbereich am Verfallstag, groesster Gewinn, groesster Verlust.
internal fun kernSatz(name: String, tag: String, kurs: Double, e: Optionsrechner.Ergebnis, beine: List<Bein>): String {
    val t = fest(tag)
    val n = fest(name)
    fun pct(a: Double) = fest(Fmt.signedPct((a / kurs - 1) * 100, 1))
    val teile = ArrayList<String>()
    teile += if (e.einsatz >= 0) S.orKernKosten(geld(e.einsatz)) else S.orKernEinnahme(geld(e.einsatz))
    teile += when (val g = OptionsrechnerPlan.gewinnbereich(beine, e.breakEven)) {
        is Gewinn.Ueber -> S.orKernGewinnUeber(n, t, kurs(g.a), pct(g.a))
        is Gewinn.Unter -> S.orKernGewinnUnter(n, t, kurs(g.a), pct(g.a))
        is Gewinn.Zwischen -> S.orKernGewinnZwischen(n, t, kurs(g.a), kurs(g.b))
        is Gewinn.Ausserhalb -> S.orKernGewinnAusserhalb(n, t, kurs(g.a), kurs(g.b))
        Gewinn.Immer -> S.orKernGewinnImmer
        Gewinn.Nie -> S.orKernGewinnNie
    }
    e.maxGewinn.wert?.takeIf { it > 0.5 }?.let { teile += S.orKernMaxGewinn(geld(it)) }
    val v = e.maxVerlust.wert
    if (v == null) teile += S.orKernVerlustUnbegrenzt else if (v < -0.5) teile += S.orKernMaxVerlust(geld(v))
    return teile.joinToString(" ")
}

// Eine Zeile zur gewaehlten Vorlage, null bei eigenen Optionen oder fehlenden Zahlen.
internal fun klarText(v: Vorlage, e: Optionsrechner.Ergebnis, beine: List<Bein>): String? {
    val be = e.breakEven
    val einnahme = geld(e.einsatz)
    val gewinn = e.maxGewinn.wert?.let { geld(it) }
    val s = when (v) {
        Vorlage.LONG_CALL, Vorlage.LONG_PUT -> be.firstOrNull()?.let { listOf(kurs(it), "", "") }
        Vorlage.COVERED_CALL -> beine.firstOrNull { it.art == Optionsrechner.Art.CALL }?.let { listOf(einnahme, kurs(it.strike), "") }
        Vorlage.CASH_SECURED_PUT -> be.firstOrNull()?.let { listOf(einnahme, kurs(it), "") }
        Vorlage.BULL_CALL_SPREAD, Vorlage.BEAR_PUT_SPREAD -> if (be.isNotEmpty() && gewinn != null) listOf(kurs(be.first()), gewinn, "") else null
        Vorlage.STRADDLE, Vorlage.STRANGLE -> if (be.size >= 2) listOf(kurs(be.last()), kurs(be.first()), "") else null
        Vorlage.IRON_CONDOR -> if (be.size >= 2) listOf(einnahme, kurs(be.first()), kurs(be.last())) else null
        Vorlage.BUTTERFLY -> if (be.size >= 2 && gewinn != null) listOf(kurs(be.first()), kurs(be.last()), gewinn) else null
        Vorlage.COLLAR -> {
            val put = beine.firstOrNull { it.art == Optionsrechner.Art.PUT }
            val call = beine.firstOrNull { it.art == Optionsrechner.Art.CALL }
            if (put != null && call != null) listOf(kurs(put.strike), kurs(call.strike), "") else null
        }
        Vorlage.SCHUTZ_PUT -> beine.firstOrNull { it.art == Optionsrechner.Art.PUT }?.let { listOf(kurs(it.strike), einnahme, "") }
        Vorlage.EIGENE -> null
    } ?: return null
    return S.orKlar(v.name, s[0], s[1], s[2]).takeIf { it.isNotBlank() }
}

// Verfall in der Auswahl: Tag und Monat, in einem anderen Jahr mit Jahr, etwa „15. Jan. 27“.
internal fun terminText(verfallS: Long): String {
    val ms = verfallS * 1000
    val jahr = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).year
    val jetzt = java.time.LocalDate.now().year
    return if (jahr == jetzt) Fmt.dayMonth(ms) else Fmt.dayMonth(ms) + " " + Fmt.shortDate(ms).takeLast(2)
}
