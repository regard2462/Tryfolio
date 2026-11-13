package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des Abschnitts „Geschichte“ auf der Wertpapierseite (Zeitleiste der Firma).
// Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object GeschichteTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Geschichte",
        "kern.gegruendet" to "Gegründet {0}",
        "kern.boersengang" to "Börsengang {0}",
        "kern.n" to "{0} Ereignisse seit {1}",
        "kern.uebernahme.1" to "1 Übernahme",
        "kern.uebernahme.n" to "{0} Übernahmen",
        "GRUENDUNG" to "Gegründet",
        "BOERSENGANG" to "Börsengang, {0}",
        "VORGAENGER" to "Hervorgegangen aus {0}",
        "NACHFOLGER" to "Aufgegangen in {0}",
        "EIGNER" to "{0} wird Mehrheitseigner",
        "MUTTER" to "Tochter von {0}",
        "MUTTER_ENDE" to "Nicht mehr Teil von {0}",
        "NEUE_TOCHTER" to "Neue Tochter: {0}",
        "TOCHTER_ENDE" to "Tochter abgegeben: {0}",
        "NAME" to "Umbenannt von {0} in {1}",
        "KUERZEL" to "Kürzel von {0} zu {1}",
        "SITZ" to "Sitz verlegt nach {0}",
        "CEO" to "{0} wird CEO",
        "SPLIT" to "Aktiensplit {0}",
        "REVERSE" to "Reverse Split {0}",
        "AUFLOESUNG" to "Aufgelöst",
        "DEAL_ANGEKUENDIGT" to "Übernahme angekündigt",
        "DEAL_ANGEBOT" to "Übernahmeangebot",
        "DEAL_ABGESCHLOSSEN" to "Übernahme abgeschlossen",
        "gegenseite" to "Gegenseite: {0}",
        "preis" to "{0} je Aktie",
        "INSOLVENZ" to "Insolvenzantrag",
        "chapter" to "Chapter {0}",
        "kurs" to "Kurs am Tag",
        "info" to "Zeitleiste aus Wikidata (Gründung, Börsengang, Vorgänger, Eigner, Töchter mit Zeitangabe, Namen, Kürzel, Sitz, CEO), den früheren Namen bei der SEC, Aktiensplits und den 8-K-Meldungen der letzten 24 Monate. Es stehen nur Ereignisse mit Datum da.\n\nKurs am Tag: Veränderung zum Schlusskurs des Vortags, nur bei Ereignissen mit genauem Tag.",
        "quelleSec" to "SEC EDGAR",
        "quelleWikidata" to "Wikidata (CC0)",
    )

    private val EN = mapOf(
        "titel" to "History",
        "kern.gegruendet" to "Founded {0}",
        "kern.boersengang" to "IPO {0}",
        "kern.n" to "{0} events since {1}",
        "kern.uebernahme.1" to "1 acquisition",
        "kern.uebernahme.n" to "{0} acquisitions",
        "GRUENDUNG" to "Founded",
        "BOERSENGANG" to "IPO, {0}",
        "VORGAENGER" to "Formed from {0}",
        "NACHFOLGER" to "Merged into {0}",
        "EIGNER" to "{0} becomes majority owner",
        "MUTTER" to "Subsidiary of {0}",
        "MUTTER_ENDE" to "No longer part of {0}",
        "NEUE_TOCHTER" to "New subsidiary: {0}",
        "TOCHTER_ENDE" to "Subsidiary left: {0}",
        "NAME" to "Renamed from {0} to {1}",
        "KUERZEL" to "Ticker changed from {0} to {1}",
        "SITZ" to "Headquarters moved to {0}",
        "CEO" to "{0} becomes CEO",
        "SPLIT" to "Stock split {0}",
        "REVERSE" to "Reverse split {0}",
        "AUFLOESUNG" to "Dissolved",
        "DEAL_ANGEKUENDIGT" to "Acquisition announced",
        "DEAL_ANGEBOT" to "Tender offer",
        "DEAL_ABGESCHLOSSEN" to "Acquisition completed",
        "gegenseite" to "Counterparty: {0}",
        "preis" to "{0} per share",
        "INSOLVENZ" to "Bankruptcy filing",
        "chapter" to "Chapter {0}",
        "kurs" to "Price on the day",
        "info" to "Timeline from Wikidata (founding, IPO, predecessors, owners, subsidiaries with dates, names, tickers, headquarters, CEO), former names at the SEC, stock splits and 8-K filings of the last 24 months. Only dated events are shown.\n\nPrice on the day: change against the previous close, only for events with an exact day.",
        "quelleSec" to "SEC EDGAR",
        "quelleWikidata" to "Wikidata (CC0)",
    )
}
