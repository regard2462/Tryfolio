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

// Haupthandelszeiten der Leitboerse je Land (Kuerzel alpha-2), lokal gerechnet, ohne Netz. Feiertage und verkuerzte
// Handelstage zaehlen nicht. Die Zeiten stammen aus den Handelskalendern der Boersen (Stand Herbst 2026) und sind
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
        "US" to h("NYSE, Nasdaq", "America/New_York", t(9, 30), t(16)),
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

    // wechsel: bei offener Boerse das Ende der Sitzung, sonst der naechste Beginn
    class Status(val offen: Boolean, val wechsel: ZonedDateTime)

    fun status(land: String, jetzt: Instant): Status? {
        val b = NACH_LAND[land] ?: return null
        val j = jetzt.atZone(b.zone)
        for (k in 0L..8L) {
            val tag = j.toLocalDate().plusDays(k)
            if (tag.dayOfWeek !in b.tage) continue
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
