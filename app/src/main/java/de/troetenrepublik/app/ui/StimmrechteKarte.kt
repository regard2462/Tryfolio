package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Investegate
import de.troetenrepublik.app.market.Tr1
import kotlin.math.abs

// Stimmrechtsmeldungen britischer Firmen (TR-1 nach DTR 5, 5.95) ueber Investegate: die neuesten Meldungen mit
// Halter, Anteilen vorher und nachher und der daraus abgeleiteten Schwelle. Ohne die Bestaetigung als
// Privatanleger steht erst die Abfrage von Investegate.

// Liste der Stimmrechtsmeldungen, die neuesten drei, auf Knopf bis zehn. Je Meldung kommt das Formular ueber
// tr1; ohne lesbares Formular bleiben Titel, Datum und Quelle.
@Composable
internal fun StimmrechteListe(eintraege: List<Tr1.Eintrag>, tr1: suspend (Tr1.Eintrag) -> Tr1.Meldung?, onOeffnen: (Tr1.Eintrag) -> Unit) {
    var mehr by remember(eintraege) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        eintraege.take(if (mehr) 10 else 3).forEach { e ->
            key(e.link) {
                var meldung by remember(e.link) { mutableStateOf<Tr1.Meldung?>(null) }
                LaunchedEffect(e.link) {
                    meldung = try { tr1(e) } catch (ce: kotlinx.coroutines.CancellationException) { throw ce } catch (_: Exception) { null }
                }
                StimmrechteZeile(e, meldung, onOeffnen)
            }
        }
        if (eintraege.size > 3) ListenPfeil(mehr) { mehr = !mehr }
    }
}

// Eine Stimmrechtsmeldung: Halter, Anteile vorher und nachher, Aufteilung auf Aktien und Instrumente, abgeleitete
// Schwelle, Stichtag und Quelle; rechts die Veraenderung in Prozentpunkten.
@Composable
private fun StimmrechteZeile(eintrag: Tr1.Eintrag, meldung: Tr1.Meldung?, onOeffnen: (Tr1.Eintrag) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOeffnen(eintrag) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(meldung?.halter?.ifBlank { eintrag.titel } ?: eintrag.titel, style = MaterialTheme.typography.bodyMedium)
            val m = meldung
            if (m == null) {
                PunktText(listOf(Fmt.date(eintrag.zeit), eintrag.quelle).filter { it.isNotBlank() }.joinToString(" · "))
            } else {
                val nachher = m.nachher
                val anteile = ArrayList<String>()
                if (nachher.unter != null) anteile.add(S.srUnter(Fmt.pct(nachher.unter, 0)))
                else nachher.gesamt?.let { anteile.add(S.srNachher(Fmt.pct(it, 2))) }
                val vorher = m.vorher
                if (vorher?.gesamt != null) anteile.add(S.srVorher(Fmt.pct(vorher.gesamt, 2)))
                else if (vorher?.unter != null) anteile.add(S.srVorher(S.srUnter(Fmt.pct(vorher.unter, 0))))
                if (anteile.isNotEmpty()) PunktText(anteile.joinToString(" · "))
                val aufteilung = ArrayList<String>()
                nachher.aktien?.let { aufteilung.add(S.srAktien(Fmt.pct(it, 2))) }
                nachher.instrumente?.takeIf { it > 0 || nachher.aktien == null }?.let { aufteilung.add(S.srInstrumente(Fmt.pct(it, 2))) }
                if (aufteilung.isNotEmpty()) PunktText(aufteilung.joinToString(" · "))
                val angaben = ArrayList<String>()
                m.schwelle?.let { s ->
                    angaben.add(if (s.steigend) S.srSchwelleUeber(Fmt.pct(s.prozent.toDouble(), 0)) else S.srSchwelleUnter(Fmt.pct(s.prozent.toDouble(), 0)))
                }
                angaben.add(m.stichtag?.let { S.srStichtag(Fmt.date(it)) } ?: Fmt.date(eintrag.zeit))
                if (eintrag.quelle.isNotBlank()) angaben.add(eintrag.quelle)
                PunktText(angaben.joinToString(" · "))
            }
        }
        val nachherWert = meldung?.nachher?.gesamt
        val vorherWert = meldung?.vorher?.gesamt
        if (nachherWert != null && vorherWert != null) {
            val d = nachherWert - vorherWert
            if (abs(d) >= 0.005) {
                Spacer(Modifier.width(12.dp))
                ChangeValue(change = d, text = S.srPunkte(Fmt.num(abs(d), 2)), style = MaterialTheme.typography.labelLarge, colored = true, triangleWidth = 8.dp)
            }
        }
    }
}

// Karte der Stimmrechtsmeldungen einer britischen Firma mit eigenem eingeklapptem Kopf; erst aufgeklappt wird
// geladen oder, ohne Bestaetigung, nach der Privatanleger-Eigenschaft gefragt. Nach einem Nein bleibt der
// Hinweis auf die Firmenseite. Auf der Seite je Meldung ein Eintrag der LazyColumn, Zustand im Merker der Seite.
@Composable
internal fun StimmrechteGbKarte(state: AppState, kuerzel: String, onMeldung: (Investegate.Meldung) -> Unit, merker: SeitenMerker? = null) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val ok = settings.okInvestegate
    val uri = appLinks()
    val offenZustand = gemerkt(merker, "sr/offen") { false }
    val offen = offenZustand.value
    var abgelehnt by gemerkt(merker, "sr/abgelehnt") { false }
    var liste by gemerkt<List<Tr1.Eintrag>?>(merker, "sr/liste") { null }
    var geladen by gemerkt(merker, "sr/geladen") { false }
    var mehr by gemerkt(merker, "sr/mehr") { false }
    // Nach einem Fehlschlag laedt erneutes Aufklappen neu
    val kopf: @Composable () -> Unit = {
        SectionHeader(S.srTitel, chevron = true, onClick = { offenZustand.value = !offenZustand.value; if (offenZustand.value && liste == null) geladen = false })
    }
    if (offen && ok) {
        LaunchedEffect(kuerzel, geladen) {
            if (geladen) return@LaunchedEffect
            liste = try { state.investegateStimmrechte(kuerzel) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            geladen = true
        }
    }
    val rows = ArrayList<ZeilenDaten>()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    if (offen) {
        if (!ok) {
            if (abgelehnt) {
                rows.add(ZeilenDaten("ohne") { Text(S.srOhne, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
                rows.add(ZeilenDaten("seite") { QuellenZeile(S.q5IgSeite) { runCatching { uri.openUri(Investegate.firmaAdresse(kuerzel)) } } })
            } else {
                rows.add(ZeilenDaten("privat") {
                    Column {
                        Text(S.q5IgPrivat, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        InvestegateBestaetigung(state, onNein = { abgelehnt = true })
                    }
                })
            }
        } else {
            val l = liste
            when {
                !geladen -> rows.add(ZeilenDaten("laedt") { EmptyHint(S.trLaedt) })
                l == null -> rows.add(ZeilenDaten("nicht") { Text(S.q5Nicht, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
                l.isEmpty() -> rows.add(ZeilenDaten("keine") { Text(S.srKeine, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
                else -> {
                    QuellenMelden(INVESTEGATE_QUELLE)
                    // Die neuesten drei, auf Knopf bis zehn; je Meldung kommt das Formular ueber tr1
                    l.take(if (mehr) 10 else 3).forEachIndexed { i, e ->
                        rows.add(ZeilenDaten("m$i-${e.link}") {
                            StimmrechteEintrag(merker, e, { state.investegateTr1(it.link) }) { x -> onMeldung(Investegate.Meldung(x.zeit, "", kuerzel, x.titel, x.quelle, x.link)) }
                        })
                    }
                    if (l.size > 3) rows.add(ZeilenDaten("mehr") { ListenPfeil(mehr) { mehr = !mehr } })
                    rows.add(ZeilenDaten("fuss") {
                        Column {
                            QuellenZeile(S.q5IgSeite) { runCatching { uri.openUri(Investegate.firmaAdresse(kuerzel)) } }
                            HinweisInfo(S.srHinweis)
                        }
                    })
                }
            }
        }
    }
    ModellAusgabe(merker, "sr", ListenModell(titel = "", gross = true, kern = null, zeilen = rows, kopf = kopf, immerOffen = true))
}

internal fun LazyListScope.stimmrechteGbAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "sr")
}

// Eine Meldung als eigener Eintrag: das Formular kommt ueber tr1 und bleibt im Merker der Seite.
@Composable
private fun StimmrechteEintrag(merker: SeitenMerker?, e: Tr1.Eintrag, tr1: suspend (Tr1.Eintrag) -> Tr1.Meldung?, onOeffnen: (Tr1.Eintrag) -> Unit) {
    var meldung by gemerkt<Tr1.Meldung?>(merker, "sr/tr1/${e.link}") { null }
    LaunchedEffect(e.link) {
        if (meldung != null) return@LaunchedEffect
        meldung = try { tr1(e) } catch (ce: kotlinx.coroutines.CancellationException) { throw ce } catch (_: Exception) { null }
    }
    StimmrechteZeile(e, meldung, onOeffnen)
}
