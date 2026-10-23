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
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Personenbilder
import de.troetenrepublik.app.plattform.LogoBild

// Profilbilder fuer Copytrading: Zuordnung nach dem Schluessel einer Person.
internal object Fotos {

    // Foto zu einem Schluessel wie beim Folgen: „k:Nachname|Vorname|ST“ (Repraesentantenhaus, mit wahlkreis
    // genauer), „n:Nachname|Vorname“ (Senat), „r:trump“ (Regierung), „i:buffett“ (Investoren).
    fun von(state: AppState, key: String, wahlkreis: String? = null): Personenbilder.Foto? {
        val b = state.bilder ?: return null
        val teile = key.substringAfter(':').split('|')
        return when (key.substringBefore(':')) {
            "k" -> b.haus(teile[0], wahlkreis?.takeIf { it.length >= 2 } ?: teile.getOrElse(2) { "" }, teile.getOrElse(1) { "" })
            "n" -> b.senator(teile[0], teile.getOrElse(1) { "" })
            "r", "i" -> b.person(teile[0])
            else -> null
        }
    }

    fun nachweis(f: Personenbilder.Foto): String =
        if (f.logo) S.copyLogoCredit(f.quelle)
        else if (f.kongress) S.copyPhotoCongress
        else S.copyPhotoCredit(f.autor.ifBlank { "Wikimedia Commons" }, f.lizenz)
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
