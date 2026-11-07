package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Einzahlplan
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Monatliche virtuelle Einzahlung (seit 5.91). Gebucht wird wie eine normale Einzahlung, mit via = "PLAN".
// Der Taktgeber ruft ausfuehren vor den Sparplaenen auf, damit das Geld am selben Tag schon fuer sie bereitsteht.
// Verpasste Monate (App lange nicht offen) werden nachgeholt, jeder Monat genau einmal.
object Einzahlplaene {

    // Hoechstens so viele Monate werden auf einmal nachgeholt.
    const val MAX_NACHHOLEN = 24

    private fun datum(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

    // Monat als Jahr x 12 + Monat, aufeinanderfolgende Monate unterscheiden sich um 1.
    fun monat(ts: Long): Int = datum(ts).let { it.year * 12 + it.monthValue }

    private fun monat(d: LocalDate): Int = d.year * 12 + d.monthValue

    // Ausfuehrungstag im Monat m; in kurzen Monaten der letzte Tag.
    private fun faelligTag(tag: Int, m: Int): Int {
        val jahr = (m - 1) / 12
        val mon = (m - 1) % 12 + 1
        return minOf(tag.coerceIn(1, 31), YearMonth.of(jahr, mon).lengthOfMonth())
    }

    // Letzter Monat, der beim Anlegen oder Fortsetzen als erledigt gilt: Ist der Tag in diesem Monat schon
    // erreicht oder vorbei, geht es im naechsten Monat los, sonst noch in diesem.
    fun startMonat(tag: Int, now: Long): Int {
        val d = datum(now)
        val m = monat(d)
        return if (d.dayOfMonth < faelligTag(tag, m)) m - 1 else m
    }

    fun neu(betrag: Double, tag: Int, now: Long): Einzahlplan =
        Einzahlplan(betrag = betrag, tag = tag.coerceIn(1, 31), letzterMonat = startMonat(tag, now), erstellt = now)

    // Nach einer Pause: die Monate der Pause werden nicht nachgeholt.
    fun fortsetzen(plan: Einzahlplan, now: Long): Einzahlplan =
        plan.copy(pausiert = false, letzterMonat = maxOf(plan.letzterMonat, startMonat(plan.tag, now)))

    // Betrag oder Tag aendern; bereits gebuchte Monate bleiben gebucht.
    fun aendern(plan: Einzahlplan, betrag: Double, tag: Int, now: Long): Einzahlplan =
        plan.copy(betrag = betrag, tag = tag.coerceIn(1, 31), letzterMonat = maxOf(plan.letzterMonat, startMonat(tag, now)))

    // Monate, die zum Zeitpunkt now zu buchen sind, aufsteigend. Leer bei Pause oder ungueltigem Betrag.
    fun faelligeMonate(plan: Einzahlplan, now: Long): List<Int> {
        if (plan.pausiert || !plan.betrag.isFinite() || plan.betrag <= 0) return emptyList()
        val d = datum(now)
        val jetzt = monat(d)
        val bis = if (d.dayOfMonth >= faelligTag(plan.tag, jetzt)) jetzt else jetzt - 1
        if (bis <= plan.letzterMonat) return emptyList()
        return (maxOf(plan.letzterMonat + 1, bis - MAX_NACHHOLEN + 1)..bis).toList()
    }

    // Naechster Ausfuehrungstag (Mitternacht, Ortszeit); 0 bei Pause.
    fun naechste(plan: Einzahlplan, now: Long): Long {
        if (plan.pausiert) return 0L
        val m = maxOf(plan.letzterMonat + 1, monat(now))
        val jahr = (m - 1) / 12
        val mon = (m - 1) % 12 + 1
        return LocalDate.of(jahr, mon, faelligTag(plan.tag, m)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    // Bucht alle faelligen Monate als Einzahlung.
    fun ausfuehren(account: Account, now: Long): Account {
        val plan = account.einzahlplan ?: return account
        val monate = faelligeMonate(plan, now)
        if (monate.isEmpty()) return account
        var a = account
        for (m in monate) a = Trading.deposit(a, plan.betrag, now, via = "PLAN")
        return a.copy(
            einzahlplan = plan.copy(
                letzterMonat = monate.last(),
                ausfuehrungen = plan.ausfuehrungen + monate.size,
                summe = plan.summe + plan.betrag * monate.size,
            ),
        )
    }
}
