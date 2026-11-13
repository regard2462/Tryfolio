package de.troetenrepublik.app

import de.troetenrepublik.app.engine.InsiderTrend
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Radar
import de.troetenrepublik.app.engine.Trendverlauf
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.RegierungOge
import de.troetenrepublik.app.ui.CopyRouten
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

// Was eine Signalquelle des Radars bekommt: die Katalogaktien, die staerksten Kandidaten nach den uebrigen Quellen
// (nur fuer Quellen mit Einzelabruf je Wert) und den heutigen Tag (Tage seit 1970). Namen von Werten ausserhalb des
// Katalogs tragen die Quellen in namen ein.
class RadarKontext(
    val aktien: List<Catalog.Stock>,
    val kandidaten: List<String>,
    val heute: Long,
    val namen: MutableMap<String, String> = ConcurrentHashMap(),
)

// Eine Signalquelle des Radars. Quellen mit jeWert = true rufen je Kandidat ab und laufen deshalb nach den uebrigen,
// nur fuer die staerksten Kandidaten. schluessel benennt die Quelle.
interface RadarQuelle {
    val schluessel: String
    val jeWert: Boolean get() = false
    suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal>
}

// Ergebnis eines Durchlaufs: Treffer, Namen, Quellen mit Daten und Zeitpunkt.
data class RadarStand(val treffer: List<Radar.Treffer>, val namen: Map<String, String>, val quellen: List<String>, val am: Long)

// Alle Quellen des Radars in der Reihenfolge der Abrufe. Anschluss fuer weitere Daten:
// • Wikipedia-Aufrufe fuer viele Werte auf einmal (Paket Maerkte): eigene RadarQuelle mit Art WIKIPEDIA anhaengen
//   oder WikipediaQuelle.aufrufe ersetzen; heute je Kandidat ueber AppState.wikiInteresseFuer.
// • Verdaechtige Wetten erfolgreicher Polymarket-Haendler (Paket Prognosemaerkte): PrognoseQuelle.wetten ersetzen;
//   heute die grossen Wetten der Woche aus PrognoseRepository.grosseWetten.
object RadarQuellen {
    val alle: MutableList<RadarQuelle> = mutableListOf(
        ErwaehnungenQuelle, InsiderQuelle, PolitikQuelle, VolumenQuelle, PrognoseQuelle,
        WikipediaQuelle, GrossaktionaerQuelle, OptionenQuelle,
    )

    // Kandidaten fuer die Quellen mit Einzelabruf.
    const val KANDIDATEN = 10
}

// Letzter Durchlauf, damit das Radar beim Wiederoeffnen sofort steht; 30 Minuten frisch.
object RadarSpeicher {
    @Volatile var stand: RadarStand? = null
    const val FRISCH_MS = 30 * 60_000L
}

private fun tagAus(iso: String): Long? = runCatching { LocalDate.parse(iso.trim().take(10)).toEpochDay() }.getOrNull()

// Radar durchlaufen: erst die Quellen ohne Einzelabruf, dann fuer die staerksten Kandidaten die uebrigen.
suspend fun AppState.radarLaden(fortschritt: (Float) -> Unit): RadarStand {
    val katalog = Catalog.loaded() ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Catalog.get() }
    val heute = LocalDate.now(ZoneOffset.UTC).toEpochDay()
    val aktien = katalog.aktien.distinctBy { it.s }
    val namen = ConcurrentHashMap<String, String>()
    aktien.forEach { namen[it.s] = it.n }
    val signale = ArrayList<Radar.Signal>()
    val genutzt = ArrayList<String>()
    val quellen = RadarQuellen.alle.toList()
    val breit = quellen.filter { !it.jeWert }
    val einzeln = quellen.filter { it.jeWert }
    var schritt = 0
    fun weiter() { schritt++; fortschritt(schritt.toFloat() / quellen.size) }
    for (q in breit) {
        val l = runCatching { q.signale(this, RadarKontext(aktien, emptyList(), heute, namen)) }.getOrDefault(emptyList())
        if (l.isNotEmpty()) { signale += l; genutzt += q.schluessel }
        weiter()
    }
    val bekannt = ErwaehnungenQuelle.bekannt
    val kandidaten = Radar.bewerten(signale, heute, bekannt).filter { !it.bekannt }.take(RadarQuellen.KANDIDATEN).map { it.symbol }
    for (q in einzeln) {
        val l = runCatching { q.signale(this, RadarKontext(aktien, kandidaten, heute, namen)) }.getOrDefault(emptyList())
        if (l.isNotEmpty()) { signale += l; genutzt += q.schluessel }
        weiter()
    }
    val stand = RadarStand(Radar.bewerten(signale, heute, bekannt), namen.toMap(), genutzt, System.currentTimeMillis())
    RadarSpeicher.stand = stand
    return stand
}

// Reddit-Erwaehnungen (ApeWisdom), mit dem mitgeschriebenen Verlauf der App. Die zehn meisterwaehnten Werte gelten
// als schon bekannt.
object ErwaehnungenQuelle : RadarQuelle {
    override val schluessel = "REDDIT"
    @Volatile var bekannt: Set<String> = emptySet()

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> {
        val liste = state.redditTrends()
        if (liste.isEmpty()) return emptyList()
        val archiv = runCatching { state.trendArchiv() }.getOrNull()
        bekannt = liste.filter { it.rang <= 10 }.map { Trendverlauf.normal(it.ticker) }.toSet()
        return liste.mapNotNull { e ->
            val s = Trendverlauf.normal(e.ticker)
            if (s.isEmpty() || s.length > 6) return@mapNotNull null
            val verlauf = archiv?.let { Trendverlauf.reihe(it, "all-stocks", s, 9, k.heute) }.orEmpty()
            val staerke = Radar.erwaehnungen(e.erwaehnungen, e.erwaehnungenVorher, verlauf) ?: return@mapNotNull null
            if (s !in k.namen && e.name.isNotBlank()) k.namen[s] = e.name
            Radar.Signal(s, Radar.Art.ERWAEHNUNGEN, k.heute, staerke, e.erwaehnungen.toDouble(), (e.erwaehnungenVorher ?: 0).toDouble())
        }
    }
}

// Insiderkaeufe der letzten 45 Tage aus den Meldungen auf dem Geraet (mitgeliefert und zuletzt auf den Insiderseiten
// geholt): SEC (alle US-Werte), BaFin und Europa (zugeordnet ueber den Namen). Ohne Abruf, damit das Radar schnell steht.
object InsiderQuelle : RadarQuelle {
    override val schluessel = "INSIDER"

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> {
        val ab = LocalDate.ofEpochDay(k.heute - 45).toString()
        val kaeufe = HashMap<String, MutableList<Radar.InsiderKauf>>()
        fun dazu(s: String, kauf: Radar.InsiderKauf) { kaeufe.getOrPut(s) { ArrayList() } += kauf }
        val usdEuro = state.market.fxKurse.value["USD"]?.takeIf { it > 0 } ?: 1.1
        runCatching { state.secOhneNetz() }
        state.sec.value?.geschaefte?.filter { it.kauf && it.d >= ab && it.wert > 0 }?.forEach { g ->
            val s = g.k.uppercase().ifBlank { return@forEach }
            if (s !in k.namen && g.e.isNotBlank()) k.namen[s] = g.e
            dazu(s, Radar.InsiderKauf(g.p, g.rolle, g.wert / usdEuro, tagAus(g.d) ?: return@forEach))
        }
        runCatching { state.bafinLaden(netz = false) }.getOrNull()?.let { d ->
            val l = d.eigengeschaefte.filter { it.kauf && it.d >= ab && it.i.equals("Aktie", true) && (it.vol ?: 0.0) > 0 }
            val namen = namenZuSymbolen(l.filter { it.k.isNullOrBlank() }.map { it.e }, k.aktien.filter { it.c == "DE" })
            l.forEach { g ->
                val s = g.k?.takeIf { it.isNotBlank() } ?: namen[g.e] ?: return@forEach
                dazu(s, Radar.InsiderKauf(g.p, InsiderTrend.rolleBafin(g.r), EuInsider.inEuro(g.vol ?: 0.0, g.w.ifBlank { "EUR" }), tagAus(g.d) ?: return@forEach))
            }
        }
        for (land in EuInsider.LAENDER) {
            val d = runCatching { state.euLaden(land, netz = false) }.getOrNull() ?: continue
            val l = d.geschaefte.filter { it.kauf && it.d >= ab && it.i.equals("Aktie", true) && it.wert > 0 }
            if (l.isEmpty()) continue
            val namen = namenZuSymbolen(l.map { it.e }, k.aktien.filter { it.c == land })
            l.forEach { g ->
                val s = namen[g.e] ?: return@forEach
                dazu(s, Radar.InsiderKauf(g.fuer.ifBlank { g.p }, g.rolle, EuInsider.inEuro(g.wert, g.w), tagAus(g.d) ?: return@forEach))
            }
        }
        return kaeufe.flatMap { (s, l) -> Radar.insider(s, l) }
    }
}

// Aktienkaeufe von Abgeordneten, Senatoren und Regierungsmitgliedern, gemeldet in den letzten 45 Tagen. Nutzt die
// geladene Auswertung „Kongress kauft“, sonst die mitgelieferten und schon gelesenen Meldungen (ohne Abruf).
object PolitikQuelle : RadarQuelle {
    override val schluessel = "POLITIK"

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> {
        val ab = (k.heute - 45) * 86_400_000L
        val kongress = HashMap<String, MutableList<Triple<String, Double, Long>>>()
        val fertig = state.kongressFertig()
        if (fertig != null) {
            fertig.handel.filter { it.kauf && it.gemeldet >= ab }.forEach { h ->
                kongress.getOrPut(h.symbol) { ArrayList() } += Triple(h.name, h.betrag, Math.floorDiv(h.gemeldet, 86_400_000L))
            }
        } else {
            fun dazu(person: String, gemeldet: Long, g: de.troetenrepublik.app.market.Kongress.Geschaeft) {
                val kz = g.kuerzel ?: return
                // Ab der Einreichung der Meldung, nicht ab dem Notification Date des Hauses
                val t = gemeldet.takeIf { it > 0 } ?: g.gemeldet
                if (g.typ != "ST" || !g.kauf || g.mitte <= 0 || t < ab) return
                kongress.getOrPut(CopyRouten.symbol(kz)) { ArrayList() } += Triple(person, g.mitte, Math.floorDiv(t, 86_400_000L))
            }
            state.haus?.let { h ->
                h.meldungen.filter { it.datum >= ab }.forEach { m ->
                    val g = h.geschaefte[m.docId] ?: state.copy?.papier?.geschaefte(m.docId) ?: return@forEach
                    g.forEach { dazu(m.name, m.datum, it) }
                }
            }
            state.senat?.meldungen?.forEach { sm ->
                if (sm.papier) return@forEach
                val t = Politik.tag(sm.eingang)
                if (t < ab) return@forEach
                sm.geschaefte.forEach { dazu(sm.name, t, Politik.geschaeft(it, t)) }
            }
        }
        val regierung = HashMap<String, MutableList<Triple<String, Double, Long>>>()
        runCatching { state.ogeLaden(netz = false) }.getOrNull()?.personen?.forEach { p ->
            RegierungOge.geschaefte(p).forEach { g ->
                val kz = g.kuerzel ?: return@forEach
                if (g.typ != "ST" || !g.kauf || g.mitte <= 0 || g.gemeldet < ab) return@forEach
                regierung.getOrPut(CopyRouten.symbol(kz)) { ArrayList() } += Triple(RegierungOge.anzeige(p.name), g.mitte, Math.floorDiv(g.gemeldet, 86_400_000L))
            }
        }
        return kongress.mapNotNull { (s, l) -> Radar.politik(s, Radar.Art.KONGRESS, l) } +
            regierung.mapNotNull { (s, l) -> Radar.politik(s, Radar.Art.REGIERUNG, l) }
    }
}

// Ungewoehnliches Handelsvolumen unter den Katalogaktien (Sammelabfrage der Kurse, fuenf Minuten gespeichert): nur
// Werte mit nennenswertem Umsatz und einem Kurs der letzten drei Tage.
object VolumenQuelle : RadarQuelle {
    override val schluessel = "VOLUMEN"

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> {
        val kurse = state.market.snapshot(k.aktien.map { it.s })
        return kurse.mapNotNull { (s, q) ->
            if (q.marketTime <= 0 || q.price * q.volume < 2_000_000.0) return@mapNotNull null
            val tag = Math.floorDiv(q.marketTime, 86_400_000L)
            if (k.heute - tag > 3) return@mapNotNull null
            val staerke = Radar.volumen(q.volume, q.avgVolume10) ?: return@mapNotNull null
            Radar.Signal(s, Radar.Art.VOLUMEN, tag, staerke, de.troetenrepublik.app.engine.RelativesVolumen.wert(q.volume, q.avgVolume10) ?: 0.0, q.changePct)
        }
    }
}

// Grosse Wetten auf Prognosemaerkten, deren Titel ein Unternehmen des Katalogs nennt (Themen Finanzen, Wirtschaft,
// Technik). wetten liefert die Wetten der letzten Woche; das Paket Prognosemaerkte setzt hier die verdaechtigen Wetten
// erfolgreicher Haendler ein.
object PrognoseQuelle : RadarQuelle {
    override val schluessel = "PROGNOSE"
    // Auffaellige Wetten erfolgreicher Haendler (Wettprofis) zuerst, dazu die grossen Wetten der Woche
    @Volatile var wetten: suspend (AppState) -> List<Prognose.GrosseWette> = { s ->
        (s.prognose.auffaelligeWetten(Prognose.WettZeit.WOCHE)?.map { it.wette }.orEmpty() + s.prognose.grosseWetten(Prognose.WettZeit.WOCHE).orEmpty())
            .distinctBy { Triple(it.titel, it.zeit, it.betrag) }
    }

    private val THEMEN = setOf("finance", "economy", "tech", "")

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> {
        val l = wetten(state).filter { it.kauf && it.thema in THEMEN }
        if (l.isEmpty()) return emptyList()
        // Nur Namen, die nicht zugleich ein haeufiges Wort sind: mindestens vier Zeichen, groessere Werte zuerst
        val namen = k.aktien.filter { it.c == "US" || it.c == "DE" }.map { it.s to it.n }
        return l.flatMap { w ->
            val titel = w.titel + " " + w.ausgang
            namen.filter { (_, n) -> titel.contains(n.substringBefore(' '), ignoreCase = true) && Radar.nennt(titel, n) }
                .mapNotNull { (s, _) -> Radar.prognose(s, Math.floorDiv(w.zeit, 86_400_000L), w.betrag, w.titel) }
        }
    }
}

// Wikipedia-Aufrufe der Kandidaten (Artikel aus den Firmendaten, sonst einmal gesucht). aufrufe laesst sich durch eine
// Quelle fuer viele Werte ersetzen.
object WikipediaQuelle : RadarQuelle {
    override val schluessel = "WIKIPEDIA"
    override val jeWert = true
    @Volatile var aufrufe: suspend (AppState, String, String) -> Double? = { s, symbol, name ->
        if (s.companies.value[symbol] == null) runCatching { s.companyInfoFor(symbol, name) }
        s.wikiInteresseFuer(symbol)?.wert?.veraenderung
    }

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> = k.kandidaten.mapNotNull { s ->
        val v = runCatching { aufrufe(state, s, k.namen[s] ?: s) }.getOrNull() ?: return@mapNotNull null
        Radar.wikipedia(v)?.let { Radar.Signal(s, Radar.Art.WIKIPEDIA, k.heute, it, v) }
    }
}

// Neue Beteiligungen nach Schedule 13D (60 Tage) aus den auf dem Geraet gespeicherten SEC-Formularen; fuer
// US-Kandidaten mit Kontaktadresse unter Zugaenge holt die App sie nach.
object GrossaktionaerQuelle : RadarQuelle {
    override val schluessel = "SEC13D"
    override val jeWert = true

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> {
        for (s in k.kandidaten.filter { '.' !in it }.take(6)) runCatching { state.secFormenLaden(s) }
        val ab = LocalDate.ofEpochDay(k.heute - 60).toString()
        return state.secFormen.value.flatMap { (s, st) ->
            st.halter.filter { it.aktiv && it.gemeldet >= ab }.mapNotNull { h ->
                Radar.grossaktionaer(s, tagAus(h.gemeldet) ?: return@mapNotNull null, h.anteil.takeIf { it > 0 }, h.name)
            }
        }
    }
}

// Auffaellige Call-Kaeufe in der Optionskette der US-Kandidaten (Yahoo, Stand letzter Handelstag).
object OptionenQuelle : RadarQuelle {
    override val schluessel = "OPTIONEN"
    override val jeWert = true

    override suspend fun signale(state: AppState, k: RadarKontext): List<Radar.Signal> =
        k.kandidaten.filter { '.' !in it && !it.startsWith("^") }.take(6).mapNotNull { s ->
            val (kurs, kontrakte) = runCatching { state.market.optionskette(s) }.getOrNull() ?: return@mapNotNull null
            Radar.optionen(s, k.heute, Optionsanalyse.auswerten(kurs, kontrakte).auffaellig)
        }
}
