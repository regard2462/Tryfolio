package de.troetenrepublik.app.engine

// Zeitraeume des Werkzeugs „Rueckblick und Vergleich“: feste Laengen in Tagen (0 = Max) oder ein eigener Zeitraum.
object RueckblickZeitraum {
    private const val TAG = Zeitraum.TAG

    // 1, 3, 6 Monate, 1, 3, 5, 10 Jahre
    private val FEST = listOf(30, 91, 182, 365, 1096, 1826, 3650)

    // Angebot zur vorhandenen Geschichte: nur Laengen, die kuerzer als die Reihe sind, dahinter immer Max.
    fun angebot(geschichteTage: Long): List<Int> = FEST.filter { it < geschichteTage - 5 } + 0

    // Beginn: eigener Starttag (Tage seit 1970) oder jetzt minus die Laenge, nie vor dem ersten Kurs; 0 Tage = erster Kurs.
    fun beginn(erster: Long, jetzt: Long, tage: Int, eigenVonTag: Long?): Long = when {
        eigenVonTag != null -> maxOf(erster, eigenVonTag * TAG)
        tage <= 0 -> erster
        else -> maxOf(erster, jetzt - tage * TAG)
    }

    // Ende: Schluss des gewaehlten letzten Tages, hoechstens jetzt.
    fun ende(jetzt: Long, eigenBisTag: Long?): Long = eigenBisTag?.let { minOf(jetzt, it * TAG + TAG - 1) } ?: jetzt
}
