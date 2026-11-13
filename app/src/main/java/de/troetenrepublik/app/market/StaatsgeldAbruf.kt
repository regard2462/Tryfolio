package de.troetenrepublik.app.market

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

// Abrufe fuer das Staatsgeld einer Firma (Rechnung und Leser in Staatsgeld): USAspending, TED, Contracts Finder, dazu der
// mitgelieferte EU-Haushalt. Jede Quelle laeuft fuer sich; faellt eine aus, zaehlen die anderen. Ohne die Antwort einer
// Online-Quelle null (die Seite versucht es spaeter erneut), sonst ein Ergebnis, auch ohne Posten.
object StaatsgeldAbruf {

    // unvollstaendig: true, wenn mindestens eine Online-Quelle ohne Antwort blieb
    class Ergebnis(
        val posten: List<Staatsgeld.Posten>,
        // Summen der letzten zwoelf Monate in Dollar (USA)
        val usaAuftraggeber: List<Pair<String, Double>>,
        val usaProgramme: List<Pair<String, Double>>,
        // Programme des EU-Haushalts in Euro, Jahr der Datei
        val euProgramme: List<Pair<String, Double>>,
        val groesste: List<Behoerden.Auftrag>,
        val unvollstaendig: Boolean,
    )

    private val TED_SEITEN = 6

    suspend fun laden(api: MarketApi, symbol: String, namen: List<String>, heute: LocalDate): Ergebnis? = coroutineScope {
        val schluessel = FirmaName.schluessel(namen)
        if (schluessel.isEmpty()) return@coroutineScope null
        val suchworte = namen.map { Staatsgeld.suchwort(it) }.filter { it.length >= 3 }.distinct()
        if (suchworte.isEmpty()) return@coroutineScope null
        val von = LocalDate.of(heute.year - 5, 1, 1)
        var fehler = false
        var antworten = 0

        val usaD = async { usa(api, suchworte, schluessel, von, heute) }
        val tedD = async { ted(api, suchworte.first(), schluessel, von) }
        val ukD = async { uk(api, suchworte.first(), schluessel, von) }
        val euD = async { eu(symbol, namen) }

        val u = usaD.await(); val t = tedD.await(); val k = ukD.await(); val e = euD.await()
        for (r in listOf(u?.ok, t?.ok, k?.ok)) { if (r == null) fehler = true else antworten++ }
        val posten = (u?.posten.orEmpty() + t?.posten.orEmpty() + k?.posten.orEmpty() + e.first)
        if (antworten == 0) return@coroutineScope null
        Ergebnis(posten, u?.agenturen.orEmpty(), u?.programme.orEmpty(), e.second, u?.groesste.orEmpty(), fehler)
    }

    private class Teil(val ok: Boolean, val posten: List<Staatsgeld.Posten>, val agenturen: List<Pair<String, Double>> = emptyList(), val programme: List<Pair<String, Double>> = emptyList(), val groesste: List<Behoerden.Auftrag> = emptyList())

    // USA: Empfaenger der Muttergesellschaft suchen (jeder Name der Firma, bis einer passt), dann Verlauf, Behoerden,
    // Programme und die groessten Auftraege der letzten zwoelf Monate
    private suspend fun usa(api: MarketApi, suchworte: List<String>, schluessel: List<String>, von: LocalDate, heute: LocalDate): Teil? {
        var gewaehlt: Staatsgeld.Empfaenger? = null
        var gesehen = false
        for (w in suchworte.take(2)) {
            val a = api.anfrage("${Staatsgeld.USA}/recipient/", mapOf("Accept" to "application/json"), methode = "POST", json = Staatsgeld.usaEmpfaengerKoerper(w))
            if (!a.ok) continue
            gesehen = true
            gewaehlt = Staatsgeld.usaEmpfaengerWaehlen(Staatsgeld.usaEmpfaengerLesen(a.text.orEmpty()), schluessel)
            if (gewaehlt != null) break
        }
        if (!gesehen) return null
        val g = gewaehlt ?: return Teil(true, emptyList())
        val bis = heute.toString()
        val vor12 = heute.minusYears(1).toString()
        return coroutineScope {
            val kopf = mapOf("Accept" to "application/json")
            val verlauf = async { api.anfrage("${Staatsgeld.USA}/search/spending_over_time/", kopf, methode = "POST", json = Staatsgeld.usaVerlaufKoerper(g.id, von.toString(), bis)) }
            val agenturen = async { api.anfrage("${Staatsgeld.USA}/search/spending_by_category/awarding_agency/", kopf, methode = "POST", json = Staatsgeld.usaAuftraggeberKoerper(g.id, vor12, bis)) }
            val programme = async { api.anfrage("${Staatsgeld.USA}/search/spending_by_category/cfda/", kopf, methode = "POST", json = Staatsgeld.usaProgrammeKoerper(g.id, vor12, bis)) }
            val liste = async { api.anfrage("${Behoerden.USA}/search/spending_by_award/", kopf, methode = "POST", json = Behoerden.auftraegeListeKoerper(g.name, vor12, bis)) }
            val v = verlauf.await()
            if (!v.ok) return@coroutineScope null
            val stamm = Behoerden.stamm(g.name)
            val auftraege = liste.await().takeIf { it.ok }?.text?.let { Behoerden.auftraegeListeLesen(it) }.orEmpty()
                .filter { Behoerden.passtEmpfaenger(it.empfaenger, stamm) }.take(5)
            Teil(
                true, Staatsgeld.usaVerlaufLesen(v.text.orEmpty()),
                agenturen.await().takeIf { it.ok }?.text?.let { Staatsgeld.usaKategorieLesen(it) }.orEmpty(),
                programme.await().takeIf { it.ok }?.text?.let { Staatsgeld.usaKategorieLesen(it) }.orEmpty(),
                auftraege,
            )
        }
    }

    // TED: bis zu sechs Seiten (neueste zuerst). Ist die Liste abgeschnitten, zaehlen nur Jahre, die ganz darin liegen.
    private suspend fun ted(api: MarketApi, suchwort: String, schluessel: List<String>, von: LocalDate): Teil? {
        val alle = ArrayList<Staatsgeld.Zuschlag>()
        var gesamt = 0
        var ok = false
        for (seite in 1..TED_SEITEN) {
            val a = api.anfrage(Staatsgeld.TED, mapOf("Accept" to "application/json"), methode = "POST", json = Staatsgeld.tedKoerper(suchwort, von, seite))
            val s = a.takeIf { it.ok }?.text?.let { Staatsgeld.tedLesen(it) }
            if (s == null) { if (!ok) return null else break }
            ok = true
            gesamt = s.gesamt
            alle += s.zuschlaege
            if (s.zuschlaege.size < Staatsgeld.TED_SEITE || alle.size >= gesamt) break
        }
        val abgeschnitten = alle.size < gesamt
        val aeltestes = alle.minOfOrNull { it.datum.take(4).toIntOrNull() ?: Int.MAX_VALUE } ?: 0
        val ab = if (abgeschnitten) aeltestes + 1 else 0
        return Teil(true, Staatsgeld.tedPosten(alle, schluessel, ab))
    }

    private suspend fun uk(api: MarketApi, suchwort: String, schluessel: List<String>, von: LocalDate): Teil? {
        val a = api.anfrage(Staatsgeld.UK, mapOf("Accept" to "application/json"), methode = "POST", json = Staatsgeld.ukKoerper(suchwort, von), max = 12_000_000)
        if (!a.ok) return null
        return Teil(true, Staatsgeld.ukPosten(Staatsgeld.ukLesen(a.text.orEmpty()), schluessel))
    }

    // EU-Haushalt aus der mitgelieferten Datei: Posten und die Programme des letzten Jahres
    private suspend fun eu(symbol: String, namen: List<String>): Pair<List<Staatsgeld.Posten>, List<Pair<String, Double>>> = withContext(Dispatchers.Default) {
        val d = Staatsgeld.europa
        val f = Staatsgeld.euFuer(d, namen, symbol)
        if (d == null || f == null) emptyList<Staatsgeld.Posten>() to emptyList<Pair<String, Double>>()
        else Staatsgeld.euPosten(d, f) to f.programme
    }
}
