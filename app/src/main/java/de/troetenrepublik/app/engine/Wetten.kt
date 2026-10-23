package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Wette

// Uebungswetten auf Prognosemaerkte. Gekauft wird zum Briefkurs der gewaehlten Seite, verkauft zum
// Geldkurs, aufgeloest mit 1 Euro je Anteil fuer die richtige Seite (bei Teilauszahlungen wie 50/50 mit dem
// Auszahlungsbetrag). Keine Gebuehren, keine Steuer; die Wetten zaehlen zum Gesamtvermoegen, nicht zum Depot.
// Wiederholte Kaeufe derselben Seite eines Marktes fliessen in eine Wette mit gemitteltem Einstand.
object Wetten {
    const val MINDESTEINSATZ = 1.0

    enum class Fehler { BETRAG, KURS, GUTHABEN, UNBEKANNT, GESCHLOSSEN }

    data class Ergebnis(val account: Account, val fehler: Fehler? = null, val wette: Wette? = null) {
        val ok: Boolean get() = fehler == null
    }

    // Anteile fuer einen Einsatz zum Kurs (je Anteil, 0 bis 1).
    fun anteile(betrag: Double, kurs: Double): Double = if (kurs > 0) betrag / kurs else 0.0

    // Kaufen: vorlage beschreibt Markt und Seite (Anteile und Einstand werden hier gesetzt), betrag in Euro,
    // kurs der Briefkurs der Seite.
    fun kaufen(account: Account, vorlage: Wette, betrag: Double, kurs: Double, jetzt: Long, txId: String): Ergebnis {
        if (!betrag.isFinite() || betrag < MINDESTEINSATZ) return Ergebnis(account, Fehler.BETRAG)
        if (!kurs.isFinite() || kurs <= 0.0 || kurs >= 1.0) return Ergebnis(account, Fehler.KURS)
        if (betrag > account.cash - account.reserved + 1e-9) return Ergebnis(account, Fehler.GUTHABEN)
        val neu = anteile(betrag, kurs)
        val vorhanden = account.wetten.firstOrNull { it.offen && it.quelle == vorlage.quelle && it.markt == vorlage.markt && it.seite == vorlage.seite }
        val wette = if (vorhanden != null) {
            val anteile = vorhanden.anteile + neu
            vorhanden.copy(anteile = anteile, einstand = (vorhanden.einsatz + betrag) / anteile, kurs = kurs, kursAm = jetzt)
        } else {
            vorlage.copy(anteile = neu, einstand = kurs, kurs = kurs, kursAm = jetzt, eroeffnet = jetzt, status = Wette.OFFEN)
        }
        val tx = Transaction(
            id = txId, ts = jetzt, type = TxType.BET, title = wette.titel, subtitle = "${wette.frage} · ${wette.seitenName}",
            amount = -betrag, qty = neu, price = kurs, key = "pm:${wette.id}", tag = "BET_BUY",
        )
        val wetten = if (vorhanden != null) account.wetten.map { if (it.id == vorhanden.id) wette else it } else account.wetten + wette
        return Ergebnis(account.copy(cash = account.cash - betrag, wetten = wetten, transactions = account.transactions + tx), wette = wette)
    }

    // Verkaufen vor der Aufloesung zum Geldkurs kurs; alle Anteile.
    fun verkaufen(account: Account, id: String, kurs: Double, jetzt: Long, txId: String): Ergebnis {
        val w = account.wetten.firstOrNull { it.id == id } ?: return Ergebnis(account, Fehler.UNBEKANNT)
        if (!w.offen) return Ergebnis(account, Fehler.GESCHLOSSEN)
        if (!kurs.isFinite() || kurs < 0.0 || kurs > 1.0) return Ergebnis(account, Fehler.KURS)
        val erloes = w.anteile * kurs
        val fertig = w.copy(status = Wette.VERKAUFT, kurs = kurs, kursAm = jetzt, geschlossen = jetzt, erloes = erloes)
        val tx = Transaction(
            id = txId, ts = jetzt, type = TxType.BET, title = w.titel, subtitle = "${w.frage} · ${w.seitenName}",
            amount = erloes, qty = w.anteile, price = kurs, key = "pm:${w.id}", tag = "BET_SELL", realized = erloes - w.einsatz,
        )
        return Ergebnis(
            account.copy(cash = account.cash + erloes, wetten = account.wetten.map { if (it.id == id) fertig else it }, transactions = account.transactions + tx),
            wette = fertig,
        )
    }

    // Aufloesen: auszahlungJeAnteil ist, was ein Anteil der gewetteten Seite bekommt (1 = gewonnen, 0 = verloren,
    // dazwischen bei Teilauszahlung).
    fun aufloesen(account: Account, id: String, auszahlungJeAnteil: Double, jetzt: Long, txId: String): Ergebnis {
        val w = account.wetten.firstOrNull { it.id == id } ?: return Ergebnis(account, Fehler.UNBEKANNT)
        if (!w.offen) return Ergebnis(account, Fehler.GESCHLOSSEN)
        val je = auszahlungJeAnteil.coerceIn(0.0, 1.0)
        val erloes = w.anteile * je
        val status = when {
            je >= 0.999 -> Wette.GEWONNEN
            je <= 0.001 -> Wette.VERLOREN
            else -> Wette.AUFGELOEST
        }
        val fertig = w.copy(status = status, kurs = je, kursAm = jetzt, geschlossen = jetzt, erloes = erloes)
        val tx = Transaction(
            id = txId, ts = jetzt, type = TxType.BET, title = w.titel, subtitle = "${w.frage} · ${w.seitenName}",
            amount = erloes, qty = w.anteile, price = je, key = "pm:${w.id}",
            tag = when (status) { Wette.GEWONNEN -> "BET_WIN"; Wette.VERLOREN -> "BET_LOSS"; else -> "BET_SETTLE" },
            realized = erloes - w.einsatz,
        )
        return Ergebnis(
            account.copy(cash = account.cash + erloes, wetten = account.wetten.map { if (it.id == id) fertig else it }, transactions = account.transactions + tx),
            wette = fertig,
        )
    }

    // Uebersicht

    // Kopf von „Meine Wetten“: offene Wetten zum aktuellen Kurs, abgeschlossene mit Erloes oder Auszahlung. gesamt
    // ist dasselbe Ergebnis wie am Ende des Verlaufs.
    data class Uebersicht(
        val offen: Int,
        val einsatz: Double,
        val wert: Double,
        val abgeschlossen: Int,
        val ergebnisAbgeschlossen: Double,
    ) {
        val ergebnisOffen: Double get() = wert - einsatz
        val gesamt: Double get() = ergebnisOffen + ergebnisAbgeschlossen
    }

    // kurse die aktuellen Kurse der offenen Wetten; fehlt einer, gilt der zuletzt bekannte.
    fun uebersicht(wetten: List<Wette>, kurse: Map<String, Double>): Uebersicht {
        val offen = wetten.filter { it.offen }
        val zu = wetten.filter { !it.offen }
        return Uebersicht(
            offen = offen.size,
            einsatz = offen.sumOf { it.einsatz },
            wert = offen.sumOf { it.anteile * (kurse[it.id] ?: it.kurs) },
            abgeschlossen = zu.size,
            ergebnisAbgeschlossen = zu.sumOf { it.erloes - it.einsatz },
        )
    }

    // Verlauf

    // Buchung einer Wette fuer Marken im Verlauf: Kauf oder Abschluss (Verkauf, Gewinn, Verlust, Aufloesung).
    data class Buchung(val t: Long, val id: String, val tag: String, val betrag: Double, val titel: String, val seite: String) {
        val kauf: Boolean get() = tag == "BET_BUY"
    }

    fun buchungen(transaktionen: List<Transaction>): List<Buchung> =
        transaktionen.filter { it.type == TxType.BET && it.key.startsWith("pm:") }.sortedBy { it.ts }
            .map { Buchung(it.ts, it.key.removePrefix("pm:"), it.tag, it.amount, it.title, it.subtitle.substringAfterLast(" · ")) }

    // Ergebnis aller Wetten ueber die Zeit in Euro: eingesetztes und ausgezahltes Geld bis zum Zeitpunkt plus der
    // Wert der gehaltenen Anteile zum damaligen Kurs. kurse je Wette der Kursverlauf der gewetteten Seite (0 bis 1);
    // fehlt er, gilt bis dahin der Kaufkurs. aktuell die Kurse von jetzt fuer den letzten Punkt. Beginnt mit dem
    // ersten Kauf; hoechstens maxPunkte Punkte (Buchungszeitpunkte bleiben immer erhalten).
    fun verlauf(
        transaktionen: List<Transaction>,
        kurse: Map<String, List<Pair<Long, Double>>>,
        aktuell: Map<String, Double>,
        jetzt: Long,
        maxPunkte: Int = 400,
    ): List<Pair<Long, Double>> {
        val tx = transaktionen.filter { it.type == TxType.BET && it.key.startsWith("pm:") }.sortedBy { it.ts }
        if (tx.isEmpty()) return emptyList()
        val start = tx.first().ts
        val buchungsZeiten = tx.map { it.ts }.toSortedSet()
        val kursZeiten = kurse.values.flatMap { l -> l.map { it.first } }.filter { it in (start + 1) until jetzt }.toSortedSet()
        // Ausduennen gleichmaessig ueber die Zeit; Buchungen und der letzte Punkt bleiben
        val raster = if (kursZeiten.size + buchungsZeiten.size <= maxPunkte) kursZeiten.toList() else {
            val schritt = (jetzt - start).toDouble() / (maxPunkte - buchungsZeiten.size).coerceAtLeast(10)
            val aus = ArrayList<Long>()
            var naechste = start.toDouble()
            for (t in kursZeiten) if (t >= naechste) { aus += t; naechste = t + schritt }
            aus
        }
        val zeiten = (buchungsZeiten + raster + jetzt).toSortedSet().toList()
        val anteile = HashMap<String, Double>()
        val kaufkurs = HashMap<String, Double>()
        val verlaeufe = kurse.mapValues { (_, l) -> l.sortedBy { it.first } }
        var geld = 0.0
        var i = 0
        val aus = ArrayList<Pair<Long, Double>>(zeiten.size)
        fun kursAm(id: String, t: Long): Double {
            if (t >= jetzt) aktuell[id]?.let { return it }
            val l = verlaeufe[id]
            if (!l.isNullOrEmpty()) {
                var lo = 0
                var hi = l.size - 1
                if (l[0].first <= t) {
                    while (lo < hi) { val m = (lo + hi + 1) / 2; if (l[m].first <= t) lo = m else hi = m - 1 }
                    return l[lo].second
                }
            }
            return kaufkurs[id] ?: 0.0
        }
        for (t in zeiten) {
            while (i < tx.size && tx[i].ts <= t) {
                val b = tx[i++]
                val id = b.key.removePrefix("pm:")
                geld += b.amount
                if (b.tag == "BET_BUY") {
                    anteile[id] = (anteile[id] ?: 0.0) + b.qty
                    kaufkurs[id] = b.price
                } else {
                    anteile[id] = 0.0
                }
            }
            val wert = anteile.entries.sumOf { (id, n) -> if (n <= 0) 0.0 else n * kursAm(id, t) }
            aus += t to geld + wert
        }
        return aus
    }

    // Zuletzt bekannte Kurse uebernehmen (zum Speichern), ohne Buchung.
    fun kurseSetzen(account: Account, kurse: Map<String, Double>, jetzt: Long): Account {
        if (kurse.isEmpty()) return account
        return account.copy(wetten = account.wetten.map { w -> kurse[w.id]?.takeIf { w.offen }?.let { w.copy(kurs = it, kursAm = jetzt) } ?: w })
    }
}
