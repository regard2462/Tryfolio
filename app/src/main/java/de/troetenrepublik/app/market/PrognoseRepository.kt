package de.troetenrepublik.app.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

// Laedt Prognosemaerkte: Listen nach Kategorie, Suche, einzelne Ereignisse und Maerkte,
// Kursverlaeufe. Listen und Ereignisse werden zwei Minuten, Verlaeufe zehn Minuten zwischengespeichert.
class PrognoseRepository(private val api: MarketApi) {

    private val cache = ConcurrentHashMap<String, Pair<Long, Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> gemerkt(schluessel: String, ms: Long, neu: Boolean, laden: suspend () -> T?): T? {
        val jetzt = System.currentTimeMillis()
        if (!neu) cache[schluessel]?.takeIf { jetzt - it.first < ms }?.let { return it.second as T }
        val wert = laden() ?: return (cache[schluessel]?.second as? T)
        cache[schluessel] = jetzt to wert
        return wert
    }

    private suspend fun text(url: String, max: Int = 6_000_000, kopf: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        api.datei(url, max, kopf)?.toString(Charsets.UTF_8)
    }

    // Eigenes Token fuer Metaculus aus den Zugaengen; leer: die Quelle bleibt aus.
    var metaculusToken: () -> String = { "" }

    // Abruf bei Metaculus mit dem Token; null ohne gueltiges Token oder ohne Antwort.
    private suspend fun metaculus(url: String): String? = PrognoseWeitere.metaculusKopf(metaculusToken())?.let { text(url, 8_000_000, it) }

    // Teil des Speicherschluessels: nach einem neuen Token gilt Gemerktes nicht mehr.
    private fun konto(quelle: Prognose.Quelle): String = if (quelle == Prognose.Quelle.METACULUS) metaculusToken().hashCode().toString() else ""

    // Ordner fuer den letzten Stand der ersten Listenseiten (gesetzt beim Start). Beim Oeffnen zeigt die App sofort
    // diesen Stand und frischt im Hintergrund auf. Metaculus bleibt aussen vor (Abruf mit eigenem Token).
    @Volatile var ordner: java.io.File? = null

    private fun datei(quelle: Prognose.Quelle, kategorie: String): java.io.File? =
        ordner?.let { java.io.File(it, "liste-${quelle.name.lowercase()}-${kategorie.ifBlank { "alle" }.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json") }

    // Adresse und Hoechstgroesse einer Listenseite (ohne Metaculus).
    private fun listeAdresse(quelle: Prognose.Quelle, kategorie: String, weiter: String?): Pair<String, Int>? = when (quelle) {
        Prognose.Quelle.POLYMARKET -> Prognose.polyListe(kategorie, weiter?.toIntOrNull() ?: 0, polyGroesse(kategorie)) to 12_000_000
        Prognose.Quelle.KALSHI -> Prognose.kalshiListe(kategorie, weiter) to 6_000_000
        Prognose.Quelle.MANIFOLD -> PrognoseWeitere.manifoldListe(kategorie, weiter?.toIntOrNull() ?: 0, 15) to 6_000_000
        Prognose.Quelle.METACULUS -> null
    }

    // Ohne Kategorie liefert Polymarket sehr grosse Ereignisse (Sport mit hunderten Maerkten): kleinere Seiten
    private fun polyGroesse(kategorie: String) = if (kategorie.isBlank()) 5 else 15

    private fun listeLesen(quelle: Prognose.Quelle, kategorie: String, weiter: String?, t: String): Prognose.Seitenliste? = when (quelle) {
        Prognose.Quelle.POLYMARKET -> {
            val versatz = weiter?.toIntOrNull() ?: 0
            val n = polyGroesse(kategorie)
            val liste = Prognose.polyListeLesen(t)
            Prognose.Seitenliste(liste, if (liste.size >= n * 2 / 3) (versatz + n).toString() else null)
        }
        Prognose.Quelle.KALSHI -> Prognose.kalshiListeLesen(t)
        Prognose.Quelle.MANIFOLD -> {
            val versatz = weiter?.toIntOrNull() ?: 0
            val liste = PrognoseWeitere.manifoldListeLesen(t, kategorie)
            // Die Quelle blaettert hoechstens bis Versatz 1.000
            Prognose.Seitenliste(liste, if (liste.size >= 15 && versatz + 15 <= 1000) (versatz + 15).toString() else null)
        }
        Prognose.Quelle.METACULUS -> PrognoseWeitere.metaculusListeLesen(t, weiter?.toIntOrNull() ?: 0)
    }

    // Eine Seite der Liste: bei Polymarket nach Umsatz der letzten 24 Stunden (optional einer Kategorie), bei Kalshi
    // die Trends der Boerse (optional einer Kategorie). weiter ist der Wert aus der vorigen Seite.
    suspend fun liste(quelle: Prognose.Quelle, kategorie: String, weiter: String? = null, neu: Boolean = false): Prognose.Seitenliste? =
        gemerkt("liste|$quelle|$kategorie|$weiter|${konto(quelle)}", 120_000L, neu) {
            if (quelle == Prognose.Quelle.METACULUS) {
                return@gemerkt metaculus(PrognoseWeitere.metaculusListe(kategorie, weiter?.toIntOrNull() ?: 0))?.let { listeLesen(quelle, kategorie, weiter, it) }
            }
            val (adresse, max) = listeAdresse(quelle, kategorie, weiter) ?: return@gemerkt null
            val t = text(adresse, max) ?: return@gemerkt null
            val s = listeLesen(quelle, kategorie, weiter, t)
            // Nur die erste Seite und nur lesbare Antworten fuer den naechsten Start sichern
            if (weiter == null && s != null) withContext(Dispatchers.IO) {
                runCatching { datei(quelle, kategorie)?.let { d -> d.parentFile?.mkdirs(); d.writeText(t) } }
            }
            s
        }

    // Letzter Stand der ersten Listenseite ohne Netz: aus dem Arbeitsspeicher (beliebig alt), sonst vom Geraet.
    @Suppress("UNCHECKED_CAST")
    suspend fun sofort(quelle: Prognose.Quelle, kategorie: String): Prognose.Seitenliste? {
        (cache["liste|$quelle|$kategorie|null|${konto(quelle)}"]?.second as? Prognose.Seitenliste)?.let { return it }
        if (quelle == Prognose.Quelle.METACULUS) return null
        return withContext(Dispatchers.IO) {
            runCatching { datei(quelle, kategorie)?.takeIf { it.exists() }?.readText()?.let { listeLesen(quelle, kategorie, null, it) } }.getOrNull()
        }
    }

    // Letzter Stand von „Wichtigste“ ohne Netz (aus den gesicherten Kategorien).
    suspend fun sofortWichtigste(quelle: Prognose.Quelle): Prognose.Seitenliste? {
        if (quelle == Prognose.Quelle.METACULUS) return null
        val teile = de.troetenrepublik.app.engine.PrognoseAuswahl.sammelKategorien(Prognose.kategorien(quelle)).mapNotNull { sofort(quelle, it) }
        if (teile.isEmpty()) return null
        return Prognose.Seitenliste(de.troetenrepublik.app.engine.PrognoseAuswahl.ereignisse(teile.flatMap { it.ereignisse }, System.currentTimeMillis()), null)
    }

    // Auswahl „Wichtigste“: die ersten Seiten der Kategorien ausser Trends, Sport und Unterhaltung zusammen, gefiltert
    // und nach Umsatz geordnet (engine/PrognoseAuswahl). Jede Kategorie laeuft ueber liste() und damit ueber denselben
    // Zwischenspeicher wie ihr eigener Chip; hoechstens vier Abrufe gleichzeitig.
    suspend fun wichtigste(quelle: Prognose.Quelle, neu: Boolean = false): Prognose.Seitenliste? {
        // Metaculus: ein Abruf der gefragtesten Fragen statt einem je Kategorie
        if (quelle == Prognose.Quelle.METACULUS) {
            return gemerkt("wichtig|$quelle|${konto(quelle)}", 120_000L, neu) {
                metaculus(PrognoseWeitere.metaculusListe("", 0, 40))?.let { PrognoseWeitere.metaculusListeLesen(it, 0, 40) }
            }?.let { s -> Prognose.Seitenliste(de.troetenrepublik.app.engine.PrognoseAuswahl.ereignisse(s.ereignisse, System.currentTimeMillis()), null, s.verborgen) }
        }
        val kategorien = de.troetenrepublik.app.engine.PrognoseAuswahl.sammelKategorien(Prognose.kategorien(quelle))
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
        val teile = coroutineScope { kategorien.map { k -> async { gleichzeitig.withPermit { liste(quelle, k, null, neu) } } }.awaitAll() }
        if (teile.all { it == null }) return null
        return Prognose.Seitenliste(
            de.troetenrepublik.app.engine.PrognoseAuswahl.ereignisse(teile.filterNotNull().flatMap { it.ereignisse }, System.currentTimeMillis()), null,
        )
    }

    suspend fun suche(quelle: Prognose.Quelle, begriff: String): List<Prognose.Ereignis>? {
        val q = begriff.trim()
        if (q.length < 2) return emptyList()
        return gemerkt("suche|$quelle|${q.lowercase()}|${konto(quelle)}", 120_000L, false) {
            when (quelle) {
                Prognose.Quelle.POLYMARKET -> text(Prognose.polySuche(q))?.let { Prognose.polyListeLesen(it) }
                Prognose.Quelle.KALSHI -> text(Prognose.kalshiSuche(q))?.let { Prognose.kalshiListeLesen(it).ereignisse }
                Prognose.Quelle.MANIFOLD -> text(PrognoseWeitere.manifoldSuche(q))?.let { PrognoseWeitere.manifoldListeLesen(it) }
                Prognose.Quelle.METACULUS -> metaculus(PrognoseWeitere.metaculusListe("", 0, 20, q))?.let { PrognoseWeitere.metaculusListeLesen(it)?.ereignisse }
            }
        }
    }

    // Ein Ereignis mit allen Maerkten, auch geschlossenen; bei Kalshi mit den Regeln.
    suspend fun ereignis(quelle: Prognose.Quelle, id: String, neu: Boolean = false): Prognose.Ereignis? =
        gemerkt("ereignis|$quelle|$id|${konto(quelle)}", 60_000L, neu) {
            when (quelle) {
                Prognose.Quelle.POLYMARKET -> text(Prognose.polyEreignis(id))?.let { Prognose.polyEreignisText(it) }
                Prognose.Quelle.KALSHI -> text(Prognose.kalshiEreignis(id))?.let { Prognose.kalshiEreignisText(it) }
                Prognose.Quelle.MANIFOLD -> text(PrognoseWeitere.manifoldMarkt(id), 3_000_000)?.let { PrognoseWeitere.manifoldEreignisText(it) }
                Prognose.Quelle.METACULUS -> metaculusBeitrag(id, neu)?.let { PrognoseWeitere.metaculusEreignisText(it) }
            }
        }

    // Ein Beitrag bei Metaculus samt Verlauf der Gemeinschaftsprognose; Ereignis und Verlauf lesen denselben Text.
    private suspend fun metaculusBeitrag(id: String, neu: Boolean = false): String? =
        PrognoseWeitere.metaculusBeitrag(id)?.let { url -> gemerkt("mc-beitrag|$id|${konto(Prognose.Quelle.METACULUS)}", 60_000L, neu) { metaculus(url) } }

    // Handel eines Manifold-Marktes, neueste zuerst: so viele Seiten zu 1.000, bis der Zeitraum abgedeckt ist, hoechstens drei.
    private suspend fun manifoldVerlauf(id: String, sekunden: Long): List<Pair<Long, Double>>? {
        val ab = System.currentTimeMillis() - sekunden * 1000L
        val alle = ArrayList<Pair<Long, Double>>()
        var vor: String? = null
        var seiten = 0
        while (seiten < 3) {
            val t = text(PrognoseWeitere.manifoldHandel(id, vor), 4_000_000) ?: if (seiten == 0) return null else break
            val teil = PrognoseWeitere.manifoldVerlaufLesen(t)
            alle += teil
            seiten++
            vor = PrognoseWeitere.manifoldHandelWeiter(t)
            if (vor == null || teil.isEmpty() || teil.first().first < ab) break
        }
        return PrognoseWeitere.ausduennen(alle.filter { it.first >= ab }.distinctBy { it.first }.sortedBy { it.first })
    }

    // Themen der Ereignisse nach ihrem slug; aendern sich nicht und bleiben bis zum Ende der App.
    private val themen = ConcurrentHashMap<String, String>()

    // Groesste Wetten im Zeitraum ueber alle Maerkte, nach Einsatz, mit Thema; zwei Minuten im Speicher.
    // Die Abfrage liefert die neuesten zuerst, deshalb wird geblaettert, bis der Zeitraum abgedeckt ist.
    suspend fun grosseWetten(zeit: Prognose.WettZeit = Prognose.WettZeit.TAG, neu: Boolean = false): List<Prognose.GrosseWette>? =
        gemerkt("gross|$zeit", 120_000L, neu) {
            val grenze = System.currentTimeMillis() - zeit.stunden * 3_600_000L
            val alle = ArrayList<Prognose.GrosseWette>()
            var cursor: String? = null
            var seiten = 0
            while (seiten < 8) {
                val t = text(Prognose.grosseWettenAdresse(zeit.mindestens, cursor = cursor)) ?: if (seiten == 0) return@gemerkt null else break
                val seite = Prognose.grosseWettenLesen(t)
                alle += seite
                seiten++
                cursor = Prognose.grosseWettenWeiter(t)
                if (cursor == null || seite.isEmpty() || seite.minOf { it.zeit } < grenze) break
            }
            val top = alle.filter { it.zeit >= grenze }.sortedByDescending { it.betrag }.take(150)
            themenLaden(top)
            top.map { it.copy(thema = themen[it.ereignis].orEmpty()) }
        }

    // Themen fuer die Ereignisse der Wetten nachladen, je acht gleichzeitig.
    private suspend fun themenLaden(wetten: List<Prognose.GrosseWette>) {
        val offen = wetten.map { it.ereignis }.distinct().filter { it.isNotBlank() && !themen.containsKey(it) }
        offen.chunked(8).forEach { gruppe ->
            coroutineScope {
                gruppe.map { s ->
                    async { text(Prognose.polyEreignis(s), 3_000_000)?.let { themen[s] = Prognose.thema(Prognose.polySchlagworte(it)) } }
                }.awaitAll()
            }
        }
    }

    // Erfolgreiche Haendler aus den Ranglisten fuer Politik, Wirtschaft und Finanzen (gesamt und letzter Monat),
    // sechs Stunden im Speicher (seit 5.92).
    suspend fun profis(neu: Boolean = false): Map<String, de.troetenrepublik.app.engine.Wettprofis.Profi>? =
        gemerkt("profis", 6 * 3_600_000L, neu) {
            val plaetze = coroutineScope {
                de.troetenrepublik.app.engine.Wettprofis.KATEGORIEN.flatMap { k ->
                    de.troetenrepublik.app.engine.Wettprofis.ZEITEN.map { z ->
                        async { text(Prognose.ranglisteAdresse(k, z), 2_000_000)?.let { Prognose.ranglisteLesen(it, k, z) } }
                    }
                }.awaitAll()
            }
            if (plaetze.all { it == null }) null else de.troetenrepublik.app.engine.Wettprofis.profis(plaetze.filterNotNull().flatten())
        }

    // Auffaellige Wetten erfolgreicher Haendler im Zeitraum: ihre juengsten Kaeufe (je Haendler eine Abfrage, die 30
    // besten) und ihre Wetten unter den grossen Wetten; zehn Minuten im Speicher (seit 5.92).
    suspend fun auffaelligeWetten(zeit: Prognose.WettZeit, neu: Boolean = false): List<de.troetenrepublik.app.engine.Wettprofis.Auffaellig>? =
        gemerkt("auffaellig|$zeit", 600_000L, neu) {
            val profis = profis() ?: return@gemerkt null
            val ab = System.currentTimeMillis() - zeit.stunden * 3_600_000L
            val auswahl = profis.values.sortedWith(compareBy<de.troetenrepublik.app.engine.Wettprofis.Profi> { it.rang }.thenByDescending { it.gewinn }).take(30)
            val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
            val eigene = coroutineScope {
                auswahl.map { p ->
                    async {
                        gleichzeitig.withPermit {
                            Prognose.haendlerAdresse(p.wallet)?.let { url -> text(url, 3_000_000)?.let { Prognose.grosseWettenLesen(it) } }.orEmpty()
                        }
                    }
                }.awaitAll().flatten()
            }
            val gross = grosseWetten(zeit).orEmpty().filter { it.wallet.lowercase() in profis }
            val alle = (eigene + gross).filter { it.zeit >= ab && it.kauf }
                .distinctBy { Triple(it.wallet.lowercase(), it.zeit, it.betrag) }
            themenLaden(alle)
            de.troetenrepublik.app.engine.Wettprofis.auffaellig(alle.map { it.copy(thema = themen[it.ereignis].orEmpty()) }, profis, ab).take(60)
        }

    // Eigene Positionen bei Polymarket zur oeffentlichen Adresse, eine Minute im Speicher; null ohne Antwort.
    suspend fun echtePositionen(adresse: String, neu: Boolean = false): List<Prognose.EchtePosition>? {
        val offen = Prognose.positionenAdresse(adresse) ?: return null
        val einloesbar = Prognose.positionenAdresse(adresse, "REDEEMABLE") ?: return null
        return gemerkt("echt|${adresse.lowercase()}", 60_000L, neu) {
            text(offen, 3_000_000)?.let { Prognose.positionenLesen(it) }?.let { o -> o + text(einloesbar, 3_000_000)?.let { Prognose.positionenLesen(it) }.orEmpty() }
        }
    }

    // Die groessten Wetten eines Polymarket-Ereignisses ab 1.000 Dollar.
    suspend fun grosseWetten(nummer: String): List<Prognose.GrosseWette>? = gemerkt("gross|$nummer", 120_000L, false) {
        text(Prognose.grosseWettenAdresse(1_000, nummer))?.let { t -> Prognose.grosseWettenLesen(t).sortedByDescending { it.betrag }.take(10) }
    }

    // Ein einzelner Markt mit aktuellem Kurs und Stand der Aufloesung (ohne Zwischenspeicher).
    suspend fun markt(quelle: Prognose.Quelle, id: String): Prognose.Markt? = when (quelle) {
        Prognose.Quelle.POLYMARKET -> text(Prognose.polyMarkt(id), 2_000_000)?.let { Prognose.polyMarktText(it) }
        Prognose.Quelle.KALSHI -> text(Prognose.kalshiMarkt(id), 2_000_000)?.let { Prognose.kalshiMarktText(it) }
        Prognose.Quelle.MANIFOLD -> text(PrognoseWeitere.manifoldMarkt(id), 3_000_000)?.let { PrognoseWeitere.manifoldMarktText(it) }
        // Keine Uebungswetten auf Metaculus, also kein einzelner Kurs
        Prognose.Quelle.METACULUS -> null
    }

    // Kursverlauf der gewetteten Seite vom Kauf bis zum Abschluss oder bis jetzt, fuer „Meine Wetten“.
    // Bis zwei Tage in 10-Minuten-Schritten, bis zwei Wochen stuendlich, sonst taeglich; zehn Minuten im Speicher.
    suspend fun wettVerlauf(w: de.troetenrepublik.app.data.Wette, jetzt: Long = System.currentTimeMillis()): List<Pair<Long, Double>> {
        val bis = (if (w.offen || w.geschlossen == 0L) jetzt else w.geschlossen) / 1000
        val von = w.eroeffnet / 1000 - 3600
        val spanne = bis - von
        return gemerkt("wett|${w.id}|${bis / 600}", 600_000L, false) {
            when (runCatching { Prognose.Quelle.valueOf(w.quelle) }.getOrNull()) {
                Prognose.Quelle.POLYMARKET -> if (w.token.isBlank()) null else {
                    // Polymarket lehnt Abschnitte ueber etwa 15 Tage ab („interval is too long“): laengere Zeitraeume
                    // in Stuecken zu 14 Tagen holen und zusammensetzen
                    val fid = if (spanne <= 2 * 86_400L) 10 else if (spanne <= 60 * 86_400L) 60 else 1440
                    val stuecke = generateSequence(von) { it + POLY_ABSCHNITT }.takeWhile { it < bis }.toList()
                    val teile = kotlinx.coroutines.coroutineScope {
                        stuecke.map { a ->
                            async {
                                text(Prognose.polyVerlaufZwischen(w.token, a, minOf(a + POLY_ABSCHNITT, bis), fid), 3_000_000)
                                    ?.let { Prognose.polyVerlaufLesen(it) }
                            }
                        }.awaitAll()
                    }
                    // Fehlt ein Stueck, nichts merken (sonst stuende eine Luecke zehn Minuten im Zwischenspeicher)
                    if (teile.any { it == null }) null else teile.filterNotNull().flatten().distinctBy { it.first }.sortedBy { it.first }
                }
                Prognose.Quelle.KALSHI -> if (w.serie.isBlank()) null else {
                    val min = if (spanne <= 14 * 86_400L) 60 else 1440
                    text(Prognose.kalshiVerlauf(w.serie, w.markt, von, bis, min), 3_000_000)?.let { Prognose.kalshiVerlaufLesen(it) }
                        ?.let { l -> if (w.seite == 0) l else l.map { it.first to 1.0 - it.second } }
                }
                // Keine Uebungswetten auf diese Quellen
                Prognose.Quelle.MANIFOLD, Prognose.Quelle.METACULUS, null -> null
            }
        } ?: emptyList()
    }

    // Verlauf der Wahrscheinlichkeit einer Seite (0 = erste Seite) im bereich; Zeit in Millisekunden.
    suspend fun verlauf(markt: Prognose.Markt, seite: Int, bereich: Prognose.Bereich): List<Pair<Long, Double>> =
        gemerkt("verlauf|${markt.quelle}|${markt.id}|$seite|$bereich|${konto(markt.quelle)}", 600_000L, false) {
            when (markt.quelle) {
                Prognose.Quelle.POLYMARKET -> {
                    val token = markt.seiten.getOrNull(seite)?.token.orEmpty()
                    if (token.isBlank()) null else text(Prognose.polyVerlauf(token, bereich), 3_000_000)?.let { Prognose.polyVerlaufLesen(it) }
                }
                Prognose.Quelle.KALSHI -> {
                    val bis = System.currentTimeMillis() / 1000
                    val von = bis - bereich.sekunden
                    text(Prognose.kalshiVerlauf(markt.serie, markt.id, von, bis, bereich.kalshiMinuten), 3_000_000)
                        ?.let { Prognose.kalshiVerlaufLesen(it) }
                        ?.let { liste -> if (seite == 0) liste else liste.map { it.first to 1.0 - it.second } }
                }
                Prognose.Quelle.MANIFOLD -> manifoldVerlauf(markt.id, bereich.sekunden)
                // serie: Nummer des Beitrags, zu dem die Frage gehoert
                Prognose.Quelle.METACULUS -> metaculusBeitrag(markt.serie)?.let { t ->
                    val ab = System.currentTimeMillis() - bereich.sekunden * 1000L
                    PrognoseWeitere.ausduennen(PrognoseWeitere.metaculusVerlaufLesen(t, markt.id).filter { it.first >= ab })
                }
            }
        } ?: emptyList()
}

// Laengster Abschnitt je Abruf des Polymarket-Kursverlaufs in Sekunden.
private const val POLY_ABSCHNITT = 14 * 86_400L
