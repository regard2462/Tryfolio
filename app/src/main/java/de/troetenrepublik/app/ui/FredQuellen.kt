package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.FredRegeln
import de.troetenrepublik.app.i18n.I18n

// Quellenangabe je FRED-Reihe, wie FRED sie verlangt, mit Link zur Seite der Reihe (doppelte Reihen einmal).
internal fun fredQuelle(id: String): QuelleLink = QuelleLink(FredRegeln.zitat(id, I18n.lang == Lang.DE), FredRegeln.serienSeite(id))

internal fun fredQuellen(ids: Collection<String>): List<QuelleLink> = ids.distinct().map(::fredQuelle)
