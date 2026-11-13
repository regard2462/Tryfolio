package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.LaenderStrukturen
import de.troetenrepublik.app.engine.Laenderrisiken
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// [Laender Strukturen 0.70]
// Einmal gelesen, gilt fuer alle Laender
private var strukturStand: LaenderStrukturen.Stand? = null

private suspend fun strukturLaden(): LaenderStrukturen.Stand? {
    strukturStand?.let { return it }
    val s = withContext(Dispatchers.Default) {
        runCatching { de.troetenrepublik.app.market.Daten.strukturen() }.getOrNull()
    }
    if (s != null) strukturStand = s
    return s
}

// Abschnitt "Strukturelle Probleme" im Laenderblatt: zuerst die strukturellen Kennzahlen der Weltbank (Reserven, Rohstoffe,
// Jugend, Alter, Auslandsschulden; ein Tipp erklaert sie), dann je Problem eine Zeile mit Ueberschrift, aufklappbar mit
// Zahl, Gegenprogrammen und Quellen.
// [0.78] Lesefluss: Problemtext in normaler Schrift, "Gegenmassnahmen" als gedaempfte Zwischenzeile, jede Massnahme auf
// einer leichten Flaeche mit hervorgehobenem Titel und gedaempftem Text.
@Composable
internal fun LaenderStrukturen(state: de.troetenrepublik.app.AppState, land: GlobusLand) {
    var stand by remember { mutableStateOf(strukturStand) }
    LaunchedEffect(Unit) { if (stand == null) stand = strukturLaden() }
    var hinweise by remember(land) { mutableStateOf<List<Laenderrisiken.Hinweis>>(emptyList()) }
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(land) {
        hinweise = de.troetenrepublik.app.market.ohneAbbruch {
            val r = state.weltbankViele(listOf(Laenderrisiken.KENN_RESERVEN, Laenderrisiken.KENN_BRENNSTOFF, Laenderrisiken.KENN_ERZE,
                Laenderrisiken.KENN_JUGEND, Laenderrisiken.KENN_ALTER, Laenderrisiken.KENN_AUSLAND))
            Laenderrisiken.struktur(
                land.a3,
                r[Laenderrisiken.KENN_RESERVEN], r[Laenderrisiken.KENN_BRENNSTOFF], r[Laenderrisiken.KENN_ERZE],
                r[Laenderrisiken.KENN_JUGEND], r[Laenderrisiken.KENN_ALTER], r[Laenderrisiken.KENN_AUSLAND],
            )
        }.getOrNull().orEmpty()
    }
    val probleme = stand?.let { LaenderStrukturen.fuer(it, land) }.orEmpty()
    // [Laender Strukturen 0.70] nennungspflichtige Quellen an den Quellenanhang des Blatts melden
    val sammler = LocalQuellenSammler.current
    val meldeQuellen = sammler != null && probleme.isNotEmpty()
    if (meldeQuellen) androidx.compose.runtime.SideEffect {
        for (q in strukturQuellen(land)) if (sammler.none { it.name == q.name }) sammler.add(q)
    }
    if (probleme.isEmpty() && hinweise.isEmpty()) return
    val deutsch = I18n.locale.language == "de"
    var offen by remember(land) { mutableStateOf(setOf<Int>()) }

    Text(
        S.lsTitel, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
    )
    if (hinweise.isNotEmpty()) QuellenLeiste(listOf(LaenderQuellen.weltbank(land.a2)))
    for (h in hinweise) {
        val zahl = Fmt.num(h.wert, if (h.art == Laenderrisiken.Art.ROHSTOFF) 0 else 1)
        val extra = when (h.art) {
            Laenderrisiken.Art.ROHSTOFF -> S.rkRohstoffAufteilung(Fmt.num(h.a ?: 0.0, 0), Fmt.num(h.b ?: 0.0, 0))
            Laenderrisiken.Art.JUGEND -> h.b?.let { Fmt.pct(it, 1) }
            else -> null
        }
        val label = S.rkStrukturLabel(h.art)
        val wert = S.rkStrukturWert(h.art, zahl)
        LbZeile(label, if (h.jahr == null) wert else "$wert (${h.jahr})") { info = label to S.rkStrukturInfo(h.art, extra) }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    probleme.forEachIndexed { i, p ->
        val auf = i in offen
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = if (auf) offen - i else offen + i }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.t.je(deutsch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(auf)
        }
        if (auf) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.d.je(deutsch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp))
            Text(S.lsProgramme, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
            if (p.p.isEmpty()) {
                Text(S.lsKeinProgramm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            for (g in p.p) Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(S.lsProgramm(g.n.je(deutsch), g.j), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(g.t.je(deutsch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// Quellen der Eintraege eines Landes fuer den Quellenanhang am Blattende (erst nach dem Laden gefuellt). Es gilt dieselbe
// Regel wie fuer alle Quellen des Blatts (pflichtQuelle): nur Quellen, deren Bedingungen eine Nennung verlangen; gleiche
// Namen (Weltbank) erscheinen einmal, Wikipedia-Artikel einzeln.
fun strukturQuellen(land: GlobusLand): List<QuelleLink> {
    val stand = strukturStand ?: return emptyList()
    val deutsch = I18n.locale.language == "de"
    return LaenderStrukturen.fuer(stand, land).flatMap { it.q }
        .mapNotNull { q -> pflichtQuelle(q.n.je(deutsch), q.u) }
        .distinctBy { it.name }
}
