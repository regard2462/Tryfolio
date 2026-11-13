package de.troetenrepublik.app.engine

// Calls und Puts eines Basiswerts aus dem Tagesbericht einer Terminboerse [0.76]: Umsatz des Tages und offene Kontrakte.
// Gezeigt wird der Umsatz; ist er zu klein, ersatzweise die offenen Kontrakte (dann beschriftet als solche).
data class OptionsZahlen(val callUmsatz: Long, val putUmsatz: Long, val callOffen: Long, val putOffen: Long) {

    // Gewaehlte Zahlen als (Calls, Puts) und ob es offene Kontrakte sind; null, wenn beides zu klein ist.
    fun wahl(mindestUmsatz: Long = MINDEST_UMSATZ, mindestOffen: Long = MINDEST_OFFEN): Pair<Pair<Long, Long>, Boolean>? = when {
        callUmsatz + putUmsatz >= mindestUmsatz -> (callUmsatz to putUmsatz) to false
        callOffen + putOffen >= mindestOffen -> (callOffen to putOffen) to true
        else -> null
    }

    companion object {
        const val MINDEST_UMSATZ = 10L
        const val MINDEST_OFFEN = 100L
    }
}
