package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Eigentuemer und Rechte (Struktur): Datenmodell. Halter stehen oben im Organigramm, darunter die Gattungen der Firma.
// Alles, was nicht gelesen wurde, ist null und erscheint als "nicht geprueft", nie als Vermutung.
object Struktur {

    // Art eines Halters (Zeichenkette, weil das Modell gespeichert und aus dem gepflegten Buendel gelesen wird)
    object Art {
        const val PERSON = "PERSON"
        const val FAMILIE = "FAMILIE"
        const val STIFTUNG = "STIFTUNG"
        const val HOLDING = "HOLDING"
        const val STAAT = "STAAT"
        const val FONDS = "FONDS"
        const val INSTITUTIONEN = "INSTITUTIONEN"
        const val STREUBESITZ = "STREUBESITZ"
        const val GRUPPE = "GRUPPE"
        const val MUTTER = "MUTTER"
        const val TRUST = "TRUST"
    }

    // Art einer Gattung
    object GArt {
        const val STAMM = "STAMM"
        const val VORZUG = "VORZUG"
        const val ADS = "ADS"
        const val GENUSS = "GENUSS"
        const val ZERTIFIKAT = "ZERTIFIKAT"
    }

    // Hinweise aus dem Text der Unterlagen (Hinweis, keine Zahl)
    object Flag {
        const val ADR = "ADR"
        const val VIE = "VIE"
        const val MEHRSTIMMEN = "MEHRSTIMMEN"
        const val OHNE_STIMME = "OHNE_STIMME"
        const val GOLDEN = "GOLDEN"
        const val DE_VORZUG = "DE_VORZUG"
        // Der Normalfall "eine Stimme je Aktie" steht im Dokument (Satz zum Stimmrecht gelesen)
        const val EINE_STIMME = "EINE_STIMME"
    }

    @Serializable
    data class Text(val de: String, val en: String) {
        fun je(deutsch: Boolean): String = if (deutsch) de else en
    }

    // Beleg mit Link; stand JJJJ-MM-TT des Dokuments
    @Serializable
    data class Beleg(val name: String, val url: String? = null, val stand: String = "")

    // kapital: Prozent aller Anteile; stimmen: Prozent aller Stimmen; klasse: Prozent der Gattung (wenn so gemeldet).
    // Fehlende Prozente rechnet die Anzeige aus klasse, soweit die Aktienzahlen bekannt sind.
    @Serializable
    data class Halter(
        val name: String,
        val art: String,
        val kapital: Double? = null,
        val stimmen: Double? = null,
        val gattung: String? = null,
        val klasse: Double? = null,
        // Prozent je Gattung (Kennung zu Prozent), wenn die Tabelle mehrere Klassen nennt
        val klassen: Map<String, Double> = emptyMap(),
        val notiz: Text? = null,
        val aktivist: Boolean = false,
        val beleg: Int = -1,
    )

    // Absichten eines Aktivisten (Schluesselwortregeln auf Item 4 des Schedule 13D, siehe StrukturRegeln.absicht)
    object Absicht {
        const val BOARD = "BOARD"
        const val VERKAUF = "VERKAUF"
        const val ABSPALTUNG = "ABSPALTUNG"
        const val RUECKKAUF = "RUECKKAUF"
        const val STRATEGIE = "STRATEGIE"
        const val BETEILIGUNG = "BETEILIGUNG"
    }

    // Eine Meldung (13D oder 13D/A) eines Aktivisten: Tag des Ereignisses, Anteil an der Klasse, Absichten, Beleg (Index in belege)
    @Serializable
    data class Verlauf(val tag: String, val prozent: Double? = null, val absicht: List<String> = emptyList(), val beleg: Int = -1)

    // Aktivist: juengste Meldung oben, Aenderungen als Verlauf (neueste zuerst)
    @Serializable
    data class Aktivist(val name: String, val aktuell: Verlauf, val verlauf: List<Verlauf> = emptyList())

    object BArt {
        const val WAHL = "WAHL"
        const val VERGUETUNG = "VERGUETUNG"
        const val PRUEFER = "PRUEFER"
        const val ANTRAG = "ANTRAG"
        const val SONST = "SONST"
    }

    // Ein Beschluss der Hauptversammlung (8-K Item 5.07): Stimmen in Stueck. Zustimmung = fuer gegen fuer plus gegen.
    @Serializable
    data class Beschluss(val titel: String, val art: String, val fuer: Double, val gegen: Double, val enthaltung: Double = 0.0, val broker: Double = 0.0) {
        val zustimmung: Double? get() = (fuer + gegen).takeIf { it > 0 }?.let { fuer * 100.0 / it }
    }

    @Serializable
    data class Versammlung(val tag: String, val beschluesse: List<Beschluss>, val beleg: Int = -1)

    // stimmen: Stimmen je Aktie (0 = keine, null = nicht geprueft). gewicht: wirtschaftlicher Wert je Aktie gegen die
    // Hauptgattung (Berkshire B: 1/1500). wirtschaftlich false: kein Anspruch auf Gewinn (Gruenderklassen, Zertifikate, ADS).
    // stimmenAnteil und kapitalAnteil setzt nur das gepflegte Buendel (feste Quoten); sonst rechnet die Anzeige.
    // ads: Aktien je ADS bei Hinterlegungsscheinen, basis: Kennung der zugrunde liegenden Gattung.
    @Serializable
    data class Gattung(
        val id: String,
        val titel: String,
        val kuerzel: String = "",
        val symbole: List<String> = emptyList(),
        val isin: String = "",
        val art: String = GArt.STAMM,
        val stimmen: Double? = null,
        val aktien: Double? = null,
        val gewicht: Double = 1.0,
        val wirtschaftlich: Boolean = true,
        val boerse: Boolean = true,
        val stimmenAnteil: Double? = null,
        val kapitalAnteil: Double? = null,
        val ads: Double? = null,
        val basis: String? = null,
        val dividende: Text? = null,
        val board: Text? = null,
        val notizen: List<Text> = emptyList(),
        val beleg: Int = -1,
    )

    @Serializable
    data class Ergebnis(
        val firma: String = "",
        val gattungen: List<Gattung> = emptyList(),
        val halter: List<Halter> = emptyList(),
        val belege: List<Beleg> = emptyList(),
        val flags: List<String> = emptyList(),
        // Name der Konzernmutter laut GLEIF, leer ohne
        val mutter: String = "",
        val notizen: List<Text> = emptyList(),
        // Stand der Angaben (JJJJ-MM-TT), leer unbekannt
        val stand: String = "",
        // Gepflegter Eintrag, dessen Ablauf ueberschritten ist
        val veraltet: Boolean = false,
        val gepflegt: Boolean = false,
        val versammlungen: List<Versammlung> = emptyList(),
        val aktivisten: List<Aktivist> = emptyList(),
        // Quellen, die fuer diese Aktie geprueft wurden (Schluessel, Anzeige als Abdeckungszeile)
        val geprueft: List<String> = emptyList(),
    ) {
        val leer: Boolean get() = gattungen.isEmpty() && halter.isEmpty() && mutter.isBlank() && notizen.isEmpty() && versammlungen.isEmpty() && aktivisten.isEmpty()
    }

    // Gepflegter Eintrag fuer Faelle ohne freie Maschinenquelle (assets/struktur/sonderfaelle.json)
    @Serializable
    data class Sonderfall(
        val id: String,
        val symbole: List<String> = emptyList(),
        val isins: List<String> = emptyList(),
        val ciks: List<String> = emptyList(),
        val stand: String = "",
        val ablauf: String = "",
        val eintrag: Ergebnis = Ergebnis(),
    )

    @Serializable
    data class Buendel(val version: Int = 1, val eintraege: List<Sonderfall> = emptyList())

    // Gespeicherter Stand je Wert auf dem Geraet
    @Serializable
    data class Eintrag(val abgerufen: Long = 0L, val cik: String = "", val ergebnis: Ergebnis? = null)

    @Serializable
    data class Datei(val werte: Map<String, Eintrag> = emptyMap())

    internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    fun buendelLesen(text: String): Buendel? = runCatching { json.decodeFromString(Buendel.serializer(), text) }.getOrNull()

    fun dateiLesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun dateiSchreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)
}
