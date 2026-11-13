package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction

// Eine Zaehlregel fuer die eigenen abgeschlossenen Verkaeufe (0.78), genutzt vom Depotverlauf, vom Anlegerprofil im
// Depot-Check und vom Handelskalender: Es zaehlt jede Buchung, die eine Position schliesst (Verkauf, Teilverkauf,
// Rueckkauf einer verkauften Option, Knock-out, Verfall), mit ihrem realisierten Ergebnis in Euro vor Gebuehren und
// Steuern. Ein Ergebnis unter einem halben Cent ist weder Gewinn noch Verlust. Trefferquote und Gewinnfaktor gibt es ab
// MIN_VERKAEUFE Verkaeufen mit Ergebnis.
object Verkaufsbilanz {

    const val MIN_VERKAEUFE = 5
    const val SCHWELLE = 0.005

    fun zaehlt(tx: Transaction): Boolean = tx.schliesst

    fun gewinn(realisiert: Double): Boolean = realisiert > SCHWELLE
    fun verlust(realisiert: Double): Boolean = realisiert < -SCHWELLE

    // Ergebnisse der zaehlenden Buchungen in Euro.
    fun ergebnisse(txs: List<Transaction>): List<Double> = txs.filter { zaehlt(it) }.map { it.realized }

    // null unter MIN_VERKAEUFE Verkaeufen mit Ergebnis.
    fun auswerten(txs: List<Transaction>): Risiko.Verkaeufe? = Risiko.verkaeufe(ergebnisse(txs))
}
