package de.troetenrepublik.app.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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

    private suspend fun text(url: String, max: Int = 6_000_000): String? = withContext(Dispatchers.IO) {
        api.datei(url, max)?.toString(Charsets.UTF_8)
    }

    // Eine Seite der Liste: bei Polymarket nach Umsatz der letzten 24 Stunden (optional einer Kategorie), bei Kalshi
    // die Trends der Boerse (optional einer Kategorie). weiter ist der Wert aus der vorigen Seite.
    suspend fun liste(quelle: Prognose.Quelle, kategorie: String, weiter: String? = null, neu: Boolean = false): Prognose.Seitenliste? =
        gemerkt("liste|$quelle|$kategorie|$weiter", 120_000L, neu) {
            when (quelle) {
                Prognose.Quelle.POLYMARKET -> {
                    val versatz = weiter?.toIntOrNull() ?: 0
                    text(Prognose.polyListe(kategorie, versatz))?.let { t ->
                        val liste = Prognose.polyListeLesen(t)
                        Prognose.Seitenliste(liste, if (liste.size >= 10) (versatz + 15).toString() else null)
                    }
                }
                Prognose.Quelle.KALSHI -> text(Prognose.kalshiListe(kategorie, weiter))?.let { Prognose.kalshiListeLesen(it) }
            }
        }

    suspend fun suche(quelle: Prognose.Quelle, begriff: String): List<Prognose.Ereignis>? {
        val q = begriff.trim()
        if (q.length < 2) return emptyList()
        return gemerkt("suche|$quelle|${q.lowercase()}", 120_000L, false) {
            when (quelle) {
                Prognose.Quelle.POLYMARKET -> text(Prognose.polySuche(q))?.let { Prognose.polyListeLesen(it) }
                Prognose.Quelle.KALSHI -> text(Prognose.kalshiSuche(q))?.let { Prognose.kalshiListeLesen(it).ereignisse }
            }
        }
    }

    // Ein Ereignis mit allen Maerkten, auch geschlossenen; bei Kalshi mit den Regeln.
    suspend fun ereignis(quelle: Prognose.Quelle, id: String, neu: Boolean = false): Prognose.Ereignis? =
        gemerkt("ereignis|$quelle|$id", 60_000L, neu) {
            when (quelle) {
                Prognose.Quelle.POLYMARKET -> text(Prognose.polyEreignis(id))?.let { Prognose.polyEreignisText(it) }
                Prognose.Quelle.KALSHI -> text(Prognose.kalshiEreignis(id))?.let { Prognose.kalshiEreignisText(it) }
            }
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
            val top = alle.filter { it.zeit >= grenze }.sortedByDescending { it.betrag }.take(100)
            val offen = top.map { it.ereignis }.distinct().filter { it.isNotBlank() && !themen.containsKey(it) }
            offen.chunked(8).forEach { gruppe ->
                coroutineScope {
                    gruppe.map { s ->
                        async { text(Prognose.polyEreignis(s), 3_000_000)?.let { themen[s] = Prognose.thema(Prognose.polySchlagworte(it)) } }
                    }.awaitAll()
                }
            }
            top.map { it.copy(thema = themen[it.ereignis].orEmpty()) }
        }

    // Eigene Positionen bei Polymarket zur oeffentlichen Adresse, eine Minute im Speicher; null ohne Antwort.
    suspend fun echtePositionen(adresse: String, neu: Boolean = false): List<Prognose.EchtePosition>? {
        val url = Prognose.positionenAdresse(adresse) ?: return null
        return gemerkt("echt|${adresse.lowercase()}", 60_000L, neu) { text(url, 3_000_000)?.let { Prognose.positionenLesen(it) } }
    }

    // Die groessten Wetten eines Polymarket-Ereignisses ab 1.000 Dollar.
    suspend fun grosseWetten(nummer: String): List<Prognose.GrosseWette>? = gemerkt("gross|$nummer", 120_000L, false) {
        text(Prognose.grosseWettenAdresse(1_000, nummer))?.let { t -> Prognose.grosseWettenLesen(t).sortedByDescending { it.betrag }.take(10) }
    }

    // Ein einzelner Markt mit aktuellem Kurs und Stand der Aufloesung (ohne Zwischenspeicher).
    suspend fun markt(quelle: Prognose.Quelle, id: String): Prognose.Markt? = when (quelle) {
        Prognose.Quelle.POLYMARKET -> text(Prognose.polyMarkt(id), 2_000_000)?.let { Prognose.polyMarktText(it) }
        Prognose.Quelle.KALSHI -> text(Prognose.kalshiMarkt(id), 2_000_000)?.let { Prognose.kalshiMarktText(it) }
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
                    val fid = if (spanne <= 2 * 86_400L) 10 else if (spanne <= 14 * 86_400L) 60 else 1440
                    text(Prognose.polyVerlaufZwischen(w.token, von, bis, fid), 3_000_000)?.let { Prognose.polyVerlaufLesen(it) }
                }
                Prognose.Quelle.KALSHI -> if (w.serie.isBlank()) null else {
                    val min = if (spanne <= 14 * 86_400L) 60 else 1440
                    text(Prognose.kalshiVerlauf(w.serie, w.markt, von, bis, min), 3_000_000)?.let { Prognose.kalshiVerlaufLesen(it) }
                        ?.let { l -> if (w.seite == 0) l else l.map { it.first to 1.0 - it.second } }
                }
                null -> null
            }
        } ?: emptyList()
    }

    // Verlauf der Wahrscheinlichkeit einer Seite (0 = erste Seite) im bereich; Zeit in Millisekunden.
    suspend fun verlauf(markt: Prognose.Markt, seite: Int, bereich: Prognose.Bereich): List<Pair<Long, Double>> =
        gemerkt("verlauf|${markt.quelle}|${markt.id}|$seite|$bereich", 600_000L, false) {
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
            }
        } ?: emptyList()
}
