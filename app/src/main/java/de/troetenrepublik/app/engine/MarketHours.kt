package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Handelszeiten, Montag bis Freitag. In Deutschland (Berliner Zeit): Aktien und ETFs 7:30 bis 23:00 ueber LS Exchange,
// Hebelprodukte 8:00 bis 22:00, Kryptowerte rund um die Uhr. Sonst je nach Einstellung die Hauptsitzung der Heimatboerse
// eines anderen Landes in dessen Ortszeit, eigene Zeiten oder rund um die Uhr an allen Tagen.
object MarketHours {

    data class Window(val open: LocalTime, val close: LocalTime, val label: String)

    // Handelszeiten eines Landes oder einer Einstellung; aktien null = rund um die Uhr.
    data class Zeiten(
        val zone: ZoneId,
        val aktien: Window?,
        val derivate: Window?,
        // Fenster der Depotkurve.
        val depot: Window,
        // Auch Samstag und Sonntag offen.
        val wochenende: Boolean = false,
        // Boersenfeiertage in Ortszeit (seit 5.94 fuer LS Exchange und die US-Boersen).
        val feiertag: (java.time.LocalDate) -> Boolean = { false },
        // Vorzeitiger Schluss an einem verkuerzten Handelstag in Ortszeit, sonst null (seit 0.78 fuer die US-Boersen).
        val schluss: (java.time.LocalDate) -> LocalTime? = { null },
    )

    private fun fenster(von: LocalTime, bis: LocalTime) = Window(von, bis, "${von.hour}:${"%02d".format(von.minute)}–${bis.hour}:${"%02d".format(bis.minute)}")

    val DEUTSCHLAND = Zeiten(
        ZoneId.of("Europe/Berlin"),
        fenster(LocalTime.of(7, 30), LocalTime.of(23, 0)),
        fenster(LocalTime.of(8, 0), LocalTime.of(22, 0)),
        // Fenster der Depotkurve: werktags 8 bis 22 Uhr. Der vor- und nachboerliche Handel der US-Plaetze
        // (10 bis 15:30 Uhr und 22 bis 2 Uhr) und die Wochenenden der Kryptowerte bleiben draussen; die Kurve beginnt
        // jeden Tag um 8 Uhr mit den deutschen Notierungen.
        fenster(LocalTime.of(8, 0), LocalTime.of(22, 0)),
        feiertag = ::lsFeiertag,
    )

    // Kein Handel bei LS Exchange: Neujahr, Karfreitag, Ostermontag, 1. Mai, 24. bis 26. und 31. Dezember
    // (ls-x.de, Handelszeiten). Der verkuerzte Handel am letzten Handelstag des Jahres ist nicht nachgebildet.
    fun lsFeiertag(d: java.time.LocalDate): Boolean {
        val ostern = WeitereQuellen.ostern(d.year)
        return (d.monthValue == 1 && d.dayOfMonth == 1) || d == ostern.minusDays(2) || d == ostern.plusDays(1) ||
            (d.monthValue == 5 && d.dayOfMonth == 1) || (d.monthValue == 12 && d.dayOfMonth in setOf(24, 25, 26, 31))
    }

    @Volatile var zeiten: Zeiten = DEUTSCHLAND

    // Arten der Handelszeiten in den Einstellungen.
    const val LAND = "land"
    const val EIGENE = "eigene"
    const val IMMER = "immer"

    // Handelszeiten fuer land setzen: modus LAND (Zeiten der Heimatboerse), EIGENE (werktags vonMin bis
    // bisMin Minuten nach Mitternacht Ortszeit) oder IMMER (rund um die Uhr, auch am Wochenende).
    fun einstellen(land: String, modus: String, vonMin: Int, bisMin: Int) {
        zeiten = zeitenFuer(land, modus, vonMin, bisMin)
    }

    fun zeitenFuer(land: String, modus: String, vonMin: Int, bisMin: Int): Zeiten {
        val l = Regionen.land(land)
        val zone = l.zoneId
        return when (modus) {
            IMMER -> Zeiten(zone, null, null, fenster(LocalTime.MIN, LocalTime.MAX).copy(label = "0:00–24:00"), wochenende = true)
            EIGENE -> {
                val von = LocalTime.of((vonMin / 60).coerceIn(0, 23), (vonMin % 60).coerceIn(0, 59))
                val bis = if (bisMin >= 24 * 60) LocalTime.MAX else LocalTime.of((bisMin / 60).coerceIn(0, 23), (bisMin % 60).coerceIn(0, 59))
                val w = if (bis.isAfter(von)) fenster(von, bis) else fenster(von, LocalTime.MAX)
                Zeiten(zone, w, w, w)
            }
            else -> if (l.code == Regionen.DE.code) DEUTSCHLAND
            else Regionen.sitzung(l).let { (von, bis) -> fenster(von, bis) }.let { w ->
                if (l.code == "US") Zeiten(zone, w, w, w, feiertag = WeitereQuellen::usFeiertag, schluss = { if (Boersenzeiten.verkuerztUs(it)) Boersenzeiten.US_KURZ_SCHLUSS else null })
                else Zeiten(zone, w, w, w)
            }
        }
    }

    val zone: ZoneId get() = zeiten.zone

    // Aktien und ETFs; rund um die Uhr ein Fenster ueber den ganzen Tag.
    val STOCKS: Window get() = zeiten.aktien ?: zeiten.depot
    val DERIVATIVES: Window get() = zeiten.derivate ?: zeiten.depot
    val XETRA = Window(LocalTime.of(9, 0), LocalTime.of(17, 30), "9:00–17:30")

    val DEPOT: Window get() = zeiten.depot

    fun imDepotFenster(ts: Long): Boolean = isOpen(DEPOT, ts)

    // Beginn und Ende des Depotfensters an dem Kalendertag (Ortszeit), in den ts faellt.
    fun depotFenster(ts: Long): Pair<Long, Long> {
        val tag = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return tag.atTime(DEPOT.open).atZone(zone).toInstant().toEpochMilli() to tag.atTime(schlussAm(DEPOT, tag)).atZone(zone).toInstant().toEpochMilli()
    }

    // Schluss des Fensters an diesem Tag: an einem verkuerzten Handelstag der vorzeitige, sonst der uebliche.
    fun schlussAm(w: Window, tag: java.time.LocalDate): LocalTime =
        zeiten.schluss(tag)?.takeIf { it.isBefore(w.close) && it.isAfter(w.open) } ?: w.close

    fun windowFor(type: AssetType?, derivative: Boolean): Window? = when {
        derivative -> zeiten.derivate
        type == AssetType.CRYPTO -> null
        else -> zeiten.aktien
    }

    fun isOpen(type: AssetType?, derivative: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val w = windowFor(type, derivative) ?: return true
        return isOpen(w, now)
    }

    fun isOpen(w: Window, now: Long): Boolean {
        val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        if (!zeiten.wochenende && (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY)) return false
        if (zeiten.feiertag(t.toLocalDate())) return false
        val lt = t.toLocalTime()
        return !lt.isBefore(w.open) && lt.isBefore(schlussAm(w, t.toLocalDate()))
    }

    fun nextOpen(w: Window, now: Long): Long {
        var t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        repeat(12) {
            val candidate = t.with(w.open)
            val weekday = (zeiten.wochenende || (t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY)) && !zeiten.feiertag(t.toLocalDate())
            if (weekday && candidate.toInstant().toEpochMilli() > now) return candidate.toInstant().toEpochMilli()
            t = t.plusDays(1).with(w.open)
        }
        return now
    }

    // Ende des laufenden Handelstags fuer tagesgueltige Orders.
    fun endOfTradingDay(w: Window, now: Long): Long {
        val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        return t.with(schlussAm(w, t.toLocalDate())).toInstant().toEpochMilli()
    }

    // Schluss des Handelstags, an dem eine zu ts aufgegebene tagesgueltige Order gilt: der laufende, bei geschlossener Boerse
    // der naechste Handelstag (vor der Eroeffnung noch der heutige).
    fun schlussDesHandelstags(w: Window, ts: Long): Long =
        if (isOpen(w, ts)) endOfTradingDay(w, ts) else endOfTradingDay(w, nextOpen(w, ts))

    // Handelsstatus fuer die Oberflaeche; die Beschriftung wird dort in der gewaehlten Sprache erzeugt.
    data class Status(
        // null = rund um die Uhr.
        val window: Window?,
        val open: Boolean,
        // Naechster Handelsbeginn, wenn geschlossen.
        val nextOpen: Long,
        // true, wenn der naechste Handelsbeginn noch heute ist.
        val opensToday: Boolean,
        val nextOpenWeekday: DayOfWeek?,
        // Vorzeitiger Schluss heute (verkuerzter Handelstag), nur bei offener Boerse gesetzt.
        val kurzSchluss: LocalTime? = null,
    )

    fun status(type: AssetType?, derivative: Boolean, now: Long = System.currentTimeMillis()): Status {
        val w = windowFor(type, derivative) ?: return Status(null, true, 0L, false, null)
        if (isOpen(w, now)) {
            val tag = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone).toLocalDate()
            return Status(w, true, 0L, false, null, schlussAm(w, tag).takeIf { it != w.close })
        }
        val next = nextOpen(w, now)
        val nextZ = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        val today = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone).toLocalDate()
        return Status(w, false, next, nextZ.toLocalDate() == today, nextZ.dayOfWeek)
    }

    // Beginn des Kalendertags in der Zeitzone der Handelszeiten.
    fun dayStart(ts: Long): Long =
        java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
