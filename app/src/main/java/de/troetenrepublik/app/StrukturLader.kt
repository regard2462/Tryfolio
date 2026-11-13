package de.troetenrepublik.app

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Struktur
import de.troetenrepublik.app.engine.StrukturRegeln
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.market.FirmenSpeicher
import de.troetenrepublik.app.market.Investegate
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.StrukturAbruf
import de.troetenrepublik.app.market.StrukturNetz
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

// Eigentuemer und Rechte: Anbindung der Abrufe (SEC, Wikidata), des gepflegten Buendels, der Konzernmutter (GLEIF), der
// britischen Stimmrechtsmeldungen (Investegate) an Netz, Einstellungen und Speicher der App.

private class StrukturNetzAbruf(private val api: MarketApi, private val kennung: () -> String?) : StrukturNetz {
    override suspend fun sec(url: String): String? {
        val k = kennung() ?: return null
        return runCatching { api.anfrage(url, mapOf("User-Agent" to k, "Accept" to "application/json, application/xml, text/xml, */*")).takeIf { it.ok }?.text }.getOrNull()
    }

    override suspend fun secBis(url: String, max: Long, genug: (String) -> Boolean): String? {
        val k = kennung() ?: return null
        return runCatching { api.strom(url, mapOf("User-Agent" to k, "Accept" to "text/html, */*")) { lesenBis(it, max, genug) } }.getOrNull()
    }

    override suspend fun wiki(url: String): String? =
        runCatching { api.anfrage(url, mapOf("Accept" to "application/json")).takeIf { it.ok }?.text }.getOrNull()

    // Liest, bis genug gilt (geprueft in Schritten) oder max entpackte Bytes erreicht sind; der Rest wird nicht abgenommen
    private fun lesenBis(ein: InputStream, max: Long, genug: (String) -> Boolean): String {
        val aus = ByteArrayOutputStream()
        val puffer = ByteArray(16_384)
        var naechste = SCHRITT
        while (aus.size() < max) {
            val n = ein.read(puffer, 0, minOf(puffer.size.toLong(), max - aus.size()).toInt())
            if (n < 0) break
            aus.write(puffer, 0, n)
            if (aus.size() >= naechste) {
                naechste = aus.size() + SCHRITT
                if (genug(aus.toByteArray().decodeToString())) break
            }
        }
        return aus.toByteArray().decodeToString()
    }

    private companion object {
        const val SCHRITT = 192 * 1024
    }
}

private class StrukturDatei(private val ordner: File) : FirmenSpeicher {
    override fun lesen(name: String): String? = runCatching { SichereDatei(File(ordner, name)).lesen().toString(Charsets.UTF_8) }.getOrNull()

    override fun schreiben(name: String, text: String) {
        SichereDatei(File(ordner, name)).schreiben(text.toByteArray(Charsets.UTF_8))
    }
}

private object StrukturDienst {
    @Volatile private var abruf: StrukturAbruf? = null

    fun holen(state: AppState): StrukturAbruf = abruf ?: synchronized(this) {
        abruf ?: StrukturAbruf(
            StrukturNetzAbruf(MarketApi()) { SecInsider.kennung(state.settingsFlow.value.secKontakt) },
            StrukturDatei(state.store.ordner),
            { System.currentTimeMillis() },
        ).also { abruf = it }
    }

    // Gepflegte Faelle aus den Assets, einmal gelesen
    val buendel: Struktur.Buendel? by lazy { runCatching { Struktur.buendelLesen(Daten.text("struktur/sonderfaelle.json")) }.getOrNull() }
}

// Gepflegter Fall zum Wert, ohne Netz; null ohne Eintrag
internal fun gepflegteStruktur(symbol: String, isin: String): Struktur.Sonderfall? = StrukturRegeln.gepflegt(StrukturDienst.buendel, symbol, isin, "")

// Eigentuemer und Rechte eines Werts: gepflegter Fall, Abrufe (SEC, Wikidata), Konzernmutter, Institutionen und britische
// Stimmrechtsmeldungen. Jede Quelle fehlt still, wenn sie nichts liefert; null, wenn nichts zu zeigen ist.
internal suspend fun AppState.eigentuemerStruktur(symbol: String, name: String): Struktur.Ergebnis? = withContext(Dispatchers.IO) {
    val isin = isinFor(symbol)
    val mitSec = SecInsider.kennung(settingsFlow.value.secKontakt) != null
    val sprache = if (I18n.lang == Lang.DE) "de" else "en"
    val auto = runCatching { StrukturDienst.holen(this@eigentuemerStruktur).laden(symbol, name, isin, mitSec, sprache) }.getOrNull()
    val heute = java.time.LocalDate.now().toString()
    var e: Struktur.Ergebnis? = StrukturRegeln.zusammen(gepflegteStruktur(symbol, isin), auto, heute)

    val belege = ArrayList(e?.belege.orEmpty())
    val halter = ArrayList(e?.halter.orEmpty())
    fun beleg(n: String, url: String?): Int { belege += Struktur.Beleg(n, url, ""); return belege.size - 1 }
    val einzeln = (e?.gattungen?.count { it.art != Struktur.GArt.ADS } ?: 1) <= 1

    // Britische Firmen: Stimmrechtsmeldungen (TR-1) ueber Investegate, nur mit der Bestaetigung unter Zugaenge
    val kuerzel = Investegate.ticker(symbol)
    if (kuerzel != null && settingsFlow.value.okInvestegate && halter.none { it.art != Struktur.Art.STREUBESITZ }) {
        val liste = runCatching { investegateStimmrechte(kuerzel) }.getOrNull().orEmpty().take(8)
        for (eintrag in liste) {
            val m = runCatching { investegateTr1(eintrag.link) }.getOrNull() ?: continue
            val p = m.nachher.gesamt ?: continue
            if (m.halter.isBlank() || halter.any { it.name.equals(m.halter, true) } || p <= 0.0) continue
            val b = beleg("Investegate, Stimmrechtsmeldung ${m.halter}", eintrag.link)
            halter += Struktur.Halter(m.halter, StrukturRegeln.halterArt(m.halter, true), stimmen = p, kapital = if (einzeln) p else null, beleg = b)
            if (halter.size >= 8) break
        }
    }
    // Konzernmutter laut GLEIF
    val geprueft = LinkedHashSet<String>(e?.geprueft.orEmpty())
    if (kuerzel != null && settingsFlow.value.okInvestegate) geprueft += "INVESTEGATE"
    var mutter = e?.mutter.orEmpty()
    if (mutter.isBlank() && isin.length == 12) {
        geprueft += "GLEIF"
        runCatching { konzernmutter(symbol) }.getOrNull()?.let { m ->
            mutter = m.name
            beleg("GLEIF (CC0)", "https://www.gleif.org/en/meta/lei-data-terms-of-use")
        }
    }
    if (e == null && halter.isEmpty() && mutter.isBlank() && geprueft.isEmpty()) return@withContext null
    val basis = e ?: Struktur.Ergebnis(firma = name)
    e = basis.copy(halter = halter, belege = belege, mutter = mutter, geprueft = geprueft.toList())
    e
}

// Halter einer Aktie fuer andere Teile der App (zum Beispiel das Netz der Verflechtungen): ein Aufruf, ein Zwischenspeicher,
// ein Satz Abrufe. Anteile sind fertig gerechnet (Prozent aller Aktien und aller Stimmen, soweit ableitbar), dazu Art, Gattung,
// ob der Halter ein Aktivist ist, und die Quelle mit Link und Stand des Dokuments.
class StrukturHalter(
    val name: String,
    val art: String,
    val kapital: Double?,
    val stimmen: Double?,
    // Prozent der Gattung, wenn so gemeldet (Schedule 13G und 13D nennen den Anteil an der Gattung)
    val gattung: String?,
    val klasse: Double?,
    val aktivist: Boolean,
    val quelle: Struktur.Beleg?,
)

internal suspend fun AppState.halterEinerAktie(symbol: String, name: String): List<StrukturHalter> {
    val e = eigentuemerStruktur(symbol, name) ?: return emptyList()
    return halterAusErgebnis(e)
}

// Reiner Teil von halterEinerAktie: Halter mit gerechneten Anteilen (ohne die Sammelzeile des Streubesitzes)
fun halterAusErgebnis(e: Struktur.Ergebnis): List<StrukturHalter> {
    val v = StrukturRegeln.verteilung(e.gattungen)
    return e.halter.filter { it.art != Struktur.Art.STREUBESITZ }.map { h ->
        val (k, s) = StrukturRegeln.halterAnteile(h, v)
        StrukturHalter(h.name, h.art, k, s, h.gattung, h.klasse, h.aktivist, e.belege.getOrNull(h.beleg))
    }
}
