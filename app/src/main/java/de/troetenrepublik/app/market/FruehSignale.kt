package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Eingepreist
import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehindikatoren.Signal
import de.troetenrepublik.app.engine.XlsLeser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

// Abrufe der fruehen Signale (seit 5.92). Je Quelle ein eigener Zwischenspeicher, damit die Seite von Oel nur die
// EIA laedt und die einer Fluglinie nur die TSA. Die Daten erscheinen woechentlich (Drought Monitor, EIA,
// Erstantraege) oder taeglich (TSA, AGSI); zwoelf Stunden reichen. Was nicht antwortet, behaelt den letzten Stand.
class FruehSignale(private val api: MarketApi) {

    private class Speicher<T>(val dauer: Long) {
        val sperre = Mutex()
        @Volatile var stand: Pair<Long, T>? = null
    }

    private val eia = Speicher<Map<String, Signal>>(12 * 3_600_000L)
    private val antraege = Speicher<Signal?>(12 * 3_600_000L)
    private val flug = Speicher<Signal?>(12 * 3_600_000L)
    private val duerre = Speicher<Signal?>(24 * 3_600_000L)
    private val gasEu = Speicher<Signal?>(6 * 3_600_000L)
    @Volatile private var gasEuSchluessel = ""

    // Gespeicherten Stand nehmen, solange er frisch ist; sonst laden. Ein leeres Ergebnis behaelt den letzten Stand.
    private suspend fun <T> holen(s: Speicher<T>, neu: Boolean, leer: (T) -> Boolean, laden: suspend () -> T): T? = s.sperre.withLock {
        val now = System.currentTimeMillis()
        s.stand?.takeIf { !neu && now - it.first < s.dauer }?.let { return@withLock it.second }
        val wert = runCatching { laden() }.getOrNull()
        if (wert != null && !leer(wert)) { s.stand = now to wert; wert } else s.stand?.second ?: wert
    }

    private fun heute() = LocalDate.now(ZoneOffset.UTC)

    // Signale zu den Schluesseln aus Fruehindikatoren; GAS_EU nur mit Schluessel fuer AGSI+.
    suspend fun signale(schluessel: Collection<String>, agsiSchluessel: String, neu: Boolean = false): Map<String, Signal> = coroutineScope {
        val teile = ArrayList<kotlinx.coroutines.Deferred<Map<String, Signal>>>()
        if (schluessel.any { it in Fruehindikatoren.EIA }) teile += async { eiaLager(neu).orEmpty().filterKeys { it in schluessel } }
        if (Fruehindikatoren.ERSTANTRAEGE in schluessel) teile += async { listOfNotNull(erstantraege(neu)).associateBy { it.schluessel } }
        if (Fruehindikatoren.FLUGGAESTE in schluessel) teile += async { listOfNotNull(fluggaeste(neu)).associateBy { it.schluessel } }
        if (Fruehindikatoren.DUERRE in schluessel) teile += async { listOfNotNull(duerre(neu)).associateBy { it.schluessel } }
        if (Fruehindikatoren.GAS_EU in schluessel && agsiSchluessel.isNotBlank()) teile += async { listOfNotNull(gasEu(agsiSchluessel, neu)).associateBy { it.schluessel } }
        teile.awaitAll().fold(emptyMap()) { a, b -> a + b }
    }

    // Lagerreihen der letzten Abfrage, fuer „Im Preis?“.
    private val eiaReihen = java.util.concurrent.ConcurrentHashMap<String, List<Pair<Long, Double>>>()

    // Lager der EIA aus den Excel-Dateien der Wochenreihen (ohne Schluessel), alle sechs gleichzeitig.
    private suspend fun eiaLager(neu: Boolean): Map<String, Signal>? = holen(eia, neu, { it.size < 2 }) {
        coroutineScope {
            Fruehindikatoren.EIA.map { (k, id) ->
                async {
                    runCatching {
                        val bytes = api.datei(Fruehindikatoren.eiaAdresse(id), 4_000_000) ?: return@async null
                        val reihe = withContext(Dispatchers.Default) { XlsLeser.reihe(bytes) }
                        if (reihe.isEmpty()) Quellen.unerwartet("fruehsignale", "eia $id") else eiaReihen[k] = reihe
                        Fruehindikatoren.lager(k, reihe)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().associateBy { it.schluessel }
        }
    }

    // Erstantraege ueber FRED, zwei Jahre, mit der offenen Kennung der App.
    private suspend fun erstantraege(neu: Boolean): Signal? = holen(antraege, neu, { it == null }) {
        val a = api.anfrage(
            Fruehindikatoren.fredAdresse(heute().minusYears(2)),
            mapOf("Accept" to "text/csv", "User-Agent" to Ersatzabrufe.KENNUNG),
        )
        a.takeIf { it.ok }?.text?.let { Fruehindikatoren.erstantraege(Fruehindikatoren.fredLesen(it)) }
    }

    // Fluggaeste: Seite des laufenden und des vorigen Jahres (fuer den Vergleich ueber 364 Tage).
    private suspend fun fluggaeste(neu: Boolean): Signal? = holen(flug, neu, { it == null }) {
        val jahr = heute().year
        val seiten = coroutineScope {
            listOf(null, jahr - 1).map { j -> async { api.anfrage(Fruehindikatoren.tsaAdresse(j), mapOf("Accept" to "text/html"), max = 3_000_000).takeIf { it.ok }?.text } }.awaitAll()
        }
        if (seiten.any { it == null }) return@holen null
        val tage = seiten.filterNotNull().flatMap(Fruehindikatoren::tsaLesen)
        if (tage.isEmpty()) Quellen.unerwartet("fruehsignale", "tsa")
        Fruehindikatoren.fluggaeste(tage)
    }

    // Duerre: fuenf Jahre und zwei Wochen, damit dieselbe Woche jedes Vorjahres dabei ist.
    private suspend fun duerre(neu: Boolean): Signal? = holen(duerre, neu, { it == null }) {
        val bis = heute()
        val a = api.anfrage(Fruehindikatoren.duerreAdresse(bis.minusYears(5).minusDays(14), bis), mapOf("Accept" to "text/csv"))
        a.takeIf { it.ok }?.text?.let { Fruehindikatoren.lager(Fruehindikatoren.DUERRE, Fruehindikatoren.duerreLesen(it), punkte = true, nurHoch = true) }
    }

    // Gasspeicher der EU: die letzten 30 Gastage und derselbe Tag in den fuenf Vorjahren, mit dem eigenen Schluessel
    // im Kopf x-key. Ein neuer Schluessel laedt neu.
    private suspend fun gasEu(schluessel: String, neu: Boolean): Signal? = holen(gasEu, neu || schluessel != gasEuSchluessel, { it == null }) {
        gasEuSchluessel = schluessel
        val kopf = mapOf("x-key" to schluessel)
        val jetzt = api.anfrage(Fruehindikatoren.agsiAdresse(), kopf).takeIf { it.ok }?.text?.let(Fruehindikatoren::agsiLesen).orEmpty()
        val letzter = jetzt.lastOrNull() ?: return@holen null
        val tag = LocalDate.ofEpochDay(letzter.first)
        val frueher = coroutineScope {
            (1..5).map { j ->
                async { api.anfrage(Fruehindikatoren.agsiAdresse(tag.minusYears(j.toLong())), kopf).takeIf { it.ok }?.text?.let(Fruehindikatoren::agsiLesen).orEmpty() }
            }.awaitAll().flatten()
        }
        Fruehindikatoren.gasEu((jetzt + frueher).distinctBy { it.first }.sortedBy { it.first })
    }

    // ------------------------------------------------------------ Im Preis (5.92)

    // Futures-Tabellen der EIA je Datei und Blatt. Die Reihen enden im April 2024 und aendern sich nicht mehr: eine
    // Woche zwischengespeichert. Einordnungen mit heutigen Kursen zwoelf Stunden.
    private val terminTabellen = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<Pair<Long, DoubleArray>>>>()
    private val terminSperre = Mutex()
    private val imPreisSpeicher = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Fruehindikatoren.ImPreis>>()
    private val reihenSpeicher = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<Pair<Long, Double>>>>()
    private val reihenSperren = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private suspend fun terminTabelle(t: Fruehindikatoren.Termin): List<Pair<Long, DoubleArray>>? = terminSperre.withLock {
        val k = t.datei + "#" + t.blatt
        val jetzt = System.currentTimeMillis()
        terminTabellen[k]?.takeIf { jetzt - it.first < 7 * 24 * 3_600_000L }?.let { return@withLock it.second }
        val bytes = runCatching { api.datei(t.datei, 6_000_000) }.getOrNull()
        val tab = bytes?.let { b -> withContext(Dispatchers.Default) { XlsLeser.tabelle(b, t.blatt) } }.orEmpty()
        if (tab.isNotEmpty() && Fruehindikatoren.terminPlausibel(t, tab)) {
            terminTabellen[k] = jetzt to tab
            tab
        } else {
            if (bytes != null) Quellen.unerwartet("fruehsignale", "eia futures ${t.wurzel}")
            terminTabellen[k]?.second
        }
    }

    // Eine Reihe ueber laden, zwoelf Stunden zwischengespeichert; leer behaelt den letzten Stand.
    private suspend fun reihe(name: String, laden: suspend () -> List<Pair<Long, Double>>): List<Pair<Long, Double>> =
        reihenSperren.getOrPut(name) { Mutex() }.withLock {
            val jetzt = System.currentTimeMillis()
            reihenSpeicher[name]?.takeIf { jetzt - it.first < 12 * 3_600_000L }?.let { return@withLock it.second }
            val r = runCatching { laden() }.getOrNull().orEmpty()
            if (r.isNotEmpty()) reihenSpeicher[name] = jetzt to r
            r.ifEmpty { reihenSpeicher[name]?.second.orEmpty() }
        }

    // Wochenschluesse bei Yahoo seit von (Tag seit 1970, Schluss).
    private suspend fun wochen(symbol: String, von: LocalDate): List<Pair<Long, Double>> = reihe("y:$symbol") {
        val s = api.candlesBetween(symbol, von.toEpochDay() * 86_400L, System.currentTimeMillis() / 1000 + 86_400L, "1wk")
        s.candles.filter { it.close.isFinite() && it.close > 0 }.map { Math.floorDiv(it.t, 86_400_000L) to it.close }
    }

    // Reihe von FRED; wie bei den Erstantraegen mit der offenen Kennung der App.
    private suspend fun fredReihe(id: String, von: LocalDate): List<Pair<Long, Double>> = reihe("fred:$id") {
        val a = api.anfrage(
            Fruehindikatoren.fredAdresse(von, id), mapOf("Accept" to "text/csv", "User-Agent" to Ersatzabrufe.KENNUNG), max = 2_000_000,
        )
        a.takeIf { it.ok }?.text?.let(Fruehindikatoren::fredLesen).orEmpty()
    }

    // Einordnung „Im Preis?“ zu den gefragten Signalen. getreide: Kuerzel bei Yahoo fuer die Duerre (Mais, Weizen
    // oder Soja), ohne Angabe Mais. Was nicht geht, fehlt in der Karte.
    suspend fun imPreis(schluessel: Collection<String>, agsiSchluessel: String, getreide: String? = null, neu: Boolean = false): Map<String, Fruehindikatoren.ImPreis> = coroutineScope {
        val korn = Fruehindikatoren.getreide(getreide)
        schluessel.distinct().map { k ->
            async {
                val name = if (k == Fruehindikatoren.DUERRE) "$k:$korn" else k
                val jetzt = System.currentTimeMillis()
                imPreisSpeicher[name]?.takeIf { !neu && jetzt - it.first < 12 * 3_600_000L }?.let { return@async k to it.second }
                val e = runCatching {
                    when (k) {
                        in Fruehindikatoren.TERMIN -> lagerImPreis(k, neu)
                        Fruehindikatoren.DUERRE -> duerreImPreis(korn)
                        Fruehindikatoren.FLUGGAESTE -> flugImPreis()
                        Fruehindikatoren.ERSTANTRAEGE -> antraegeImPreis()
                        Fruehindikatoren.GAS_EU -> if (agsiSchluessel.isBlank()) null else gasEuImPreis(agsiSchluessel)
                        in Fruehindikatoren.ERSATZ_MARKT -> ersatzImPreis(k)
                        else -> null
                    }
                }.getOrNull()
                if (e != null) imPreisSpeicher[name] = jetzt to e
                k to (e ?: imPreisSpeicher[name]?.second)
            }
        }.awaitAll().mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    }

    // Lager gegen die Terminkurve: Lagerreihe der EIA, Futures-Tabelle der EIA, heutige Kontrakte bei Yahoo.
    private suspend fun lagerImPreis(k: String, neu: Boolean): Fruehindikatoren.ImPreis? {
        val t = Fruehindikatoren.TERMIN[k] ?: return null
        if (eiaReihen[k] == null || neu) eiaLager(neu)
        val lager = eiaReihen[k] ?: return null
        val tab = terminTabelle(t) ?: return null
        val h = heute()
        val kandidaten = Eingepreist.kontrakte(t.wurzel, t.boerse, h.year, h.monthValue)
        val kurve = Eingepreist.kurve(kandidaten, api.schlusskurse(kandidaten.map { it.symbol }, "5d"))
        return withContext(Dispatchers.Default) { Fruehindikatoren.imPreisLager(k, lager, tab, kurve, h.toEpochDay()) }
    }

    // Duerre gegen den Getreidepreis (Abstand zum Jahresschnitt), beide seit 2000.
    private suspend fun duerreImPreis(korn: String): Fruehindikatoren.ImPreis? {
        val von = LocalDate.of(2000, 1, 1)
        val duerre = reihe("duerre") {
            val a = api.anfrage(Fruehindikatoren.duerreAdresse(von, heute()), mapOf("Accept" to "text/csv"), max = 3_000_000)
            a.takeIf { it.ok }?.text?.let(Fruehindikatoren::duerreLesen).orEmpty()
        }
        val preis = wochen(korn, von)
        if (duerre.isEmpty() || preis.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            Fruehindikatoren.einordnungAusReihen(Eingepreist.abweichungen(duerre, punkte = true), Eingepreist.gegenSchnitt(preis), 1, saison = true)
                ?.let { Fruehindikatoren.ImPreis(Fruehindikatoren.DUERRE, Fruehindikatoren.Mass.PREIS, it, markt = korn) }
        }
    }

    // Ersatzquellen gegen ihre Branche (5.94): der GSCPI als Stand, Fracht und Chipproduktion gegen den
    // Vorjahresmonat; Transport- bzw. Chipwerte gegen den S&P 500 ueber ein Jahr.
    private suspend fun ersatzImPreis(k: String): Fruehindikatoren.ImPreis? {
        val markt = Fruehindikatoren.ERSATZ_MARKT[k] ?: return null
        val x = if (k == Ersatzquellen.LIEFERKETTEN) {
            reihe("gscpi") {
                val bytes = api.datei(Ersatzquellen.GSCPI_DATEI, 4_000_000, mapOf("User-Agent" to Ersatzabrufe.KENNUNG)) ?: return@reihe emptyList()
                withContext(Dispatchers.Default) { Ersatzquellen.gscpiLesen(bytes, heute()) }
            }
        } else {
            Eingepreist.monatsVorjahr(fredReihe(Ersatzquellen.FRED_SIGNALE[k] ?: return null, LocalDate.of(1997, 1, 1)))
        }
        val von = LocalDate.of(1999, 1, 1)
        val branche = wochen(markt, von)
        val spy = wochen(Fruehindikatoren.BREITER_MARKT, von)
        if (x.isEmpty() || branche.isEmpty() || spy.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            Fruehindikatoren.einordnungAusReihen(
                x, Eingepreist.relativ(branche, spy, 364), Fruehindikatoren.ERSATZ_VORZEICHEN[k] ?: 0, saison = false,
                abstand = Fruehindikatoren.ERSATZ_ABSTAND,
            )?.let { Fruehindikatoren.ImPreis(k, Fruehindikatoren.Mass.BRANCHE, it, markt = markt) }
        }
    }

    // Fluggaeste gegen Airline-Aktien: TSA-Seiten ab dem Jahr vor FLUG_AB, JETS gegen SPY ueber ein Jahr.
    private suspend fun flugImPreis(): Fruehindikatoren.ImPreis? {
        val jahr = heute().year
        val tage = reihe("tsa") {
            val jahre: List<Int?> = (Fruehindikatoren.FLUG_AB.year - 1 until jahr).toList() + listOf(null)
            val seiten = coroutineScope {
                jahre.map { j -> async { api.anfrage(Fruehindikatoren.tsaAdresse(j), mapOf("Accept" to "text/html"), max = 3_000_000).takeIf { it.ok }?.text } }.awaitAll()
            }
            if (seiten.any { it == null }) emptyList() else seiten.filterNotNull().flatMap(Fruehindikatoren::tsaLesen).distinctBy { it.first }.sortedBy { it.first }
        }
        val von = Fruehindikatoren.FLUG_AB.minusYears(2)
        val jets = wochen(Fruehindikatoren.AIRLINES, von)
        val spy = wochen(Fruehindikatoren.BREITER_MARKT, von)
        if (tage.isEmpty() || jets.isEmpty() || spy.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            Fruehindikatoren.einordnungAusReihen(
                Eingepreist.jahresvergleiche(tage), Eingepreist.relativ(jets, spy, 364), 1, saison = false, ab = Fruehindikatoren.FLUG_AB.toEpochDay(),
            )?.let { Fruehindikatoren.ImPreis(Fruehindikatoren.FLUGGAESTE, Fruehindikatoren.Mass.AIRLINES, it, markt = Fruehindikatoren.AIRLINES) }
        }
    }

    // Erstantraege gegen die zweijaehrige Rendite (Abstand zum Jahreshoch), dazu zyklische gegen defensive Aktien
    // ueber ein halbes Jahr.
    private suspend fun antraegeImPreis(): Fruehindikatoren.ImPreis? {
        val antraege = fredReihe("ICSA", LocalDate.of(1967, 1, 1))
        val zins = fredReihe(Fruehindikatoren.ZWEI_JAHRE, LocalDate.of(1976, 1, 1))
        if (antraege.isEmpty() || zins.isEmpty()) return null
        val von = LocalDate.of(1999, 1, 1)
        val zyk = wochen(Fruehindikatoren.ZYKLISCH, von)
        val def = wochen(Fruehindikatoren.DEFENSIV, von)
        return withContext(Dispatchers.Default) {
            val x = Eingepreist.anstiege(antraege)
            val haupt = Fruehindikatoren.einordnungAusReihen(x, Eingepreist.unterHoch(zins), -1, saison = false) ?: return@withContext null
            val zusatz = if (zyk.isEmpty() || def.isEmpty()) null else Fruehindikatoren.einordnungAusReihen(x, Eingepreist.relativ(zyk, def, 182), -1, saison = false)
            Fruehindikatoren.ImPreis(Fruehindikatoren.ERSTANTRAEGE, Fruehindikatoren.Mass.ZINS, haupt, zusatz, markt = Fruehindikatoren.ZWEI_JAHRE)
        }
    }

    // EU-Gasspeicher gegen den TTF-Preis (Abstand zum Jahresschnitt), Speicher seit 2016 in Seiten zu 300 Tagen.
    private suspend fun gasEuImPreis(schluessel: String): Fruehindikatoren.ImPreis? {
        val von = LocalDate.of(2016, 1, 1)
        val bis = heute()
        val speicher = reihe("agsi") {
            val kopf = mapOf("x-key" to schluessel)
            val aus = ArrayList<Pair<Long, Double>>()
            var seite = 1
            var letzte = 1
            while (seite <= letzte && seite <= 30) {
                val a = api.anfrage(Fruehindikatoren.agsiZeitraum(von, bis, seite), kopf).takeIf { it.ok }?.text ?: return@reihe emptyList()
                if (seite == 1) letzte = Fruehindikatoren.agsiSeiten(a)
                aus += Fruehindikatoren.agsiLesen(a)
                seite++
            }
            aus.distinctBy { it.first }.sortedBy { it.first }
        }
        val preis = wochen(Fruehindikatoren.TTF, von)
        if (speicher.isEmpty() || preis.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            // Taeglicher Speicherstand, fuer die Schaetzung jeder siebte Tag rueckwaerts vom letzten
            val woechentlich = speicher.filterIndexed { i, _ -> (speicher.size - 1 - i) % 7 == 0 }
            Fruehindikatoren.einordnungAusReihen(Eingepreist.abweichungen(woechentlich, punkte = true), Eingepreist.gegenSchnitt(preis), -1, saison = true)
                ?.let { Fruehindikatoren.ImPreis(Fruehindikatoren.GAS_EU, Fruehindikatoren.Mass.PREIS, it, markt = Fruehindikatoren.TTF) }
        }
    }
}
