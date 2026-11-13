package de.troetenrepublik.app.engine

import java.time.LocalDate

// Zentrale Uhr: Alles, was vom Datum abhaengt und geprueft werden soll, fragt hier statt bei der Systemuhr. Im Betrieb
// ist das die Systemuhr; Tests stellen sie vor (Zeitreise).
object Uhr {
    @Volatile private var versatzMs = 0L

    fun jetzt(): Long = System.currentTimeMillis() + versatzMs

    // Heutiges Datum nach UTC.
    fun heute(): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(jetzt(), 86_400_000L))

    // Nur fuer Tests: die Uhr um tage Tage vorstellen (0: Systemuhr).
    internal fun vorstellenTage(tage: Long) { versatzMs = tage * 86_400_000L }
}
