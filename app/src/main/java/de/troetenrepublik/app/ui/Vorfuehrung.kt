package de.troetenrepublik.app.ui

// Nur fuer die Aufnahme der Anleitungsfotos: der Test setzt den Schalter vor dem Start. Solange er an ist, laden
// weder Firmen- und Quellenlogos noch Personen-, Markt- oder sonstige Fremdbilder; es erscheinen Buchstaben,
// Initialen und neutrale Platzhalter. Im normalen Betrieb bleibt er aus, es gibt keine Einstellung dafuer.
object Vorfuehrung {
    @Volatile var aktiv: Boolean = false
}
