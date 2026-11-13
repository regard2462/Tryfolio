package de.troetenrepublik.app.engine

// Ehrliches Altern: Jeder gepflegte Inhalt hat einen Stand (Tag der Daten) und ein Profil mit zwei Schwellen in Monaten.
// Ab der ersten Schwelle zeigt die App den Stand dezent am Inhalt, ab der zweiten blendet sie den Inhalt aus oder
// kennzeichnet ihn klar als veraltet, je nachdem, ob er dann noch nuetzt. Der Baustein rechnet nur; die Uhr kommt von Uhr.
object Alter {

    enum class Zustand { FRISCH, ALT, VERALTET }

    // Was nach der zweiten Schwelle geschieht: AUSBLENDEN (Inhalt faellt weg) oder KENNZEICHNEN (bleibt, mit Hinweis
    // "veraltet"). nie: zweite Schwelle 0 bedeutet, dass der Inhalt nie ausgeblendet oder als veraltet gekennzeichnet wird.
    enum class Danach { AUSBLENDEN, KENNZEICHNEN }

    class Profil(val name: String, val warnenMonate: Int, val ausblendenMonate: Int, val danach: Danach)

    // Profile je Inhaltsart (Schwellen in Monaten). Begruendung: Je schneller sich eine Aussage aendert, desto frueher
    // der Hinweis; was ohne Aktualitaet noch Sinn hat (Rueckblick, Hintergrund), bleibt mit Stand stehen.
    val LAGEBILD = Profil("lagebild", 9, 24, Danach.AUSBLENDEN)
    val REKORD = Profil("rekord", 36, 0, Danach.KENNZEICHNEN)
    val STRUKTUREN = Profil("strukturen", 12, 36, Danach.KENNZEICHNEN)
    val LAENDER_PERSONEN = Profil("personen", 6, 0, Danach.KENNZEICHNEN)
    val KATALOG = Profil("katalog", 12, 0, Danach.KENNZEICHNEN)
    val IPO_BEVORSTEHEND = Profil("ipo", 3, 6, Danach.AUSBLENDEN)
    val FINANZWISSEN = Profil("finanzwissen", 12, 0, Danach.KENNZEICHNEN)
    val SANKTIONEN = Profil("sanktionen", 6, 18, Danach.KENNZEICHNEN)
    val INVESTOREN = Profil("investoren", 6, 24, Danach.KENNZEICHNEN)
    val ETF_LAENDER = Profil("etfLaender", 6, 24, Danach.KENNZEICHNEN)
    val REGIERUNG = Profil("regierung", 6, 0, Danach.KENNZEICHNEN)
    val HANDEL = Profil("handel", 24, 0, Danach.KENNZEICHNEN)
    val KONFLIKTE = Profil("konflikte", 14, 38, Danach.KENNZEICHNEN)
    val STEUERWERTE = Profil("steuerwerte", 12, 0, Danach.KENNZEICHNEN)
    // Vierteljaehrliche Veroeffentlichung mit Verzug von drei Monaten (Hauspreise Dallas Fed)
    val QUARTAL = Profil("quartal", 6, 18, Danach.KENNZEICHNEN)
    // Erscheint im Januar und im Juli (Big-Mac-Index)
    val HALBJAHR = Profil("halbjahr", 9, 24, Danach.KENNZEICHNEN)
    // Monatliche Veroeffentlichung (Oel weltweit, EIA): nach einem Jahr ohne neue Datei faellt der Abschnitt weg
    val MONATLICH = Profil("monatlich", 3, 12, Danach.AUSBLENDEN)
    val STANDARD = Profil("standard", 12, 36, Danach.KENNZEICHNEN)
    // Verflechtungen: Vertraege und Beteiligungen aendern sich oft; die Angaben bleiben mit Stand sichtbar
    val NETZ = Profil("netz", 6, 24, Danach.KENNZEICHNEN)
    // Monatliche Indexreihen (Geopolitical Risk, Economic Policy Uncertainty): nach vier Monaten ohne neue Datei der Hinweis
    // auf den Stand, ausgeblendet wird die lange Reihe nie
    val MONATSREIHE = Profil("monatsreihe", 4, 0, Danach.KENNZEICHNEN)
    // Jahresdaten mit Verzug von rund einem Jahr (Emissionen je Firma: EPA und EU-Emissionshandel)
    val JAEHRLICH = Profil("jaehrlich", 24, 0, Danach.KENNZEICHNEN)

    // Profil je Datei des Datenbuendels (Name wie in assets/); unbekannte Dateien bekommen STANDARD.
    private val DATEIEN = mapOf(
        "durchbrueche/inhalt.json" to LAGEBILD,
        "globus/strukturen.json" to STRUKTUREN,
        "globus/laenderdaten.json" to LAENDER_PERSONEN,
        "katalog.json" to KATALOG,
        "globus/etf-laender.json" to ETF_LAENDER,
        "globus/sanktionen.json" to SANKTIONEN,
        "globus/konflikte.json" to KONFLIKTE,
        "globus/firmen.json" to KATALOG,
        "globus/firmen-privat.json" to KATALOG,
        "globus/handel.json" to HANDEL,
        "globus/ersatz.json" to HANDEL,
        "globus/steckbrief.json" to HANDEL,
        "globus/taetigkeit.json" to KATALOG,
        "globus/wiki-wirtschaft.json" to KATALOG,
        "copytrading/investoren.json" to INVESTOREN,
        "copytrading/gesamt13f.json" to INVESTOREN,
        "copytrading/amt.json" to REGIERUNG,
        "firmen/emissionen.json" to JAEHRLICH,
        "firmen/emissionen-eu.json" to JAEHRLICH,
        "firmen/staatsgeld-eu.json" to JAEHRLICH,
        "copytrading/personen.json" to REGIERUNG,
        "steuerwerte.json" to STEUERWERTE,
        "welt/einfluss.json" to STRUKTUREN,
        "welt/rohstoffe.json" to HANDEL,
        "welt/anlagen.json" to HANDEL,
        "indikatoren/warnzeichen_rueckblick.json" to HANDEL,
        "copytrading/cluster.json" to HANDEL,
        "etf-rueckwaerts.json" to HANDEL,
        "analysten/haeuser.json" to STANDARD,
        "copytrading/sec_historie.json" to REKORD,
        // JST erscheint in Fassungen (R6 seit 2022), Dallas Fed vierteljaehrlich
        "globus/jst.json" to REKORD,
        "globus/hauspreise.json" to QUARTAL,
        // Jahreswerte der Weltbank und des UNDP, Big-Mac-Index halbjaehrlich
        "globus/waehrung.json" to STANDARD,
        "globus/bigmac.json" to HALBJAHR,
        "globus/ranglisten.json" to STANDARD,
        "indikatoren/rezession_nyfed.json" to STANDARD,
        // Archiv der CFTC: beim Nutzer kommen die neuen Wochen dazu, die Datei selbst genuegt einmal im Jahr
        "indikatoren/cot_archiv.json" to STANDARD,
        "indikatoren/krypto_fng.json" to STANDARD,
        // Mittelwerte ueber Jahrzehnte: ein weiteres Jahr aendert wenig
        "indikatoren/kursmuster.json" to REKORD,
        // Werte an frueheren Hochs und Tiefs: neu erst mit dem naechsten grossen Hoch oder Boden
        "indikatoren/zeichen_rueckblick.json" to REKORD,
        "welt/oel.json" to MONATLICH,
        // Jahreswerte der EIA je Land und Fackelschaetzungen der EOG: ein weiteres Jahr kommt einmal im Jahr dazu
        "welt/energie.json" to JAEHRLICH,
        // Monatsmeldungen der Zentralbanken an den IWF, Verzug von ein bis zwei Monaten
        "welt/gold.json" to MONATLICH,
        // Jahreswerte der Weltbank zu Staatseinnahmen, Zinsausgaben und Reserven
        "globus/schulden.json" to STANDARD,
        // Monatswerte seit 1913: ein weiteres Jahr aendert die lange Sicht wenig
        "indikatoren/lange_reihen.json" to STANDARD,
        // Abgeschlossene Vergangenheit (Bitcoin bis 2014): aendert sich nur bei Berichtigungen der Quelle
        "indikatoren/btc_frueh.json" to REKORD,
        // Die Quelle legt jedes Fruehjahr eine Tabelle mit einem weiteren Jahr vor
        "indikatoren/lebensrenditen.json" to STANDARD,
        // Jahrhundertreihen der Notenbanken, selten ergaenzt
        "globus/langreihen.json" to REKORD,
        // Jahresberichte der Fonds (Form N-CEN): ein weiteres Jahr kommt im Fruehjahr dazu
        "etf-abstand.json" to STANDARD,
        // Monatsreihe der BIS: neuere Werte holt die App je Land selbst nach
        "globus/leitzins.json" to STANDARD,
        // Projektionen der Fed viermal im Jahr; eine neue Sitzung holt die App selbst nach
        "indikatoren/fed_punkte.json" to QUARTAL,
        "netz/kanten.json" to NETZ,
        // Jahresberichte erscheinen einmal im Jahr; die Datei wird mit den neuen Berichten erneuert
        "netz/zulieferer.json" to NETZ,
        // Monatsreihen mit Verzug von ein bis drei Monaten; die Quelle erscheint monatlich, die Reihe bleibt mit Stand sichtbar
        "indikatoren/gpr.json" to MONATSREIHE,
        "indikatoren/epu.json" to MONATSREIHE,
        // Branchen-KGV und Risikopraemie erscheinen einmal im Jahr, Anfang Januar
        "welt/damodaran.json" to JAEHRLICH,
        // Jahresreihe der UCDP mit Verzug von drei Monaten, neue Fassung im Fruehjahr
        "globus/konflikte-reihe.json" to KONFLIKTE,
    )

    fun profilFuer(datei: String): Profil = DATEIEN[datei] ?: STANDARD

    // Alle Dateien mit eigenem Profil (der Test fordert, dass jede gepflegte Datei hier steht).
    fun dateien(): Set<String> = DATEIEN.keys

    // Tage seit 1970 zu JJJJ-MM-TT oder JJJJ-MM (Monatsanfang); null, wenn nichts passt.
    fun tag(stand: String): Long? {
        val m = Regex("^(\\d{4})-(\\d{2})(?:-(\\d{2}))?$").find(stand.trim()) ?: return null
        val j = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val t = m.groupValues[3].ifEmpty { "1" }.toInt()
        if (mo !in 1..12 || t !in 1..31) return null
        // Gueltiges Datum? (zum Beispiel nicht der 31. Februar)
        return runCatching { java.time.LocalDate.of(j, mo, t).toEpochDay() }.getOrNull()
    }

    // Volle Monate zwischen Stand und jetzt (Millisekunden); 0 bei unlesbarem Stand oder Stand in der Zukunft.
    fun monate(stand: String, jetzt: Long): Int {
        val tage = tag(stand) ?: return 0
        val s = java.time.LocalDate.ofEpochDay(tage)
        val h = java.time.LocalDate.ofEpochDay(Math.floorDiv(jetzt, 86_400_000L))
        val m = (h.year - s.year) * 12 + (h.monthValue - s.monthValue) - if (h.dayOfMonth < s.dayOfMonth) 1 else 0
        return m.coerceAtLeast(0)
    }

    // Zustand eines Inhalts mit Stand. Ohne lesbaren Stand gilt der Inhalt als VERALTET, wenn das Profil nicht
    // KENNZEICHNEN ohne zweite Schwelle ist: Was sein Alter nicht verraet, wird nicht als aktuell gezeigt.
    fun zustand(stand: String, profil: Profil, jetzt: Long): Zustand {
        if (tag(stand) == null) return Zustand.ALT
        val m = monate(stand, jetzt)
        return when {
            profil.ausblendenMonate > 0 && m >= profil.ausblendenMonate -> Zustand.VERALTET
            m >= profil.warnenMonate -> Zustand.ALT
            else -> Zustand.FRISCH
        }
    }

    // Soll der Inhalt ganz wegfallen (VERALTET und Profil blendet aus)?
    fun ausgeblendet(stand: String, profil: Profil, jetzt: Long): Boolean =
        profil.danach == Danach.AUSBLENDEN && zustand(stand, profil, jetzt) == Zustand.VERALTET

    // Marke "11/2026" aus einem Stand (leer bei unlesbarem Stand)
    fun marke(stand: String): String {
        val t = tag(stand) ?: return ""
        val d = java.time.LocalDate.ofEpochDay(t)
        return d.monthValue.toString().padStart(2, '0') + "/" + d.year
    }
}
