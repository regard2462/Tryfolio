package de.troetenrepublik.app.engine

// Kleine Hilfen fuer Vergleichstabellen und Kernaussagen der Abschnittszeilen (Strategietest, Rotation).
object Vergleich {
    // Schmalste Wertspalte und Polster links davon, in dp
    const val MIN_SPALTE_DP = 80f
    const val POLSTER_DP = 8f
    // Farbpunkt (8 dp) und Abstand (4 dp) vor dem Spaltennamen
    const val PUNKT_DP = 12f

    // Breite einer Wertspalte aus dem breitesten Wert der Spalte (und damit nie schmaler als der Kopf zulaesst).
    fun spaltenBreite(breitesterWertDp: Float): Float = maxOf(MIN_SPALTE_DP, breitesterWertDp + POLSTER_DP)

    // Index des groessten vorhandenen Werts, null ohne Werte.
    fun bester(werte: List<Double?>): Int? {
        var best: Int? = null
        werte.forEachIndexed { i, v -> if (v != null && (best == null || v > werte[best!!]!!)) best = i }
        return best
    }

    // Jede Zeile einer Vergleichstabelle braucht je Spalte einen Wert; fehlende werden zu einem Strich aufgefuellt.
    fun auffuellen(werte: List<String>, spalten: Int): List<String> =
        if (werte.size >= spalten) werte.take(spalten) else werte + List(spalten - werte.size) { "–" }
}
