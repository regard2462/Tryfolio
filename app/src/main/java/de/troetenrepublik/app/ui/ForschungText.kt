package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.ForschungTexte
import de.troetenrepublik.app.i18n.I18n

// Text zu den Forschungsdaten (i18n/ForschungTexte.kt) in der Sprache der App
internal fun forschungText(key: String, vararg w: String): String = ForschungTexte.text(I18n.lang == Lang.DE, key, w.toList())
