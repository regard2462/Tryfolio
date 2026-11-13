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
import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.EnergieWelt
import de.troetenrepublik.app.i18n.EnergieWeltTexte
import de.troetenrepublik.app.i18n.I18n

// Oel und Erdgas je Land auf den Seiten von Oel und Gas: Kernsatz zum groessten Foerderer, Balken der zehn groessten
// Foerderer und Verbraucher, Ausfuhr und Einfuhr, Gasfackeln als Schaetzung aus Satellitenbildern. Daten aus dem Datenbuendel
// (engine/EnergieWelt.kt), ohne Abruf. thema: "OEL" oder "GAS". Jede Zeile ist ein Eintrag der Seitenliste, Listen aller
// Laender stehen eingeklappt.

private const val SPITZE = 10

@Composable
internal fun EnergieWeltLader(merker: SeitenMerker, thema: String) {
    val d = EnergieWelt.datei
    val offen by merker.zustand("energiewelt/$thema/offen") { emptySet<String>() }
    val sprache = I18n.lang
    val farbe = MaterialTheme.colorScheme.primary
    val farbeNeben = MaterialTheme.colorScheme.tertiary
    val modell = remember(d, offen, sprache, thema, farbe, farbeNeben) { if (d == null) null else energieModell(merker, d, thema, offen, farbe, farbeNeben) }
    if (modell != null) {
        QuellenMelden(EIA_QUELLE)
        if (EnergieWelt.hat(d?.fackel)) QuellenMelden(QuelleLink(EnergieWeltTexte.text(sprache == Lang.DE, "quelle.eog"), EnergieWelt.EOG_SEITE))
    }
    ModellMelden(merker, "energiewelt-$thema", modell)
}

internal fun LazyListScope.energieWeltAbschnitt(merker: SeitenMerker, thema: String) {
    listenAbschnitt(merker, "energiewelt-$thema")
}

// Zahl je Thema: Oel in Millionen Barrel am Tag (Quelle in Tausend), Gas in Milliarden Kubikmetern
private fun zahl(thema: String, v: Double): String =
    if (thema == "OEL") Fmt.num(v / 1000, 2) else Fmt.num(v, if (v >= 100) 0 else 1)

private fun energieModell(merker: SeitenMerker, d: EnergieWelt.Datei, thema: String, offen: Set<String>, farbe: Color, farbeNeben: Color): ListenModell? {
    val deutsch = I18n.lang == Lang.DE
    fun t(k: String, vararg w: String) = EnergieWeltTexte.text(deutsch, k, w.toList())
    val zustand = merker.zustand("energiewelt/$thema/offen") { emptySet<String>() }
    // Der Lader steht oben in der Liste und ist beim Antippen weiter unten oft schon verworfen: das Modell darum hier neu
    // setzen, sonst bliebe die Zeile zu, bis der Kopf der Karte wieder ins Bild kommt
    fun umschalten(k: String) {
        zustand.value = if (k in zustand.value) zustand.value - k else zustand.value + k
        merker.zustand<ListenModell?>("modell/energiewelt-$thema") { null }.value = energieModell(merker, d, thema, zustand.value, farbe, farbeNeben)
    }
    val zeilen = ArrayList<ZeilenDaten>()
    val block = if (thema == "OEL") d.oel else d.gas

    // Rangfolge als Balken: die ersten zehn stehen da, alle weiteren hinter einer eingeklappten Zeile
    fun liste(kennung: String, kopf: String, rang: EnergieWelt.Rang, einheit: (Double) -> String, f: Color, alleTitel: String, unter: String? = null) {
        zeilen.add(ZeilenDaten("k-$kennung") { Zwischenkopf(kopf) })
        unter?.let { u -> zeilen.add(ZeilenDaten("u-$kennung") { PunktText(u, Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp)) }) }
        val groesst = rang.eintraege.firstOrNull()?.wert ?: return
        fun zeile(e: EnergieWelt.Eintrag) = ZeilenDaten("$kennung-${e.land.c}") {
            androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 20.dp)) {
                LageBalkenZeile(
                    e.land.name(deutsch), einheit(e.wert) + "  " + Fmt.pct(e.anteil, if (e.anteil < 1) 1 else 0), e.wert / groesst, f, null,
                )
            }
        }
        rang.eintraege.take(SPITZE).forEach { zeilen.add(zeile(it)) }
        val rest = rang.eintraege.drop(SPITZE)
        if (rest.isEmpty()) return
        val auf = kennung in offen
        zeilen.add(ZeilenDaten("alle-$kennung") { AufklappZeile(alleTitel, t("laender", rest.size.toString()), auf) { umschalten(kennung) } })
        if (auf) rest.forEach { zeilen.add(zeile(it)) }
    }

    if (EnergieWelt.hat(block)) {
        val b = block!!
        val f = EnergieWelt.rang(b)
        if (f != null) {
            val erster = f.eintraege.first()
            zeilen.add(ZeilenDaten("kern") {
                Text(
                    t("kern.$thema", erster.land.name(deutsch), zahl(thema, erster.wert), Fmt.pct(erster.anteil, 0), Fmt.pct(f.spitze(SPITZE), 0)),
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp), style = MaterialTheme.typography.bodyLarge,
                )
            })
            liste("f", t("f.$thema", f.jahr.toString()), f, { zahl(thema, it) }, farbe, t("alleF"))
        }
        val vr = EnergieWelt.rang(b, verbrauch = true)
        vr?.let { v ->
            liste("v", t("v.$thema", v.jahr.toString()), v, { zahl(thema, it) }, farbe, t("alleV"))
        }
        // Ausfuhr und Einfuhr im letzten Jahr mit voller Foerderung und vollem Verbrauch, eingeklappt
        if (f != null) {
            val nj = minOf(f.jahr, vr?.jahr ?: f.jahr)
            val netto = EnergieWelt.netto(b, nj)
            val aus = EnergieWelt.ausfuhr(netto, 5)
            val ein = EnergieWelt.einfuhr(netto, 5)
            if (aus.isNotEmpty() || ein.isNotEmpty()) {
                val auf = "n" in offen
                zeilen.add(ZeilenDaten("kopf-n") { AufklappZeile(t("netto.titel", nj.toString()), t("netto.unter"), auf) { umschalten("n") } })
                if (auf) {
                    val groesst = (aus + ein).maxOf { kotlin.math.abs(it.saldo) }
                    fun gruppe(kennung: String, kopf: String, l: List<EnergieWelt.Netto>, fb: Color) {
                        if (l.isEmpty()) return
                        zeilen.add(ZeilenDaten("n-kopf-$kennung") { Zwischenkopf(kopf) })
                        l.forEach { n ->
                            zeilen.add(ZeilenDaten("n-$kennung-${n.land.c}") {
                                androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 20.dp)) {
                                    LageBalkenZeile(n.land.name(deutsch), zahl(thema, kotlin.math.abs(n.saldo)), kotlin.math.abs(n.saldo) / groesst, fb, null)
                                }
                            })
                        }
                    }
                    gruppe("a", t("netto.aus"), aus, farbe)
                    gruppe("e", t("netto.ein"), ein, farbeNeben)
                }
            }
        }
    }

    // Gasfackeln: Schaetzung aus Satellitenbildern, als solche gekennzeichnet
    if (EnergieWelt.hat(d.fackel)) {
        EnergieWelt.rang(d.fackel!!, mindestens = 5)?.let { x ->
            liste("x", t("fackel.titel", x.jahr.toString()), x, { Fmt.num(it, if (it >= 10) 1 else 2) }, farbeNeben, t("alleX"), unter = t("fackel.unter"))
        }
    }
    if (zeilen.isEmpty()) return null
    val stand = Alter.tag(d.stand)?.let { Fmt.date(it * 86_400_000L + 43_200_000L) } ?: d.stand
    return ListenModell(titel = t("titel.$thema"), gross = true, kern = null, zeilen = zeilen, immerOffen = true, info = t("info.$thema", stand),
        // Die Laenderlisten klappen selbst ein; eine Kappung der ganzen Karte wuerde die Fackeln mitten in der Liste abschneiden
        kappe = Int.MAX_VALUE)
}
