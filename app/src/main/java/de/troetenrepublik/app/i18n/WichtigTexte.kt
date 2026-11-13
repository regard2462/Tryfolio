package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.engine.WichtigThemen.Kat

// Texte des Reiters „Wichtigste“ (Etiketten der Kategorien, Zeile mit weiteren Schlagzeilen), deutsch und englisch.
internal object WichtigTexte {

    fun kat(deutsch: Boolean, k: Kat): String = if (deutsch) when (k) {
        Kat.POLITIK -> "Politik"
        Kat.ZENTRALBANKEN -> "Notenbanken"
        Kat.KONJUNKTUR -> "Konjunktur"
        Kat.MAERKTE -> "Märkte"
        Kat.UNTERNEHMEN -> "Unternehmen"
        Kat.ROHSTOFFE -> "Rohstoffe"
        Kat.KRYPTO -> "Krypto"
    } else when (k) {
        Kat.POLITIK -> "Politics"
        Kat.ZENTRALBANKEN -> "Central banks"
        Kat.KONJUNKTUR -> "Economy"
        Kat.MAERKTE -> "Markets"
        Kat.UNTERNEHMEN -> "Companies"
        Kat.ROHSTOFFE -> "Commodities"
        Kat.KRYPTO -> "Crypto"
    }

    // Zusammenfassung der Unterkategorie Durchbrueche: Meldungen und Themen
    fun durchbrueche(deutsch: Boolean, meldungen: Int, themen: Int): String =
        if (deutsch) "$meldungen neu in " + (if (themen == 1) "1 Thema" else "$themen Themen")
        else "$meldungen new in " + (if (themen == 1) "1 topic" else "$themen topics")

    fun zumThema(deutsch: Boolean): String = if (deutsch) "Zum Thema" else "Open topic"
}
