package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig

// Glocke je Wertpapier (0.77): welche Mitteilungsarten es fuer einen Wert gibt, welche davon an sind und die
// Zusammenfassung fuer die Liste der Glocken. Jede Art ist ein PriceAlert mit art; Kursmarken und Tagesbewegung
// bleiben, was sie waren.
object Wertglocke {
    enum class Gruppe { KURS, NACHRICHTEN, INSIDER, SIGNALE, TERMINE, WARNZEICHEN }

    const val MARKE = "MARKE"
    const val BEWEGUNG = "MOVE"

    // Vorgabe der Tagesbewegung beim Einschalten, Auswahl der Schwellen.
    const val BEWEGUNG_STANDARD = 5.0
    val BEWEGUNG_STUFEN = listOf(2.0, 3.0, 5.0, 10.0)

    class Eintrag(val gruppe: Gruppe, val arten: List<String>)

    // Art eines Alarms im Sinne der Glocke.
    fun artVon(a: PriceAlert): String = when {
        a.art.isNotEmpty() -> a.art
        a.tagesProzent > 0 -> BEWEGUNG
        else -> MARKE
    }

    // Arten, die fuer den Wert moeglich sind, in der Reihenfolge der Anzeige; leere Gruppen fehlen.
    // dividende: ob der Wert Dividende zahlt (null = unbekannt, dann ohne Ex-Tag). us: US-Aktie.
    fun moeglich(typ: AssetType, us: Boolean, dividende: Boolean?): List<Eintrag> {
        val aktie = typ == AssetType.STOCK
        val handelbar = typ == AssetType.STOCK || typ == AssetType.ETF || typ == AssetType.CRYPTO
        val kurs = buildList {
            add(MARKE)
            add(BEWEGUNG)
            add(Alarmarten.SCHNITT)
            if (handelbar) add(Alarmarten.VOLUMEN)
            if (handelbar || typ == AssetType.INDEX) add(Alarmarten.WOCHEN52)
        }
        val liste = ArrayList<Eintrag>()
        liste += Eintrag(Gruppe.KURS, kurs)
        if (handelbar) liste += Eintrag(Gruppe.NACHRICHTEN, listOf(Alarmarten.NEWS))
        if (aktie && us) liste += Eintrag(Gruppe.INSIDER, listOf(Alarmarten.INSIDER, Alarmarten.INSIDER_VK))
        if (aktie) liste += Eintrag(Gruppe.SIGNALE, listOf(Alarmarten.ANALYST))
        if (aktie) liste += Eintrag(Gruppe.TERMINE, listOf(Alarmarten.QUARTAL) + if (dividende == true) listOf(Alarmarten.EXDIV) else emptyList())
        if (aktie && us) liste += Eintrag(Gruppe.WARNZEICHEN, listOf(Alarmarten.ACHTK))
        return liste
    }

    // Moegliche Arten ergaenzt um Arten, die der Nutzer schon gesetzt hat (ein bestehender Alarm bleibt sichtbar und
    // abschaltbar, auch wenn die Art inzwischen nicht mehr moeglich scheint).
    fun sichtbar(moeglich: List<Eintrag>, vorhanden: Set<String>): List<Eintrag> {
        val bekannt = moeglich.flatMap { it.arten }.toSet()
        val fehlend = (Alarmarten.ALLE + BEWEGUNG).filter { it in vorhanden && it !in bekannt } + regelArten(vorhanden)
        if (fehlend.isEmpty()) return moeglich
        val gruppen = (moeglich.map { it.gruppe } + fehlend.map { gruppeVon(it) }).distinct().sortedBy { it.ordinal }
        return gruppen.map { g ->
            Eintrag(g, moeglich.firstOrNull { it.gruppe == g }?.arten.orEmpty() + fehlend.filter { gruppeVon(it) == g })
        }
    }

    // Glocken auf Regeln des Strategietests (RegelGlocke) entstehen dort und stehen hier nur, wenn sie gesetzt sind.
    fun regelArten(vorhanden: Collection<String>): List<String> = vorhanden.filter { RegelGlocke.istArt(it) }.distinct().sorted()

    fun gruppeVon(art: String): Gruppe = if (RegelGlocke.istArt(art)) Gruppe.SIGNALE else when (art) {
        MARKE, BEWEGUNG, Alarmarten.SCHNITT, Alarmarten.VOLUMEN, Alarmarten.WOCHEN52 -> Gruppe.KURS
        Alarmarten.NEWS -> Gruppe.NACHRICHTEN
        Alarmarten.INSIDER, Alarmarten.INSIDER_VK -> Gruppe.INSIDER
        Alarmarten.ANALYST -> Gruppe.SIGNALE
        Alarmarten.QUARTAL, Alarmarten.EXDIV -> Gruppe.TERMINE
        else -> Gruppe.WARNZEICHEN
    }

    // Arten, die der Schalter "Alle" betrifft: alle moeglichen ausser den Kursmarken.
    fun fuerAlle(moeglich: List<Eintrag>): List<String> = moeglich.flatMap { it.arten }.filter { it != MARKE }

    // Ob "Alle" an ist: jede dieser Arten ist gesetzt.
    fun alleAn(moeglich: List<Eintrag>, vorhanden: Set<String>): Boolean {
        val arten = fuerAlle(moeglich)
        return arten.isNotEmpty() && arten.all { it in vorhanden }
    }

    // Alarme eines Werts nach dem Umlegen von Schaltern. an: Arten einschalten (fehlende kommen neu hinzu, schon
    // vorhandene bleiben mit ihrem Merker), sonst alle Alarme dieser Arten loeschen; Kursmarken nur mit MARKE in arten.
    // neu liefert den Alarm fuer eine fehlende Art (Kennung und Zeitpunkt vergibt der Aufrufer).
    fun umlegen(alarme: List<PriceAlert>, symbol: String, arten: Collection<String>, an: Boolean, neu: (String) -> PriceAlert): List<PriceAlert> {
        if (!an) return alarme.filterNot { it.symbol == symbol && artVon(it) in arten }
        val da = alarme.filter { it.symbol == symbol }.map { artVon(it) }.toSet()
        return alarme + arten.filter { it != MARKE && it !in da }.map(neu)
    }

    // Bewegungsalarm auf eine Schwelle setzen: ein vorhandener behaelt Kennung und Meldetag, weitere entfallen.
    fun bewegungSetzen(alarme: List<PriceAlert>, symbol: String, prozent: Double, neu: () -> PriceAlert): List<PriceAlert> {
        val vorhanden = alarme.firstOrNull { it.symbol == symbol && artVon(it) == BEWEGUNG }
        val rest = alarme.filterNot { it.symbol == symbol && artVon(it) == BEWEGUNG }
        return rest + (vorhanden?.copy(tagesProzent = prozent) ?: neu().copy(tagesProzent = prozent))
    }

    // Wichtige Schlagzeilen zu einem Wert: von der Nachrichtenseite als wichtig eingestuft, den Wert nennend, hoechstens
    // zwoelf Stunden alt (wie die Mitteilungen zu Schlagzeilen). Welche schon gemeldet sind, entscheidet der Alarm.
    fun newsTreffer(eintraege: List<Rss.Eintrag>, wichtige: Set<String>, begriffe: List<String>, jetzt: Long): List<Alarmarten.NewsTreffer> {
        if (begriffe.isEmpty()) return emptyList()
        return eintraege.filter {
            it.id in wichtige && it.zeit > jetzt - 12 * 3_600_000L && it.zeit <= jetzt + 5 * 60_000L &&
                begriffe.any { b -> Wichtig.trifft(it.titel, b) }
        }.map { Alarmarten.NewsTreffer(it.zeit, it.titel) }
    }

    // Schlagzeilen fuer den News-Kanal ohne die, die in derselben Runde eine Glocke „wichtige Meldungen“ meldet (gleicher
    // Titel oder dieselbe Geschichte in anderer Formulierung): eine Nachricht loest so nur eine Mitteilung aus.
    fun ohneGlockenNews(melden: List<Rss.Eintrag>, glockenTitel: Collection<String>): List<Rss.Eintrag> =
        if (glockenTitel.isEmpty()) melden
        else melden.filterNot { e -> glockenTitel.any { it == e.titel || Wichtig.gleicheGeschichte(it, e.titel) } }

    // Zeile fuer die Liste der Glocken: aktive Arten in fester Reihenfolge, getrennt durch Mittelpunkte.
    fun zusammenfassung(
        alarme: List<PriceAlert>, artName: (String) -> String, marken: (List<Double>) -> String, bewegung: (Double) -> String,
        weitere: ((Int) -> String)? = null,
    ): String {
        val teile = ArrayList<String>()
        val kursmarken = alarme.filter { artVon(it) == MARKE }.map { it.target }
        if (kursmarken.isNotEmpty()) teile += marken(kursmarken)
        alarme.firstOrNull { artVon(it) == BEWEGUNG }?.let { teile += bewegung(it.tagesProzent) }
        val vorhanden = alarme.map { it.art }.toSet()
        for (art in Alarmarten.ALLE + regelArten(vorhanden)) if (art in vorhanden) teile += artName(art)
        // Mehr als drei Arten: die ersten zwei und die Zahl der uebrigen, damit die Zeile nie abgeschnitten wird
        val gezeigt = if (teile.size > 3 && weitere != null) teile.take(2) + weitere(teile.size - 2) else teile
        return gezeigt.joinToString(" · ")
    }

    // Werte mit mindestens einem Alarm, in der Reihenfolge des ersten Alarms.
    fun werte(alarme: List<PriceAlert>): List<Pair<String, String>> =
        alarme.filter { it.symbol.isNotBlank() }.distinctBy { it.symbol }.map { it.symbol to it.name }
}
