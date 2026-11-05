package de.troetenrepublik.app.engine

import java.time.LocalDate

// VX-Terminkurve (seit 5.95): Abrechnungspreise der VIX-Futures der Cboe Futures Exchange aus der taeglichen
// Settlement-CSV. Monatskontrakte (VX/MonatscodeJahr) ergeben die Kurve; das Verhaeltnis der ersten beiden mit
// mehr als sieben Tagen Restlaufzeit zeigt die Stimmung des Terminmarkts: ueber 1 Backwardation (akute Angst),
// ab 0,95 flach, sonst Contango (der Normalfall, ruhiger Markt). Nur der letzte Handelstag, nur im Speicher.
object VxKurve {

    const val SEITE = "https://www.cboe.com/us/futures/market_statistics/settlement/"

    fun adresse(tag: LocalDate): String = SEITE + "csv?dt=$tag"

    // Ein Kontrakt mit Verfall und Abrechnungspreis; monat true bei Monatskontrakt (VX/X6), false etwa bei
    // Wochenkontrakten (VX44/X6).
    data class Kontrakt(val symbol: String, val verfall: LocalDate, val preis: Double, val monat: Boolean)

    // Abrechnungen eines Tages, nach Verfall sortiert.
    data class Stand(val tag: LocalDate, val kontrakte: List<Kontrakt>)

    // Monatskontrakte wie VX/X6 und VX/Z6; Wochenkontrakte beginnen mit einer Zahl.
    private val MONAT = Regex("VX/[A-Z][0-9]+")

    // CSV der Abrechnungspreise; die Spalten werden ueber die Kopfzeile gefunden (Reihenfolge, Anfuehrungszeichen
    // und Wagenruecklauf egal). Nur Zeilen mit Produkt VX, Preis ueber 0 und Verfall am oder nach dem Tag; ohne
    // solche Zeile null (Feiertag, Datei noch leer).
    fun lesen(csv: String, tag: LocalDate): Stand? {
        val zeilen = csv.trimStart('\uFEFF').lines()
        for ((i, z) in zeilen.withIndex()) {
            val kopf = felder(z).map { it.trim().trim('"').lowercase() }
            val iProdukt = kopf.indexOf("product")
            val iSymbol = kopf.indexOf("symbol")
            val iVerfall = kopf.indexOf("expiration date")
            val iPreis = kopf.indexOf("price")
            if (iProdukt < 0 || iSymbol < 0 || iVerfall < 0 || iPreis < 0) continue
            val kontrakte = zeilen.drop(i + 1).mapNotNull { zeile ->
                val f = felder(zeile)
                if (f.getOrNull(iProdukt)?.trim() != "VX") return@mapNotNull null
                val symbol = f.getOrNull(iSymbol)?.trim().orEmpty()
                val verfall = runCatching { LocalDate.parse(f.getOrNull(iVerfall)?.trim().orEmpty()) }.getOrNull()
                    ?: return@mapNotNull null
                val preis = f.getOrNull(iPreis)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
                if (preis <= 0.0 || verfall < tag) return@mapNotNull null
                Kontrakt(symbol, verfall, preis, MONAT.matches(symbol))
            }.sortedBy { it.verfall }
            return if (kontrakte.isEmpty()) null else Stand(tag, kontrakte)
        }
        return null
    }

    // Kurve aus den Monatskontrakten: Laufzeit in Tagen gegen den Abrechnungspreis.
    fun kurve(s: Stand): Marktkurven.Kurve = Marktkurven.Kurve(
        s.tag.toEpochDay(),
        s.kontrakte.filter { it.monat }
            .map { Marktkurven.Punkt((it.verfall.toEpochDay() - s.tag.toEpochDay()).toInt(), it.preis) })

    // Verhaeltnis des ersten zum zweiten Monatskontrakt mit mehr als sieben Tagen Restlaufzeit; null, wenn es
    // weniger als zwei solche Kontrakte gibt.
    fun verhaeltnis(s: Stand): Double? {
        val monate = s.kontrakte.filter { it.monat && it.verfall.toEpochDay() - s.tag.toEpochDay() > 7 }.take(2)
        if (monate.size < 2) return null
        return monate[0].preis / monate[1].preis
    }

    // Form des Terminmarkts mit denselben Schwellen wie Marktkurven.vixForm (dort fuer VIX gegen VIX3M, hier
    // fuer das Kontraktverhaeltnis): ueber 1 faellt die Kurve (Backwardation, Angst), ab 0,95 ist sie flach,
    // sonst steigt sie (Contango, der Normalfall).
    fun form(q: Double): Marktkurven.VixForm = when {
        q > 1.0 -> Marktkurven.VixForm.BACKWARDATION
        q >= 0.95 -> Marktkurven.VixForm.FLACH
        else -> Marktkurven.VixForm.CONTANGO
    }

    // Eine CSV-Zeile in Felder; Anfuehrungszeichen und doppelte Anfuehrungszeichen darin beachtet,
    // Wagenruecklauf faellt weg.
    private fun felder(zeile: String): List<String> {
        val aus = ArrayList<String>()
        val sb = StringBuilder()
        var inAnf = false
        var i = 0
        while (i < zeile.length) {
            val c = zeile[i]
            if (inAnf) {
                when {
                    c == '"' && i + 1 < zeile.length && zeile[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inAnf = false
                    c != '\r' -> sb.append(c)
                }
            } else when (c) {
                '"' -> inAnf = true
                ',' -> { aus.add(sb.toString()); sb.clear() }
                '\r' -> {}
                else -> sb.append(c)
            }
            i++
        }
        aus.add(sb.toString())
        return aus
    }
}
