package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.GoldReserven
import de.troetenrepublik.app.i18n.GoldReservenTexte
import de.troetenrepublik.app.i18n.I18n

// Goldreserven der Zentralbanken je Land auf der Seite von Gold: Kernsatz zum groessten Halter, Balken der zehn groessten
// Halter, Kaeufe und Verkaeufe der letzten zwoelf Monate. Daten aus dem Datenbuendel (engine/GoldReserven.kt), ohne Abruf.
// Jede Zeile ist ein Eintrag der Seitenliste, die Liste aller Laender und die Kaeufe und Verkaeufe stehen eingeklappt.

private const val SPITZE = 10
private const val KAEUFER = 5

private val IWF_QUELLE = QuelleLink("International Monetary Fund, IRFCL", GoldReserven.IWF_SEITE)

@Composable
internal fun GoldReservenLader(merker: SeitenMerker) {
    val d = GoldReserven.datei
    val offen by merker.zustand("goldreserven/offen") { emptySet<String>() }
    val sprache = I18n.lang
    val farbe = MaterialTheme.colorScheme.primary
    val farbeNeben = MaterialTheme.colorScheme.tertiary
    val modell = remember(d, offen, sprache, farbe, farbeNeben) { if (d == null) null else goldModell(merker, d, offen, farbe, farbeNeben) }
    if (modell != null) QuellenMelden(IWF_QUELLE)
    ModellMelden(merker, "goldreserven", modell)
}

internal fun LazyListScope.goldReservenAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "goldreserven")
}

// Tonnen mit Einheit: ab 100 ohne Nachkommastelle, darunter eine, unter einer Tonne zwei
private fun tonnen(t: Double): String = Fmt.num(t, if (t >= 100) 0 else if (t >= 1) 1 else 2)

// Sehr kleine Bestaende als "< 0,01 %" statt "0,00 %"
private fun anteil(a: Double): String = if (a < 0.005) "< " + Fmt.pct(0.01, 2) else Fmt.pct(a, if (a < 0.1) 2 else if (a < 1) 1 else 0)

private fun goldModell(merker: SeitenMerker, d: GoldReserven.Datei, offen: Set<String>, farbe: Color, farbeNeben: Color): ListenModell? {
    val deutsch = I18n.lang == Lang.DE
    fun t(k: String, vararg w: String) = GoldReservenTexte.text(deutsch, k, w.toList())
    val zustand = merker.zustand("goldreserven/offen") { emptySet<String>() }
    // Der Lader steht oben in der Liste und ist beim Antippen weiter unten oft schon verworfen: das Modell darum hier neu
    // setzen, sonst bliebe die Zeile zu, bis der Kopf der Karte wieder ins Bild kommt
    fun umschalten(k: String) {
        zustand.value = if (k in zustand.value) zustand.value - k else zustand.value + k
        merker.zustand<ListenModell?>("modell/goldreserven") { null }.value = goldModell(merker, d, zustand.value, farbe, farbeNeben)
    }
    val rang = GoldReserven.rang(d) ?: return null
    val zeilen = ArrayList<ZeilenDaten>()

    val erster = rang.eintraege.first()
    zeilen.add(ZeilenDaten("kern") {
        Text(
            t("kern", erster.land.name(deutsch), tonnen(erster.tonnen), anteil(erster.anteil), tonnen(rang.spitzeTonnen(SPITZE)), anteil(rang.spitze(SPITZE))),
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp), style = MaterialTheme.typography.bodyLarge,
        )
    })

    // Rangfolge als Balken: die ersten zehn stehen da, alle weiteren hinter einer eingeklappten Zeile
    zeilen.add(ZeilenDaten("k-r") { Zwischenkopf(t("rang")) })
    fun zeile(e: GoldReserven.Eintrag) = ZeilenDaten("r-${e.land.c}") {
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 20.dp)) {
            LageBalkenZeile(e.land.name(deutsch), tonnen(e.tonnen) + " t  " + anteil(e.anteil), e.tonnen / erster.tonnen, farbe, null)
        }
    }
    rang.eintraege.take(SPITZE).forEach { zeilen.add(zeile(it)) }
    val rest = rang.eintraege.drop(SPITZE)
    if (rest.isNotEmpty()) {
        val auf = "r" in offen
        zeilen.add(ZeilenDaten("alle-r") { AufklappZeile(t("alle", rang.eintraege.size.toString()), t("weitere", rest.size.toString()), auf) { umschalten("r") } })
        if (auf) rest.forEach { zeilen.add(zeile(it)) }
    }

    // Kaeufe und Verkaeufe gegenueber zwoelf Monaten davor, eingeklappt
    val aenderungen = GoldReserven.aenderungen(d)
    val kauf = GoldReserven.kaeufer(aenderungen, KAEUFER)
    val verkauf = GoldReserven.verkaeufer(aenderungen, KAEUFER)
    if (kauf.isNotEmpty() || verkauf.isNotEmpty()) {
        val auf = "n" in offen
        zeilen.add(ZeilenDaten("kopf-n") { AufklappZeile(t("netto.titel"), t("netto.unter"), auf) { umschalten("n") } })
        if (auf) {
            val groesst = (kauf + verkauf).maxOf { kotlin.math.abs(it.tonnen) }
            fun gruppe(kennung: String, kopf: String, l: List<GoldReserven.Aenderung>, fb: Color) {
                if (l.isEmpty()) return
                zeilen.add(ZeilenDaten("n-kopf-$kennung") { Zwischenkopf(kopf) })
                l.forEach { n ->
                    zeilen.add(ZeilenDaten("n-$kennung-${n.land.c}") {
                        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 20.dp)) {
                            LageBalkenZeile(n.land.name(deutsch), tonnen(kotlin.math.abs(n.tonnen)) + " t", kotlin.math.abs(n.tonnen) / groesst, fb, null)
                        }
                    })
                }
            }
            gruppe("k", t("netto.kauf"), kauf, farbe)
            gruppe("v", t("netto.verkauf"), verkauf, farbeNeben)
        }
    }

    // Stand: der haeufigste letzte Monat, nicht je Zeile
    val monat = GoldReserven.haeufigsterMonat(d)?.let { m -> runCatching { Fmt.monthYear(java.time.YearMonth.parse(m)) }.getOrNull() } ?: d.stand
    return ListenModell(titel = t("titel"), gross = true, kern = null, zeilen = zeilen, immerOffen = true,
        info = t("info", monat, d.laender.size.toString()), kappe = Int.MAX_VALUE)
}
