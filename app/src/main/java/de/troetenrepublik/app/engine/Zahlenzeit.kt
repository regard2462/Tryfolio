package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Quartalszahlen

// Uhrzeit eines Termins mit Quartalszahlen (5.97): aus der Lage zur Hauptsitzung am Heimatplatz wird ein Zeitpunkt
// oder Zeitfenster, dazu die Uhrzeit in der Ortszeit des Nutzers.
object Zahlenzeit {

    enum class Art { GENAU, VOR, HANDEL, NACH, OFFEN }

    // von, bis: Zeitpunkte in ms seit 1970 (UTC); null = unbekannt
    data class Uhrzeit(val art: Art, val von: Long? = null, val bis: Long? = null)

    // Uhrzeit eines Termins: ein genauer Zeitpunkt schlaegt alles, OFFEN bleibt ohne Zeit, ohne Platz nur die Art.
    // Beginn und Schluss gelten am Heimatplatz an genau diesem Tag, die Sommerzeit ist damit beruecksichtigt.
    fun berechnen(tag: java.time.LocalDate, zeit: Quartalszahlen.Zeit, genau: Long?, platz: Quartalszahlen.Platz?): Uhrzeit {
        if (genau != null) return Uhrzeit(Art.GENAU, von = genau)
        if (zeit == Quartalszahlen.Zeit.OFFEN) return Uhrzeit(Art.OFFEN)
        if (platz == null) {
            val art = when (zeit) {
                Quartalszahlen.Zeit.VOR -> Art.VOR
                Quartalszahlen.Zeit.HANDEL -> Art.HANDEL
                Quartalszahlen.Zeit.NACH -> Art.NACH
                Quartalszahlen.Zeit.OFFEN -> Art.OFFEN
            }
            return Uhrzeit(art)
        }
        val beginn = java.time.ZonedDateTime.of(tag, platz.oeffnet, platz.zone).toInstant().toEpochMilli()
        val schluss = java.time.ZonedDateTime.of(tag, platz.schliesst, platz.zone).toInstant().toEpochMilli()
        return when (zeit) {
            Quartalszahlen.Zeit.VOR -> Uhrzeit(Art.VOR, bis = beginn)
            Quartalszahlen.Zeit.HANDEL -> Uhrzeit(Art.HANDEL, von = beginn, bis = schluss)
            Quartalszahlen.Zeit.NACH -> Uhrzeit(Art.NACH, von = schluss)
            Quartalszahlen.Zeit.OFFEN -> Uhrzeit(Art.OFFEN)
        }
    }

    // Ortszeit beim Nutzer: Stunde, Minute und Tagesversatz gegen tag (-1 Vortag, 0 gleicher Tag, +1 Folgetag)
    data class Ort(val stunde: Int, val minute: Int, val tagVersatz: Int)

    fun ort(ms: Long, tag: java.time.LocalDate, zone: java.time.ZoneId): Ort {
        val z = java.time.Instant.ofEpochMilli(ms).atZone(zone)
        return Ort(z.hour, z.minute, (z.toLocalDate().toEpochDay() - tag.toEpochDay()).toInt())
    }

    // "HH:mm" zweistellig
    fun hhmm(o: Ort): String = o.stunde.toString().padStart(2, '0') + ":" + o.minute.toString().padStart(2, '0')

    // Gemeinsame Uhrzeit aller Eintraege, wenn sie gleich sind; sonst null
    fun gemeinsam(liste: List<Uhrzeit>): Uhrzeit? = liste.firstOrNull()?.takeIf { e -> liste.all { it == e } }
}
