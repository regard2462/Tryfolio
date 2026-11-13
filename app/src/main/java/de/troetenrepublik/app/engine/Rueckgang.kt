package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TxType
import kotlin.math.abs

// Groesster Rueckgang des Depots in Prozent (negativ, 0 ohne Rueckgang), ohne Ein- und Auszahlungen. Analytics und Widget
// zeigen den Wert aus Portfolio Performance (Zeitraum Max, ausKurve): zeitgewichtete Kurve aus Kursen und Buchungen mit
// Cash. Der aufgezeichnete Verlauf gilt nur, solange diese Kurve fehlt (keine Kurse geladen).
object Rueckgang {

    private const val TAG = 86_400_000L

    // Der aufgezeichnete Verlauf beginnt mehr als zwei Tage nach der ersten Buchung.
    fun luecke(acc: Account): Boolean {
        val erste = acc.transactions.minOfOrNull { it.ts } ?: return false
        val beginn = acc.history.firstOrNull()?.ts ?: Long.MAX_VALUE
        return beginn > erste + 2 * TAG
    }

    // Beginn der Rechnung, wenn die aeltesten Buchungen fehlen: die erste Buchung ist ein Verkauf, ein Depotuebertrag oder
    // eine uebernommene Position (Anfangsbestand). Sonst null.
    fun unvollstaendigAb(acc: Account): Long? {
        val erste = acc.transactions.minByOrNull { it.ts } ?: return null
        val fehlt = (erste.type == TxType.SELL && !erste.verkaufteOption) || erste.tag == "TRANSFER_IN" || erste.via.startsWith("IMPORT")
        return if (fehlt) erste.ts else null
    }

    fun aufgezeichnet(acc: Account): Double {
        val fluesse = acc.transactions.mapNotNull { t -> Performanceanalyse.vonAussen(t).takeIf { it != 0.0 }?.let { t.ts to it } }
        return Performanceanalyse.maxRueckgangProzent(acc.history.map { it.ts to it.value }, fluesse)
    }

    // wertpapiere: Wert der Wertpapiere ueber die Zeit wie fuer das Leistungs-Werkzeug; null, wenn sich nichts rechnen laesst.
    fun ausKurve(wertpapiere: List<Pair<Long, Double>>, acc: Account, jetzt: Long): Double? {
        val e = Performanceanalyse.berechnen(wertpapiere, acc.transactions, acc.cash, 0L, jetzt) ?: return null
        return -abs(e.maxRueckgang) * 100.0
    }

    // Schluessel, solange sich weder Buchungen noch Bestand aendern.
    fun schluessel(acc: Account): String =
        "${acc.transactions.size}|${acc.positions.size}|${acc.positions.sumOf { it.qty }}|${acc.cash}|${luecke(acc)}"
}
