package de.troetenrepublik.app.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Haupthandelszeiten der Leitboerse je Land (Kuerzel alpha-2), lokal gerechnet, ohne Netz. Boersenfeiertage kennt der
// Code als feste Regeln fuer die USA, Deutschland, Grossbritannien, Japan und die Euronext-Plaetze Paris, Amsterdam,
// Bruessel und Lissabon (siehe feiertag), verkuerzte Handelstage nur fuer die USA (verkuerztUs). Die Zeiten stammen aus den Handelskalendern der Boersen (Stand Herbst 2026) und sind
// auf die Hauptsitzung begrenzt (kein Vor- und Nachhandel).
object Boersenzeiten {
    private val MO_FR = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val SO_DO = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

    // Mehrere Sitzungen am Tag (Mittagspause) stehen als mehrere Paare. sitzungenAm liefert sie fuer ein Datum, weil
    // einzelne Boersen freitags oder mit der US-Sommerzeit andere Zeiten haben.
    class Handel(val name: String, zone: String, val sitzungenAm: (LocalDate) -> List<Pair<LocalTime, LocalTime>>, val tage: Set<DayOfWeek> = MO_FR) {
        val zone: ZoneId = ZoneId.of(zone)
        // Zeiten eines gewoehnlichen Mittwochs im Winter
        val sitzungen: List<Pair<LocalTime, LocalTime>> get() = sitzungenAm(LocalDate.of(2026, 11, 4))
    }

    // Brasilien und Argentinien richten sich nach der US-Sommerzeit (B3: ab 3.11.2026 bis 17:55, von Maerz bis November bis
    // 16:55; BYMA entsprechend 18:00 und 17:00).
    private val NEW_YORK = ZoneId.of("America/New_York")
    private fun usSommer(d: LocalDate) = NEW_YORK.rules.isDaylightSavings(d.atTime(12, 0).atZone(NEW_YORK).toInstant())

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun h(name: String, zone: String, von: LocalTime, bis: LocalTime, tage: Set<DayOfWeek> = MO_FR) = Handel(name, zone, { listOf(von to bis) }, tage)
    private fun h2(name: String, zone: String, a: LocalTime, b: LocalTime, c: LocalTime, d: LocalTime) = Handel(name, zone, { listOf(a to b, c to d) })
    private fun frei(name: String, zone: String, f: (LocalDate) -> List<Pair<LocalTime, LocalTime>>, tage: Set<DayOfWeek> = MO_FR) = Handel(name, zone, f, tage)

    val NACH_LAND: Map<String, Handel> = linkedMapOf(
        "US" to frei("NYSE, Nasdaq", "America/New_York", { listOf(t(9, 30) to (if (verkuerztUs(it)) US_KURZ_SCHLUSS else t(16))) }),
        "CA" to h("Toronto Stock Exchange", "America/Toronto", t(9, 30), t(16)),
        "MX" to h("Bolsa Mexicana de Valores", "America/Mexico_City", t(8, 30), t(15)),
        "BR" to frei("B3 São Paulo", "America/Sao_Paulo", { listOf(t(10) to (if (usSommer(it)) t(16, 55) else t(17, 55))) }),
        "AR" to frei("Bolsas y Mercados Argentinos", "America/Argentina/Buenos_Aires", { listOf(t(10, 30) to (if (usSommer(it)) t(17) else t(18))) }),
        "CL" to h("Bolsa de Santiago", "America/Santiago", t(9, 30), t(16)),
        "CO" to h("Bolsa de Valores de Colombia", "America/Bogota", t(9, 30), t(16)),
        "GB" to h("London Stock Exchange", "Europe/London", t(8), t(16, 30)),
        "IE" to h("Euronext Dublin", "Europe/Dublin", t(8), t(16, 30)),
        "DE" to h("Xetra Frankfurt", "Europe/Berlin", t(9), t(17, 30)),
        "FR" to h("Euronext Paris", "Europe/Paris", t(9), t(17, 30)),
        "NL" to h("Euronext Amsterdam", "Europe/Amsterdam", t(9), t(17, 30)),
        "BE" to h("Euronext Brüssel", "Europe/Brussels", t(9), t(17, 30)),
        "PT" to h("Euronext Lissabon", "Europe/Lisbon", t(8), t(16, 30)),
        "IT" to h("Borsa Italiana", "Europe/Rome", t(9), t(17, 30)),
        "ES" to h("Bolsa de Madrid", "Europe/Madrid", t(9), t(17, 30)),
        "CH" to h("SIX Swiss Exchange", "Europe/Zurich", t(9), t(17, 30)),
        "AT" to h("Wiener Börse", "Europe/Vienna", t(9), t(17, 30)),
        "SE" to h("Nasdaq Stockholm", "Europe/Stockholm", t(9), t(17, 30)),
        "DK" to h("Nasdaq Kopenhagen", "Europe/Copenhagen", t(9), t(17)),
        "FI" to h("Nasdaq Helsinki", "Europe/Helsinki", t(10), t(18, 30)),
        "NO" to h("Oslo Børs", "Europe/Oslo", t(9), t(16, 20)),
        "PL" to h("Warschauer Börse", "Europe/Warsaw", t(9), t(17)),
        "CZ" to h("Prager Börse", "Europe/Prague", t(9), t(16, 20)),
        "HU" to h("Budapester Börse", "Europe/Budapest", t(9), t(17)),
        "GR" to h("Athens Exchange", "Europe/Athens", t(10), t(17, 20)),
        "TR" to h("Borsa Istanbul", "Europe/Istanbul", t(10), t(18)),
        // seit 5.1.2026 Montag bis Freitag, freitags kurz
        "IL" to frei("Tel Aviv Stock Exchange", "Asia/Jerusalem", { listOf(t(9, 59) to (if (it.dayOfWeek == DayOfWeek.FRIDAY) t(13, 50) else t(17, 25))) }),
        "SA" to h("Tadawul", "Asia/Riyadh", t(10), t(15), SO_DO),
        "AE" to h("Dubai Financial Market", "Asia/Dubai", t(10), t(15)),
        "EG" to h("Egyptian Exchange", "Africa/Cairo", t(10), t(14, 30), SO_DO),
        "ZA" to h("Johannesburg Stock Exchange", "Africa/Johannesburg", t(9), t(17)),
        "IN" to h("NSE und BSE", "Asia/Kolkata", t(9, 15), t(15, 30)),
        "CN" to h2("Shanghai Stock Exchange", "Asia/Shanghai", t(9, 30), t(11, 30), t(13), t(15)),
        "HK" to h2("Hong Kong Exchanges", "Asia/Hong_Kong", t(9, 30), t(12), t(13), t(16)),
        "JP" to h2("Tokyo Stock Exchange", "Asia/Tokyo", t(9), t(11, 30), t(12, 30), t(15, 30)),
        "KR" to h("Korea Exchange", "Asia/Seoul", t(9), t(15, 30)),
        "TW" to h("Taiwan Stock Exchange", "Asia/Taipei", t(9), t(13, 30)),
        "SG" to h("Singapore Exchange", "Asia/Singapore", t(9), t(17)),
        "MY" to h2("Bursa Malaysia", "Asia/Kuala_Lumpur", t(9), t(12, 30), t(14, 30), t(17)),
        "TH" to h2("Stock Exchange of Thailand", "Asia/Bangkok", t(10), t(12, 30), t(14, 30), t(16, 30)),
        "ID" to frei("Indonesia Stock Exchange", "Asia/Jakarta", { if (it.dayOfWeek == DayOfWeek.FRIDAY) listOf(t(9) to t(11, 30), t(14) to t(15, 50)) else listOf(t(9) to t(12), t(13, 30) to t(15, 50)) }),
        "PH" to h2("Philippine Stock Exchange", "Asia/Manila", t(9, 30), t(12), t(13, 30), t(15, 30)),
        "VN" to h2("Ho Chi Minh Stock Exchange", "Asia/Ho_Chi_Minh", t(9), t(11, 30), t(13), t(14, 45)),
        "AU" to h("ASX Sydney", "Australia/Sydney", t(10), t(16)),
        "NZ" to h("NZX Wellington", "Pacific/Auckland", t(10), t(16, 45)),
        // Weitere Laender. Quellen: Webseiten der Boersen und Handelszeiten-Verzeichnisse (Stand 06.11.2026); bei
        // widersprechenden Angaben gilt die der Boerse selbst. Nicht von der Boerse bestaetigt: RO, RS, SK, BG, BD, MA, CY, KE, PE, LK, LU.
        "RU" to h("Moskauer Börse", "Europe/Moscow", t(10), t(18, 45)),
        "RO" to h("Bukarester Börse", "Europe/Bucharest", t(9, 30), t(17, 45)),
        "HR" to h("Zagreber Börse", "Europe/Zagreb", t(9, 30), t(16)),
        "RS" to h("Belgrader Börse", "Europe/Belgrade", t(9), t(14)),
        "SI" to h("Ljubljana Stock Exchange", "Europe/Ljubljana", t(9, 15), t(15, 15)),
        "BG" to h("Bulgarische Börse Sofia", "Europe/Sofia", t(10, 10), t(16, 55)),
        "SK" to h("Börse Bratislava", "Europe/Bratislava", t(9), t(16)),
        "EE" to h("Nasdaq Tallinn", "Europe/Tallinn", t(10), t(16)),
        "LV" to h("Nasdaq Riga", "Europe/Riga", t(10), t(16)),
        "LT" to h("Nasdaq Vilnius", "Europe/Vilnius", t(10), t(16)),
        "IS" to h("Nasdaq Island", "Atlantic/Reykjavik", t(9, 30), t(15, 30)),
        "LU" to h("Luxemburger Börse", "Europe/Luxembourg", t(9), t(17, 35)),
        "CY" to h("Cyprus Stock Exchange", "Asia/Nicosia", t(10, 30), t(17)),
        "PE" to h("Bolsa de Valores de Lima", "America/Lima", t(9, 30), t(16)),
        "PK" to frei("Pakistan Stock Exchange", "Asia/Karachi", { if (it.dayOfWeek == DayOfWeek.FRIDAY) listOf(t(9, 17) to t(12), t(14, 32) to t(16, 30)) else listOf(t(9, 32) to t(15, 30)) }),
        "BD" to h("Dhaka Stock Exchange", "Asia/Dhaka", t(10), t(14, 30), SO_DO),
        "LK" to h("Colombo Stock Exchange", "Asia/Colombo", t(9, 30), t(14, 30)),
        "QA" to h("Qatar Stock Exchange", "Asia/Qatar", t(9, 30), t(13, 15), SO_DO),
        "KW" to h("Boursa Kuwait", "Asia/Kuwait", t(9), t(12, 30), SO_DO),
        "BH" to h("Bahrain Bourse", "Asia/Bahrain", t(9, 30), t(13), SO_DO),
        "OM" to h("Muscat Stock Exchange", "Asia/Muscat", t(10), t(13), SO_DO),
        "MA" to h("Börse Casablanca", "Africa/Casablanca", t(9, 30), t(15, 20)),
        "TN" to h("Börse Tunis", "Africa/Tunis", t(9), t(12, 10)),
        "NG" to h("Nigerian Exchange", "Africa/Lagos", t(9, 30), t(14, 30)),
        "KE" to h("Nairobi Securities Exchange", "Africa/Nairobi", t(9, 30), t(15)),
    )

    // ---- Boersenfeiertage als feste Regeln. Gesetzliche Feiertage und Boersenfeiertage weichen voneinander ab (die NYSE
    // handelt am Columbus Day, schliesst am Karfreitag; Xetra schliesst am 24. und 31.12.). Darum stehen hier die
    // Kalender der Boersen selbst, nicht die Feiertagslisten der Laender. Feiertage nach dem Mondkalender oder per
    // Jahresbeschluss (Hongkong, China, Indien) lassen sich nicht aus Regeln herleiten und stehen in keiner Quelle der
    // App; fuer diese Laender und alle ohne Regeln zaehlen Feiertage nicht.

    // Ostersonntag (gregorianisch, Meeus)
    fun ostern(jahr: Int): LocalDate {
        val a = jahr % 19; val b = jahr / 100; val c = jahr % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monat = (h + l - 7 * m + 114) / 31
        val tag = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(jahr, monat, tag)
    }

    private fun nteWochentag(jahr: Int, monat: Int, tag: DayOfWeek, n: Int): LocalDate {
        val erster = LocalDate.of(jahr, monat, 1)
        return erster.plusDays(((tag.value - erster.dayOfWeek.value + 7) % 7).toLong()).plusWeeks((n - 1).toLong())
    }

    private fun letzterWochentag(jahr: Int, monat: Int, tag: DayOfWeek): LocalDate {
        val ende = LocalDate.of(jahr, monat, 1).plusMonths(1).minusDays(1)
        return ende.minusDays(((ende.dayOfWeek.value - tag.value + 7) % 7).toLong())
    }

    // NYSE und Nasdaq: Neujahr, Martin Luther King (3. Montag im Januar), Presidents Day (3. Montag im Februar),
    // Karfreitag, Memorial Day, Juneteenth (seit 2022), Unabhaengigkeitstag, Labor Day, Thanksgiving und Weihnachten.
    // Ein fester Tag am Samstag wird am Freitag davor, einer am Sonntag am Montag danach begangen; nur Neujahr am
    // Samstag faellt nicht auf den Freitag davor.
    fun feiertagUs(d: LocalDate): Boolean {
        val j = d.year
        fun beobachtet(x: LocalDate) = when (x.dayOfWeek) { DayOfWeek.SATURDAY -> x.minusDays(1); DayOfWeek.SUNDAY -> x.plusDays(1); else -> x }
        val neujahr = LocalDate.of(j, 1, 1)
        if (neujahr.dayOfWeek != DayOfWeek.SATURDAY && d == beobachtet(neujahr)) return true
        return d == nteWochentag(j, 1, DayOfWeek.MONDAY, 3) || d == nteWochentag(j, 2, DayOfWeek.MONDAY, 3) ||
            d == ostern(j).minusDays(2) || d == letzterWochentag(j, 5, DayOfWeek.MONDAY) ||
            (j >= 2022 && d == beobachtet(LocalDate.of(j, 6, 19))) || d == beobachtet(LocalDate.of(j, 7, 4)) ||
            d == nteWochentag(j, 9, DayOfWeek.MONDAY, 1) || d == nteWochentag(j, 11, DayOfWeek.THURSDAY, 4) ||
            d == beobachtet(LocalDate.of(j, 12, 25))
    }

    // Verkuerzte Handelstage von NYSE und Nasdaq (Schluss 13:00 Uhr New Yorker Zeit, Kalender der NYSE): der Freitag nach
    // Thanksgiving, der 3. Juli und der 24. Dezember, jeweils nur als Handelstag (nicht am Wochenende und nicht, wenn der
    // Feiertag selbst auf diesen Tag vorgezogen ist).
    val US_KURZ_SCHLUSS: LocalTime = LocalTime.of(13, 0)

    fun verkuerztUs(d: LocalDate): Boolean {
        if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || feiertagUs(d)) return false
        return d == nteWochentag(d.year, 11, DayOfWeek.THURSDAY, 4).plusDays(1) ||
            (d.monthValue == 7 && d.dayOfMonth == 3) || (d.monthValue == 12 && d.dayOfMonth == 24)
    }

    // Verkuerzter Handelstag nach Land; false, wo keine Regeln im Code stehen.
    fun verkuerzt(land: String, d: LocalDate): Boolean = land == "US" && verkuerztUs(d)

    // Xetra: Neujahr, Karfreitag, Ostermontag, 1. Mai, 24., 25., 26. und 31. Dezember. Den vorzeitigen Schluss am letzten
    // Handelstag des Jahres legt die Boerse jedes Jahr per Bekanntmachung fest; er ist nicht nachgebildet.
    fun feiertagDe(d: LocalDate): Boolean {
        val o = ostern(d.year)
        return (d.monthValue == 1 && d.dayOfMonth == 1) || d == o.minusDays(2) || d == o.plusDays(1) ||
            (d.monthValue == 5 && d.dayOfMonth == 1) || (d.monthValue == 12 && d.dayOfMonth in setOf(24, 25, 26, 31))
    }

    // Euronext (Paris, Amsterdam, Bruessel, Lissabon): Neujahr, Karfreitag, Ostermontag, 1. Mai, 25. und 26. Dezember.
    fun feiertagEuronext(d: LocalDate): Boolean {
        val o = ostern(d.year)
        return (d.monthValue == 1 && d.dayOfMonth == 1) || d == o.minusDays(2) || d == o.plusDays(1) ||
            (d.monthValue == 5 && d.dayOfMonth == 1) || (d.monthValue == 12 && d.dayOfMonth in setOf(25, 26))
    }

    // London: Neujahr, Karfreitag, Ostermontag, Early May Bank Holiday (1. Montag im Mai), Spring Bank Holiday (letzter
    // Montag im Mai), Summer Bank Holiday (letzter Montag im August), Weihnachten und Boxing Day; fallen Neujahr, Weihnachten
    // oder Boxing Day auf ein Wochenende, gilt der naechste freie Werktag.
    fun feiertagGb(d: LocalDate): Boolean {
        val j = d.year
        val o = ostern(j)
        if (d == o.minusDays(2) || d == o.plusDays(1)) return true
        if (d == nteWochentag(j, 5, DayOfWeek.MONDAY, 1) || d == letzterWochentag(j, 5, DayOfWeek.MONDAY) || d == letzterWochentag(j, 8, DayOfWeek.MONDAY)) return true
        fun werktag(x: LocalDate) = when (x.dayOfWeek) { DayOfWeek.SATURDAY -> x.plusDays(2); DayOfWeek.SUNDAY -> x.plusDays(1); else -> x }
        val weihnacht = werktag(LocalDate.of(j, 12, 25))
        var boxing = werktag(LocalDate.of(j, 12, 26))
        if (boxing == weihnacht) boxing = boxing.plusDays(1)
        return d == werktag(LocalDate.of(j, 1, 1)) || d == weihnacht || d == boxing
    }

    // Tokio: 1. bis 3. Januar und 31. Dezember (Boersenschluss), dazu die Nationalfeiertage mit Ersatztag nach einem Sonntag
    // und dem Werktag zwischen zwei Feiertagen.
    fun feiertagJp(d: LocalDate): Boolean {
        if ((d.monthValue == 1 && d.dayOfMonth <= 3) || (d.monthValue == 12 && d.dayOfMonth == 31)) return true
        return d in nationalJp(d.year)
    }

    private val jpCache = HashMap<Int, Set<LocalDate>>()

    private fun nationalJp(j: Int): Set<LocalDate> = synchronized(jpCache) { jpCache.getOrPut(j) { nationalJpRechnen(j) } }

    private fun nationalJpRechnen(j: Int): Set<LocalDate> {
        val schritt = 0.242194 * (j - 1980) - ((j - 1980) / 4)
        val fruehling = (20.8431 + schritt).toInt()
        val herbst = (23.2488 + schritt).toInt()
        val fest = listOf(
            nteWochentag(j, 1, DayOfWeek.MONDAY, 2), LocalDate.of(j, 2, 11), LocalDate.of(j, 2, 23), LocalDate.of(j, 3, fruehling),
            LocalDate.of(j, 4, 29), LocalDate.of(j, 5, 3), LocalDate.of(j, 5, 4), LocalDate.of(j, 5, 5),
            nteWochentag(j, 7, DayOfWeek.MONDAY, 3), LocalDate.of(j, 8, 11), nteWochentag(j, 9, DayOfWeek.MONDAY, 3),
            LocalDate.of(j, 9, herbst), nteWochentag(j, 10, DayOfWeek.MONDAY, 2), LocalDate.of(j, 11, 3), LocalDate.of(j, 11, 23),
        )
        val alle = LinkedHashSet(fest)
        for (f in fest.sorted()) if (f.dayOfWeek == DayOfWeek.SUNDAY) {
            var x = f.plusDays(1)
            while (x in alle) x = x.plusDays(1)
            alle += x
        }
        var x = LocalDate.of(j, 1, 2)
        while (x.year == j) {
            if (x !in alle && x.dayOfWeek != DayOfWeek.SUNDAY && x.minusDays(1) in alle && x.plusDays(1) in alle) alle += x
            x = x.plusDays(1)
        }
        return alle
    }

    // Boersenfeiertag nach Land; false, wo keine Regeln im Code stehen.
    fun feiertag(land: String, d: LocalDate): Boolean = when (land) {
        "US" -> feiertagUs(d)
        "DE" -> feiertagDe(d)
        "GB" -> feiertagGb(d)
        "JP" -> feiertagJp(d)
        "FR", "NL", "BE", "PT" -> feiertagEuronext(d)
        else -> false
    }

    // wechsel: bei offener Boerse das Ende der Sitzung, sonst der naechste Beginn
    class Status(val offen: Boolean, val wechsel: ZonedDateTime)

    fun status(land: String, jetzt: Instant): Status? {
        val b = NACH_LAND[land] ?: return null
        val j = jetzt.atZone(b.zone)
        for (k in 0L..14L) {
            val tag = j.toLocalDate().plusDays(k)
            if (tag.dayOfWeek !in b.tage || feiertag(land, tag)) continue
            for ((von, bis) in b.sitzungenAm(tag)) {
                val auf = tag.atTime(von).atZone(b.zone)
                val zu = tag.atTime(bis).atZone(b.zone)
                if (!jetzt.isBefore(auf.toInstant()) && jetzt.isBefore(zu.toInstant())) return Status(true, zu)
                if (auf.toInstant().isAfter(jetzt)) return Status(false, auf)
            }
        }
        return null
    }

    fun offen(land: String, jetzt: Instant): Boolean? = status(land, jetzt)?.offen

    // Zeitpunkt in der Zeitzone des Nutzers: "17:30" am selben Tag, sonst mit Wochentag "Mo 09:00".
    fun kurz(wechsel: ZonedDateTime, nutzer: ZoneId, jetzt: Instant, sprache: Locale): String {
        val z = wechsel.withZoneSameInstant(nutzer)
        val heute = jetzt.atZone(nutzer).toLocalDate()
        val uhr = z.format(DateTimeFormatter.ofPattern("HH:mm"))
        if (z.toLocalDate() == heute) return uhr
        val tag = z.format(DateTimeFormatter.ofPattern("EEE", sprache)).trimEnd('.')
        return "$tag $uhr"
    }

    // Sitzungen als Text in Ortszeit der Boerse, etwa "9:00 bis 11:30 und 12:30 bis 15:30"
    fun zeiten(b: Handel, und: String, bis: String, datum: LocalDate = LocalDate.now(b.zone)): String {
        fun f(x: LocalTime) = "${x.hour}:${"%02d".format(x.minute)}"
        return b.sitzungenAm(datum).joinToString(" $und ") { "${f(it.first)} $bis ${f(it.second)}" }
    }

    // ---- Sonnenstand (einfache Naeherung nach NOAA), fuer die Nachtseite des Globus

    // Punkt, an dem die Sonne im Zenit steht, in Grad
    class Sonne(val breite: Double, val laenge: Double)

    fun sonne(jetzt: Instant): Sonne {
        val z = jetzt.atZone(java.time.ZoneOffset.UTC)
        val stunde = z.hour + z.minute / 60.0 + z.second / 3600.0
        val g = 2 * PI / 365.0 * (z.dayOfYear - 1 + (stunde - 12) / 24.0)
        val deklination = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val zeitgleichung = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        var lon = (12.0 - stunde - zeitgleichung / 60.0) * 15.0
        while (lon > 180.0) lon -= 360.0
        while (lon < -180.0) lon += 360.0
        return Sonne(Math.toDegrees(deklination), lon)
    }

    // Nachtseite als Flaeche auf dem Bild: die sichtbare Haelfte der Tag-Nacht-Grenze (Ellipse) und der Rand der Kugel auf
    // der Nachtseite. Gleiche Bildpunkte wie GlobusAnsicht. false, wenn auf der sichtbaren Seite keine Nacht ist.
    fun nacht(s: Sonne, a: GlobusAnsicht, pfad: GlobusPfad): Boolean {
        val la = Math.toRadians(-s.breite); val lo = Math.toRadians(s.laenge + 180.0)
        val wx = cos(la) * cos(lo); val wy = cos(la) * sin(lo); val wz = sin(la)
        // Achse der Nacht in Bildkoordinaten (x rechts, y oben, z zum Betrachter)
        val ax = (a.rx * wx + a.ry * wy).toDouble()
        val ay = (a.ux * wx + a.uy * wy + a.uz * wz).toDouble()
        val az = (a.fx * wx + a.fy * wy + a.fz * wz).toDouble()
        val r = a.radius
        fun px(x: Double) = (a.cx + r * x).toFloat()
        fun py(y: Double) = (a.cy - r * y).toFloat()
        val len = hypot(ax, ay)
        if (len < 1e-4) {
            if (az <= 0) return false
            val n = 72
            for (i in 0 until n) {
                val w = 2 * PI * i / n
                if (i == 0) pfad.nach(px(cos(w)), py(sin(w))) else pfad.linie(px(cos(w)), py(sin(w)))
            }
            pfad.schliessen()
            return true
        }
        val ux = ax / len; val uy = ay / len
        val wxs = -uy; val wys = ux
        val schritte = 48
        for (i in 0..schritte) {
            val th = -PI / 2 + PI * i / schritte
            val x = sin(th) * wxs + cos(th) * (-az * ux)
            val y = sin(th) * wys + cos(th) * (-az * uy)
            if (i == 0) pfad.nach(px(x), py(y)) else pfad.linie(px(x), py(y))
        }
        val rand = 24
        for (i in 0..rand) {
            val ps = PI / 2 - PI * i / rand
            pfad.linie(px(cos(ps) * ux + sin(ps) * wxs), py(cos(ps) * uy + sin(ps) * wys))
        }
        pfad.schliessen()
        return true
    }
}
