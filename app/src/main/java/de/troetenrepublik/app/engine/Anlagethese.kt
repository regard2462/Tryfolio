package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.DepotRegeln
import de.troetenrepublik.app.data.These
import java.time.Instant
import java.time.ZoneId

// Anlage-These und eigene Depotregeln (seit 5.91). Die These haelt fest, warum ein Wert gekauft wurde, wann man
// aussteigen will und wann man den Grund wieder prueft. Die Regeln warnen beim Kauf, verbieten aber nichts.
object Anlagethese {

    // Eingaben aus der Ordermaske. obergrenze in Prozent (0 = keine), pruefAm 0 = kein Termin.
    data class Eingabe(val grund: String, val ausstieg: String, val obergrenze: Double, val pruefAm: Long)

    // Vorschlag fuer den Prueftermin: heute in monate Monaten, gleiche Uhrzeit.
    fun vorschlag(now: Long, monate: Int = 3): Long =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).plusMonths(monate.toLong()).toInstant().toEpochMilli()

    // Prueftermin erreicht: auf der Wertpapierseite steht dann die Frage „Grund noch gueltig?“.
    fun offen(t: These, now: Long): Boolean = t.pruefAm in 1..now

    // Werte, an deren Prueftermin noch nicht erinnert wurde. Nur gehaltene Werte; nach einem Verkauf bleibt die
    // These stehen, erinnert wird aber nicht mehr.
    fun faellig(thesen: Map<String, These>, gehalten: Set<String>, now: Long): List<String> =
        thesen.filter { (s, t) -> s in gehalten && offen(t, now) && t.gemeldetAm < t.pruefAm }.keys.sorted()

    // „Ja, gilt noch“: neuer Termin in drei Monaten.
    fun bestaetigen(t: These, now: Long): These = t.copy(pruefAm = vorschlag(now), gemeldetAm = 0L)

    enum class Verstoss { POSITION, HEBEL, BARGELD, THESE }

    // ist und grenze in Prozent; name: betroffene Position im Depot-Check.
    data class Befund(val verstoss: Verstoss, val ist: Double, val grenze: Double, val name: String = "")

    // Warnungen zu einem Kauf. depot: Wertpapiere plus Guthaben vor dem Kauf, position: bisheriger Wert der
    // Position, betrag: Gegenwert des Kaufs. Die Gebuehr verlaesst das Depot, der Gegenwert wechselt nur vom
    // Guthaben in die Position. obergrenze: Grenze aus der These in Prozent (0 = keine).
    fun kaufPruefen(
        regeln: DepotRegeln, obergrenze: Double, depot: Double, bargeld: Double, position: Double, betrag: Double,
        hebel: Boolean, gebuehr: Double = 1.0,
    ): List<Befund> {
        if (!betrag.isFinite() || betrag <= 0) return emptyList()
        val nachher = depot - gebuehr
        if (nachher <= 0) return emptyList()
        val anteil = (position + betrag) / nachher * 100
        val rest = (bargeld - betrag - gebuehr) / nachher * 100
        val aus = ArrayList<Befund>()
        if (regeln.maxPosition > 0 && anteil > regeln.maxPosition + 1e-9) aus += Befund(Verstoss.POSITION, anteil, regeln.maxPosition)
        if (obergrenze > 0 && anteil > obergrenze + 1e-9) aus += Befund(Verstoss.THESE, anteil, obergrenze)
        if (regeln.keineHebel && hebel) aus += Befund(Verstoss.HEBEL, 0.0, 0.0)
        if (regeln.minBargeld > 0 && rest < regeln.minBargeld - 1e-9) aus += Befund(Verstoss.BARGELD, rest.coerceAtLeast(0.0), regeln.minBargeld)
        return aus
    }

    // Eine Position im Depot fuer die Pruefung: name, wert und ob es ein Hebelprodukt ist.
    data class Posten(val name: String, val wert: Double, val hebel: Boolean)

    // Regelverstoesse des aktuellen Depots, etwa fuer den Depot-Check.
    fun depotPruefen(regeln: DepotRegeln, posten: List<Posten>, bargeld: Double): List<Befund> {
        val depot = posten.sumOf { it.wert } + bargeld
        if (depot <= 0) return emptyList()
        val aus = ArrayList<Befund>()
        if (regeln.maxPosition > 0) {
            posten.filter { it.wert / depot * 100 > regeln.maxPosition + 1e-9 }.sortedByDescending { it.wert }
                .forEach { aus += Befund(Verstoss.POSITION, it.wert / depot * 100, regeln.maxPosition, it.name) }
        }
        if (regeln.keineHebel) {
            val h = posten.filter { it.hebel && it.wert > 0 }
            if (h.isNotEmpty()) aus += Befund(Verstoss.HEBEL, h.sumOf { it.wert } / depot * 100, 0.0, h.joinToString(", ") { it.name })
        }
        if (regeln.minBargeld > 0 && bargeld / depot * 100 < regeln.minBargeld - 1e-9) {
            aus += Befund(Verstoss.BARGELD, (bargeld / depot * 100).coerceAtLeast(0.0), regeln.minBargeld)
        }
        return aus
    }
}
