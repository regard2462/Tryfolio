package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.engine.TradeRepublicImport.Zeile
import kotlin.math.abs

// Abgleich des Depots mit einem Flex-Bericht (FlexBericht). Zuerst kommen die Geschaefte und Geldbewegungen des Berichts
// als Buchungen dazu (schon bekannte nur einmal), danach gleicht der Bestand an die offenen Positionen des Berichts an:
// fehlende Stuecke als Uebertrag zum Einstand des Berichts, ueberzaehlige als Uebertrag aus dem Depot. Ueberzaehlig ist
// nur, was aus einem Flex-Bericht stammt oder im Bericht mit weniger Stueck steht; alles andere im Depot bleibt.
// Das Ergebnis ist ein Vorschlag, gebucht wird erst nach der Bestaetigung.
object FlexAbgleich {
    const val QUELLE = "Interactive Brokers"
    const val PRAEFIX = "ibf"
    private const val VIA = "IMPORT_IBF"
    private const val EPS = 1e-6

    enum class Art { NEU, GEAENDERT, ENTFAELLT }

    data class Unterschied(val art: Art, val key: String, val name: String, val vorher: Double, val nachher: Double)

    data class Plan(
        // Depot, auf dem der Vorschlag rechnet, und Depot nach der Uebernahme.
        val basis: Account,
        val konto: Account,
        val unterschiede: List<Unterschied>,
        // Neue Buchungen und Zeilen des Berichts, die schon im Depot stehen.
        val buchungen: Int,
        val bekannt: Int,
        // Errechnete Einzahlung, damit das Guthaben nicht unter null faellt; 0 = keine.
        val einzahlung: Double,
        // Im Bericht, aber nicht im Depot abbildbar oder ohne Kuerzel: Name und Stueck.
        val offen: List<FlexBericht.Offen>,
        val ohneKauf: Set<String>,
        val ungelesen: Int,
        val ohneWechselkurs: Int,
        // Bargeld laut Bericht in Euro, nur zur Anzeige.
        val bargeld: Double?,
        val von: Long,
        val bis: Long,
        // Der Bericht nennt keine offenen Positionen: nur Buchungen, kein Abgleich des Bestands.
        val ohnePositionen: Boolean,
    ) {
        val leer: Boolean get() = unterschiede.isEmpty() && buchungen == 0
    }

    // Schluessel, unter denen die App Kuerzel sucht: ISIN oder Boersenkuerzel mit Waehrung, bei Krypto das Kuerzel.
    data class Gesucht(val schluessel: String, val klasse: String, val waehrung: String)

    fun gesucht(b: FlexBericht.Bericht): List<Gesucht> {
        val papiere = b.positionen.orEmpty().map { it.papier } + b.geschaefte.orEmpty().map { it.papier } + b.geld.orEmpty().mapNotNull { it.papier }
        return papiere.filter { it.abbildbar && it.zeilenSymbol.isNotBlank() }.map { Gesucht(it.zeilenSymbol, it.zeilenKlasse, it.waehrung) }.distinctBy { it.schluessel to it.klasse }
    }

    // kuerzel: Schluessel des Papiers (Papier.zeilenSymbol) zum Kuerzel der App; was fehlt, gilt als nicht zugeordnet.
    fun plan(b: FlexBericht.Bericht, kuerzel: Map<String, String>, depot: Account, jetzt: Long): Plan {
        val lese = BrokerExport.Bericht()
        val offen = ArrayList<FlexBericht.Offen>()
        val alle = FlexBericht.zeilen(b, lese, offen)
        val ids = depot.transactions.mapTo(HashSet()) { it.id }
        fun bekannt(z: Zeile) = "$PRAEFIX-${z.id}" in ids || "$PRAEFIX-${z.id}-g" in ids
        val neu = alle.filterNot { bekannt(it) }
        val stichzeit = (if (b.erzeugt > 0) b.erzeugt else if (b.bis > 0) b.bis + 43_199_000L else jetzt).coerceAtMost(jetzt)

        var karte = kuerzel
        var zeilen = neu
        // Ohne neue Zeilen bleibt das Depot unberuehrt
        fun lauf() = if (zeilen.isEmpty()) TradeRepublicImport.Ergebnis(depot, QUELLE, emptyList(), emptySet(), 0, 0L, 0L)
            else TradeRepublicImport.konto(zeilen, karte, depot, jetzt, QUELLE, PRAEFIX, vorhanden = depot)
        fun kuerzelVon(symbol: String, klasse: String) = kuerzel[if (klasse == "CRYPTO") TradeRepublicImport.kryptoKuerzel(symbol) else symbol]
        var erg = lauf()

        // Soll je Kuerzel der App aus den offenen Positionen
        class Soll(var stueck: Double = 0.0, var kosten: Double? = 0.0, var name: String = "", var seit: Long = 0L, var kurs: Double? = null)
        val soll = LinkedHashMap<String, Soll>()
        for (p in b.positionen.orEmpty()) {
            val app = if (p.papier.abbildbar && p.stueck > 0) kuerzelVon(p.papier.zeilenSymbol, p.papier.zeilenKlasse) else null
            if (app == null) { offen += FlexBericht.Offen(p.papier.anzeige, p.stueck); continue }
            val k = b.inEuro(p.papier.waehrung, p.fx)
            val s = soll.getOrPut(app) { Soll() }
            s.stueck += p.stueck
            val kosten = (p.einstand ?: p.einstandJeStueck?.let { it * p.stueck })?.let { e -> k?.let { e * it } }
            s.kosten = if (kosten != null && s.kosten != null) (s.kosten ?: 0.0) + kosten else null
            s.name = s.name.ifBlank { p.papier.anzeige }
            if (p.eroeffnet > 0) s.seit = if (s.seit == 0L) p.eroeffnet else minOf(s.seit, p.eroeffnet)
            if (s.kurs == null) s.kurs = p.kurs?.let { m -> k?.let { m * it } }
        }

        fun bestand(a: Account, key: String) = a.positions.firstOrNull { it.key == key && it.derivative == null }?.qty ?: 0.0
        val zu = ArrayList<Pair<String, Double>>()
        if (b.positionen != null) {
            // Zeile.symbol, unter dem ein Kuerzel in den Zeilen schon vorkommt, damit der Bestand nicht doppelt gefuehrt wird
            val schluesselJe = HashMap<String, Zeile>()
            for (z in neu) if (z.symbol.isNotBlank() && (z.klasse == "STOCK" || z.klasse == "FUND" || z.klasse == "CRYPTO")) kuerzelVon(z.symbol, z.klasse)?.let { schluesselJe.getOrPut(it) { z } }
            val ab = ArrayList<Zeile>()
            fun raus(key: String, name: String, menge: Double) {
                val vorlage = schluesselJe[key]
                val symbol = vorlage?.symbol ?: key
                if (vorlage == null) karte = karte + (key to key)
                ab += Zeile(stichzeit, "TRADING", "TRANSFER_OUT", vorlage?.klasse ?: "STOCK", name, symbol, -menge, null, 0.0, 0.0, 0.0, "", "k$jetzt-$key")
            }
            val vonHier = (depot.transactions + erg.konto.transactions).filter { it.via == VIA }.flatMapTo(HashSet()) { listOf(it.key, it.symbol) }
            for (p in erg.konto.positions) {
                if (p.derivative != null) continue
                val s = soll[p.key]
                if (s != null) { if (p.qty - s.stueck > EPS) raus(p.key, p.name, p.qty - s.stueck) }
                else if (p.key in vonHier) raus(p.key, p.name, p.qty)
            }
            if (ab.isNotEmpty()) { zeilen = neu + ab; erg = lauf() }
            for ((key, s) in soll) { val d = s.stueck - bestand(erg.konto, key); if (d > EPS) zu += key to d }
        }

        // Kein negatives Guthaben: fehlen dem Bericht fruehere Einzahlungen, kommt eine errechnete dazu
        var einzahlung = 0.0
        if (erg.konto.cash < -0.005) {
            einzahlung = kotlin.math.ceil(-erg.konto.cash * 100.0 - 1e-6) / 100.0
            val erst = (zeilen.minOfOrNull { it.zeit } ?: stichzeit) - 1000L
            zeilen = zeilen + Zeile(erst, "CASH", "DEPOSIT", "", "", "", null, null, einzahlung, 0.0, 0.0, "", "e$jetzt")
            erg = lauf()
        }

        // Fehlende Stuecke als Uebertrag ohne Geldfluss, so dass der Einstand des Berichts herauskommt
        var konto = erg.konto
        for ((key, menge) in zu) {
            val s = soll.getValue(key)
            val alt = konto.positions.firstOrNull { it.key == key }
            val rest = s.kosten?.let { (it - (alt?.invested ?: 0.0)) / menge }?.takeIf { it.isFinite() && it > 0 }
            val preis = rest ?: s.kosten?.let { it / s.stueck }?.takeIf { it.isFinite() && it > 0 } ?: s.kurs?.takeIf { it > 0 }
            val wann = (if (s.seit > 0) s.seit else stichzeit).coerceAtMost(jetzt)
            val r = if (preis == null) null else Trading.importPosition(konto, key, s.name.ifBlank { key }, menge, preis, wann, jetzt)
            if (r == null || !r.ok) { offen += FlexBericht.Offen(s.name.ifBlank { key }, menge); continue }
            val vorher = konto.transactions.mapTo(HashSet()) { it.id }
            konto = r.account.copy(transactions = r.account.transactions.map { if (it.id !in vorher) it.copy(via = VIA) else it })
        }

        val unterschiede = unterschiede(depot, konto)
        return Plan(
            basis = depot, konto = konto, unterschiede = unterschiede,
            buchungen = (konto.transactions.size - depot.transactions.size).coerceAtLeast(0), bekannt = alle.size - neu.size,
            einzahlung = einzahlung, offen = (offen + erg.ohneKurs.map { FlexBericht.Offen(it.first, it.second) }).distinct(), ohneKauf = erg.ohneKauf,
            ungelesen = lese.ungelesen, ohneWechselkurs = lese.ohneWechselkurs, bargeld = b.bargeldEuro, von = b.von, bis = b.bis,
            ohnePositionen = b.positionen == null,
        )
    }

    // Aktien, Fonds und Krypto vorher und nachher: neu, mit anderer Stueckzahl, entfallen.
    fun unterschiede(vorher: Account, nachher: Account): List<Unterschied> {
        val alt = vorher.positions.filter { it.derivative == null }.associateBy { it.key }
        val neu = nachher.positions.filter { it.derivative == null }.associateBy { it.key }
        val aus = ArrayList<Unterschied>()
        for ((k, p) in neu) {
            val a = alt[k]
            if (a == null) aus += Unterschied(Art.NEU, k, p.name, 0.0, p.qty)
            else if (abs(a.qty - p.qty) > EPS) aus += Unterschied(Art.GEAENDERT, k, p.name, a.qty, p.qty)
        }
        for ((k, a) in alt) if (k !in neu) aus += Unterschied(Art.ENTFAELLT, k, a.name, a.qty, 0.0)
        return aus.sortedWith(compareBy({ it.art.ordinal }, { it.name.lowercase() }))
    }
}
