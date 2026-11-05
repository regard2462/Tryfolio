package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Tendenz.Lage
import de.troetenrepublik.app.engine.Tendenz.Richtung

// Einordnung einer Schlagzeile (5.94): fuer welche Aktie oder welchen Markt sie eher bullish oder bearish ist.
// Firmen erkennt der Katalog (Namensstamm oder Kuerzel im Titel), ihre Richtung kommt aus den Themen von BullBear
// (Gewinnwarnung, Hochstufung, Rueckkauf ...). Fuer allgemeine Meldungen gelten Stichwortregeln wie im Ausblick.
// Grobe Faustregeln, keine Empfehlung; ohne klares Signal bleibt die Liste leer.
object NachrichtEinordnung {

    // symbol: die genannte Aktie (fuer den Link zur Seite), null bei einem ganzen Markt.
    data class Einordnung(val lage: Lage, val symbol: String?)

    private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    // Allgemeine Meldungen: Muster und die Tendenz, die es nahelegt.
    private val MARKT: List<Pair<Regex, Lage>> = listOf(
        r("\\b(cuts?|lowers?|slashes)\\s+(interest\\s+)?rates?\\b|\\brate\\s+cuts?\\b|zinssenkung|senkt\\s+(den\\s+)?(leit)?zins") to Lage(Richtung.BULLISH, Tendenz.AKTIEN),
        r("\\b(raises?|hikes?|lifts?)\\s+(interest\\s+)?rates?\\b|\\brate\\s+hikes?\\b|zinserh(ö|oe)hung|erh(ö|oe)ht\\s+(den\\s+)?(leit)?zins") to Lage(Richtung.BEARISH, Tendenz.AKTIEN),
        r("inflation\\s+(rises|rose|jumps|jumped|accelerates|heats|hotter|higher|steigt|zieht\\s+an)|teuerung\\s+steigt") to Lage(Richtung.BEARISH, Tendenz.ANLEIHEN),
        r("inflation\\s+(eases|eased|cools|cooled|slows|slowed|falls|fell|lower|sinkt|geht\\s+zur(ü|ue)ck|schw(ä|ae)cht)") to Lage(Richtung.BULLISH, Tendenz.ANLEIHEN),
        r("\\boil(\\s+prices?)?\\s+(jumps?|surges?|soars?|rises?|spikes?|rall(y|ies)|climbs?)|(ö|oe)lpreis(e)?\\s+(steigt|steigen|springt|zieht\\s+an|klettert)|opec\\+?\\s+(cuts?|k(ü|ue)rzt)") to Lage(Richtung.BULLISH, Tendenz.OEL),
        r("\\boil(\\s+prices?)?\\s+(falls?|drops?|slumps?|plunges?|tumbles?|sinks?|slides?)|(ö|oe)lpreis(e)?\\s+(f(ä|ae)llt|fallen|sinkt|sinken|bricht\\s+ein|rutscht)") to Lage(Richtung.BEARISH, Tendenz.OEL),
        r("\\b(imposes?|new|raises?|hikes?|threatens?)\\s+(\\w+\\s+)?tariffs?|zoll(e|ä|ae)?(rh(ö|oe)hung)?\\s+(auf|gegen)|verh(ä|ae)ngt\\s+z(ö|oe)lle|droht\\s+mit\\s+z(ö|oe)llen|neue\\s+z(ö|oe)lle") to Lage(Richtung.BEARISH, Tendenz.AKTIEN),
        r("\\b(trade|tariff)\\s+(deal|truce|pause|agreement)|handelsabkommen|zollpause|einigung\\s+im\\s+zollstreit|z(ö|oe)lle\\s+(ausgesetzt|gesenkt)") to Lage(Richtung.BULLISH, Tendenz.AKTIEN),
        r("\\brecession\\s+(fears?\\s+(grow|rise|mount)|risks?\\s+(rise|grow)|looms?|warning|deepens?)|\\b(enters?|slides?\\s+into|falls?\\s+into|tips?\\s+into)\\s+(a\\s+)?recession|rezession\\w*\\s+(droht|befürchtet)|in\\s+die\\s+rezession") to Lage(Richtung.BEARISH, Tendenz.AKTIEN),
        r("\\bgold\\s+(hits|record|jumps?|surges?|soars?|rall(y|ies))|goldpreis\\s+(steigt|springt|auf\\s+rekord)|gold\\s+auf\\s+rekord") to Lage(Richtung.BULLISH, Tendenz.GOLD),
        r("(chip|semiconductor)\\s+export\\s+(ban|curbs?|controls?|restrictions?)|exportverbot\\w*\\s+f(ü|ue)r\\s+(chips|halbleiter)|exportbeschr(ä|ae)nkung\\w*\\s+f(ü|ue)r\\s+(chips|halbleiter)") to Lage(Richtung.BEARISH, Tendenz.CHIPS),
        r("(closes?|closure\\s+of|blocks?|blockade\\s+of|threatens?\\s+to\\s+close|sperrt|blockiert|sperrung\\s+der)\\s+(the\\s+)?(strait\\s+of\\s+hormuz|stra(ß|ss)e\\s+von\\s+hormus)|houthis?\\s+(attack|strike|target)|red\\s+sea\\s+attack|angriff\\w*\\s+im\\s+roten\\s+meer") to Lage(Richtung.BULLISH, Tendenz.OEL),
    )

    // Firmen aus dem Katalog als (Kuerzel, Name); einmal je Katalog vorbereitet.
    private class Firma(val symbol: String, val name: String, val muster: Regex)

    @Volatile private var firmenCache: Pair<List<Pair<String, String>>, List<Firma>>? = null

    private fun firmen(katalog: List<Pair<String, String>>): List<Firma> {
        firmenCache?.let { (k, f) -> if (k === katalog) return f }
        val f = katalog.mapNotNull { (s, n) ->
            val stamm = GleicheFirma.stamm(n)
            // Kurze oder allgemeine Staemme (etwa „Sea“, „Arm“) nur als Kuerzel in Grossbuchstaben
            val kuerzel = s.substringBefore('.').substringBefore('-')
            val teile = listOfNotNull(
                // Je Wort einzeln maskieren: \Q...\E um den ganzen Stamm machte aus \s+ reinen Text
                stamm.takeIf { it.length >= 5 }?.let { "\\b" + it.split(' ').filter { w -> w.isNotEmpty() }.joinToString("\\s+") { w -> Regex.escape(w) } + "\\b" },
                kuerzel.takeIf { it.length >= 3 && it.all { c -> c.isUpperCase() } }?.let { "(?-i:\\b" + Regex.escape(it) + "\\b)" },
            )
            if (teile.isEmpty()) null else Firma(s, n, Regex(teile.joinToString("|"), RegexOption.IGNORE_CASE))
        }
        firmenCache = katalog to f
        return f
    }

    // Genannte Firmen in der Reihenfolge des Katalogs, hoechstens zwei.
    fun firmenIn(titel: String, katalog: List<Pair<String, String>>): List<Pair<String, String>> =
        firmen(katalog).filter { it.muster.containsMatchIn(titel) }.take(2).map { it.symbol to it.name }

    fun einordnen(titel: String, katalog: List<Pair<String, String>>): List<Einordnung> {
        val aus = ArrayList<Einordnung>()
        val themen = BullBear.themen(titel)
        val bull = themen.filter { it.seite == BullBear.Seite.BULL }.sumOf { it.gewicht }
        val bear = themen.filter { it.seite == BullBear.Seite.BEAR }.sumOf { it.gewicht }
        val richtung = when {
            bull > bear -> Richtung.BULLISH
            bear > bull -> Richtung.BEARISH
            else -> null
        }
        if (richtung != null) firmenIn(titel, katalog).forEach { (s, n) -> aus += Einordnung(Lage(richtung, n), s) }
        MARKT.filter { it.first.containsMatchIn(titel) }.map { it.second }.distinct().forEach { aus += Einordnung(it, null) }
        return aus.distinctBy { it.lage }.take(3)
    }
}
