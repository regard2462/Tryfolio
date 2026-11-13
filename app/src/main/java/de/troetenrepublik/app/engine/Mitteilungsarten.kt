package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.plattform.Notifier

// Gliederung der Mitteilungen in den Einstellungen: welche Arten es gibt, ob sie an sind und was ein Schalter aendert.
object Mitteilungsarten {
    enum class Art { DEPOT, ALARME, RUECKBLICK, NEWS, COPY, WELT, UPDATES }

    // Alle Arten in fester Reihenfolge; Updates nur, wenn die App sich aktualisieren kann.
    fun arten(aktualisierbar: Boolean): List<Art> =
        Art.values().filter { it != Art.UPDATES || aktualisierbar }

    fun an(s: Settings, art: Art): Boolean = when (art) {
        Art.DEPOT -> s.mitteilungDepot
        Art.ALARME -> s.mitteilungAlarme
        Art.RUECKBLICK -> s.tagesrueckblick && s.rueckblickMitteilung
        Art.NEWS -> s.newsMitteilung != MITTEILUNG_AUS
        Art.COPY -> s.mitteilungCopy
        Art.WELT -> s.mitteilungWelt
        Art.UPDATES -> s.updateHinweis
    }

    // Schalter umlegen. Rueckblick aus laesst die Rueckblicke selbst an, News merken sich die Stufe.
    fun setzen(s: Settings, art: Art, an: Boolean): Settings = when (art) {
        Art.DEPOT -> s.copy(mitteilungDepot = an)
        Art.ALARME -> s.copy(mitteilungAlarme = an)
        Art.COPY -> s.copy(mitteilungCopy = an)
        Art.WELT -> s.copy(mitteilungWelt = an)
        Art.UPDATES -> s.copy(updateHinweis = an)
        Art.RUECKBLICK ->
            if (an) s.copy(rueckblickMitteilung = true, tagesrueckblick = true) else s.copy(rueckblickMitteilung = false)
        Art.NEWS ->
            if (!an) {
                val zuletzt = if (s.newsMitteilung != MITTEILUNG_AUS) s.newsMitteilung else s.newsMitteilungZuletzt
                s.copy(newsMitteilungZuletzt = zuletzt, newsMitteilung = MITTEILUNG_AUS)
            } else if (s.newsMitteilung == MITTEILUNG_AUS) {
                val stufe = s.newsMitteilungZuletzt.takeIf { it.isNotEmpty() && it != MITTEILUNG_AUS } ?: MITTEILUNG_WICHTIGE
                s.copy(newsMitteilung = stufe)
            } else s
    }

    // Stelle der Ueberschrift „Glocken“ in der Liste der Seite Mitteilungen: davor stehen Kopf, Hauptschalter und
    // je Art eine Zeile.
    fun glockenStelle(arten: Int): Int = 2 + arten

    // Wie viele der Arten sind an.
    fun aktiv(s: Settings, aktualisierbar: Boolean): Int = arten(aktualisierbar).count { an(s, it) }

    // Kanaele, die der Notifier unterdrueckt (Rueckblick, News und Updates prueft AppState selbst).
    fun gesperrteKanaele(s: Settings): Set<String> = buildSet {
        if (!s.mitteilungDepot) {
            add(Notifier.CHANNEL_TRADES)
            add(Notifier.CHANNEL_CASH)
        }
        if (!s.mitteilungAlarme) add(Notifier.CHANNEL_ALERTS)
        if (!s.mitteilungCopy) add(Notifier.CHANNEL_COPY)
        if (!s.mitteilungWelt) add(Notifier.CHANNEL_WELT)
    }
}
