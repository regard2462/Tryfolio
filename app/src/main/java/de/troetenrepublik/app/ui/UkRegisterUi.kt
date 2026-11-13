package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.UkRegister
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.UkRegisterTexte
import de.troetenrepublik.app.market.Politik

// Register der finanziellen Interessen des britischen Unterhauses (0.78): gemeldete Beteiligungen unter Copytrading,
// Politik, Weitere mit Suche nach Person oder Firma, und auf der Seite einer Aktie die Abgeordneten, die eine
// Beteiligung an genau dieser Firma gemeldet haben. Das Register nennt Bestaende, keine Geschaefte.

internal fun ukText(key: String, vararg w: String): String = UkRegisterTexte.text(I18n.lang == Lang.DE, key, w.toList())

internal val UK_REGISTER_QUELLE = QuelleLink(UkRegister.NENNUNG, UkRegister.LIZENZ)

private fun ukDatum(iso: String): String? = iso.takeIf { it.isNotEmpty() }?.let { Politik.tag(it) }?.takeIf { it > 0 }?.let { Fmt.date(it) }

// Meldeschwelle des Eintrags in Worten, null ohne Angabe.
private fun ukSchwelle(b: UkRegister.Beteiligung): String? =
    b.schwelleProzent?.let { ukText("schwelle.prozent", Fmt.pct(it.toDouble(), 0)) }
        ?: b.schwellePfund?.let { ukText("schwelle.wert", Fmt.mitSymbol(Fmt.num(it.toDouble(), 0), "£")) }

// Eine Beteiligung: oben die Firma, darunter Person und Partei (auf der Aktienseite Person, darunter Partei), Schwelle und Beginn;
// rechts das Meldedatum. Ein Tipp oeffnet die Seite des Parlaments mit den Meldungen der Person.
@Composable
private fun UkZeile(b: UkRegister.Beteiligung, mitFirma: Boolean) {
    val links = appLinks()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .then(if (b.personId > 0) Modifier.clickable(role = Role.Button) { runCatching { links.openUri(UkRegister.personSeite(b.personId)) } } else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (mitFirma) b.firma else b.person, style = MaterialTheme.typography.titleSmall)
            val zweite = if (mitFirma) listOf(b.person, b.partei) else listOf(b.partei)
            PunktText(zweite.filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            val dritte = listOfNotNull(
                ukSchwelle(b),
                ukDatum(b.ende)?.let { ukText("beendet", it) } ?: ukDatum(b.seit)?.let { ukText("seit", it) },
            )
            if (dritte.isNotEmpty()) PunktText(dritte.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = grau)
        }
        ukDatum(b.gemeldet)?.let { d ->
            Spacer(Modifier.width(12.dp))
            Text(d, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

// ------------------------------------------------------------ Copytrading, Politik, Weitere

// Zustand des Abschnitts in der Liste „Weitere“.
internal class UkRegisterZustand(
    val liste: MutableState<List<UkRegister.Beteiligung>?>, val geladen: MutableState<Boolean>,
    val offen: MutableState<Boolean>, val suche: MutableState<String>, val mehr: MutableState<Boolean>,
)

@Composable
internal fun rememberUkRegister(state: AppState): UkRegisterZustand {
    val z = UkRegisterZustand(
        remember { mutableStateOf<List<UkRegister.Beteiligung>?>(null) }, remember { mutableStateOf(false) },
        rememberSaveable { mutableStateOf(false) }, rememberSaveable { mutableStateOf("") }, rememberSaveable { mutableStateOf(false) },
    )
    LaunchedEffect(Unit) {
        z.liste.value = state.ukRegister.beteiligungen()
        z.geladen.value = true
    }
    return z
}

private const val UK_ZUERST = 20

// Eintraege der Liste: Kopf mit Kernzeile (Zahl der bestehenden Beteiligungen und der Abgeordneten, letzte Meldung),
// aufgeklappt Suchfeld und je Beteiligung ein Eintrag. Ohne Daten nach dem Laden steht der Hinweis der App.
internal fun LazyListScope.ukRegisterAbschnitt(z: UkRegisterZustand) {
    val alle = z.liste.value
    val offen = z.offen.value
    item(key = "uk-kopf", contentType = "kern-kopf") {
        var info by remember { mutableStateOf(false) }
        val kern = remember(alle, I18n.lang) {
            alle?.takeIf { it.isNotEmpty() }?.let { l ->
                KernZeile(
                    listOf(
                        KernTeil(ukText("kern.anzahl", Fmt.num(l.count { it.aktuell }.toDouble(), 0))),
                        KernTeil(ukText("kern.personen", Fmt.num(UkRegister.personen(l).toDouble(), 0))),
                    ),
                    l.firstOrNull()?.gemeldet?.let(::ukDatum)?.let { KernTeil(ukText("kern.zuletzt", it)) },
                )
            }
        }
        KernKopf(ukText("titel"), kern, offen, onInfo = { info = true }) { z.offen.value = !z.offen.value }
        if (info) MarktInfoBlatt(ukText("titel"), ukText("info")) { info = false }
    }
    if (!offen) return
    when {
        !z.geladen.value -> item(key = "uk-laedt") { EmptyHint(S.loading) }
        alle.isNullOrEmpty() -> item(key = "uk-fehlt") { EmptyHint(S.copyUnavailable) }
        else -> {
            item(key = "uk-suche") {
                OutlinedTextField(
                    value = z.suche.value, onValueChange = { z.suche.value = it.take(40) }, singleLine = true,
                    placeholder = { Text(ukText("suche")) }, shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            val treffer = UkRegister.suche(alle, z.suche.value)
            val gezeigt = if (z.mehr.value || z.suche.value.isNotBlank()) treffer else treffer.take(UK_ZUERST)
            if (treffer.isEmpty()) item(key = "uk-leer") { EmptyHint(ukText("leer")) }
            items(gezeigt, key = { "uk-${it.id}" }, contentType = { "uk-zeile" }) { UkZeile(it, mitFirma = true) }
            if (z.suche.value.isBlank() && treffer.size > UK_ZUERST) item(key = "uk-mehr") { ListenPfeil(z.mehr.value, label = if (z.mehr.value) null else S.bbAlle(treffer.size)) { z.mehr.value = !z.mehr.value } }
        }
    }
}

// ------------------------------------------------------------ Seite einer Aktie

// Abgeordnete mit gemeldeter Beteiligung an dieser Firma. firmenname: voller Name mit Rechtsform; ohne ihn oder ohne
// Treffer erscheint nichts.
@Composable
internal fun UkRegisterKarte(state: AppState, firmenname: String?, merker: SeitenMerker?) {
    val ziel = remember(firmenname) { firmenname?.takeIf { UkRegister.firmenSchluessel(it) != null } }
    if (ziel == null) { ModellAusgabe(merker, "ukreg", null); return }
    var liste by gemerkt<List<UkRegister.Beteiligung>?>(merker, "ukreg/daten") { null }
    var geladen by gemerkt(merker, "ukreg/geladen") { false }
    LaunchedEffect(ziel) {
        if (geladen) return@LaunchedEffect
        liste = state.ukRegister.beteiligungen()
        geladen = true
    }
    val treffer = remember(liste, ziel) { liste?.let { UkRegister.zuFirma(it, ziel) }.orEmpty() }
    if (treffer.isNotEmpty()) QuellenMelden(UK_REGISTER_QUELLE)
    val modell = remember(treffer, I18n.lang) {
        if (treffer.isEmpty()) null else {
            val personen = UkRegister.personen(treffer)
            val kern = KernZeile(listOf(KernTeil(if (personen == 1) ukText("aktie.kern.1") else ukText("aktie.kern.n", Fmt.num(personen.toDouble(), 0)))))
            ListenModell(
                ukText("aktie.titel"), true, kern, treffer.map { b -> ZeilenDaten("b${b.id}") { UkZeile(b, mitFirma = false) } },
                info = ukText("info.aktie"),
            )
        }
    }
    ModellAusgabe(merker, "ukreg", modell)
}

internal fun LazyListScope.ukRegisterAktieAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "ukreg")
}
