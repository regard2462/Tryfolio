package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.KongressBestand
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Personenbilder
import de.troetenrepublik.app.market.Personeninfo
import de.troetenrepublik.app.plattform.LogoBild

// Profilbilder fuer Copytrading: Zuordnung nach dem Schluessel einer Person.
internal object Fotos {

    // Foto zu einem Schluessel wie beim Folgen: „k:Nachname|Vorname|ST“ (Repraesentantenhaus, mit wahlkreis
    // genauer), „n:Nachname|Vorname“ (Senat), „r:trump“ (Regierung), „i:buffett“ (Investoren).
    fun von(state: AppState, key: String, wahlkreis: String? = null): Personenbilder.Foto? {
        val b = state.bilder ?: return null
        val teile = key.substringAfter(':').split('|')
        return when (key.substringBefore(':')) {
            "k" -> state.kongressVerzeichnis()?.haus(teile[0], wahlkreis?.takeIf { it.length >= 2 } ?: teile.getOrElse(2) { "" }, teile.getOrElse(1) { "" })?.let { b.fotoVon(it.mitglied.id) } ?: b.haus(teile[0], wahlkreis?.takeIf { it.length >= 2 } ?: teile.getOrElse(2) { "" }, teile.getOrElse(1) { "" })
            "n" -> state.kongressVerzeichnis()?.senat(teile[0], teile.getOrElse(1) { "" })?.let { b.fotoVon(it.mitglied.id) } ?: b.senator(teile[0], teile.getOrElse(1) { "" })
            // Weitere Regierungsmitglieder (seit 5.92) unter ihrer Kennung („o:bessent-scott“)
            "r", "i", "o" -> b.person(teile[0])
            else -> null
        }
    }

    fun nachweis(f: Personenbilder.Foto): String =
        if (f.logo) S.copyLogoCredit(f.quelle)
        else if (f.kongress) S.copyPhotoCongress
        else S.copyPhotoCredit(f.autor.ifBlank { "Wikimedia Commons" }, f.lizenz)
}

// Kurzinfos zu einer Person (seit 5.92): Partei, Kammer, Staat, seit wann, Ausschuesse. Schluessel wie bei Fotos;
// Abgeordnete und Senatoren ueber ihre Bioguide-Kennung, Praesident, Vizepraesident und Regierung ueber ihre Kennung.
internal object Infos {
    private val daten: Personeninfo? by lazy {
        runCatching { Personeninfo.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/personen.json").decodeToString()) }.getOrNull()
    }

    // Praesident und Vizepraesident gehoeren den Republikanern an (fest, die Datei fuehrt nur den Kongress).
    private val REGIERUNG = mapOf("trump" to "R", "vance" to "R")

    fun von(state: AppState, key: String, wahlkreis: String? = null): Personeninfo.Info? {
        val d = daten
        val b = state.bilder
        val teile = key.substringAfter(':').split('|')
        return when (key.substringBefore(':')) {
            "k" -> state.kongressVerzeichnis()?.haus(teile[0], wahlkreis?.takeIf { it.length >= 2 } ?: teile.getOrElse(2) { "" }, teile.getOrElse(1) { "" })?.let { ausBestand(state, it, d) }
                ?: b?.hausId(teile[0], wahlkreis?.takeIf { it.length >= 2 } ?: teile.getOrElse(2) { "" }, teile.getOrElse(1) { "" })?.let { d?.p?.get(it) }
            "n" -> state.kongressVerzeichnis()?.senat(teile[0], teile.getOrElse(1) { "" })?.let { ausBestand(state, it, d) }
                ?: b?.senatorId(teile[0], teile.getOrElse(1) { "" })?.let { d?.p?.get(it) }
            "r" -> REGIERUNG[teile[0]]?.let { Personeninfo.Info(pa = it) }
            "o" -> d?.x?.get(teile[0])
            else -> null
        }
    }

    // Aus dem Bestand der Kongressmitglieder (5.96). Ausschuesse aus dem geladenen Stand, sonst aus personen.json;
    // Ehemalige ohne Ausschuesse, dafuer mit dem Ende der Mitgliedschaft.
    private fun ausBestand(state: AppState, t: KongressBestand.Treffer, d: Personeninfo?): Personeninfo.Info {
        val m = t.mitglied
        val geladen = state.kongressMitglieder()?.a.orEmpty()
        val a = when {
            t.ehemalig -> emptyList()
            geladen.isNotEmpty() -> geladen[m.id].orEmpty()
            else -> d?.p?.get(m.id)?.takeIf { it.k == m.k }?.a.orEmpty()
        }
        return Personeninfo.Info(pa = m.pa, k = m.k, st = m.st, d = m.d, seit = m.seit, a = a, bis = if (t.ehemalig) m.bis else "")
    }

    // Kurzform fuer Listen: „Dem. · CA-11“.
    fun kurz(i: Personeninfo.Info?): String? = i?.pa?.takeIf { it.isNotBlank() }?.let { S.parteiKurz(it) }

    // Zeitraum fuer Listen bei Ehemaligen (5.96): „2019 bis 2027“.
    fun zeitraum(i: Personeninfo.Info?): String? = i?.bisJahr?.let { bis -> i.seitJahr?.let { S.amtZeitraum(it, bis) } }

    // Zeile unter dem Namen auf der Seite einer Person: Partei, Staat und Wahlkreis, seit wann.
    fun zeile(i: Personeninfo.Info): String = listOfNotNull(
        i.pa.takeIf { it.isNotBlank() }?.let { S.parteiLang(it) },
        i.st.takeIf { it.isNotBlank() }?.let { st ->
            val name = S.usStaat(st, Personeninfo.STAATEN[st] ?: st)
            if (i.k == "rep" && i.d != null && i.d > 0) S.wahlkreisVon(name, i.d) else name
        },
        // Bei Ehemaligen (5.96) der Zeitraum der Mitgliedschaft, sonst der Beginn.
        i.bisJahr?.let { bis -> i.seitJahr?.let { von -> S.kammerZeitraum(i.k == "sen", von, bis) } } ?: i.seitJahr?.let { S.kammerSeit(i.k == "sen", it) },
    ).joinToString(" · ")
}

// Partei, Staat und Ausschuesse unter dem Kopfbild einer Person (seit 5.92). Ausschuesse als Stichpunkte, Funktion
// in Klammern; mehr als vier hinter „Mehr“.
@Composable
internal fun PersonInfoBlock(i: Personeninfo.Info?) {
    if (i == null) return
    val zeile = Infos.zeile(i)
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        if (zeile.isNotBlank()) PunktText(zeile, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        val a = i.ausschuesse
        if (a.isNotEmpty()) {
            Text(S.ausschuesse, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            val punkte = a.joinToString("\n") { (name, funktion) -> "• " + name + (S.ausschussFunktion(funktion)?.let { " ($it)" } ?: "") }
            AufklappText(punkte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, nachZeile = a.size > 4)
        }
    }
}

// Rundes Profilbild; ohne Bild (oder wenn es nicht laedt) die Initialen.
@Composable
internal fun PersonBild(foto: Personenbilder.Foto?, name: String, size: Int = 38) {
    var fehler by remember(foto?.adresse) { mutableStateOf(false) }
    var mitErsatz by remember(foto?.adresse) { mutableStateOf(false) }
    if (foto == null || foto.adresse.isBlank() || fehler) {
        InstrumentAvatar(name, name, size)
        return
    }
    // Firmenlogos ganz und auf hellem Grund, Fotos fuellen den Kreis
    Box(Modifier.size(size.dp).clip(CircleShape).background(if (foto.logo) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        LogoBild(
            url = if (mitErsatz) foto.ersatz else foto.adresse,
            contentDescription = name,
            modifier = if (foto.logo) Modifier.fillMaxSize().padding((size / 5).dp) else Modifier.fillMaxSize(),
            contentScale = if (foto.logo) ContentScale.Fit else ContentScale.Crop,
            onSuccess = {},
            onError = { if (!mitErsatz && foto.ersatz.isNotBlank()) mitErsatz = true else fehler = true },
        )
    }
}

// Grosses Profilbild oben auf der Seite einer Person, daneben Urheber und Lizenz.
@Composable
internal fun PersonKopfbild(foto: Personenbilder.Foto?, name: String) {
    if (foto == null) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonBild(foto, name, size = 72)
        Spacer(Modifier.width(14.dp))
        Text(Fotos.nachweis(foto), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
