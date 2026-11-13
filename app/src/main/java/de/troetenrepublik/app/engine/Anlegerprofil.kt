package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType

// Anlegerprofil aus den eigenen Trades (5.94): wie lange Positionen gehalten werden, wie oft Verkaeufe im Plus enden,
// ob Gewinne die Verluste tragen, ob Verlierer laenger liegen bleiben als Gewinner (Dispositionseffekt), wie oft
// gehandelt wird, wie viel davon Hebelprodukte sind und wie oft nach Verlusten verbilligt wird. Kaeufe und Verkaeufe
// werden je Depotschluessel nach FIFO verrechnet; Knock-out und Verfall zaehlen als Verkauf zum Restwert. Die
// Trefferquote kommt aus der gemeinsamen Zaehlregel (Verkaufsbilanz).
// Sparplaene und uebernommene Positionen zaehlen bei der Haeufigkeit nicht mit.
object Anlegerprofil {

    enum class Merkmal { HALTEDAUER, TREFFER, CHANCE_RISIKO, DISPOSITION, HAEUFIGKEIT, HEBEL, NACHKAUF }

    // wert und wert2 je nach Merkmal (Tage, Anteil 0 bis 1, Verhaeltnis, Prozent), zahl eine Anzahl.
    data class Befund(val merkmal: Merkmal, val stufe: DepotCheck.Stufe, val wert: Double, val wert2: Double = 0.0, val zahl: Int = 0)

    // Ein abgeschlossener Verkauf: Ergebnis in Prozent des Einstands und mittlere Haltedauer in Tagen.
    data class Verkauf(val ts: Long, val prozent: Double, val tage: Double, val euro: Double = 0.0, val id: String = "")

    const val MIN_VERKAEUFE = Verkaufsbilanz.MIN_VERKAEUFE
    private const val TAG = 86_400_000L

    private class Posten(var stueck: Double, var preis: Double, val ts: Long)

    private fun verkauft(t: TxType) = t == TxType.SELL || t == TxType.KNOCKOUT || t == TxType.EXPIRY

    // Eroeffnung und Schluss einer Position; bei verkauften Optionen (0.77) eroeffnet der Verkauf, Rueckkauf und Verfall
    // schliessen.
    private fun oeffnet(t: Transaction) = t.eroeffnet
    private fun schliesst(t: Transaction) = t.schliesst

    fun derivat(t: Transaction): Boolean = t.key.isNotBlank() && t.key != t.symbol

    // Abgeschlossene Verkaeufe nach FIFO; Verkaeufe ohne bekannten Kauf fehlen.
    fun verkaeufe(txs: List<Transaction>): List<Verkauf> {
        val offen = HashMap<String, ArrayDeque<Posten>>()
        val aus = ArrayList<Verkauf>()
        for (t in txs.sortedBy { it.ts }) {
            val k = t.key.ifBlank { t.symbol }
            // Split: offene Posten in neue Stueckeinheiten (Buchungen bleiben in alten Einheiten)
            if (t.type == TxType.SPLIT && t.extra > 0 && k.isNotBlank()) {
                offen[k]?.forEach { it.stueck *= t.extra; it.preis /= t.extra }
                continue
            }
            if (k.isBlank() || !(t.qty > 0)) continue
            when {
                oeffnet(t) -> offen.getOrPut(k) { ArrayDeque() }.addLast(Posten(t.qty, t.price, t.ts))
                schliesst(t) -> {
                    val l = offen[k] ?: continue
                    var rest = t.qty
                    var einstand = 0.0
                    var tage = 0.0
                    var menge = 0.0
                    while (rest > 1e-9 && l.isNotEmpty()) {
                        val p = l.first()
                        val m = minOf(rest, p.stueck)
                        einstand += m * p.preis
                        tage += m * (maxOf(t.closedAt.takeIf { it > 0 } ?: t.ts, p.ts) - p.ts).toDouble() / TAG
                        menge += m
                        p.stueck -= m
                        rest -= m
                        if (p.stueck <= 1e-9) l.removeFirst()
                    }
                    // Verkaufte Option: Gewinn ist die Praemie (einstand) minus Rueckkauf bzw. Ausgleich
                    val euro = if (t.verkaufteOption) einstand - menge * t.price else menge * t.price - einstand
                    if (menge > 0 && einstand > 0) aus += Verkauf(t.ts, euro / einstand * 100, tage / menge, euro, t.id)
                }
            }
        }
        return aus
    }

    private fun median(l: List<Double>): Double {
        val s = l.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // Leer bei weniger als MIN_VERKAEUFE abgeschlossenen Verkaeufen und ohne Orders im letzten Jahr.
    fun auswerten(txs: List<Transaction>, jetzt: Long = System.currentTimeMillis()): List<Befund> {
        val aus = ArrayList<Befund>()
        val v = verkaeufe(txs)
        // Trefferquote nach der gemeinsamen Zaehlregel (Verkaufsbilanz) wie im Depotverlauf: wert die Quote, wert2 die
        // Verkaeufe im Plus, zahl alle Verkaeufe mit Ergebnis
        val treffer = Verkaufsbilanz.auswerten(txs)?.let { Befund(Merkmal.TREFFER, DepotCheck.Stufe.HINWEIS, it.trefferquote, it.gewinne.toDouble(), it.anzahl) }
        if (v.size < MIN_VERKAEUFE && treffer != null) aus += treffer
        if (v.size >= MIN_VERKAEUFE) {
            val tage = median(v.map { it.tage })
            aus += Befund(Merkmal.HALTEDAUER, if (tage < 7) DepotCheck.Stufe.ACHTUNG else DepotCheck.Stufe.HINWEIS, tage, zahl = v.size)
            val gewinner = v.filter { it.prozent > 0 }
            val verlierer = v.filter { it.prozent < 0 }
            if (treffer != null) aus += treffer
            if (gewinner.isNotEmpty() && verlierer.isNotEmpty()) {
                val plus = gewinner.map { it.prozent }.average()
                val minus = -verlierer.map { it.prozent }.average()
                val verhaeltnis = plus / minus
                // Erwartetes Ergebnis je Verkauf in Prozent; ob das Handeln Geld gekostet hat, sagt die Summe in Euro.
                val erwartung = v.map { it.prozent }.average()
                val saldo = v.sumOf { it.euro }
                aus += Befund(
                    Merkmal.CHANCE_RISIKO,
                    when { saldo < 0 -> DepotCheck.Stufe.KRITISCH; verhaeltnis < 1 -> DepotCheck.Stufe.ACHTUNG; else -> DepotCheck.Stufe.GUT },
                    verhaeltnis, erwartung,
                )
                if (gewinner.size >= 2 && verlierer.size >= 2) {
                    val tGewinn = median(gewinner.map { it.tage })
                    val tVerlust = median(verlierer.map { it.tage })
                    val lang = tVerlust >= 1.5 * tGewinn && verhaeltnis < 1
                    aus += Befund(Merkmal.DISPOSITION, if (lang) DepotCheck.Stufe.ACHTUNG else DepotCheck.Stufe.GUT, tVerlust, tGewinn)
                }
            }
        }
        // Haeufigkeit und Hebel aus den selbst gegebenen Orders der letzten zwoelf Monate
        val orders = txs.filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.ts >= jetzt - 365 * TAG && it.via != "PLAN" && !it.via.startsWith("IMPORT") }
        if (orders.isNotEmpty()) {
            // Durch die Monate seit der ersten Order, hoechstens zwoelf, mindestens einer
            val seit = maxOf(jetzt - 365 * TAG, orders.minOf { it.ts })
            val proMonat = orders.size / ((jetzt - seit) / (30.44 * TAG)).coerceIn(1.0, 12.0)
            aus += Befund(
                Merkmal.HAEUFIGKEIT,
                when { proMonat > 20 -> DepotCheck.Stufe.KRITISCH; proMonat > 8 -> DepotCheck.Stufe.ACHTUNG; else -> DepotCheck.Stufe.GUT },
                proMonat, zahl = orders.size,
            )
            val volumen = orders.sumOf { it.qty * it.price }
            if (volumen > 0) {
                val hebel = orders.filter { derivat(it) }.sumOf { it.qty * it.price } / volumen
                aus += Befund(
                    Merkmal.HEBEL,
                    when { hebel > 0.5 -> DepotCheck.Stufe.KRITISCH; hebel > 0.2 -> DepotCheck.Stufe.ACHTUNG; else -> DepotCheck.Stufe.GUT },
                    hebel,
                )
            }
        }
        // Verbilligen: Nachkauf mehr als 20 % unter dem bisherigen Einstand derselben Position (ohne Sparplan)
        val nachkauf = verbilligt(txs)
        if (nachkauf > 0) aus += Befund(Merkmal.NACHKAUF, if (nachkauf >= 3) DepotCheck.Stufe.ACHTUNG else DepotCheck.Stufe.HINWEIS, 0.0, zahl = nachkauf)
        return aus
    }

    fun verbilligt(txs: List<Transaction>): Int {
        val bestand = HashMap<String, Pair<Double, Double>>()
        var n = 0
        for (t in txs.sortedBy { it.ts }) {
            val k = t.key.ifBlank { t.symbol }
            if (t.type == TxType.SPLIT && t.extra > 0 && k.isNotBlank()) {
                bestand[k]?.let { (s, c) -> bestand[k] = (s * t.extra) to c }
                continue
            }
            // Verkaufte Optionen verbilligt man nicht
            if (k.isBlank() || !(t.qty > 0) || t.verkaufteOption) continue
            val (stueck, kosten) = bestand[k] ?: (0.0 to 0.0)
            when {
                t.type == TxType.BUY -> {
                    if (stueck > 1e-9 && t.via != "PLAN" && t.price < 0.8 * kosten / stueck) n++
                    bestand[k] = (stueck + t.qty) to (kosten + t.qty * t.price)
                }
                verkauft(t.type) && stueck > 1e-9 -> {
                    val m = minOf(t.qty, stueck)
                    bestand[k] = (stueck - m) to (kosten * (1 - m / stueck))
                }
            }
        }
        return n
    }
}
