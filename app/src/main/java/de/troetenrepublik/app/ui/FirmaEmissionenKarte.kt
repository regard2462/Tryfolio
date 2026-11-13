package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.market.FirmaEmissionen
import de.troetenrepublik.app.market.FirmaName
import de.troetenrepublik.app.i18n.FirmaFaktenTexte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// Tonnen in Kurzform: Millionen mit einer Stelle, Tausend, sonst Tonnen
private fun tonnen(t: Double): String = when {
    t >= 1e6 -> firmaText("t.mio", Fmt.num(t / 1e6, 1))
    t >= 1e4 -> firmaText("t.tsd", Fmt.num(t / 1e3, 0))
    t >= 1e3 -> firmaText("t.tsd", Fmt.num(t / 1e3, 1))
    else -> firmaText("t.t", Fmt.num(t, 0))
}

// Emissionen des letzten Jahres aus den mitgelieferten Daten; null ohne eindeutigen Treffer. Auch der Block „Unternehmen“ nutzt es,
// um schon zugeklappt zu wissen, ob es das Thema gibt.
internal suspend fun emissionenLesen(symbol: String, name: String, langerName: String?): FirmaEmissionen.Stand? = withContext(Dispatchers.Default) {
    FirmaEmissionen.auswerten(FirmaEmissionen.teile(symbol, FirmaName.namenDerSeite(symbol, name, langerName)))
}

// Baustein „Direkte Emissionen“ auf der Wertpapierseite einer Aktie: Kernzeile mit den Emissionen des letzten Jahres der
// Daten (EPA-Meldungen GHGRP, EU-Emissionshandel) und der Aenderung zum Vorjahr, aufgeklappt der Verlauf je Jahr und die
// eingeklappte Liste der groessten Standorte mit Land. Gemeldet sind nur meldepflichtige Anlagen, das Gebiet (USA, EU oder
// beide) steht als Zusatz unter der Kernzeile. Die Daten liegen mitgeliefert vor (firmen/emissionen.json und
// firmen/emissionen-eu.json), es gibt keinen Abruf. Ohne eindeutigen Treffer zeichnet der Baustein nichts.
@Composable
internal fun EmissionenKarte(symbol: String, name: String, langerName: String?, merker: SeitenMerker? = null) {
    var stand by gemerkt<FirmaEmissionen.Stand?>(merker, "fu/emi") { null }
    var gelesen by gemerkt(merker, "fu/emi/gelesen") { false }
    LaunchedEffect(symbol) {
        if (gelesen) return@LaunchedEffect
        stand = emissionenLesen(symbol, name, langerName)
        gelesen = true
    }
    val offen = firmaOffen(merker, "fu-emi")
    val standorteOffen = gemerkt(merker, "fu/emi/standorte") { false }
    val s = stand ?: return
    val aender = s.aenderung
    val teile = buildList {
        add(KernTeil(firmaText("emi.co2e", tonnen(s.tonnen))))
        if (aender != null) {
            val rund = Math.round(abs(aender))
            add(KernTeil(Fmt.pct(abs(aender), 0), if (rund == 0L) 0.0 else if (aender > 0) 1.0 else -1.0, umgekehrt = true))
        }
    }
    val gebiet = firmaText(if (s.gebiete.size > 1) "emi.g.useu" else if (s.gebiete.firstOrNull() == "EU") "emi.g.eu" else "emi.g.us")
    val unten = firmaText(if (aender != null) "emi.unten.vorjahr" else "emi.unten", gebiet)
    FirmaAbschnitt(
        firmaText("emi.titel", s.jahr.toString()), KernZeile(teile, KernTeil(unten)), firmaText("emi.info"), offen,
    ) {
        Zwischenkopf(firmaText("emi.verlauf"), oben = 4.dp)
        FirmaBalken(s.reihe.map { it.first.toString() to it.second }) { tonnen(it) }
        if (s.standorte.isNotEmpty()) {
            AufklappZeile(firmaText("emi.standorte"), firmaText("emi.anlagen", Fmt.num(s.anlagen.toDouble(), 0)), standorteOffen.value) { standorteOffen.value = !standorteOffen.value }
            if (standorteOffen.value) s.standorte.forEach { x ->
                val ort = listOf(x.ort, if (x.gebiet == "EU") FirmaFaktenTexte.land(x.staat) else x.staat).filter { it.isNotBlank() }.joinToString(", ")
                KeyValueRow(x.name, tonnen(x.tonnen), hint = if (x.anteil in 1..99) firmaText("emi.standort.unter", ort, x.anteil.toString()) else ort)
            }
        }
    }
}
