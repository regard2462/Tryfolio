package de.troetenrepublik.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Metric
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.data.WatchMetric
import java.time.DayOfWeek
import java.util.Locale

// Sprachschicht: jede sichtbare Zeichenkette hat hier einen Schluessel, De und En liefern die Texte.
// I18n.lang ist ein Compose-Zustand, damit sich die Oberflaeche beim Sprachwechsel neu zeichnet.
// Code ausserhalb von Compose wie Mitteilungen und Buchungstexte liest dieselbe Quelle.
object I18n {
    var lang: Lang by mutableStateOf(Lang.DE)
    val strings: Strings get() = if (lang == Lang.DE) De else En
    val locale: Locale get() = if (lang == Lang.DE) Locale.GERMANY else Locale.UK
}

// Kurzform fuer die Oberflaeche, etwa S.deposit.
val S: Strings get() = I18n.strings

interface Strings {
    val locale: Locale

    // Allgemein
    val appName: String
    val back: String
    val cancel: String
    val save: String
    val ok: String
    val next: String
    val done: String
    val delete: String
    val close: String
    val apply: String
    val change: String
    val buy: String
    val sell: String
    val search: String
    val transfer: String
    val invest: String
    val all: String
    val none: String
    val today: String
    val tomorrow: String
    val pieces: String
    val perAnnum: String
    val openEnd: String
    val loading: String
    val open: String
    val closed: String
    val no: String
    val more: String
    val less: String
    val copied: String
    val error: String

    // Zeitraeume und Kennzahlen
    fun rangeLabel(r: Range): String
    fun rangeCaption(r: Range): String
    // Widgets
    val widgetWerte: String
    val widgetGesperrt: String
    val widgetLeer: String
    fun widgetStand(zeit: String): String
    val widgetsTitel: String
    val widgetsText: String
    val widgetsAuswahl: String
    val widgetsNurAndroid: String
    val screenshotsTitel: String
    fun screenshotsOption(modus: String): String
    fun screenshotsHinweis(modus: String): String
    val widgetBestand: String
    val widgetMover: String
    val widgetDividenden: String
    val widgetsAkku: String
    val widgetsAkkuOeffnen: String
    val widgetKeinePositionen: String
    val widgetMoverRuhig: String
    val widgetDivKeinBestand: String
    val widgetDivKeine: String
    fun widgetDivErhalten(jahr: Int): String
    val widgetDivErwartet: String
    // Kategorie-Widgets (0.73)
    val widgetGrundDafuer: String
    val widgetGrundDagegen: String
    val widgetFinderLeer: String
    val widgetCopyLeer: String
    val widgetOrdersLeer: String
    // Seit 0.77: Widgets zu Unterseiten und Werkzeugen
    val widgetBlasenTitel: String
    fun widgetBlasenStufe(stufe: Int): String
    fun widgetBlasenRot(rot: Int, gesamt: Int): String
    val widgetTermineTitel: String
    val widgetTermineLeer: String
    fun widgetFwHaupt(rot: Int, gelb: Int, n: Int): String
    fun widgetPhase(art: String, korrektur: Boolean): String
    fun widgetLageAuffaellig(n: Int): String
    val widgetEinJahr: String
    val widgetZinsfuss: String
    val widgetStufeKritisch: String
    val widgetStufeAchtung: String
    val widgetCheckGruen: String
    // Seit 0.78: Widgets zu Themenkoerben, Sektoren, Kongress-Flow, Stillhalter, Signalen und Volatilitaet
    val widgetKoerbePlus: String
    val widgetSektorenPlus: String
    fun widgetFlussZaehlung(kaeufe: Int, verkaeufe: Int): String
    val widgetFlussNetto: String
    val widgetFlussLeer: String
    val widgetStillhalterLeer: String
    val widgetSignaleLeer: String
    val widgetVolaLeer: String
    val widgetWertWaehlen: String
    val widgetGroessteAbweichung: String
    val widgetPlaeneLeer: String
    val widgetWettenLeer: String
    val widgetCopyNiemand: String
    val widgetCopyRegierung: String
    val widgetSeite: String
    val widgetAktuellesUniversum: String
    val widgetDurchbruchLeer: String
    val widgetRendite: String
    val widgetRueckgang: String
    val widgetWissenFertig: String
    fun widgetWissenMinuten(n: Int): String
    fun widgetWissenModul(nr: Int, titel: String): String
    fun widgetWissenStand(fertig: Int, gesamt: Int): String
    fun widgetManuell(name: String): String
    val widgetsHinzufuegen: String
    val widgetEinzelName: String
    val widgetEinstellung: String
    val widgetQuelle: String
    val widgetQuelleWatchlist: String
    val widgetQuelleDepot: String
    val widgetEinzelWahl: String
    val widgetEinstellungGesperrt: String
    val widgetEinstellungLeer: String
    // Widget-Einstellungen und neue Widgets (seit 5.4)
    val widgetZeitraum: String
    val widgetZeitJahr: String
    val widgetZeitKauf: String
    val widgetNurProzent: String
    val widgetUntertitel: String
    val widgetAnzeige: String
    val widgetBetrag: String
    val widgetBarometer: String
    val widgetAlle: String
    val widgetGlobusEinfaerbung: String
    val widgetGlobusMitte: String
    val widgetMitteEuropa: String
    val widgetMitteAmerika: String
    val widgetMitteAsien: String
    val widgetQuelleListe: String
    val widgetListenName: String
    val widgetListeWahl: String
    val widgetSortiert: String
    val widgetDarstellung: String
    val widgetGrund: String
    val widgetGrundVoll: String
    val widgetGrundHalb: String
    val widgetGrundLeer: String
    val widgetSchrift: String
    val widgetSchriftHell: String
    val widgetSchriftDunkel: String
    val widgetStimmung: String
    val widgetNewsTitel: String
    val widgetNewsKeine: String
    val sperreTitel: String
    fun sperreStatus(art: String?): String
    val sperreText: String
    val sperreHinweisPin: String
    val sperreVergessenHinweis: String
    val sperrePinFestlegen: String
    val sperrePasswortFestlegen: String
    val sperreAendern: String
    val sperreAusschalten: String
    val sperreAlt: String
    fun sperreNeu(pin: Boolean): String
    val sperreWiederholen: String
    val sperreUngleich: String
    fun sperreZuKurz(pin: Boolean): String
    val sperreEingerichtet: String
    val sperreGeaendert: String
    val sperreAusgeschaltet: String
    val sperreGesperrt: String
    val sperreEntsperren: String
    fun sperreFalsch(versuch: Int): String
    fun sperreWarten(sekunden: Long): String
    val sperreVergessen: String
    val sperreSchreibFehler: String
    val sperreZuruecksetzenTitel: String
    val sperreZuruecksetzenText: String
    val sperreZuruecksetzen: String
    val sperreDatenFehlen: String
    val sperreNurApp: String
    val sperreSpeichern: String
    val ppExport: String                             // Export fuer Portfolio Performance
    fun ppExportSub(n: Int): String
    val ppHinweis: String
    val ppSeite: String
    fun ppFertig(n: Int): String
    val chartProfi: String
    fun chartArt(art: String): String
    val chartHandelsplatzSuche: String
    val chartHandelsplatzLeer: String
    val chartVergleich: String
    val chartVergleichTitel: String
    val chartVergleichSuche: String
    val chartVergleichVoll: String
    val chartIndikatorenKurs: String
    val chartIndikatorenUnten: String
    fun chartIndikatorName(key: String): String
    fun chartIndikatorInfo(key: String): String
    fun chartWerkzeug(key: String): String
    fun chartAllesLoeschen(name: String): String
    val chartKeineDaten: String
    val heatmap: String
    val heatmapDepot: String
    val heatmapMarkt: String
    val heatmapBranchen: String
    val heatmapRegionen: String
    fun heatmapSteigen(n: Int): String
    fun heatmapFallen(n: Int): String
    val heatmapLeer: String
    val globusTitel: String
    val globusGesamt: String // [Globus Gesamtbild 0.70]
    val ggTitel: String
    fun ggPunkteVon(punkte: Int): String
    fun ggPlatz(platz: Int, von: Int): String
    fun ggBereich(b: de.troetenrepublik.app.engine.GlobusGesamt.Bereich): String
    fun ggZutat(z: de.troetenrepublik.app.engine.GlobusGesamt.Zutat): String
    fun ggDesBip(wert: String): String
    val ggInfo: String
    val globusBoerse: String
    val globusWachstum: String
    val globusAusblick: String
    val globusInflation: String
    val globusKeineDaten: String
    val globusFehlt: String
    val globusFehltTeil: String
    val globusNeuLaden: String
    val boerseMoskau: String
    fun globusWachstumJahr(jahr: Int): String
    fun globusInflationJahr(jahr: Int): String
    val globusArbeitslos: String
    val globusSchulden: String
    val globusLeistung: String
    fun globusHinweis(art: de.troetenrepublik.app.engine.Imf.Art, a: String, b: String): String
    val globusWelt: String
    // [Globus Gesten 0.70] Weltwirtschaft: Balken, Treiber, Anteile, Aufsteiger
    val wwBalkenTitel: String
    val wwPrognose: String
    fun wwSchnittLabel(bis: Int): String
    fun wwGesamtLabel(bis: Int): String
    fun wwSchnittInfo(von: Int, bis: Int): String
    fun wwGesamtInfo(von: Int, bis: Int): String
    val wwTreiberTitel: String
    val wwTreiber: List<String>
    val wwQuelle: String
    // [0.73] Stand des festen IWF-Texts, gezeigt unter dessen Ueberschrift
    val wwStand: String
    val wwTragenTitel: String
    fun wwTragenInfo(von: Int, bis: Int, welt: String): String
    val wwUebrige: String
    fun wwBeschleunigt(raum: String, nun: String, frueher: String): String
    fun wwSchwaecht(raum: String, nun: String, frueher: String): String
    val wwAufsteigerTitel: String
    val wwRezTitel: String
    val wwAnteileTitel: String
    val wwAnteileInfo: String
    val wwEuroraum: String
    // [0.71] Kurzer Name eines Wirtschaftsraums der Anteilsgrafik; sonst der volle Name aus der Karte
    fun wwRaum(code: String, sonst: String): String
    fun wwVerschiebung(gewinner: String, gewinn: String, verlierer: String?, verlust: String?, bis: Int): String
    fun wwSchnellsteTitel(bis: Int): String
    fun wwPlaetzeTitel(bis: Int): String
    fun wwPlatz(von: Int, bis: Int): String
    val globusAbschwaecher: String
    val globusRisiken: String
    fun globusHoheInflation(n: Int, namen: String): String
    fun globusSchuldenSchwach(n: Int, namen: String): String
    val globusFruehwarn: String
    // [Globus Wirtschaft]
    val globusWirtschaft: String
    val globusStruktur: String
    val globusMarkt: String
    val globusEtf: String
    val globusEtfHinweis: String
    val globusHeute: String
    val globusEinJahr: String
    fun globusMio(zahl: String): String
    fun globusVorjahr(jahr: Int, wert: String): String
    fun globusKurs(code: String, kurs: String): String
    fun globusWaehrungJahrLabel(code: String): String
    fun globusWirtLabel(k: de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl): String
    fun globusWirtInfo(k: de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl): String
    // [Globus Trend und Ebenen]
    val globusPhase: String
    fun globusIndexEtf(name: String): String
    val globusLegBoerseHeute: String
    val globusBoerseZeit: String
    val globusDepot: String
    fun globusJahr(jahr: Int, prognose: Boolean): String
    val globusJahrRegler: String
    fun globusJahrZurueck(jahr: Int): String
    fun trendPhase(p: de.troetenrepublik.app.engine.Fruehindikator.Phase): String
    val globusOffen: String
    val globusGeschlossen: String
    val globusLegArbeitslos: String
    val globusLegSchulden: String
    val globusLegLeistung: String
    val globusLegPhase: String
    val globusLegBoerse: String
    val globusLegDepot: String
    fun globusDepotNichtZuordenbar(anteil: String): String
    val globusDepotLeer: String
    fun globusKlumpen(land: String, anteil: String): String
    // [Globus Depot 0.70]
    val globusDepotEtfs: String
    val globusDepotEtfsInfo: String
    val globusBilanzTitel: String
    val globusBilanzLaender: String
    val globusBilanzGroesstes: String
    val globusBilanzHeim: String
    val globusBilanzIndustrie: String
    val globusBilanzSchwellen: String
    val globusBilanzKonz: String
    val globusBilanzWelt: String
    fun globusBilanzZugeordnet(anteil: String): String
    fun globusBilanzRegion(r: de.troetenrepublik.app.engine.DepotStreuung.Region): String
    fun globusBilanzStufe(k: de.troetenrepublik.app.engine.DepotStreuung.Konzentration): String
    fun globusBilanzEffektiv(n: String): String
    val globusHerkunftTitel: String
    val globusHerkunftInfo: String
    fun globusHerkunft(sitz: String, taetig: String, boerse: String?): String
    val globusAlleAktien: String
    val kursquelleAntwortetNicht: String
    fun globusWeltweit(zentrale: String): String
    val globusWenigerAktien: String
    fun globusLandFuer(name: String): String
    val globusLandAutomatisch: String
    val globusLandGewaehlt: String
    val globusLandUnbekannt: String
    fun globusRangliste(ebene: String): String
    val globusHoechste: String
    val globusNiedrigste: String
    val globusRangGesamt: String
    val globusRangGesamtInfo: String
    val globusRangGebiete: String
    fun globusRangAlle(n: Int): String
    val rezTitel: String
    val rezTitelWachstum: String
    val rezAbschwung: String
    fun rezAbschwungWert(n: Int, von: Int, anteil: String): String
    val rezZins: String
    val rezSahm: String
    val rezWahrsch: String
    val rezInfoWachstum: String
    val rezInfoAbschwung: String
    val rezInfoZins: String
    val rezInfoWahrsch: String
    val trendTitel: String
    val trendFrueh: String
    val trendPhaseLabel: String
    val trendFruehInfo: String
    val trendPhaseInfo: String
    val trendEsi: String
    val trendEsiInfo: String
    val trendBoerse: String
    fun trendOffenBis(zeit: String): String
    fun trendOeffnet(zeit: String): String
    val trendVerkuerzt: String
    fun trendBoerseInfo(name: String, zeiten: String, zone: String): String
    val trendVergleichen: String
    val trendLandWaehlen: String
    val trendSuche: String
    val trendVergleich: String
    fun trendPhaseCli(monat: String): String
    fun trendPhaseImf(jahr: Int): String
    val rezPhaseWelt: String
    fun rezPhaseWeltWert(n: Int, von: Int, anteil: String, bip: String?): String
    val rezInfoPhaseWelt: String
    // [Globus Risiken]
    val rkRegierung: String
    fun rkWgiName(k: String): String
    fun rkWgiInfo(k: String): String
    val rkReise: String
    val rkUsLabel: String
    fun rkUsStufe(n: Int): String
    val rkUsInfo: String
    val rkAaLabel: String
    val rkAaLink: String
    val rkAaWarnung: String
    val rkAaTeil: String
    val rkSanktionen: String
    val rkEuLabel: String
    fun rkRegimeAnzahl(n: Int): String
    fun rkEuInfo(regime: List<String>, stand: String): String
    val rkUsProgrammLabel: String
    fun rkProgrammeAnzahl(n: Int): String
    fun rkUsProgrammInfo(programme: List<String>, stand: String): String
    val rkOsLabel: String
    val rkOsInfo: String
    val rkRatings: String
    fun rkAusblick(a: String): String
    val rkRatingInfo: String
    val rkKonflikte: String
    fun rkKonfliktArt(stufe: Int): String
    fun rkKonfliktInfo(stufe: Int, jahr: Int): String
    val rkKatastrophen: String
    fun rkKatArt(typ: String): String
    fun rkKatStufe(rot: Boolean, von: String): String
    val rkKatInfo: String
    val rkWahlen: String
    val rkWahlParlament: String
    val rkWahlPraesident: String
    val rkWahlBeide: String
    val rkWahlInfo: String
    val rkStruktur: String
    fun rkStrukturLabel(art: de.troetenrepublik.app.engine.Laenderrisiken.Art): String
    fun rkStrukturWert(art: de.troetenrepublik.app.engine.Laenderrisiken.Art, zahl: String): String
    fun rkStrukturInfo(art: de.troetenrepublik.app.engine.Laenderrisiken.Art, extra: String?): String
    fun rkRohstoffAufteilung(brennstoff: String, erze: String): String
    val lsTitel: String
    val lsProgramme: String
    val lsKeinProgramm: String
    fun lsProgramm(name: String, jahr: Int): String
    // [Globus Unternehmen]
    // [Laenderblatt 0.70]
    val lbKopfArbeitslos: String
    val lbKopfSchulden: String
    val lbKopfBipKopf: String
    val lbKopfEinwohner: String
    val lbKopfLeben: String
    val lbKopfWaehrung: String
    fun lbKopfZumEuro(code: String): String
    val lbKopfLeitzins: String
    fun lbMrd(zahl: String): String
    fun lbJahreKurz(zahl: String): String
    val lbVerlaufWachstum: String
    val lbVerlaufInflation: String
    val lbPrognose: String
    val lbUebrige: String
    val lbTreemapHinweis: String
    val lbRezLaender: String
    fun lbAnteilVon(n: Int, von: Int, anteil: String): String
    val lbRezBip: String
    fun lbKonfliktZeile(stufe: Int, a: String, b: String): String
    val lbRisikoWeitere: String
    val lbWirtWeitere: String
    fun lbIndexLabel(name: String): String
    fun lbEtfLabel(symbol: String): String
    fun lbIndexInfo(name: String): String
    fun lbLaenderIndexLabel(name: String): String
    fun lbLaenderIndexInfo(name: String): String
    val lbStandVom: String
    val lbIndexQuelleStumm: String
    val lbBoerseLabel: String
    val lbKeineBoerse: String
    val lbKeinIndexLabel: String
    val lbKeinIndex: String
    fun lbIndexSelbstLabel(name: String): String
    fun lbIndexSelbstInfo(name: String): String
    val lbIndexSelbstOeffnen: String
    val globusIndexSelbstFertig: String
    val globusIndexSelbstLeer: String
    val saIndexTitel: String
    val saIndexText: String
    val lbWgiHinweis: String
    val globusLandTitel: String
    val globusHauptstadt: String
    val globusWaehrung: String
    val globusOberhaupt: String
    val globusRegierungschef: String
    val globusOberhauptChef: String
    val globusRegierungsform: String
    val globusMitgliedschaften: String
    fun globusFormName(code: String, bundesstaat: Boolean): String
    fun globusOrgName(code: String): String
    val globusInfoOberhaupt: String
    val globusInfoRegierungschef: String
    val globusInfoOberhauptChef: String
    val globusInfoRegierungsform: String
    val globusInfoMitgliedschaften: String
    val globusWikiTitel: String
    val globusWeiterlesen: String
    fun globusLaenderartikel(name: String): String
    val globusUnternehmenTitel: String
    val globusBoersennotiert: String
    val globusNichtBoersennotiert: String
    val globusStaatsfirma: String
    val globusFirmenOhneWerte: String
    val globusInfoGelistet: String
    val globusInfoNichtGelistet: String
    val globusFirmaBranche: String
    val globusFirmaSitz: String
    val globusFirmaBoerse: String
    val globusFirmaEigner: String
    val globusFirmaStaat: String
    val globusFirmaWert: String
    val globusFirmaMitarbeiter: String
    val globusFirmaGegruendet: String
    val globusFirmaMutter: String
    val globusFirmaWebsite: String
    val globusFirmaKeinVerlauf: String
    val globusBranchenTitel: String
    fun globusInfoBranchen(n: Int): String
    val globusWeitereFirmen: String
    val globusInfoWeitere: String
    fun globusBoersenKurse(name: String): String
    val globusSelbstHinweis: String
    fun globusSelbstFertig(n: Int): String
    val globusSelbstLeer: String
    fun globusBoersenwertGesamt(wert: String, quelle: String): String
    fun globusBoersenwertJahr(jahr: Int): String
    val kkpDollar: String
    // [Globus Mehr]
    // [Engpaesse]
    val globusEngpaesse: String
    val globusLegEngpaesse: String
    val engOhneVergleich: String
    val engListe: String
    fun engStand(datum: String): String
    fun engName(id: String): String
    fun engSuche(id: String): String
    fun engGegenVorjahr(prozent: String): String
    val engKeineDaten: String
    val engProTag: String
    val engVorjahr: String
    val engTanker: String
    val engContainer: String
    val engInfoProTag: String
    val engInfoVorjahr: String
    val engInfoSchiffsart: String
    val engVerlauf: String
    val engBedeutung: String
    val engWarenTitel: String
    fun engInfo(id: String): String
    fun engWaren(id: String): String
    val engSanktionen: String
    val engUsa: String
    fun engSanktionInfo(eu: String, us: String): String
    val engWerte: String
    val engSchlagzeilen: String
    fun engLieferZeile(name: String, prozent: String): String
    // [Grosses Ganzes 0.73]
    val abhTitel: String
    fun abhZusammen(land: Boolean, rohstoffe: Int, einfluesse: Int, politik: Boolean, empfindlichkeit: Boolean): String
    fun abhAbschnitt(key: String): String
    fun abhArt(key: String): String
    fun abhStand(monat: String): String
    val abhSitz: String
    val abhTaetigkeit: String
    val abhMutter: String
    val abhGesamtbild: String
    val abhLandrisiko: String
    fun abhVon100(punkte: String): String
    val abhKatastrophen: String
    fun abhKatastrophenWert(n: Int, rot: Int): String
    fun abhTop3(p: String): String
    val abhAnteileOffen: String
    val abhKritisch: String
    fun abhFoerderung(laender: String): String
    val abhBetroffen: String
    fun abhRichtung(richtung: Int): String
    fun abhAnstieg(richtung: Int): String
    val abhWegPraefix: String
    val abhStaerkeRoute: String
    val abhStaerkeGebiet: String
    fun abhGiltFuer(branche: String, gebiet: String?): String
    fun abhFaktor(key: String): String
    fun abhAussage(key: String): String
    fun abhEmpfHinweis(r2: String, jahre: Int): String
    val abhEmpfOhne: String
    val abhKeinBeleg: String
    val abhEmpfZuKurz: String
    val abhEmpfLabel: String
    val abhEtfErfasst: String
    fun abhEtfBetrifft(n: Int): String
    val abhWirkungUnter: String
    val abhInfo: String
    // [0.76] Kernaussagen und ruhigere Darstellung der Karte
    val abhKernTitel: String
    fun abhKernSegment(name: String, anteil: String): String
    fun abhKernRegion(name: String, anteil: String): String
    fun abhKernKunde(anteil: String): String
    fun abhKernKurs(liste: String): String
    fun abhKernMit(name: String, wert: String): String
    fun abhKernGegen(name: String, wert: String): String
    fun abhKernRohstoffe(land: String, liste: String): String
    fun abhKernKritisch(n: Int): String
    // [0.76] Rohstoffe je Unternehmen
    fun abhVerwendung(key: String): String
    fun abhWeg(key: String): String
    fun abhRohQuelle(key: String): String
    // Verwendung, die in der Branche ueblich ist (kein Wissen ueber das einzelne Unternehmen)
    fun abhBrancheUeblich(verwendung: String): String
    val abhRohSd: String
    val abhRohBericht: String
    fun abhRohWelt(jahr: Int): String
    fun abhRohWert(jahr: Int): String
    fun abhRohFirma(jahr: Int): String
    fun abhRohKapazitaet(jahr: Int): String
    val abhRohKapKopf: String
    val abhRohAnlagen: String
    fun abhRohAnlagenWert(verwertbar: Int, gesamt: Int): String
    val abhRohLand: String
    val abhRohFoerderland: String
    val abhRohMenge: String
    val abhRohAnteil: String
    val abhRohWertKopf: String
    val abhRohUebrige: String
    fun abhRohEinheit(key: String): String
    fun abhKernRohstoffeFirma(land: String, liste: String): String
    fun abhKernFoerdert(liste: String): String
    fun abhKernErzeugt(liste: String): String
    fun abhKernLizenz(liste: String): String
    val abhRohGenannt: String
    val abhRohZuordnung: String
    fun abhKernKritischFirma(n: Int): String
    val wnUeberProfil: String
    fun abhKernLand(sitz: String, land: String, stufe: String): String
    fun abhStufe(key: String): String
    fun abhKernAuffaellig(liste: String): String
    fun abhKernEinfluesse(liste: String): String
    fun abhMessung(text: String): String
    fun abhBaustein(key: String): String
    fun wsLuecke(grund: String): String
    // [Grosses Ganzes 0.74] Wovon sie lebt: Umsatz nach Segment, Region und Grosskunde aus dem Jahresbericht
    val wsTitel: String
    fun wsGruppe(key: String): String
    fun wsStand(jahr: Int): String
    val wsUebrige: String
    fun wsKunde(nr: String): String
    fun wsKundenGruppe(n: String): String
    val wsSitzland: String
    val wsNichtUsa: String
    fun wsGebiet(name: String): String
    val wsLaedt: String
    val wsFehler: String
    val wsZuGross: String
    val wsNichtLesbar: String
    val wsNichtEindeutig: String
    val wsNichtVerfuegbar: String
    val wsOhneKontakt: String
    fun wsInfo(key: String): String
    val wnTitel: String
    val wnLeer: String
    fun wnBetrifft(depot: Int, watch: Int, anteil: String): String
    val wnWirkung: String
    val wnLaender: String
    val wnBranchen: String
    val wnDepot: String
    val wnWatchlist: String
    val wnBelege: String
    val wnOhneTreffer: String
    val wnUeberBranche: String
    val wnGenannt: String
    val wnLaedt: String
    fun wnTyp(key: String): String
    fun wnZur(key: String): String
    val wnInfo: String
    val lbTitel: String
    fun lbThema(key: String): String
    fun lbName(key: String, zusatz: String): String
    fun lbSchritte(n: String): String
    fun lbVonGesamt(n: String, gesamt: String): String
    fun lbRot(n: String, rot: String): String
    fun lbVorjahr(wert: String): String
    fun lbErhoehtAb(wert: String): String
    fun lbVorher(wert: String): String
    fun lbStand(datum: String): String
    val lbOffen: String
    val lbSeit: String
    fun lbKeineAenderung(datum: String): String
    fun lbThemaKurz(key: String): String
    fun lbAuffaellig(n: String, gesamt: String): String
    fun lbAenderungen(n: Int): String
    fun lbTab(key: String): String
    fun lbErk(key: String, zusatz: String): String
    fun lbWeg(key: String): String
    fun lbGegen(a: String, b: String): String
    val lbUnd: String
    fun lbImMinus(n: Int): String
    fun lbAbSchwelle(n: Int, schwelle: String): String
    fun lbGestoert(n: Int): String
    val mtWelt: String
    val mtWeltHinweis: String
    fun mtWeltText(was: String, werte: String): String
    fun mtWeltEngpass(name: String, prozent: String): String
    fun mtWeltSpannung(name: String, faktor: String): String
    fun mtWeltWerte(namen: List<String>, rest: Int): String
    val weltlageTitel: String
    val ensoTitel: String
    val ensoStatus: String
    val ensoIndex: String
    val ensoStaerke: String
    fun ensoZustand(z: String): String
    fun ensoStaerkeText(n: Int): String
    val ensoStatusInfo: String
    val ensoIndexInfo: String
    val ensoStaerkeInfo: String
    val ensoFolgenNino: String
    val ensoFolgenNinoInfo: String
    val ensoFolgenNina: String
    val ensoFolgenNinaInfo: String
    val spTitel: String
    val spErklaerung: String
    fun spName(id: String): String
    fun spSuche(id: String): String
    fun spGegenDavor(faktor: String): String
    val spFaktor: String
    val spFaktorInfo: String
    val spArtikel: String
    val spArtikelInfo: String
    fun spKonflikte(jahr: Int): String
    fun spKonfliktZeile(a: String, b: String, stufe: Int): String
    fun spKonfliktAnzahl(n: Int): String
    val spKonfliktInfo: String
    // [Wirkungskette]
    val wkTitel: String
    fun wkLage(thema: String, lage: String): String
    fun wkZinsKopf(zentralbank: String, fall: String): String
    fun wkFallKopf(fall: String): String
    fun wkArt(art: String): String
    fun wkWertStatt(art: String, ist: String, referenz: String): String
    fun wkWertGleich(art: String, ist: String): String
    val wkVorwertZusatz: String
    fun wkZentralbank(region: String): String
    fun wkWaehrungName(region: String): String
    fun wkZins(folge: String, zentralbank: String): String
    fun wkRendite(r: String): String
    fun wkWaehrung(r: String, name: String): String
    fun wkBelastet(liste: String): String
    fun wkGestuetzt(liste: String): String
    fun wkSektor(sektor: String): String
    val wkUnd: String
    fun wkOhneFolge(fall: String): String
    val wkReaktion: String
    fun wkMarkt(schluessel: String): String
    fun wkUrteil(urteil: String): String
    fun wkZinsFutures(vorher: String, nachher: String): String
    fun wkBedeutung(folge: String): String
    fun wkRueckblick(kopf: String, folge: String?): String
    val wkBlattMeldungen: String
    val wkBlattHerleitung: String
    val wkBlattReaktion: String
    val wkBlattSektoren: String
    val wkBlattGrenzen: String
    val wkBlattBelege: String
    fun wkWarumZins(folge: String): String
    fun wkWarumRendite(r: String): String
    fun wkWarumWaehrung(r: String): String
    fun wkWarumSektor(sektor: String): String
    val wkGrenzen: String
    val wkReaktionErklaerung: String
    val wkBelege: String
    val wkBp: String
    val globusMehrGesellschaft: String
    val globusMehrStaat: String
    val globusMehrEnergie: String
    val globusMehrHandel: String
    val globusMehrFeiertage: String
    val globusMehrSchlagzeilen: String
    fun globusMehrLabel(k: de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl): String
    fun globusMehrInfo(k: de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl): String
    val globusMehrFlaeche: String
    val globusMehrFlaecheInfo: String
    fun globusMehrSprachen(n: Int): String
    val globusMehrSprachenInfo: String
    fun globusMehrZeitzone(n: Int): String
    val globusMehrZeitzoneInfo: String
    val globusMehrBis: String
    fun globusMehrWeitere(n: Int): String
    fun globusMehrExporte(jahr: Int): String
    val globusMehrExporteInfo: String
    val globusMehrGueter: String
    val globusMehrPartner: String
    fun globusMehrGruppe(g: de.troetenrepublik.app.engine.LaenderZusatz.Warengruppe): String
    fun globusMehrGruppeInfo(g: de.troetenrepublik.app.engine.LaenderZusatz.Warengruppe, jahr: Int): String
    fun globusMehrPartnerInfo(land: String, jahr: Int): String
    val globusMehrFeiertagInfo: String
    val globusMehrSpiegel: String
    // [Durchbrueche]
    val dbTitel: String
    val dbInfo: String
    val dbNeu: String // [Durchbrueche 0.70]
    val dbErwartet: String
    val dbEntwicklung: String
    val dbRueckblick: String
    val dbBeschleunigt: String
    val dbKeineNeuen: String
    val dbLog: String
    fun dbNeuAnzahl(n: Int): String
    val dbZuletzt: String
    fun dbZuletztDatum(jahr: Int, monat: Int, tag: Int, mitTag: Boolean): String
    val dbNichtGeladen: String
    fun dbArt(art: String): String
    fun dbBis(jahr: Int): String
    fun dbStand(jahr: Int, monat: Int): String
    fun dbRate(faellt: Boolean, monate: Int): String
    fun dbAlter(n: Long, einheit: de.troetenrepublik.app.engine.Durchbrueche.Einheit): String
    val heatmapOhneBranche: String
    val zeitraumEigener: String
    val zeitraumTitel: String
    val zeitraumStart: String
    val zeitraumEnde: String
    fun metricLabel(m: Metric): String
    fun metricShort(m: Metric): String
    fun watchMetricLabel(m: WatchMetric): String
    val metricTitle: String
    fun typeLabel(t: AssetType): String
    fun inDays(n: Long): String
    val inOneMonth: String
    fun inMonths(n: Long): String
    fun daysAgo(n: Long): String
    fun minutesAgo(n: Long): String
    fun hoursAgo(n: Long): String
    val justNow: String
    fun weekday(d: DayOfWeek): String

    // Handelszeiten
    fun closedOpensToday(time: String): String
    fun closedOpensOn(weekday: String, time: String): String
    fun kurzerHandelstag(time: String): String

    // Depotuebersicht
    val portfolio: String
    val cash: String
    val openProfile: String
    val brokerage: String
    val securities: String
    val cashAccount: String
    val total: String
    val reloadPrices: String
    val switchMetric: String
    val watchlist: String
    val savingsPlans: String
    val orders: String
    val analytics: String
    val statistics: String
    val investments: String
    val groupStocksEtfs: String
    val noWatchlist: String
    val knockedOut: String
    val available: String
    fun reservedForOrders(amount: String): String
    val deposit: String
    val withdraw: String
    val interest: String
    val transactions: String
    val noTransactions: String
    val incoming: String
    val outgoing: String
    val since: String

    // Statistik
    val year: String
    val allTime: String
    val categories: String
    fun statNode(id: String): String                 // id wie INCOME oder EXPENSES
    val netCashflow: String
    val investRate: String
    val noStatistics: String
    fun bookingsCount(n: Int): String
    val averagePerMonth: String

    // Watchlist
    fun entriesCount(n: Int): String
    val sortByName: String
    val sortByChange: String
    val sortByPrice: String
    val add: String
    fun addedOn(date: String): String
    val removeFromWatchlist: String
    val alsWidget: String
    val addToWatchlist: String
    val wllNeu: String
    val wllName: String
    val wllUmbenennen: String
    val wllLoeschenTitel: String
    fun wllLoeschenText(name: String): String
    val wllWaehlen: String

    // Suche
    val searchPlaceholder: String
    val topMovers: String
    val popular: String
    val stocks: String
    val etfs: String
    val crypto: String
    val indices: String
    val results: String
    val otherVenues: String
    val searching: String
    val searchUnavailable: String
    val noResults: String
    val isinPruefziffer: String
    val isinUnvollstaendig: String
    val clear: String

    // Wertpapierseite
    val security: String
    val yourPosition: String
    val quantity: String
    val avgPrice: String
    val value: String
    val result: String
    val information: String
    val uebersetzen: String
    val uebersetzenOriginal: String
    val uebersetzenFehler: String
    val sourceWikipedia: String
    val rohstoffTitel: String
    val etfTitel: String
    val etfTop: String
    val etfBranchen: String
    fun etfKlassen(aktien: String, anleihen: String, bar: String): String
    val etfHinweis: String
    val etfDepotTitel: String
    fun etfDepotZeile(direkt: String, ueber: String, etfs: Int): String
    val etfDepotHinweis: String
    fun rohstoffAbschnitte(schluessel: String, termin: Boolean): List<Pair<String, String>>
    val keyStats: String
    val bid: String
    val ask: String
    val spread: String
    val openPrice: String
    val dayHigh: String
    val dayLow: String
    val prevClose: String
    val high52: String
    val low52: String
    val marketCap: String
    val enterpriseValue: String
    val peRatio: String
    val forwardPe: String
    val eps: String
    val epsZwoelf: String
    val dividendYield: String
    val volume: String
    fun priceIn(ccy: String): String
    val volatility1y: String
    val beta: String
    val isin: String
    val isinCopied: String
    // Erklaerung einer Kennzahl in einfachen Worten (Tipp auf die Zeile), null fuer Zeilen ohne Erklaerung (seit 5.9)
    fun kennzahlErklaerung(label: String): String?
    val isinKopieren: String
    val kind: String
    val sector: String
    val industry: String
    val country: String
    val employees: String
    val website: String
    val exchange: String
    val priceAlerts: String
    val derivatives: String
    val news: String
    val events: String
    val financials: String
    val yearly: String
    val quarterly: String
    val insights: String
    val analysts: String
    fun fromEstimates(n: Int): String
    fun targetPrice(price: String): String
    fun toTargetLage(prozent: String, richtung: Int): String
    val revenue: String
    val ebitda: String
    val netIncome: String
    val grossProfit: String
    val operatingIncome: String
    val savingsPlan: String
    val eventEarnings: String
    val eventExDividend: String
    val eventDividendPayment: String
    val pastEvents: String
    val upcomingEvents: String
    val noEvents: String
    fun epsActualVsEstimate(actual: String, estimate: String): String
    fun epsStatt(actual: String, estimate: String): String
    val description: String
    fun recommendation(key: String): String
    val knockedOutBanner: String
    fun knockedOutSince(date: String, residual: String): String

    // Finanzen (Detail)
    val income: String
    val balance: String
    val cashflow: String
    val margins: String
    val grossMargin: String
    val operatingMargin: String
    val profitMargin: String
    val totalCash: String
    val totalDebt: String
    val freeCashflow: String
    val operatingCashflow: String
    val returnOnEquity: String
    val totalAssets: String
    val totalLiabilities: String
    val equity: String
    val revenueGrowth: String
    val earningsGrowth: String
    val payoutRatio: String
    val dividendPerShare: String
    val priceToBook: String
    val noFinancials: String
    fun reportingCurrency(ccy: String): String
    val epsHistory: String
    val estimate: String
    val actual: String

    // Nachrichten
    fun newsSource(source: String): String
    val openInBrowser: String
    val showOriginal: String
    val showText: String
    val articleLoading: String
    val articleNoText: String

    // Handelskalender
    val calToggle: String
    fun calRealizedIn(month: String): String
    val calPrevMonth: String
    val calNextMonth: String
    val calWeekdays: List<String>
    val calRealized: String
    val calTrades: String
    fun calWins(n: Int): String
    fun calLosses(n: Int): String
    fun calExpectedAmount(amount: String): String
    val calLegendReceived: String
    val calLegendExpected: String
    val calClosedTrades: String
    val calDayResult: String
    val calFees: String
    val calAfterCosts: String
    val calPositions: String
    val calLegendReview: String
    val calReviewLaedt: String
    val calReviewKeineKurse: String
    val calTradeExpiry: String
    fun calTradeSell(qty: String, price: String): String
    val calDivExpected: String
    fun calDivReceived(qty: String, perShare: String): String

    // Ordermaske
    val overview: String
    val amount: String
    val shares: String
    val everything: String
    val market: String
    val limit: String
    val stop: String
    val trailingStop: String
    val limitPrice: String
    val stopPrice: String
    val trailDistance: String
    val setValue: String
    val validity: String
    fun validityLabel(v: Validity): String
    val enterLimit: String
    val enterStop: String
    val enterTrail: String
    fun corresponds(qty: String): String
    fun correspondsAmount(amount: String): String
    val askPrice: String
    val bidPrice: String
    val inDepot: String
    val notEnoughCash: String
    val notEnoughShares: String
    val notEnoughForFee: String
    val tradeType: String
    fun tradeTypeValue(side: String, type: OrderType): String
    val piecesLabel: String
    val counterValue: String
    val estCounterValue: String
    val fee: String
    val venue: String
    val bestPrice: String
    val productCosts: String
    val derivativeRisk: String
    val cashAfter: String
    val notePlaceholder: String
    val placeOrder: String
    val orderNotExecuted: String
    val initialStop: String

    // Orders
    val noOpenOrders: String
    val doneOrders: String
    val cancelOrder: String
    fun validUntil(ts: String): String
    fun orderStatus(s: OrderStatus): String
    fun trailingInfo(pct: String, stop: String): String
    fun reason(code: String): String

    // Sparplaene
    val plan: String
    val plans: String
    fun planCount(n: Int): String
    val interval: String
    fun intervalLabel(i: PlanInterval): String
    val intervalBiweeklyDays: String
    val execution: String
    val startOfMonth: String
    val midOfMonth: String
    fun onDay(d: Int): String
    val savingsRate: String
    val executionDay: String
    val executionDays: String
    val firstExecution: String
    val paymentMethod: String
    val planNote: String
    val create: String
    val managePlan: String
    val status: String
    val paused: String
    val active: String
    val nextExecution: String
    val executions: String
    val investedSoFar: String
    val lastFailure: String
    val changeRate: String
    val changeInterval: String
    val planUhrzeit: String
    val planUhrzeitAendern: String
    val planUhrzeitAuto: String
    val planUhrzeitAutoText: String
    val planUhrzeitHinweis: String
    fun planUhrzeitWert(uhr: String): String
    val resume: String
    val pause: String
    val deletePlanTitle: String
    val deletePlanText: String
    val noPlans: String
    fun perMonthPlans(amount: String, n: Int): String
    val createPlan: String
    fun nextOn(date: String): String
    fun investedAmount(amount: String): String

    // Preisalarme
    val enterTargetPrice: String
    fun alertRises(price: String, pct: String): String
    fun alertFalls(price: String, pct: String): String
    val targetEqualsPrice: String
    val setAlert: String
    val whenRises: String
    val whenFalls: String
    fun setAt(price: String): String
    // Bullen- oder Baerenmarkt (seit 5.9)
    val mpTitel: String
    fun mpGesamt(bullen: Int, alle: Int): String
    fun mpIndex(schluessel: String): String
    fun mpPhase(art: String, korrektur: Boolean, seit: String): String
    fun mp200(wert: String): String
    fun mpDetail(art: String, vomTief: String, golden: Boolean?, kreuzSeit: String?): String
    val mpHinweis: String
    // Positionierung an den Terminmaerkten (CFTC, seit 5.8)
    val cotTitel: String
    fun cotMarkt(schluessel: String): String
    // Leerverkaufspositionen der AMF (seit 5.8)
    val lpTitel: String
    fun lpSumme(anteil: String, anzahl: Int): String
    fun lpVeraenderung(aenderung: String): String
    val lpKeine: String
    fun lpSeit(tag: String): String
    fun lpNotiz(land: String, stand: String): String
    fun lpHinweis(land: String): String
    fun lpGesamt(anteil: String): String
    // FDA (seit 5.9)
    val fdaTitel: String
    val fdaZulassungen: String
    val fdaRueckrufe: String
    fun fdaArt(art: String, vorrang: Boolean): String
    fun fdaKlasse(klasse: Int, geraet: Boolean, laufend: Boolean): String
    val fdaHinweis: String
    // Insider Schweden (seit 5.8)
    fun insiderNotiz(land: String, stand: String): String
    val euSedi: String
    val euSediLink: String
    fun euArt(art: String): String
    fun euRolle(r: String): String
    // Bull- und Bear-Case (seit 5.7)
    val bbDafuer: String
    val bbDagegen: String
    val bbHinweis: String
    val bbWaage: String
    fun bbStufe(stufe: Int): String
    fun bbWaageSatz(stufe: Int): String
    val bbGewichtKopf: Pair<String, String>
    val bbGewichte: List<Pair<String, String>>
    val bbMehrfach: String
    fun bbWarum(schluessel: String): String
    fun bbAlle(n: Int): String
    fun bbThema(thema: String): String
    fun bbKennzahl(kennzahl: String, w: List<String>): String
    // US-Insider (SEC Form 4, seit 5.6)
    val vdKaeufe1: String
    val usHinweis: String
    val usOhneKontakt: String
    fun usRolle(r: String): String
    fun usBestandDanach(stueck: String, aenderung: String?): String
    val usBestaendeTitel: String
    fun usStueck(stueck: String): String
    fun usBestaendeSumme(wert: String, personen: Int): String
    val usBestaendeHinweis: String
    val zgSecTitel: String
    val zgSecFeld: String
    val zgSecUngueltig: String
    // [0.77] Erklaer-Assistent
    val erkTitel: String
    val erkZweck: String
    val erkPunkte: List<String>
    val erkOpenai: String
    val erkAdresse: String
    val erkAdresseUngueltig: String
    val erkModell: String
    val erkModellname: String
    val erkKnopf: String
    val erkBlatt: String
    val erkNachfrage: String
    val erkSenden: String
    val erkNochmal: String
    val erkSchluessel: String
    val erkLimit: String
    val erkUeberlastet: String
    val erkNetz: String
    val erkLeer: String
    fun erkAnbieterSagt(m: String): String
    // Insider-Trend (seit 5.6)
    val itTitel: String
    fun itStufe(stufe: Int): String
    // Ein Satz zur Stufe fuer Einsteiger (seit 5.9)
    fun itSatz(stufe: Int): String
    fun itAnzahl(kaeufer: Int, verkaeufer: Int): String
    fun itRolle(rolle: Int): String
    fun itRolleZeile(rolle: String, kaeufer: Int, verkaeufer: Int): String
    val itCluster: String
    val itClusterKurz: String
    val itHinweis: String
    // Eigengeschaefte der Fuehrung und Grossaktionaere aus den Datenbanken der BaFin (seit 5.5)
    val copyExecutives: String
    val vdMeistgekauft: String
    val vdMeistverkauft: String
    val vdNeueste: String
    val vdKaeufe: String
    val vdVerkaeufe: String
    val vdLeer: String
    val vdKarteTitel: String
    val kernMeldungen: String
    fun kernKaeufe(n: Int): String
    fun kernVerkaeufe(n: Int): String
    fun kernNetto(betrag: String): String
    fun kernGross(n: Int, groesster: String): String
    fun kernHalter(anteil: String): String
    fun kernHalterUnten(insider: String, anzahl: Int): String
    fun kernKaufen(n: Int): String
    fun kernVerkaufen(n: Int): String
    fun kernHalten(n: Int): String
    fun kernTrend(prozent: String, einheit: Int): String
    fun kernTrendQuellen(n: Int): String
    fun kernPositionen(n: Int): String
    fun kernPersonen(n: Int): String
    val lpLeerverkaeufer: String
    fun trKennErw(n: Int): String
    fun trKennBeitraege(n: Int): String
    val trYahooKurz: String
    fun insStand(bis: String): String
    fun insLuecke(von: String, bis: String): String
    val grTitel: String
    val grHinweis: String
    fun vdAnzahl(kaeufe: Int, verkaeufe: Int): String
    fun vdOhneKurs(n: Int): String
    fun vdZusammenfassung(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String): String
    fun vdArt(art: String): String
    fun vdRolle(rolle: String): String
    // Kursalarme auf die Tagesbewegung und Notizen je Wert (seit 5.4)
    val alertMoveUngueltig: String
    fun notifAlertMoveText(pct: String, price: String): String
    val notiz: String
    val notizHinzufuegen: String
    val exportNotizen: String
    val notizLoeschen: String

    // Ueberweisung
    fun availableAmount(amount: String): String
    val withdrawToReference: String
    val enterAmount: String
    fun availableForWithdrawal(amount: String): String
    val moreThanAvailable: String
    val process: String
    val from: String
    val to: String
    val virtualFunds: String
    val referenceAccount: String
    val arrival: String
    val bankDays: String
    val availableAfter: String
    val feeLabel: String
    val free: String
    val send: String
    val withdrawFailed: String
    val depositBooked: String
    val withdrawalSent: String
    fun depositAvailableNow(amount: String): String
    fun withdrawalArrives(amount: String): String

    // Profil
    val yourProfile: String
    fun practiceSince(date: String): String
    val totalWealth: String
    val activity: String
    val documents: String
    val notSet: String
    val interestOnCash: String
    val interestAuto: String
    fun ecbRate(rate: String, since: String): String
    val ecbNotLoaded: String
    val customRate: String
    val enterRate: String
    val accruedThisMonth: String
    val deposited: String
    val withdrawn: String
    val taxes: String
    val taxOverview: String
    fun allowanceSet(amount: String): String
    val allowanceNotSet: String
    val hideBalances: String
    val shakeToHide: String
    val trading: String
    val spreadStocks: String
    val spreadDerivatives: String
    val spreadHint: String
    val appearance: String
    val appearanceSystem: String
    val appearanceLight: String
    val appearanceDark: String
    val appearanceSepia: String
    val logos: String
    val logosFarbig: String
    val logosSchwarzWeiss: String
    val logoForm: String
    val logoRund: String
    val logoEckig: String
    val logoHintergrund: String
    val hintergrundBehalten: String
    val hintergrundEntfernen: String
    val trennlinien: String
    val trennAn: String
    val trennAus: String
    fun beispielName(k: Int): String
    val language: String
    val languageGerman: String
    val languageEnglish: String
    val coloredChart: String
    val esDesign: String
    val esNeutral: String
    val esWichtigKategorie: String
    fun esUntertitel(design: String, logosFarbig: Boolean, rund: Boolean): String
    val notifications: String
    val refreshEvery: String
    fun everySeconds(n: Int): String
    val statusLimited: String
    val statusConnected: String
    val refreshNow: String
    val exportPortfolio: String
    val importPortfolio: String
    val aboutTitle: String
    val kaffeeTitel: String
    val aboutText: String
    val version: String
    val resetDepot: String
    val resetTitle: String
    fun resetText(amount: String): String
    val reset: String
    val personalData: String
    val firstName: String
    val exportTitle: String
    fun exportDone(kb: Int): String
    val exportFailed: String
    val importFailedTitle: String
    val importNotReadable: String
    fun importError(code: String, extra: Int): String
    val importTitle: String
    fun exportedAt(ts: String): String
    fun importSummary(cash: String, positions: Int, plans: Int, tx: Int): String
    val knowledge: String
    // Windows und Linux: Finanzwissen oeffnet im Browser.
    val knowledgeInBrowser: String
    val knowledgeOpenAgain: String
    fun knowledgeBrowserFailed(url: String): String
    val knowledgeOeffnenIn: String
    val knowledgeImBrowser: String
    val knowledgeInApp: String
    val knowledgeInAppOffen: String
    val knowledgeInAppNochmal: String
    fun knowledgeBausteinAngebot(mb: Int): String

    // Steuern
    val allowanceTitle: String
    val allowanceSingle: String
    val allowanceJoint: String
    val allowanceNotSetTitle: String
    fun allowanceUsed(used: String, year: Int, free: String): String
    val activate: String
    val manage: String
    fun taxOverviewYear(year: Int): String
    val taxRate: String
    fun taxRateValue(pct: String): String
    fun taxWerteGeschaetzt(jahr: Int, mit: Int): String
    val taxWithheld: String
    val realizedStockGains: String
    val otherIncome: String
    val lossPotStocks: String
    val lossPotGeneral: String
    val privateGains: String
    val taxNote: String
    val taxData: String
    val taxId: String
    val editTaxId: String
    val churchTax: String
    val notModelled: String
    fun taxCertificate(year: Int): String

    // Aktivitaet und Buchungen
    val filterTrade: String
    val filterIncome: String
    val noActivity: String
    fun txLabel(t: TxType): String
    val toSecurity: String
    val showDocument: String
    fun txTitle(tag: String, storedTitle: String): String
    fun txSubtitle(tag: String, via: String, qty: String, price: String, realized: String, extra: String, stored: String): String

    // Dokumente
    val taxSection: String
    val statements: String
    val settlements: String
    fun taxCertificateTitle(year: Int): String
    fun statementTitle(month: String): String
    val noBookings: String
    fun docTitle(t: TxType): String
    val docCustomer: String
    val docDate: String
    val docNumber: String
    val docTradeType: String
    val docSymbol: String
    val docExecutionPrice: String
    val docMarketValue: String
    val docRealized: String
    val docTax: String
    val docVenueValue: String
    val docDebit: String
    val docCredit: String
    val docDividendPerShare: String
    val docGross: String
    fun docUntil(date: String): String
    val docInterestGross: String
    val docRefundPerPiece: String
    val docRecipient: String
    val docOpening: String
    val docClosing: String
    val docTaxYear: String
    val docRealizedGains: String
    val docDividendsGross: String
    val docAllowanceUsed: String
    val docLossPotStocks: String
    val docLossPotGeneral: String
    val docTaxWithheld: String
    val docDisclaimer: String
    val docPracticeLine: String
    val docNoTaxEffect: String

    // Einrichtung und PIN
    fun stepOf(n: Int, total: Int): String
    val welcome: String
    val welcomeText: String
    val startCapital: String
    val startCapitalText: String
    val letsGo: String

    // Werkzeuge auf der Wertpapierseite
    val toolsTitle: String
    fun toolTitle(key: String): String
    fun toolSubtitle(key: String): String
    fun toolYears(n: Int): String
    // Nach Praepositionen mit Dativ (nach 10 Jahren); im Englischen gleich toolYears
    fun toolYearsDativ(n: Int): String
    // Kurzform fuer Zeitraum-Leisten („10J“), damit jede Wahl in eine Zeile passt
    fun toolYearsKurz(n: Int): String
    val toolMax: String
    val toolMonthly: String
    val toolLoading: String
    val toolNoData: String
    fun toolBoughtOn(date: String, price: String): String
    val toolPerYear: String
    val toolWorldEtfShort: String
    val toolNoCosts: String
    val toolRiskWorldNote: String
    val toolWorldNote: String
    val toolInvested: String
    val toolExecutions: String
    val toolAverageCost: String
    val toolPerYearMoneyWeighted: String
    val toolVolatility: String
    val toolDrawdownPeriod: String
    fun toolDrawdownRange(from: String, to: String): String
    val toolRecovered: String
    val toolNotRecovered: String
    val toolWorstDay: String
    val toolBestDay: String
    val toolUpDays: String
    val toolRiskNote: String
    val toolDividendGrowth: String
    val toolShares: String
    val toolGrowth: String
    val toolExitPe: String
    val toolRequired: String
    val toolEpsNow: String
    val toolPeNow: String
    fun toolEpsIn(n: Int): String
    fun toolPriceIn(n: Int): String
    val toolFairToday: String
    val toolCurrentPrice: String
    fun toolAbove(pct: String): String
    fun toolBelow(pct: String): String
    val toolImpliedReturn: String
    val toolFairNote: String
    val toolRiskPerTrade: String
    val toolStopDistance: String
    val toolDepotValue: String
    val toolStopPrice: String
    val toolLossPerUnit: String
    val toolMaxLoss: String
    val toolCapital: String
    fun toolShareOfDepot(pct: String): String
    val toolDepotShare: String
    val toolMoreThanCash: String
    val toolPositionNote: String
    val toolSellShare: String
    val toolSellPrice: String
    val toolProceeds: String
    val toolRealized: String
    val toolSellNote: String

    // Position importieren
    val importPosition: String
    val importIntro: String
    val importSearchHint: String
    val importDate: String
    val importPrice: String
    fun importCloseOn(date: String, price: String): String
    val importUseClose: String
    val importNoClose: String
    val importCostBasis: String
    val importValueToday: String
    val importResult: String
    val importConfirm: String
    fun importDone(qty: String, name: String): String
    val importDividendNote: String
    val transferredIn: String
    val docTransferIn: String
    val docNoCashFlow: String
    val noHistory: String

    // Analytics
    val assets: String
    val positions: String
    val breakdown: String
    val sortTotal: String
    val sortName: String
    val keyFigures: String
    val resultSinceBuy: String
    val maxDrawdown: String
    fun maxDrawdownAb(datum: String): String
    val largestPosition: String
    val assetClasses: String
    fun assetClass(key: String): String

    // Werkzeuge in Analytics
    fun ptTitle(key: String): String
    fun ptSubtitle(key: String): String
    val wzGruppeDepot: String
    val wzGruppePlanen: String
    val wzGruppeFinden: String
    val ptCheckRules: String
    val ptCheckRulesSub: String
    fun ptBenchName(key: String): String
    val ptYourDepot: String
    val ptTimeWeightedHint: String
    val ptInvested: String
    val ptDepotToday: String
    val ptDepotTodayHint: String
    fun ptWith(name: String): String
    val ptDifference: String
    val ptPerYear: String
    fun ptNoPricesBefore(name: String, date: String): String
    val ptSameMoneyNote: String
    fun ptCashNote(rate: String): String
    fun ptSingleTitle(name: String): String
    val ptNoPurchases: String
    val ptNoPositions: String
    val ptStartValue: String
    fun ptStartSecurities(v: String): String
    fun ptStartTotal(v: String): String
    val ptStartZero: String
    val ptStartEigen: String
    val ptMonthlySaving: String
    val ptHorizon: String
    val ptRaise: String
    fun ptRealTerms(infl: String): String
    val ptTarget: String
    val ptMedian: String
    val ptWeak: String
    val ptStrong: String
    val ptPaidIn: String
    val ptGrowth: String
    fun ptTargetChance(pct: String): String
    fun ptTargetReached(amount: String): String
    val ptTargetMedian: String
    fun ptTargetWhen(years: Int): String
    val ptTargetNever: String
    fun ptInYears(n: Int): String
    val ptBandLabel: String
    val ptWealthNote: String
    val ptMonthlyWithdrawal: String
    fun ptIndexed(infl: String): String
    fun ptLasts(pct: String, years: String): String
    fun ptLeftAfter(years: Int): String
    val ptUsedUpAfter: String
    val ptSafeRate: String
    val ptFourPercent: String
    fun ptPerMonth(v: String): String
    val ptPlannedTotal: String
    val ptNoStart: String
    val ptWithdrawalNote: String
    fun ptCrashName(key: String): String
    val ptReference: String
    fun ptHardestHit(name: String, pct: String): String
    fun ptRecovery(duration: String): String
    val ptNotRecovered: String
    fun ptDuration(days: Long): String
    fun ptDurationKurz(days: Long): String
    val ptCrashVerlust: String
    val ptCrashAmStaerksten: String
    val ptCrashErholung: String
    val ptCrashErholungOffen: String
    fun ptEstimated(n: Int, total: Int): String
    val ptCustomScenario: String
    val ptMarketFalls: String
    val ptEstimatedDepot: String
    val ptCrashNote: String
    val ptCrashMein: String
    val ptCrashTest: String
    val ptTestSuche: String
    val ptTestUebernehmen: String
    val ptTestLeer: String
    val ptTestBetrag: String
    val ptTestEntfernen: String
    val ptTestSumme: String

    // [0.75] Werkzeuge: Verbindung, Beta-Richtwert, fremde Depots, Positionsgroesse, Finder, Abhaengigkeiten
    val ohneVerbindung: String
    val erneutVersuchen: String
    fun ptBetaRichtwert(n: Int, total: Int): String
    fun ptVarNoteBestand(anzahl: Int, schwankung: String): String
    val ptDivBrutto: String
    val ptDivPerMonthBrutto: String
    val ptDivNoteBrutto: String
    fun toolEinStueckZuViel(jeStueck: String, erlaubt: String): String
    val afOhneAntwort: String
    val abhAbgelaufen: String
    fun abhAbgelaufenHinweis(datum: String): String
    val ptCrashInfoTitel: String
    val ptCrashInfo: String
    val ptFxTitel: String
    val ptFxInfo: String
    fun ptFxSteigt(waehrung: String): String
    fun ptFxFaellt(waehrung: String): String
    val ptFxUm: String
    val ptFxFremd: String
    fun ptFxZeile(anteil: String): String
    fun ptFxKeine(waehrung: String): String
    val szTitel: String
    val szInfo: String
    fun szName(key: String, schock: String): String
    fun szPunkte(n: Int): String
    val szEreignisse: String
    val szEigenerWert: String
    fun szEreignis(key: String): String
    fun szSpanne(a: String, b: String): String
    fun szBelegt(anteil: String): String
    val szKeinBeleg: String
    val szNichtBerechenbar: String
    fun szBetrifft(anteil: String): String
    val szKeinTreffer: String
    val szKeineBelegten: String
    val szGroessteBeitraege: String
    val szBetroffenePositionen: String
    fun szAnteil(anteil: String): String
    fun szBraucht(rohstoffe: String): String
    fun szFortschritt(erledigt: Int, gesamt: Int): String
    fun szDerivate(anteil: String): String
    fun szRichtung(richtung: Int): String
    fun szDollarAnteil(anteil: String): String
    val ptDivExpected: String
    fun ptDivAfterTax(v: String): String
    val ptDivPerMonth: String
    val ptDivYield: String
    val ptDivYieldOnCost: String
    val ptDivReceived: String
    val ptDivNext: String
    val ptDivTop: String
    fun ptDivTimes(n: Int): String
    val ptDivNone: String
    val ptDivNote: String
    val ptTop3: String
    val ptEffective: String
    fun ptEffectiveValue(n: String): String
    // [0.72] Klumpenrisiko, Laender gegen Welt-BIP, Schwankungsrisiko, Sparplan-Rueckblick
    val ptTop10: String
    val ptHhi: String
    val ptHhiHint: String
    val ptCountriesBip: String
    val ptCountriesBipNote: String
    val ptOhneLand: String
    val ptOhneBip: String
    val ptVarTitle: String
    fun ptVar(niveau: String): String
    fun ptEs(niveau: String): String
    val ptVarHint: String
    val ptEsHint: String
    fun ptVarNote(anzahl: Int, schwankung: String?): String
    val ptSparAnlage: String
    val ptSparSeit: String
    val ptSparValue: String
    fun ptSparMonths(n: Int, name: String, seit: String): String
    val ptSparIrr: String
    val ptSparLump: String
    val ptSparLumpHint: String
    fun ptSparLumpSparplan(euro: String): String
    fun ptSparLumpEinmal(euro: String): String
    val ptSparKeineDaten: String
    val ptSparNote: String
    val ptCurrencies: String
    fun ptGroup(key: String): String
    val ptCorrelation: String
    val ptAverage: String
    val ptCorrelationNote: String
    val ptEtfCosts: String
    fun ptEtfCostValue(ter: String, perYear: String): String
    val ptDiversificationNote: String
    val ptModeRebalance: String
    val ptModeBuyOnly: String
    val ptNewMoney: String
    fun ptCash(v: String): String
    val ptEqualWeights: String
    val ptCurrentWeights: String
    val katJeWert: String
    val katJeKategorie: String
    val katOhne: String
    val katEine: String
    val katTitel: String
    val katBearbeiten: String
    val katNeu: String
    val katName: String
    val katZiel: String
    val katSumme: String
    val katPositionen: String
    val ptSaveTargets: String
    val ptSavedTargets: String
    val divTitel: String
    val divGleich: String
    val divRisiko: String
    val divLaenderBip: String
    val divBranchen: String
    fun divHoechstens(prozent: Int): String
    val divLaedt: String
    val divKeineDaten: String
    val ptTargetWeights: String
    fun ptBuyAmount(v: String): String
    fun ptSellAmount(v: String): String
    val ptOnTarget: String
    val ptBuys: String
    val ptSells: String
    fun ptOrdersFees(n: Int, fee: String): String
    val ptTaxEstimate: String
    val ptDeviation: String
    val ptNeedTwo: String
    val ptOthers: String
    fun ptFromTo(a: String, b: String): String

    // Derivate
    val knockOuts: String
    val warrants: String
    val factors: String
    val call: String
    val put: String
    val long: String
    val short: String
    val strikePrice: String
    val maturity: String
    val issuer: String
    val ecbAbbrev: String
    val leverage: String
    val factor: String
    val delta: String
    val knockOut: String
    val ratio: String
    val product: String
    val allLeverages: String
    val allFactors: String
    val customLeverage: String
    val leverageOwn: String
    val noProductForFilter: String
    val noPriceForProduct: String
    fun productKind(kind: String): String        // TURBO, WARRANT oder FACTOR
    fun distance(pct: String): String
    fun resultsCount(n: Int): String
    val minStrike: String
    val maxStrike: String
    val startDateFilter: String
    val endDateFilter: String
    val strategy: String
    val productInfo: String
    val underlying: String
    val underlyingPrice: String
    val distanceToBarrier: String
    val remainingDays: String
    fun days(n: Long): String
    val impliedVol: String
    val intrinsicValue: String
    val timeValue: String
    val premium: String
    val breakEven: String
    val sensitivities: String
    val gamma: String
    val vega: String
    val theta: String
    val currency: String
    val referencePrice: String
    val riskKnockout: String
    val riskFactor: String
    val riskWarrant: String
    val modelledHistory: String
    val noFactorHistory: String
    val productUnavailable: String
    fun leverageValue(v: String): String
    val derivative: String
    val deltaFilter: String

    // Fehler und Meldungen
    fun error(code: String, extra: String): String
    fun buyDone(qty: String, price: String): String
    fun sellDone(qty: String, price: String): String
    val refreshFirst: String
    val marketClosedOrders: String
    val limitPlaced: String
    val stopPlaced: String
    val trailingPlaced: String
    val orderCancelled: String
    fun depositedToast(amount: String): String
    fun withdrawnToast(amount: String): String
    val invalidAmount: String
    val invalidPlan: String
    val planCreated: String
    val planPaused: String
    val planResumed: String
    val planChanged: String
    val planDeleted: String
    val invalidPrice: String
    val alertSet: String
    val accountReset: String
    val depotImported: String
    val watchAdded: String
    val watchRemoved: String

    // Mitteilungen
    fun notifDividendText(name: String, amount: String): String
    val notifInterest: String
    fun notifInterestText(amount: String): String
    fun notifKnockoutText(name: String): String
    val notifKnockoutSettled: String
    fun notifKnockoutSettledText(name: String, amount: String): String
    fun notifExpiryText(name: String, amount: String): String
    fun notifAlert(name: String): String
    fun notifAlertText(price: String, target: String): String
    val notifPlanFailed: String
    fun notifPlanFailedText(name: String): String
    val notifPlanDone: String
    fun notifPlanDoneText(name: String, amount: String): String
    val notifOrder: String
    fun notifOrderText(side: String, qty: String, name: String, price: String): String
    val channelTrades: String
    val channelCash: String
    val channelUpdates: String
    val profilEinstellungen: String
    val profilSicherheit: String
    val profilDaten: String
    val profilErweitert: String
    // Tagesrueckblick
    val rbTitel: String
    fun rbProfil(anzahl: Int, an: Boolean): String
    val rbSchalter: String
    val rbLeer: String
    val rbJetzt: String
    val rbJetztLaeuft: String
    val rbJetztFehler: String
    fun rbWoche(von: String, bis: String): String
    val rbWocheUeberblick: String
    val rbMarkt: String
    val rbBewegungen: String
    val rbNachrichten: String
    fun rbWahrscheinlich(titel: String, quelle: String): String
    fun rbWahrscheinlichKurz(titel: String): String
    fun rbMitMarkt(markt: String, aenderung: String): String
    val rbOhneGrund: String
    val rbKeinDepot: String
    val rbKeineBewegung: String
    val rbKeineNachrichten: String
    val rbVorlaeufig: String
    val rbVorlaeufigAlt: String
    val rbNachtraeglich: String
    val rbHinweis: String
    fun rbMitteilungTitel(datum: String): String
    fun rbWochenTitel(von: String, bis: String): String
    fun rbFazitTon(stufe: Int, woche: Boolean): String
    fun rbFazitDepot(ton: String, hoch: Boolean, prozent: String, betrag: String): String
    fun rbFazitVergleich(markt: String, punkte: String, lage: Int): String
    fun rbFazitBeweger(name: String, prozent: String): String
    fun rbFazitMarkt(markt: String, prozent: String, lage: Int, woche: Boolean): String
    fun rbFazitBranchen(beste: String, bp: String, schwaechste: String, sp: String): String
    val rbStaerkste: String
    val rbSchwaechste: String
    val rbGewinner: String
    val rbVerlierer: String
    val rbMerkliste: String
    val rbAufgeklappt: String
    val rbZugeklappt: String
    fun newsNotifMany(n: Int): String
    fun newsNotifyOption(stufe: String): String
    fun newsNotifyOptionHint(stufe: String): String
    val awTitle: String
    val awLoading: String
    val awNone: String
    fun awHorizon(tage: Int): String
    val awFrequency: String
    val awPerMonth: String
    val awBusiest: String
    val awTypical: String
    val awOverMillion: String
    val awBuyShare: String
    val awUnusual: String
    val awBeforeMoves: String
    fun dcSummary(gut: Int, von: Int): String
    fun dcTitle(regel: String): String
    fun dcValue(regel: String, anteil: String, name: String, anzahl: Int): String
    fun dcExplain(regel: String): String
    val terTeurerTitel: String
    val etfTdHinweis: String
    val etfTdLink: String
    fun terTeurerText(name: String, alt: String, neu: String): String
    fun dcEinzelwerte(anzahl: Int): String
    val dcTeuersten: String
    val dcNote: String
    fun copyPaperRead(meldungen: Int, unlesbar: Int): String
    val derivInvestorIntro: String
    val derivStructured: String
    val derivOther: String
    // Basispreis in US-Dollar; preis nur die Zahl.
    fun derivStrike(preis: String): String
    fun derivExpiry(datum: String): String
    fun derivContracts(anzahl: String): String
    val ctcDays: String
    fun ctcApprox(betrag: String): String
    fun ctcCountApprox(anzahl: String, betrag: String): String
    fun ctcReported(datum: String): String
    val ctcEmpty: String
    val ctcHint: String
    val updNotifTitle: String
    fun updNotifText(fassung: String): String
    fun updRowTitle(fassung: String): String
    fun altTitel(monate: Int): String
    val altText: String
    fun altZeile(monate: Int): String

    // Kurzanleitung
    val guideTitle: String
    // Portfolio Performance als Werkzeug
    fun ppfZeitraum(key: String): String
    val ppfTtwror: String
    fun ppfVonBis(von: String, bis: String): String
    val ppfTtwrorJahr: String
    val ppfUnterJahr: String
    val ppfIzf: String
    val ppfIzfZeitraum: String
    val ppfIzfHinweis: String
    val ppfAbsolut: String
    val ppfMaxRueckgang: String
    val ppfNurWertpapiere: String
    val ppfNurWertpapiereHinweis: String
    val ppfBilanz: String
    val ppfLetzterTag: String
    val ppfRisiko: String
    val ppfRueckgangDauer: String
    val ppfRueckgangDauerHinweis: String
    fun ppfTage(n: Long): String
    val ppfSemivolatilitaet: String
    val ppfSemivolatilitaetHinweis: String
    val ppfHoch: String
    val rdUnterwasser: String
    val rdUnterwasserInfo: String
    val rdLeerUnterwasser: String
    val rdVerteilung: String
    val rdVerteilungInfo: String
    val rdLeerVerteilung: String
    fun rdTage(n: Int): String
    fun rdKlasse(von: String, bis: String, n: Int, anteil: String): String
    fun rdKlasseBis(bis: String, n: Int, anteil: String): String
    fun rdKlasseAb(von: String, n: Int, anteil: String): String
    val rdRollierend: String
    val rdRollierendInfo: String
    val rdRoll12: String
    val rdRoll60: String
    val rdLeerRoll12: String
    val rdLeerRoll60: String
    fun ppfWertAm(datum: String): String
    val ppfEinlagen: String
    val ppfEntnahmen: String
    val ppfKursgewinne: String
    fun ppfRealisiert(betrag: String): String
    val ppfDividenden: String
    val ppfZinsen: String
    val ppfMonate: String
    val wbTitel: String
    val wbInfo: String
    val wbUnrealisiert: String
    val wbOhneWertpapier: String
    val wbIzf: String
    val wbTtwror: String
    val ppfHinweis: String
    val guideAllesTitel: String
    val guideAllesText: String
    // Was die App kann, nach Bereichen (Titel zu Text); widgets, bildschutz: gibt es auf dieser Plattform.
    fun guideFunktionen(widgets: Boolean, bildschutz: Boolean): List<Pair<String, String>>
    val guideSkip: String
    val guideMeldungenTitel: String
    val guideMeldungenText: String
    val guideMeldungenLaden: String
    fun guideMeldungenLaedt(gelesen: Int, gesamt: Int): String
    fun guideMeldungenFertig(gesamt: Int): String
    // Je Seite Titel und Text, in der Reihenfolge der Bilder.
    val guidePages: List<Pair<String, String>>
    // Windows und Linux: Bedienung mit Maus und Tastatur statt Wischen.
    val guidePagesDesktop: List<Pair<String, String>>

    // Entdecken (Suche)
    val bestPerformers: String
    val worstPerformers: String
    val ipos: String
    val upcoming: String
    val completed: String
    fun ipoCardTitle(name: String): String
    fun ipoListed(date: String): String
    fun ipoSinceIssue(pct: String): String
    fun ipoAsOf(date: String): String
    val filterTitle: String
    fun resultCount(n: Int): String
    val industries: String
    val countries: String
    val regions: String
    val assetClass: String
    val resetFilters: String
    val filterSearchPlaceholder: String
    val noFilterResults: String
    fun industryName(key: String): String
    fun industryShort(key: String): String           // kurze Form fuer die Heatmap
    fun regionShort(key: String): String
    fun regionName(key: String): String
    fun assetClassName(key: String): String          // INDEX, AKTIE, ROHSTOFF, WAEHRUNG, KRYPTO oder ANDERE
    fun countryLabel(iso: String): String

    fun koRefundOn(date: String): String              // Depotliste, unter dem Verlust
    fun koAutoSettle(date: String): String            // Automatische Ausbuchung nach zwei Tagen
    val settleNow: String
    fun settledNow(amount: String): String
    fun firstAddedOn(date: String): String

    // Nachrichten
    fun newsCategory(k: de.troetenrepublik.app.market.Rss.Kategorie): String
    fun newsAnbieter(a: de.troetenrepublik.app.market.Rss.Anbieter): String
    val newsLoading: String
    val newsEmpty: String
    val newsDepotLeer: String
    val newsDepotStufen: String
    val newsEmptySearch: String
    val newsYesterday: String
    val newsSearchHint: String
    val newsSearch: String
    val newsSources: String
    val newsSourcesHint: String
    val newsOwnFeeds: String
    val newsFeedUrl: String
    val newsFeedChecking: String
    val newsRemove: String
    val newsSave: String
    val newsUnsave: String
    val newsEmptySaved: String

    // Uebersetzungstabellen
    fun sectorName(key: String): String
    fun countryName(key: String): String

    // Copytrading
    val copyMillion: String
    val copyTitle: String
    val copyCongress: String
    val copySenate: String
    val copyGovernment: String
    val copySenators: String
    val copySenateNote: String
    val copySenatorOf: String
    val copyGovIntro: String
    fun copyGovNote(stand: String): String
    val copyPresident: String
    val copyVicePresident: String
    fun copyAmtBis(amt: String, bis: String): String
    fun copyVanceNote(stichtag: String): String
    val copyFilingsAll: String
    val copyTypeMuniBond: String
    fun copyTradesCount(n: Int): String
    fun copyHoldingsAsOf(date: String): String
    val copyWhoTitle: String
    val copyWhoNone: String
    val copyTendBuy: String
    val copyTendSell: String
    val copyTendMixed: String
    fun copyWhoHouseOpen(offen: Int, gesamt: Int): String
    val copyWhoHouseLoad: String
    fun copyWhoHouseReading(fertig: Int, gesamt: Int): String
    fun copyWhoShare(pct: String): String
    fun copyWhoTrades(kaeufe: Int, verkaeufe: Int): String
    val copyWhoMixed: String
    fun copyWhoStueck(stichtag: String, veraenderung: String, bestand: String, wert: String): String
    fun copyWhoStueckNeu(stichtag: String, bestand: String, wert: String): String
    fun copyWhoStueckWeg(stichtag: String, vorher: String): String
    fun copyWhoAlle(n: Int): String
    val copyWhoWeniger: String
    val copyWhoHolds: String
    val copyWhoNewPos: String
    val copyWhoIncreased: String
    val copyWhoReduced: String
    val copyWhoSoldOut: String
    val copyWhoUnchanged: String
    val copyPhotoCongress: String
    val copyBellOn: String
    val copyBellOff: String
    fun copyBellOnToast(name: String): String
    fun copyBellOffToast(name: String): String
    val copyBellNotificationsOff: String
    val copyBellsTitle: String
    fun copyBellsSubtitle(n: Int): String
    val copyBellsAdd: String
    val copyBellHouse: String
    val copyBellSenate: String
    val copyBellGovernment: String
    val copyBellInvestor: String
    // Import von Registerdaten (seit 5.9)
    val imTitel: String
    val imUntertitel: String
    fun imInsider(n: Int, land: String): String
    fun imLeer(n: Int, land: String): String
    val imPdf: String
    val imUnbekannt: String
    // Lobbyarbeit in Europa (seit 5.9)
    val lwTitel: String
    val lwHinweis: String
    fun lwRegister(l: String): String
    fun lwSpanne(von: String, bis: String): String
    fun lwAb(von: String): String
    fun lwJahr(von: String, bis: String): String
    fun lwFte(n: String): String
    fun lwTaetigkeiten(n: Int): String
    fun lwThemen(t: String): String
    val lwEintrag: String
    // Neue Meldungen bei der OGE (seit 5.9): Mitteilung, Hinweis auf der Seite der Person und Link
    fun copyNotifOge(n: Int): String
    val importPortfolioJson: String
    val exportPortfolioJson: String
    val importChoice: String
    val importMerge: String
    val importReplace: String
    val depotMerged: String
    fun copyPhotoCredit(autor: String, lizenz: String): String
    val copyPhotosCommons: String
    val copyInvestors: String
    val copyStrategies: String
    val copyCongressIntro: String
    val copySearchMember: String
    val copyLoading: String
    val copyUnavailable: String
    val copyLatestTrades: String
    val copyMembers: String
    fun copyFilings(n: Int): String
    fun copyLast(date: String): String
    val copyCongressNote: String
    val copyFollowing: String
    val copySell: String
    val copyPartialSale: String
    val copyExchange: String
    val copyTypeOption: String
    val copyTypeGovBond: String
    val copyTypeCorpBond: String
    val copyTypeFund: String
    fun copyOver(v: String): String
    fun copyHouseOf(district: String): String
    val copyFilings12m: String
    val copyBuysEst: String
    val copySalesEst: String
    val copyReplicate: String
    val copyMostTraded: String
    fun copyBuySellEst(buy: String, sell: String, n: Int): String
    val copyAllTrades: String
    fun copyFiledOn(date: String): String
    fun copyPaper(n: Int, dates: String): String
    val copyNoFilings: String
    fun copyNote(name: String): String
    val copyFollow: String
    val copyUnfollow: String
    val copyInvestorsIntro: String
    fun copyInvestorLine(date: String, n: String, value: String): String
    val copyInvestorsNote: String
    val copy13fSuche: String
    val copy13fEigene: String
    val copy13fNichts: String
    val copy13fFehler: String
    val copy13fOhneKontakt: String
    fun copyNewer13f(asOf: String, filed: String, shown: String): String
    val copyAsOf: String
    val copyFiled: String
    val copyHoldings: String
    val copySoldOut: String
    val copyCall: String
    val copyPut: String
    val copyNew: String
    fun copyIncreased(pct: String): String
    fun copyReduced(pct: String): String
    val copySold: String
    val copyStrategiesIntro: String
    val copyDefensive: String
    val copyBalanced: String
    val copyOffensive: String
    val copyStrategiesNote: String
    val copyAllocation: String
    val copyHistory: String
    val copyNoHistory: String
    fun copyRange(from: String, to: String): String
    val copyMaxDrawdown: String
    val copyYears: String
    val copyAsPlans: String
    fun copyReplicateTitle(name: String): String
    val copyReplicateHint: String
    fun copyCash(v: String): String
    val copyCount: String
    val copyNothingToCopy: String
    fun copyFees(n: Int, v: String): String
    val copyWorking: String
    val copyReplicateNow: String
    fun copyPlansTitle(name: String): String
    val copyPlansHint: String
    fun copyPlansMin(betrag: String): String
    val copyMonthly: String
    val copyPlanDay: String
    val copyPlansCreate: String
    fun copyDone(bought: Int, orders: Int): String
    fun copyPlansDone(n: Int): String
    fun copyNotifTitle(name: String): String
    fun copyNotifTrades(n: Int, list: String): String
    val copyNotifPaper: String
    fun copyNotif13f(asOf: String, filed: String): String

    // Anzeige in US-Dollar
    val switchCurrency: String
    val usdUnavailable: String
    val regLandTitel: String
    val regLandText: String
    val regVorschlag: String
    val regAndere: String
    val regWaehrungTitel: String
    val regWaehrungText: String
    fun waehrungName(code: String): String
    val regZeitenTitel: String
    val regZeitenText: String
    fun regZeitenLand(land: String): String
    fun regZeitenLandHint(boerse: String, zeiten: String, deutschland: Boolean): String
    val regZeitenEigene: String
    val regZeitenImmer: String
    fun regVonBis(von: String, bis: String): String
    val regionTitel: String
    val regAnzeige: String
    val regAnzeigeHint: String
    fun regSteuerHinweis(land: String): String
    fun regWissenUs(n: Int): String
    fun fxRate(von: String, nach: String, kurs: String): String
    fun tradingHoursFor(aktien: String?, derivate: String?, wochenende: Boolean, ortszeit: Boolean): String
    val rrTitel: String
    val rrLaedt: String
    val rrKeine: String
    fun rrRendite(jahre: Int): String
    val rrLaengster: String
    fun rrDauer(tage: Long): String
    val rrLaeuft: String
    fun rrBeta(index: String): String
    fun rrKorrelation(index: String): String
    fun rrHinweis(ab: String, index: String?, beta: String?): String
    val trendsTitel: String
    val trBranchenHinweis: String
    val trReddit: String
    val trRedditHinweis: String
    val trFilterDeutsch: String
    val sbTitel: String
    val sbKrypto: String
    val sbCnn: String
    val sbAaii: String
    fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String): String
    val sbPutCall: String
    fun sbPutCallMehr(wert: String): String
    fun wqPcZeile(gesamt: String?, index: String?, tag: String): String
    val sbWsb: String
    fun sbRedditWert(prozent: Int): String
    fun sbStufe(stufe: Int): String
    fun sbTeil(schluessel: String): String
    val sbVortag: String
    val sbWoche: String
    val sbMonat: String
    val sbWsbSteigend: String
    val sbWsbFallend: String
    val sbHinweis: String
    fun sbKachel(schluessel: String): String
    fun sbAnbieter(schluessel: String): String
    fun sbSteigend(prozent: Int): String
    val fwTitel: String
    fun fwZusammen(rot: Int, gelb: Int, n: Int): String
    fun fwName(schluessel: String): String
    fun fwLage(schluessel: String, ampel: Int): String
    fun fwErklaerung(schluessel: String): String
    fun blsHinweis(datum: String): String
    val b13107Titel: String
    val b13107Text: String
    val b13107Ok: String
    val b13107Zurueck: String
    val fwPunkte: String
    val fwHinweis: String
    val wrTitel: String
    val wrUntertitel: String
    val wrInfo: String
    val wrFest: String
    fun wrIndex(id: String): String
    fun wrZeichen(id: String): String
    val wrZeitleiste: String
    val wrRueckgaenge: String
    val wrLegende: String
    fun wrEreignis(hoch: String, tief: String, tiefe: String, monate: Int): String
    val wrKetteTitel: String
    val wrKeineKette: String
    val wrTabelle: String
    fun wrZeile(treffer: Int, von: Int, fehl: Int, vorlauf: Int?): String
    fun wrHeute(stand: Int): String
    val wrKurve: String
    val wrFolgen: String
    val wrHalten: String
    val wrBefolgen: String
    fun wrKurveWerte(rendite: String, rueck: String, wechsel: Int): String
    val wrWechsel: String
    val wrKosten: String
    fun wrSatz(treffer: Int, von: Int, fehl: Int, phasen: Int, spanneVon: Int, spanneBis: Int, zeit: Int): String
    val wrFehlendTitel: String
    fun wrFehlend(ids: String): String
    fun wrKurzzeile(treffer: Int, von: Int, fehl: Int): String
    fun wrHinweisZeichen(id: String, ersatz: Boolean, revidiert: Boolean, eigeneRegel: Boolean): String
    fun wrStand(stand: String, bis: String): String
    val wrBeleg: String
    fun redditMeldungTitel(name: String): String
    fun redditMeldungText(jetzt: Int, vorher: Int): String
    val trRedditDeHinweis: String
    fun trRedditDeAbdeckung(stunden: Int): String
    fun trRedditDeFehlt(subs: String): String
    val trRedditBeitraege: String
    fun trRedditAnzahl(n: Int): String
    val trRedditKeineBeitraege: String
    fun trRedditStimmung(steigend: Int, fallend: Int, offen: Int): String
    fun trRedditPunkte(punkte: Int, kommentare: Int): String
    val trRedditKeine: String
    val trLaedt: String
    val trMehr: String
    fun trRang(rang: Int, ticker: String): String
    fun trErwaehnungen(n: Int): String
    val trYahoo: String
    val trWiki: String
    fun trWikiProTag(n: String): String
    fun trRedditZeit(tage: Int): String
    fun trSpitze(datum: String): String
    fun trSpitzeWoche(datum: String): String
    fun trVerlaufSeit(datum: String?): String
    val trErwaehnungenKurz: String
    val trWertHeuteNicht: String
    val trWertYahooText: String
    // Orders und Import
    val orderGeaendert: String
    val orderAendern: String
    val orderAendernSpeichern: String
    fun trImportTitel(quelle: String): String
    val trImportLaedt: String
    fun trImportZeitraum(von: String, bis: String, zeilen: Int): String
    fun trImportOhneKurs(liste: String): String
    fun trImportOhneKauf(liste: String): String
    fun trImportUngelesen(n: Int, beispiele: String): String
    fun trImportWechselkurs(n: Int): String
    fun trImportBekannt(bekannt: Int, gesamt: Int): String
    fun trImportUeberGrenze(n: Int, max: Int): String
    fun trImportUngeprueft(quelle: String): String
    fun trImportGuthabenNegativ(betrag: String, degiro: Boolean): String
    val taxImportOhneSteuer: String
    val trImportHinweis: String
    val brokerImportHinweis: String
    fun trImportiert(quelle: String, n: Int): String
    val importWahlSicherung: String
    val importWahlSicherungText: String
    val importWahlBroker: String
    fun importWahlBrokerText(namen: String): String
    val importWahlAnleitung: String
    fun importUnbekannt(namen: String): String

    // Prediction Markets
    val pmTitle: String
    val pmMyBets: String
    val pmSearchHint: String
    val pmLoading: String
    val pmUnreachable: String
    val pmNoResults: String
    val pmMore: String
    fun pmDataNote(quelle: String): String
    fun pmVolume(v: String): String
    fun pmEnds(d: String): String
    val pmIn24h: String
    val pmYes: String
    val pmBet: String
    val pmOutcomes: String
    val pmPlaceBet: String
    val pmAmount: String
    fun pmSideAt(seite: String, kurs: String): String
    fun pmChanceOf(seite: String): String
    val pmSharesLabel: String
    val pmPayoutIf: String
    val pmProfitIf: String
    val pmPracticeNote: String
    fun pmBought(betrag: String, seite: String): String
    fun pmSold(betrag: String): String
    fun pmFehler(code: String): String
    val pmSellTitle: String
    val pmSellErloes: String
    val pmSellGeldkurs: String
    val pmSellWert: String
    val pmSellAbschlag: String
    val pmSellLaedt: String
    val pmClosed: String
    val pmOpenBets: String
    val pmValue: String
    val pmResultOpen: String
    val pmResultClosed: String
    val pmResultTotal: String
    fun pmClosedCount(n: Int): String
    fun pmStatus(status: String): String
    fun pmSharesAt(n: String, einstand: String, kurs: String): String
    val pmNoBets: String
    val pmYourBets: String
    val pmRules: String
    fun pmOpenAt(quelle: String): String
    val pmNoHistory: String
    fun pmRange(b: de.troetenrepublik.app.market.Prognose.Bereich): String
    fun pmResolvedCount(n: Int): String
    fun pmNotifTitle(tag: String): String
    fun pmNotifText(titel: String, betrag: String): String

    // Portfolioverlauf im Copytrading
    val pvTitle: String
    fun pvRange(jahre: Int): String
    val pvCompare: String
    val pvCompareTitle: String
    val pvGroupIndex: String
    val pvGroupMembers: String
    val pvLoading: String
    val pvNone: String
    fun pvCurrencyNote(waehrung: String): String
    fun pvNoteInvestor(quartale: Int, abdeckung: Double = 1.0): String
    val pvMoreStats: String
    val pvNoInvestorHistory: String
    val pvLessStats: String
    val ptClosedSales: String
    fun ptClosedSalesLine(anzahl: Int, gewinne: Int, quote: String, faktor: String?, mittelGewinn: String?, mittelVerlust: String?, erwartung: String): String
    fun pvNoteCongress(geschaefte: Int, ohneKurs: Int, ohneBestand: Int): String
    // Praesident: vorberechneter Verlauf, Zahlen schon formatiert, die letzten drei Teile null bei 0.
    fun pvNotePresident(geschaefte: String, jahr: String, einzel: String, eingerechnet: String, werte: String, anleihen: String, anteil: String,
        ohneKuerzel: String?, ohneKurs: String?, ohneBestand: String?, stichtag: String): String
    // Truth Social ueber das Archiv Trump's Truth
    val truthTitle: String
    val truthHinweis: String
    val truthRemoved: String
    val truthRepost: String
    val truthQuote: String
    fun truthImages(n: Int): String
    val truthVideo: String
    val truthNone: String
    val truthMore: String
    val pvNoteStrategy: String
    val pvNoteCompare: String
    val pvCongressNoBuys: String
    fun pvMarkBuys(datum: String, werte: String): String
    fun pvMarkSells(datum: String, werte: String): String
    fun pvMarkQBuys(quartal: String, werte: String): String
    fun pvMarkQSells(quartal: String, werte: String): String
    val pvMoveOut: String
    fun pvQuarter(q: Int, jahr: Int): String
    val pvMoves: String
    fun pvQuarterHead(quartal: String, gemeldet: String): String
    fun pvAllQuarters(n: Int): String
    val pvFewerQuarters: String
    val pvCompareStrategies: String
    val pvCompareInvestors: String
    val pvCompareScreen: String

    // Depotverlauf
    val dvSubtitle: String
    val dvNone: String
    val dvMoves: String
    fun dvRaster(name: String): String
    val dvRises: String
    val dvDrops: String
    val dvNoMoves: String
    fun dvWeek(datum: String): String
    fun dvBy(werte: String): String
    val dvContrib: String
    val dvHeld: String
    val dvEstimated: String
    val dvTrades: String
    fun dvQtyAt(qty: String, price: String): String
    fun dvAll(n: Int): String
    val dvFewer: String
    // Beim Ziehen durch den Depotverlauf, Geschaefte des Tages nur mit Namen
    fun dvTagKauf(werte: String): String
    fun dvTagVerkauf(werte: String): String
    fun dvUndWeitere(n: Int): String
    val dvNote: String
    val languageSystem: String
    // Zugaenge und Dienste mit eigenem Schluessel
    val zgTitle: String
    val importFileSection: String
    val pvDepotSub: String
    val newsImportantTitle: String
    val newsImportantExplain: String
    val newsWichtigEnglisch: String
    val newsWichtigEnglischHinweis: String
    val newsWichtigFarbig: String
    val ptIndexTitle: String
    fun zgTerms(dienst: String): String
    val zgKey: String
    val zgShow: String
    val zgHide: String
    fun zgUsage(dienst: String, verbraucht: String, erlaubt: String): String
    val zgLimit: String
    val zgKeyRejected: String
    fun zgProviderSays(meldung: String): String
    fun zgGetKey(name: String): String
    fun zgProfileSubtitle(n: Int): String
    val zgRedditId: String
    val zgRedditSecret: String
    val zgRedditHolen: String
    fun newsKeyMissing(name: String): String
    val newsKeyEnter: String
    val newsGuardianNote: String
    val newsGuardianNoText: String
    val pmTranslateHint: String
    // Verlauf unter Meine Wetten
    val pmHistory: String
    val pmHistoryLoading: String
    val pmHistoryNone: String
    val pmHistoryNote: String
    fun pmHistoryRange(tage: Int): String
    fun pmHistoryMark(tag: String, datum: String, betrag: String, seite: String, titel: String): String
    fun pvSince(monat: String): String
    val indButton: String
    fun indName(art: String): String
    // 0.76 Gesamtrichtung und Kerzenmuster im erweiterten Chart
    val gsrTitel: String
    fun gsrWort(wort: String): String
    fun gsrZaehlung(steigend: Int, fallend: Int, neutral: Int): String
    val gsrHinweis: String
    fun gsrFamilie(key: String): String
    val gsrEinSignal: String
    fun kzImChart(name: String, datum: String): String
    fun kzName(muster: String): String
    fun indLage(art: String, lage: String): String
    fun indTendenz(tendenz: String): String
    fun indGrund(art: String, lage: String, prozent: String, zahl: String, kreuz: String?, kreuzAuf: Boolean, eng: Boolean): String
    fun indErklaerung(art: String): String
    val indFuss: String
    val copyPolitics: String
    fun truthRelevant(themen: List<String>): String
    fun truthThema(k: String): String
    val newsOhneLesemodus: String
    fun newsSeiteAbgelehnt(code: Int): String
    fun newsSeiteWeg(code: Int): String
    fun newsSeiteZuOft(code: Int): String
    fun newsSeiteNichtErreichbar(code: Int): String
    val newsSeiteVpn: String
    val newsBrowserOnly: String
    val copyPositionsTitle: String
    fun copyPositionShare(anteil: String): String
    val copyPositionsNote: String
    fun copyPositionsAll(n: Int): String
    val copyPositionsFewer: String
    val copyPartShares: String
    fun copyPartOption(call: Boolean, basispreis: String, verfall: String, verfallen: Boolean): String
    fun copyPartShare(anteil: String): String
    fun copyPartsToggle(offen: Boolean): String
    val copyToolsRow: String
    fun copyToolsFor(name: String): String
    fun pvMetric(k: String): String
    fun pvMetricExplain(k: String): String
    fun copyLogoCredit(domain: String): String
    val copyNameUnreadable: String
    fun webBausteinAngebot(mb: Int): String
    val webBausteinLaden: String
    fun webBausteinLaedt(prozent: String): String
    val webBausteinFehler: String
    val webBausteinImFenster: String
    val webBausteinLeer: String
    val webBausteinFenster: String
    val newsTrumpHinweis: String
    val webBausteinNurAnfang: String
    fun dfUniversumName(k: String): String
    val opTitel: String
    fun opHerkunft(h: de.troetenrepublik.app.market.MarketApi.Herkunft, offen: Boolean = false): String?
    val opPcr: String
    val opPcrOffen: String
    val opHvLabel: String
    fun opBewegung(tag: String): String
    fun opMaxPain(tag: String): String
    fun opHv(wert: String): String
    val opAuffaellig: String
    val opStand: String
    // Optionsrechner (0.78)
    val orKarte: String
    val orSymbol: String
    val orLaden: String
    val orVerfall: String
    fun orVorlage(key: String): String
    val orKauf: String
    val orVerkauf: String
    fun orArt(key: String): String
    val orBasispreis: String
    val orMenge: String
    val orBeinNeu: String
    fun orBeinOption(preis: String, iv: String, berechnet: Boolean): String
    fun orBeinAktie(menge: Int, preis: String): String
    val orBreakEven: String
    val orMaxGewinn: String
    val orMaxVerlust: String
    val orUnbegrenzt: String
    val orChance: String
    val orKosten: String
    val orErloes: String
    val orHeute: String
    fun orAmDatum(datum: String): String
    fun orGriechName(key: String): String
    val orBewegungTitel: String
    val orKeine: String
    val orNichtMoeglich: String
    fun orLesen(kurs: String, verfall: String, tag: String, datum: String): String
    fun opErklaerung(k: String): String
    val opStillhalter: String
    fun opTage(tage: Int): String
    val opStillCalls: String
    val opStillPuts: String
    val opBasis: String
    val opPraemie: String
    val opRenditeJahr: String
    // call: Basis, Praemie, Rendite, Rendite bei Ausuebung, Schwelle; put: Basis, Praemie, Rendite, Einstand
    fun opStillBeispiel(call: List<String>?, put: List<String>?): String
    val opEbenen: String
    val opLegendeCalls: String
    val opLegendePuts: String
    val opCallWand: String
    val opPutWand: String
    val opNettoGex: String
    val opNullpunkt: String
    val opNaeherung: String
    val opKurs: String
    val opVola: String
    val opIv30: String
    val opRv30: String
    val opIvZuRv: String
    val opPutAufschlag: String
    val opLaufzeit: String
    val opIvRang: String
    val opIvPerzentil: String
    fun opIvSeit(tag: String): String
    val slAbstand: String
    val slLaufzeit: String
    val slCall: String
    val slPut: String
    val slPraemie: String
    val slRenditeJahr: String
    val slAusserhalb: String
    val slSpread: String
    fun slZahlen(tag: String): String
    val slLeer: String
    val slLaedt: String
    fun slFortschritt(n: Int, m: Int): String
    val kaTitel: String
    val kaNav: String
    val kaLaedt: String
    val kaVergleich: String
    val kaFenster: String
    fun kaFensterKurz(tage: Int): String
    fun kaMonate(n: Int): String
    val kaFensterEigen: String
    val kaFensterMonate: String
    val kaHinweisVerlauf: String
    val kaJetzt: String
    val cuTitel: String
    val cuNavUnter: String
    fun cuSeite(k: String): String
    fun cuErklaerung(k: String): String
    fun cuKurz(k: String): String
    fun cuPersonen(n: Int): String
    fun cuKaeufeAnzahl(n: Int): String
    fun cuVerkaeufeAnzahl(n: Int): String
    val cuNachAnzahl: String
    val cuNachVolumen: String
    fun cuKaeufer(n: Int): String
    fun cuKaufAm(datum: String, betrag: String): String
    fun cuKaeufeBis(n: Int, datum: String, betrag: String): String
    fun cuQuartalBis(datum: String, betrag: String): String
    fun cuQuartaleBis(n: Int, datum: String, betrag: String): String
    val cuLeer: String
    val cuLeerZeitraum: String
    val cuKeineUnter: String
    fun ieKern(unter: Int, alle: Int): String
    fun ieKeiner(alle: Int): String
    fun ieEinstieg(preis: String): String
    fun ieReduziert(anteil: String): String
    val ieVerkauft: String
    fun ieVerkauftAm(datum: String): String
    fun ieVerkaufskurs(preis: String): String
    fun ieVerkaeufe(betrag: String): String
    fun ieWerte(n: Int): String
    fun cuStand(wann: String): String
    val cuKeineKurse: String
    val rgTitel: String
    val rgNav: String
    fun rgOrdnung(k: String): String
    fun rgVermoegen(s: String): String
    fun vmSpanne(von: String, bis: String): String
    fun vmTitel(jahr: Int): String
    val vmHinweis: String
    val bhTitel: String
    val bhLobbying: String
    fun bhJeQuartal(betrag: String): String
    fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int): String
    fun bhThemen(liste: String): String
    fun bhLobbyingHinweis(stand: String): String
    fun bhGesetzeWert(gesetze: Int, personen: Int): String
    fun bhGesetz(g: String, n: Int): String
    val bhAuftraege: String
    fun bhFiskaljahr(betrag: String, jahr: Int): String
    fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean): String
    fun bhZitat(text: String): String
    val bhAuftraegeHinweis: String
    val bhSpenden: String
    fun bhSpendenWert(betrag: String, zyklus: Int): String
    val bhKeinPac: String
    fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String): String
    fun bhSpendenHinweis(demo: Boolean): String
    val bhLeer: String
    fun bhLeerTage(anteil: String, tage: Int): String
    fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?): String
    fun bhLeerAnteil(tage: Int): String
    val bhLeerAnteilHinweis: String
    fun bhLeerOffen(stichtag: String): String
    val bhLeerAenderung: String
    val bhLeerEindecken: String
    val dpPlattformen: String
    val bhLeerErklaerung: String
    val bhFinraOhne: String
    val bhFinraLeerLink: String
    val bhFinraAtsLink: String
    val dpTitel: String
    fun dpWoche(woche: String, aktien: String, anteil: String?, schnitt: String?, wochen: Int): String
    fun dpPlaetze(plaetze: List<Pair<String, String>>): String
    fun dpVerzug(stufe: String?): String
    val dpErklaerung: String
    val zgFinraSecret: String
    val zgFinraHolen: String
    fun bhLeerVerlauf(seit: String, tief: String, hoch: String): String
    // [q78e] Bewertung gegen die eigene Geschichte, Leerverkaufsverlauf, Fail-to-Deliver, Verlaeufe, CSV
    val hqTitel: String
    fun hqArt(art: Int): String
    fun hqSatz(art: String, heute: String, median: String, abstand: String, ueber: Boolean): String
    fun hqBand(unten: String, oben: String): String
    fun hqBereich(seit: String, n: Int): String
    val hqLeerTitel: String
    fun hqLeerStand(datum: String, aktien: String, anteil: String?): String
    fun hqLeerTage(tage: String, vorher: String?): String
    val hqLeerAnteil: String
    val hqLeerAktien: String
    val hqLeerTageReihe: String
    val hqKurs: String
    val hqFtdTitel: String
    fun hqFtdStand(datum: String, menge: String): String
    fun hqFtdHoch(menge: String, datum: String): String
    val hqFtdKeine: String
    fun hqMitarbeiter(seit: Int): String
    val hqFtdReihe: String
    fun hqRegSho(ja: Boolean): String
    val hqVerlaeufe: String
    val hqUmsatzTtm: String
    val hqMarktwert: String
    val hqExportFinanzen: String
    val hqExportKurse: String
    fun hqExportFertig(n: Int): String
    fun bhThema(code: String): String
    val pmGross: String
    val pmEchtTitel: String
    val pmEchtKeine: String
    val pmEchtHinweis: String
    val pmUebungTitel: String
    fun pmEchtZeile(ausgang: String, anteile: String, einstand: String, kurs: String): String
    val pmEchtEinloesbar: String
    val zgPolyAdresse: String
    val zgPolyGeheim: String
    val zgPolyUngueltig: String
    val pmGrossKeine: String
    fun pmGrossZeile(ausgang: String, preis: String, wann: String): String
    val bhSpendenLimit: String

    // 5.91 Kurschart und Marktkurven
    val kcSpanne: String
    fun kcLinie(art: String): String
    fun kcMarke(art: String, stueck: String, betrag: String, kurs: String): String
    fun kcSpanneQuelle(implizit: Boolean, sigma: String): String
    val mkTitel: String
    val mkUntertitel: String
    val mkZins: String
    val mkUsa: String
    val mkEuro: String
    val mkDeutschland: String
    fun mkStichtag(k: Int, datum: String): String
    fun mkLaufzeit(tage: Int): String
    fun mkForm(form: String): String
    fun mkInvertiert(kurz: String, zins: String, zehn: String): String
    fun mkAbstand(kurz: String, wert: String): String
    val mkZinsMehr: String
    val mkVix: String
    fun mkVixForm(form: String): String
    val mkVixMehr: String
    val mkKeineDaten: String

    // 5.91 Strategie-Labor, Backtest-Lotterie, Anlage-These, eigene Regeln, monatliche Einzahlung
    val slTitel: String
    val slUntertitel: String
    val slErklaerung: String
    val slZuWenig: String
    fun slKosten(spread: String, dividenden: Boolean): String
    val slRegeln: String
    fun slRegel(art: String, a: Int, b: Int, n: Int): String
    val slDieRegel: String
    val slHalten: String
    fun slVergleich(regel: String, halten: String): String
    val slSpalten: String
    val slEndwert: String
    val slTreffer: String
    val slImMarkt: String
    fun slLern(datum: String): String
    fun slPruef(datum: String): String
    val slLernText: String
    val slLotterie: String
    val slLotterieText: String
    fun slFamilie(key: String): String
    val slLotterieLaeuft: String
    val slVarianten: String
    val slBeste: String
    val slBesteLern: String
    val slBestePruef: String
    val slDsr: String
    val slDsrText: String
    val slAussage: String
    val slAussagePruef: String
    fun slAussageStufe(key: String): String
    fun slAussageText(trades: Int, jahre: String): String
    fun slAussageHinweis(zuWenig: Boolean, trades: Int, jahre: String): String
    val slParam: String
    val slParamText: String
    val slParamKurz: String
    val slParamLang: String
    val slParamLaenge: String
    fun slParamTage(n: Int): String
    val slParamKauf: String
    val slParamVerkauf: String
    val slParamLern: String
    val slParamPruef: String
    val slNachbarnLern: String
    val slNachbarnPruef: String
    val slZufall: String
    val slZufallText: String
    val slZufallAnteil: String
    fun slZufallErgebnis(besserAls: Int): String
    val slZufallRegelWaehlen: String
    val slHaltenKurz: String
    val slWf: String
    val slWfText: String
    val slWfStart: String
    fun slWfLaeuft(prozent: String): String
    val slWfZuWenig: String
    val slWfFenster: String
    val slWfZusammen: String
    val slWfBesser: String
    fun slWfBesserWert(besser: Int, gesamt: Int): String
    val slWfAbfall: String
    fun slWfErgebnis(besser: Int, gesamt: Int, regel: String, halten: String): String
    fun slWfRegel(art: String, a: Int, b: Int, n: Int): String
    val slWfKopfBeginn: String
    val slWfKopfRegel: String
    val slWfKopfLern: String
    val slWfKopfPruef: String
    val slWfKopfHalten: String
    val thTitel: String
    val thHinzufuegen: String
    val thGrund: String
    val thAusstieg: String
    val thObergrenze: String
    val thPruefen: String
    val thKeinTermin: String
    fun thMonate(m: Int): String
    fun thPruefAm(datum: String): String
    val thRegelWarnung: String
    fun thVerstoss(art: String, ist: String, grenze: String, name: String): String
    fun thSeit(datum: String): String
    val thAusstiegLabel: String
    fun thObergrenzeWert(grenze: String, ist: String): String
    val thFrage: String
    val thVerkaufenErwaegen: String
    val thEntfernen: String
    fun thMitteilung(name: String): String
    val thBestaetigtToast: String
    val thRegelnTitel: String
    val thRegelnText: String
    val thRegelPosition: String
    val thRegelHebel: String
    val thRegelBargeld: String
    val thAus: String
    val thAn: String
    val thRegelnOk: String
    val thAuswertung: String
    val thMitThese: String
    val thBestaetigt: String
    fun thZaehlTeile(bestaetigt: Int, erwogen: Int, regelbrueche: Int, offen: Int): List<String>
    val thErwogen: String
    val thRegelbrueche: String
    val thOffeneTermine: String
    val thAuswertungText: String
    val ezTitel: String
    fun ezAktiv(betrag: String, tag: String): String
    fun ezTagWert(tag: Int): String
    val ezText: String
    val ezTag: String
    fun ezNaechste(datum: String): String
    val ezBisher: String
    val ezAnzahl: String
    fun ezBisherWert(n: Int, summe: String): String
    val ezLoeschen: String
    val ezGespeichert: String
    val ezMitteilung: String
    fun ezMitteilungText(betrag: String): String

    // 5.91 Risikobeitrag, Bilanzqualitaet, Kaufkraft, Kennzahlenvergleich, Reverse DCF
    val rskTitel: String
    fun rskDepotSchwankung(pct: String): String
    val rskGewicht: String
    val rskRisiko: String
    fun rskZeile(gewicht: String, risiko: String): String
    fun rskSatz(name: String, gewicht: String, risiko: String): String
    val rskAusgeglichen: String
    val rskOhneDaten: String
    fun rskNichtEingerechnet(n: Int): String
    val rskHinweis: String
    val rsTitel: String
    fun rsInfo(index: String): String
    val rsLeer: String
    val rsRendite: String
    fun rsOhneVerlauf(n: Int): String
    val rskParitaetTitel: String
    val rskParitaetText: String
    val rskUebernehmen: String
    val rskWarnung: String
    val rskLaden: String
    val qsTitel: String
    val qsPiotroski: String
    fun qsScore(score: Int, von: Int): String
    fun qsStufe(stufe: String): String
    fun qsJahre(jahr: String, vorjahr: String): String
    fun qsPunkt(punkt: String): String
    fun qsBegruendung(punkt: String, jetzt: String, vorher: String?): String
    val qsAktienSpielraum: String
    val qsNichtPruefbar: String
    val qsAltman: String
    fun qsZone(zone: String): String
    val qsZonen: String
    val qsZonenAndere: String
    fun qsAltmanJahrAndere(jahr: String): String
    fun qsBaustein(k: String): String
    fun qsAltmanJahr(jahr: String): String
    val qsHinweis: String
    val qsLaden: String
    val qsKeineDaten: String
    val kvTitel: String
    val kvEinstieg: String
    val kvAehnlich: String
    val kvBranche: String
    val kvSuche: String
    val kvSucheHinweis: String
    fun kvZeile(k: String): String
    val kvHinweis: String
    val kvLaden: String
    val rdTitel: String
    fun rdSatz(x: String): String
    fun rdSatzRand(x: String, mehr: Boolean): String
    val rdRendite: String
    val rdZielKgv: String
    fun rdBranche(kgv: String): String
    fun rdKgvJetzt(kgv: String): String
    fun rdBisher(x: String, jahre: Int): String
    fun rdAnalysten(x: String): String
    val rdHinweis: String

    // 5.91 Aufraeumen
    val auStornierenTitel: String
    fun auStornierenText(name: String): String
    val auBehalten: String
    val auIndikatorenAbMonat: String

    // 5.91 Insider weltweit und US-Regierung
    val ogeTitel: String
    val ogeLeer: String
    fun ogeMeldungen(n: Int): String
    val ogeHinweis: String
    fun ogeUngelesen(n: Int): String
    fun ogePdf(datum: String): String
    fun ogeGehandelt(n: Int): String
    val f144Titel: String
    val f144Hinweis: String
    fun f144Stueck(n: String): String
    fun f144Geplant(tag: String): String
    val f13Titel: String
    val f13Hinweis: String
    val f13Aktiv: String
    val f13Passiv: String
    val omTitel: String
    val omHinweis: String
    fun omAnteil(p: String): String
    val saTitel: String
    val saUntertitel: String
    val saEinleitung: String
    fun saName(id: String): String
    fun saInhalt(id: String): String
    fun saSchritte(id: String): List<String>
    val saOeffnen: String
    fun saRegion(code: String): String
    fun saTyp(typ: String): String

    // 5.91 Profil
    val erwUntertitel: String
    val erwHandel: String
    val erwKurse: String
    val erwZinsAuto: String
    val erwRegister: String
    val erwPp: String

    // 5.91 Wertpapierseite
    val bwAlleKennzahlen: String
    val bwTer: String
    val bwPreisalarm: String
    fun bwPreisalarmZahl(n: Int): String
    val bwOffeneOrders: String
    val bwFuerUndWider: String
    val bwHintergrund: String
    fun bwHintergrundUnter(themen: List<String>): String
    fun bwThema(key: String): String
    val bwRrUnter: String
    val bwGesetze: String

    // 5.91 Startseite
    val maerkteTitel: String
    val maerkteBewegungen: String
    val handeln: String
    val zeitraumMenue: String

    // 5.92 Datenquellen
    val dqTitel: String
    fun bkFairerWertKurz(preis: String, abstand: String, darueber: Boolean): String
    fun awHaeufTeile(monat: String?, anzahl: String, ueberMillion: String): List<String>
    val chartBeschreibung: String
    val dqEinleitung: String
    fun dqName(id: String): String
    val dqQuellen: String
    // Verzeichnis je Quelle: Lizenz in Kurzform, Links zu den Bedingungen, Rueckfall bei Ausfall
    val dqLizenzLabel: String
    val dqBedingungenLabel: String
    val dqAusfallLabel: String
    fun dqLizenz(l: de.troetenrepublik.app.engine.Quellenbuendel.Lizenz): String
    fun dqAusfall(ersatz: List<String>, schluessel: Boolean): String
    // Aufklappbarer Quellenbereich am Seitenende (0.70)
    val quellenAnhang: String
    val quelleCboe: String
    val quelleVixYahoo: String
    val quelleIwfPortWatch: String
    val quelleWeltbank: String
    val dqAus: String
    val dqNie: String
    fun dqAntwortet(seit: String): String
    fun dqUmweg(seit: String): String
    fun dqFehler(art: String, letzterErfolg: String?): String
    fun dqArt(art: de.troetenrepublik.app.engine.Quellenbuendel.Fehler?, code: Int): String
    val dqStandKopf: String
    val dqStandLabel: String
    val dqQuellenStandLabel: String
    val dqQuellenGesucht: String
    val dqDatenKopf: String
    val dqDatenEingebaut: String
    fun dqDatenNichtGepflegt(marke: String): String
    val dqInhalteKopf: String
    fun dqInhalt(name: String): String
    fun dqStand(version: Long, geladen: Boolean): String
    val dqGesucht: String
    val dqNochNicht: String
    fun dqErgebnis(ergebnis: String, http: Int): String
    val dqSuchen: String
    val dqSucht: String
    val dqZuruecksetzen: String
    val dqHinweisLink: String
    val dqAusblenden: String
    val dqVerstanden: String
    fun dqHinweisTitel(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis): String
    fun dqHinweisText(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis): String

    // 5.92 P3 Startseite, Profil, Einstellungen
    val wfTitel: String
    val wfReihenfolge: String
    val wfHoch: String
    val wfRunter: String
    fun wfVoll(n: Int): String
    val wfOriginal: String
    val wfOriginalKurz: String
    val wfOriginalHinweis: String
    val wfVorgabe: String
    val wlZeile: String
    val wlWieEinstellung: String
    val wlEigeneWahl: String
    val wlGlobal: String
    val wlGlobalHinweis: String
    val wlZinssatz: String
    val wlSortSeit: String
    val wlSortDatum: String
    val zgKostenpflichtig: String
    val zgZustandNein: String
    val zgZustandJa: String
    val zgZustandFehler: String
    val zgIntroPunkte: List<String>
    fun zgZweck(dienst: String): String
    fun zgPunkte(dienst: String): List<String>
    val saAnsehenTitel: String
    val saAnsehenText: String
    fun saAnsehenName(id: String): String
    fun saAnsehenInhalt(id: String): String

    // 5.92 P1 Wertpapierseite oben
    val p1BilanzLaedt: String
    val p1BilanzWenig: String
    fun p1BilanzZeile(stufe: String, treffer: String, vergleich: String, unruhe: Boolean): String
    fun p1BilanzSeit(jahr: Int, signale: Int): String
    fun indForschung(art: String): String
    val indForschungAllgemein: String
    fun p1BilanzAllgemein(treffer: String, vergleich: String, werte: Int, ab: Int): String
    fun p1BilanzDetail(art: String, jahr: Int, signale: Int, richtig: Int, treffer: String?, vergleich: String?, tage: Int): String
    fun p1Spanne(unten: String, oben: String, tage: Int): String
    val p1SpanneMehr: String
    val p1SeitKauf: String
    fun p1Haltedauer(datum: String, tage: Long): String
    val p1HebelLong: String
    val pcDarstellung: String
    val pcEinblenden: String
    val pcZeichnen: String
    val pcOhlc: List<String>
    val pcEinstieg: String
    fun pcWerkzeug(key: String): String

    // 5.92 P2 Wertpapierseite Analyse
    val bkTitel: String
    val bkBewertung: String
    val bkWachstum: String
    fun bkBewertungSatz(noetig: String, art: Int, bisher: String?): String
    fun bkFairerWert(preis: String, wachstum: String, abstand: String, darueber: Boolean, zielKgv: String): String
    val bkBewertungVerlust: String
    fun bkBilanzSatz(ampel: Int, gruende: List<String>): String
    fun bkGrund(key: String): String
    val bkGewinn: String
    fun bkWachstumUnter(jahr: String): String
    val bkFairSelbst: String
    fun bkGruppe(key: String): String
    fun bkVonBis(von: String, bis: String): String
    val bkKuv: String
    val bkEvEbitda: String
    val bkPeg: String
    val bkFcfRendite: String
    val bkGesamtkapitalRendite: String
    val bkEbitdaMarge: String
    val bkVerschuldung: String
    val bkLiquiditaet: String
    val bkDividendeFuenf: String
    val bkDividendenWachstum: String
    val bkLeerQuote: String
    val bkLeerTage: String
    val bkInsider: String
    val bkInstitutionen: String
    val bkAktien: String
    val bkAktienVeraenderung: String
    val bkZielSpanne: String
    val bkZielMedian: String
    val bkGewinnErwartet: String
    val bkGewinnWachstumJahr: String
    val bkGewinnWachstumFolge: String
    val bkUmsatzWachstumFolge: String
    val bkNaechsteZahlen: String
    val bkJahresVeraenderung: String
    val anLaedt: String
    val anKeineDaten: String
    val anVerteilung: String
    fun anVergleich(diff: Double): String
    val anNurUs: String
    val anBereinigt: String
    fun anAlleHaeuser(n: Int): String
    val anGewichtet: String
    val anSpalteGewichtet: String
    val anStufeZeile: String
    val anMittelwert: String
    val anTrefferZeile: String
    val anZiel: String
    fun anZielGewichtet(preis: String): String
    val anTrefferAlle: String
    fun anTrefferSatz(quote: String, n: Int, monate: Int, index: String?, wenig: Boolean): String
    fun anHinweis(herde: String): String
    val anHaeuser: String
    fun anStufe(s: Int): String
    fun anKursziel(preis: String): String
    val anVeraltet: String
    fun anZuWenige(n: Int): String
    fun anTreffer(je: List<String>, bereinigt: String): String
    val anWiderspruch: String
    val crInfo: String
    fun crLeer(anteil: String, trend: Int?, us: Boolean): String
    val crOptionen: String
    fun crOptionenWert(long: String): String
    fun evErwartet(eps: String?, umsatz: String?): String
    val evLetzteZahlen: String
    fun evQuartal(ende: String): String
    val rrErholung: String
    val rrNochNicht: String
    fun rrVonBis(von: String, bis: String): String
    val wzRueckblick: String
    val rvKnopf: String
    val wzRueckblickUnter: String
    val wzEinmal: String

    // 5.92 Maerkte
    val maerkteAusblick: String
    val infoErklaerung: String
    val mpVomHochKurz: String
    val cotInfo: String
    fun cotKern(markt: String?, index: Int?): String
    fun cotSatz(index: Int): String
    fun cotDetail(anteil: String, aenderung: String, index: Int): String
    val sbSorgen: String
    val sbSorgenMehr: String
    val trZumEtf: String
    val trZumWert: String
    fun trKursLese(zeit: String, kurs: String, erwaehnungen: Int): String
    val rotTitel: String
    val rotInfo: String
    val rotKeine: String
    fun rotGrund(grund: String): String
    val rotAus: String
    val rotNach: String
    val meTitel: String
    val meInfo: String
    fun meThema(schluessel: String): String
    val zeTitel: String
    fun zeSatz(schritte: Double?): String
    val zeHeute: String
    fun zeIn(monate: Int): String
    val blTitel: String
    val blInfo: String
    fun blStufe(stufe: Int): String
    fun blSatz(rot: Int, alle: Int, heiss: String?): String
    val blDafuer: String
    val blDagegen: String
    val blBranchen: String
    fun blFarben(rot: Int, gelb: Int, gruen: Int): String
    fun blGezaehlt(n: Int): String
    fun blGruppe(titel: String, rot: Int, n: Int): String
    val engUebersichtTitel: String
    fun engUebersichtUnter(datum: String): String
    val engAlleGlobus: String
    fun blSektor(jahr: String, zu200: String): String
    fun blName(schluessel: String): String
    fun blLage(schluessel: String, ampel: Int): String
    // Karte je Blasenzeichen (ui/BlasenKarte.kt): was es misst, warum es warnt, seine Schwaeche; leer bei unbekanntem Schluessel
    fun blkKern(schluessel: String): String
    fun blkWarum(schluessel: String): String
    fun blkSchwaeche(schluessel: String): String
    // Schwellen mit Einheit; faellt: niedrige Werte warnen, rotUeber: Rot erst ueber der Schwelle
    fun blkSchwellen(gelb: String, rot: String, faellt: Boolean, rotUeber: Boolean): String
    val blkSchnitt: String
    val blkVerlauf: String
    fun blkStand(datum: String): String
    val blkStaerke: String
    fun blkStaerkeWert(stufe: Int, gemessen: Boolean): String
    fun blkRang(anteil: String): String
    val blkKredite: String
    val blkBip: String
    // Begriff im Glossar des Finanzwissens, null ohne passenden Eintrag
    fun blkBegriff(schluessel: String): String?

    // 5.92 Copytrading und Politik
    fun copyOgeNeu2(n: Int, datum: String): String
    val pdfLaden: String
    val pdfKurz: String
    val pdfGestartet: String
    val pdfFehler: String
    fun parteiKurz(p: String): String
    fun parteiLang(p: String): String
    fun usStaat(code: String, name: String): String
    fun wahlkreisVon(staat: String, nr: Int): String
    fun kammerSeit(senat: Boolean, jahr: Int): String
    val ausschuesse: String
    fun ausschussFunktion(f: String): String?
    val vmHinweisOge: String
    fun rgStand(wann: String): String
    fun rgErklaerung(k: String): String
    val rgText2: String
    fun rgPunkte(p: String): String
    fun rgAusKaeufen(n: Int): String
    fun rgTreffer(besser: Int, von: Int): String
    fun rgKaufsumme(betrag: String): String
    val kaText2: String
    fun awKaeufeDanach(n: Int): String
    fun awVerkaeufeDanach(n: Int): String
    val awImPlus: String
    val awBesserAlsMarkt: String
    val awGefallen: String
    val awSchwaecherAlsMarkt: String
    val awMitte: String
    fun awUnusualKurz(anzahl: String, schnitt: String): String
    fun awVorAnstieg(n: Int): String
    fun awVorRueckgang(n: Int): String
    fun awMitZufall(anteil: String, zufall: String): String
    fun awHinweise(bewertet: Int, werte: Int): String
    fun ifFilterZahl(n: Int): String
    fun ifTreffer(n: Int): String
    val ifKeine: String
    val ifRolle: String
    val ifGroesse: String
    val ifMehr: String
    val ifOhnePlan: String
    val ifHinweis: String
    fun ifAb(betrag: String): String
    fun ifTage(t: Int): String
    val pmGrossGroesste: String
    val pmProfis: String
    val pmProfisLaedt: String
    val pmProfisKeine: String
    val pmProfisText: String
    val pmOhneSport: String
    val pmGrossText2: String
    fun pmGrossZeit2(key: String): String
    fun pmProfiPlatz(rang: Int, kategorie: String, monat: Boolean): String
    fun pmProfiGrund(g: String): String

    // 5.92 Aktienfinder und Radar
    val afTitel: String
    val afStart: String
    val afLaedt: String
    val afDetailsLaedt: String
    fun afDetailsMehr(n: Int): String
    val afKeine: String
    fun afBereich(b: String): String
    fun afEinordnung(e: String): String
    fun afGrund(k: String, gut: Boolean, wert: String, vergleich: String?): String
    val afGewichte: String
    val afGewichteHinweis: String
    val afStandard: String
    fun afSortierung(name: String): String
    val afPunktzahl: String
    val afDividende: String
    val afBoersenwert: String
    val afAlleBranchen: String
    val afAlleRegionen: String
    val afAlleLaender: String
    val afAlleGroessen: String
    val afJedeDividende: String
    fun afDividendeAb(p: String): String
    val afJedeBewertung: String
    fun afGroesse(g: String): String
    fun afMindest(p: Int): String
    fun afTreffer(n: Int, gesamt: Int): String
    fun afDatenlage(p: String): String
    val afZurAktie: String
    val afSoRechnet: String
    val afErklaerung: String
    val afHinweis: String
    fun afNachbilden(n: Int): String
    fun afStand(zeit: String): String
    val wdTitel: String
    val wdNav: String
    val wdKeineDaten: String
    val wdClusterTitel: String
    fun wdClusterRendite(halten: Int): String
    val wdClusterIndex: String
    val wdClusterTreffer: String
    val wdClusterMedian: String
    val wdClusterSchlechtester: String
    val wdClusterZahl: String
    fun wdClusterRegel(mindest: Int, fenster: Int, halten: Int, ab: String): String
    val wdClusterName: String
    val wdClusterIndexName: String
    fun wdClusterAchse(halten: Int): String
    val wdKopfJahr: String
    val wdKopfCluster: String
    val wdKopfClusterRendite: String
    val wdKopfIndexRendite: String
    val wdKopfBesser: String
    fun wdClusterAusgenommen(ohne: Int, offen: Int): String
    val wdPutTitel: String
    val wdPutLaedt: String
    val wdPutLeer: String
    val wdPutProtokoll: String
    val wdPutAbgeschlossen: String
    val wdPutNominal: String
    val wdPutRealisiert: String
    val wdPutVerlust: String
    val wdPutSchlechtester: String
    val wdPutNominalKurz: String
    val wdPutRealisiertKurz: String
    fun wdPutSeit(datum: String, offen: Int): String
    val wdKopfAktie: String
    val wdKopfVerfall: String
    val wdKopfStrike: String
    val wdKopfNominal: String
    val wdKopfRealisiert: String
    val wdPutOffen: String
    val wdKongressTitel: String
    val wdKongressAbstand: String
    val wdKongressRechnen: String
    val wdIpoTitel: String
    val wdIpoTag1: String
    val wdIpoMinus: String
    val wdIpoDrei: String
    val wdIpoUnterMarkt: String
    fun wdIpoRegel(n: Int, von: Int, bis: Int): String
    val wdKopfIpos: String
    val wdKopfTag1: String
    val wdKopfMinus: String
    val wdKopfDrei: String
    val wdAlleJahre: String
    val wdWenigerJahre: String
    val afWeniger: String
    val afCsv: String
    val afCsvSpalten: List<String>
    fun afCsvFertig(n: Int): String
    fun afCsvDatei(name: String): String
    fun afMehrZeigen(n: Int): String
    val radStart: String
    val radNeu: String
    val radLaedt: String
    val radKeine: String
    fun radArt(art: String): String
    fun radSignal(art: String, n: Int, a: String, b: String, text: String): String
    val radBekannt: String
    fun radArten(n: Int): String
    val radSoRechnet: String
    val radErklaerung: String
    fun radStand(zeit: String): String
    val radHeute: String

    // 5.92 Fruehindikatoren
    val fiTitel: String
    val fiInfo: String
    fun fiName(schluessel: String): String
    fun fiLagerSatz(lage: Int, abweichung: String, unter: Boolean, jahre: Int): String
    fun fiDuerreSatz(punkte: String, mehr: Boolean, rekord: Boolean, jahre: Int): String
    fun fiAntraegeSatz(anstieg: String, ampel: Int): String
    fun fiFlugSatz(veraenderung: String, mehr: Boolean): String
    fun fiErklaerung(schluessel: String): String
    fun fiStand(datum: String): String
    fun fiWoche(aenderung: String): String
    fun fiBereich(von: String, bis: String, jahre: Int): String
    fun fiMioBarrel(wert: String): String
    fun fiMrdKubikfuss(wert: String): String
    fun fiMioTag(wert: String): String
    val fiPunkte: String
    fun fiSpr(wert: String, woche: String?): String
    val fiOhneZugang: String
    val zgAgsiName: String
    val zgAgsiZweck: String
    val zgAgsiPunkte: List<String>
    val zgAgsiHolen: String
    val fiAntraegeTief: String
    val fiBrancheTitel: String

    // 5.92 Vermoegen Regierung
    fun ogeVmTitel(art: String, jahr: Int): String
    val ogeVmHinweis: String
    fun ogeVmPdf(datum: String): String
    // 5.92 Aktienfinder Bilanz
    val afNurSolide: String
    fun afBilanzStand(n: Int, gesamt: Int): String
    val afNeuOrdnen: String
    val afBilanzWeiter: String
    val afBilanzFolgt: String
    fun afBilanzZahlen(f: String?, zone: String?): String
    // 5.92 Ersatzquellen
    fun ersSenatIntro(stand: String): String
    val ersSenatGlocke: String
    fun ersBlName(schluessel: String): String?
    fun ersBlLage(schluessel: String, ampel: Int): String?
    val ersTitel: String
    val ersInfo: String
    fun ersName(schluessel: String): String
    fun ersSatz(schluessel: String, ampel: Int): String
    fun ersErklaerung(schluessel: String): String
    fun ersStand(monat: String): String
    fun ersVorjahr(wert: String): String
    val ersHnTitel: String
    fun ersHnZeile(woche: Int, schnitt: String): String
    fun ersMastodonTitel(tag: String): String
    val ersNetzHinweis: String
    fun ersMrdDollar(wert: String): String
    fun ersQuartal(q: Int, jahr: Int): String
    // 5.92 Eingepreist
    val epTitel: String
    // richtung +1 eher long, -1 eher short, 0 keine klare Richtung; was: worauf, falls nicht aus der Seite klar
    fun epTendenz(richtung: Int, was: String?): String
    val epWasAnleihen: String
    val epWasZyklisch: String
    val epWasAirlines: String
    fun epWasKorn(symbol: String): String
    val epInfo: String
    fun epRisiken(mass: String): String
    // urteil: 0 passt, 1 Markt hoeher, 2 Markt tiefer; lage: -1 knapp, 0 ueblich, 1 reichlich
    fun epLagerSatz(urteil: Int, lage: Int, gas: Boolean, kurve: Boolean): String
    fun epDuerreSatz(urteil: Int, viel: Boolean): String
    fun epFlugSatz(urteil: Int): String
    fun epAntraegeSatz(urteil: Int): String
    fun epZyklischSatz(urteil: Int): String
    val epKeine: String
    val epUnsicher: String
    fun epKurve(spanne: String, teurer: Boolean): String
    fun epPreis(abstand: String, ueber: Boolean, name: String): String
    fun epAirlines(abstand: String, besser: Boolean): String
    fun epZins(abstand: String): String
    fun epZyklisch(abstand: String, besser: Boolean): String
    fun epHeute(wert: String): String
    fun epUeblich(wert: String): String
    fun epSelten(anteil: String, hoeher: Boolean): String
    fun epStaerke(staerke: Int, anteil: String): String
    fun epZeitraum(wochen: Int, von: String, bis: String): String
    val epKurveTitel: String
    val epZusatzTitel: String
    fun epKorn(symbol: String): String
    // Datierte Signale
    fun epSeit(art: String, mehr: String, index: String?): String
    fun epLauf(lauf: Int): String
    fun epFrueher(schnitt: String, n: Int): String
    fun epRichtwert(wert: String): String
    fun epHandelstage(n: Int): String
    val epSeitInfo: String
    val epKaeufeTitel: String
    val epKaeufeHinweis: String
    fun epKaufZeile(datum: String, mehr: String, index: String): String
    val epAnalystenTitel: String
    val epInsiderTitel: String
    // 5.92 Finder-Test
    val afRueckblickTitel: String
    val afRueckblickAlt: String
    fun afRueckblickText(von: String, bis: String, werte: Int, top20: String, msci: String, spx: String, treffer: String): String
    fun afRueckblickGrenzen(bilanzAb: String?, ganz: Boolean, ic: Double, icSe: Double, bewertung: Pair<Double, Double>?, abdeckung: String?): String
    // 5.92 Weitere Quellen
    fun wqName(k: String): String
    fun wqSatz(k: String, ampel: Int, steigt: Boolean): String
    fun wqErklaerung(k: String): String
    fun wqWoche(wert: String): String
    fun wqBahnTeile(wagen: String?, container: String?): String
    fun wqWocheBis(tag: String): String
    fun wqStandTag(tag: String): String
    fun wqEinheiten(n: String): String
    val wqFbxLink: String
    val wqBalticLink: String
    val wqComexLink: String
    val lmeTitel: String
    fun lmeStand(tag: String): String
    fun lmeTonnen(zahl: String): String
    val lmeVormonat: String
    fun lmeName(kuerzel: String): String
    fun lmeVerlauf(monate: Int): String
    val lmeBrowser: String
    val lmeEinlesen: String
    val lmeUnbekannt: String
    val comexTitel: String
    fun comexName(schluessel: String): String
    fun comexMenge(zahl: String, einheit: String): String
    val comexVorigerTag: String
    val comexLieferbar: String
    val comexEingelagert: String
    fun comexVerlauf(tage: Int): String
    val wqFinraLink: String
    fun wqBrowserText(land: String): String
    fun wqBrowserLink(land: String): String
    val wqRichterTitel: String
    val wqRichterIntro: String
    val wqRichterOhneZugang: String
    val wqRichterLeer: String
    val wqRichterLaedt: String
    val wqRichterName: String
    fun wqRichterZeile(jahr: Int, tag: String): String
    fun wqSpanne(von: String, bis: String?): String
    val wqJefsLink: String
    val wqParlamenteTitel: String
    val wqRegisterBrowser: String
    val wqEuLink: String
    val wqAusHausLink: String
    val wqAusSenatLink: String
    val wqBskyTitel: String
    fun wqBskyZeile(woche: Int, vorwoche: Int, gekappt: Boolean): String
    val wqXLink: String
    val wqStocktwitsLink: String
    val wqClName: String
    val wqClZweck: String
    val wqClPunkte: List<String>
    val wqClFeld: String
    val wqClHolen: String
    val wqBskyZweck: String
    val wqBskyPunkte: List<String>
    val wqBskyHandle: String
    val wqBskyPasswort: String
    val wqBskyUngueltig: String
    val wqBskyHandleUngueltig: String
    val wqBskyHolen: String
    fun wqMeldung(m: String): String
    // 5.92 Tendenz-Rueckblick
    fun trFrueher(rot: Boolean, treffer: String, grund: String, wochen: Int, mittel: String, faelle: Int): String
    val trOhneVorteil: String
    fun trNichtGeprueft(zuKurz: Boolean): String
    fun trNach(wochen: Int): String
    val trDetailTitel: String
    fun trDetailZeile(wochen: Int, treffer: String, grund: String, mittel: String, faelle: Int, belegt: Boolean): String
    fun trGeprueft(von: String, bis: String): String
    fun trPunkte(wert: String): String
    fun trIdeeTitel(call: Boolean, name: String): String
    fun trIdeeDaten(basis: String, verfall: String, hebel: String): String
    fun trIdeeRisiko(zeitwert: String): String
    val trIdeeHinweis: String
    fun trBasisName(symbol: String): String
    val trInfo: String

    // 5.92 Investorenbriefe
    val ibTitel: String
    fun ibRichtung(r: Int): String
    fun ibRichtungKurz(r: Int): String
    fun ibArt(art: String): String
    val ibSecBericht: String
    fun ibBeleg(beleg: String, stand: String): String
    fun ibPasst(passt: String): String
    val ibWerbung: String
    fun ibQuelle(wo: String): String
    val ibLeer: String
    val ibSuchen: String
    val ibSecHinweis: String

    // 5.92 Textrunde
    val indFussTitel: String

    // 5.93 depotverlauf
    val pvYtd: String
    fun ptGapBeats(v: String): String
    fun ptGapTrails(v: String): String
    // 5.93 briefe
    // 5.93 Investorenbriefe: kurzer Infotext, Mehr aus dem Brief
    val ibInfoKurz: String
    val ibMehrBrief: String

    // 5.93 nachrichten2
    val rbMitteilung: String
    val rbMitteilungHinweis: String
    val notifWerkHinweis: String
    // 5.93 strategielabor
    val slEigen: String
    val slEigenHinweis: String
    fun slLotterieErgebnis(pruefBesser: Boolean, echt: Boolean, anzahl: Int, prozent: String): String
    // 5.93 hintergrund
    val rrHintRendite: String
    val rrHintBeta: String
    val rrHintKorrelation: String
    val lvKurz: String
    // 5.93 finder-radar
    val afAnsichtListe: String
    val afAnsichtRadar: String
    val afImGespraech: String
    val afRadarAlle: String
    val afRadarErwaehnungen: String
    val afRadarWikipedia: String
    fun afRadarStaerke(n: String): String
    val afRadarHinweis: String
    // 5.93 stimmung
    val sbWsbUnklar: String
    val sbWsbNachFall: String
    val sbWsbNachAnstieg: String
    fun sbWsbAnlass(ticker: String, aenderung: String): String
    fun sbWsbUnklarMehr(prozent: Int): String
    val sbWsbOffen: String
    val sbVix: String
    fun sbVixMehr(vix: String): String
    fun tdText(richtung: Int, ziel: String): String

    // 5.93 ipo
    val secIpoTitel: String
    val secIpoKontakt: String
    val secIpoLeer: String
    val secIpoProspekt: String
    val secIpoAenderung: String
    val secIpoPreis: String

    // 5.93 finder-punkte
    // 5.93 Aktienfinder: kaufen und halten im Rueckblick
    val afHaltenTitel: String
    fun afHaltenSeit(von: String, jahre: String, top20: String, msci: String, spx: String): String
    fun afHaltenZeile(jahre: String, top20: String, msci: String, spx: String): String
    fun afHaltenHinweis(fehlt: String): String

    // 5.93 kennzahlen
    val bkKursCashflow: String
    val bkZinsdeckung: String
    val bkRoic: String
    val bkOwnerEarnings: String

    // 5.93 halter-gewinne
    val hlTitel: String
    val hlInstitutionen: String
    val hlInsider: String
    fun hlAnzahl(n: Int): String
    val hlGroesste: String
    fun hlStand(datum: String): String
    fun erTitel(n: Int): String
    fun erEtikett(tiefe: Int, datum: String): String
    val hlHinweis: String
    fun zuNaechster(datum: String): String
    // 5.93 maerkte
    // Maerkte (0.76, q78f)
    val korbTitel: String
    fun korbName(i: Int): String
    fun korbSteigen(n: Int, m: Int): String
    val indexBeitragTitel: String
    fun indexPunkte(zahl: String): String
    val moverRvolFilter: String
    fun moverRvol(v: String): String
    fun moverGrund(i: Int): String
    fun moverZeit(i: Int): String
    fun nwArt(i: Int): String
    fun nwKursAb(x: Int): String
    val stTitel: String
    val stKgv: String
    val stRendite: String
    val stMarktwert: String
    fun ipoErsterTag(p: String): String
    // [Ausblick 0.70] Gesamtaussage
    val agTitel: String
    val agInfo: String
    fun agQuelleZeile(name: String, gewicht: String, woechentlich: Boolean): String
    fun agInfoRest(standard: String): String
    val agBlasenTitel: String
    fun agBlasenZahl(rot: Int, gesamt: Int): String
    fun agBlasenStand(schlaegtAn: Boolean): String
    fun agZustand(schluessel: String, positiv: Boolean): String
    val agStaerkeInfo: String
    fun agRichtung(urteil: Int): String
    fun agZeile(monate: Boolean): String
    fun agStaerke(staerke: Int): String
    fun agMarkt(markt: Int): String
    fun agWort(urteil: Int): String
    val agWochen: String
    val agMonate: String
    val agWeitere: String
    val agZuWenig: String
    val agWaageTitel: String
    fun agPositiv(n: Int): String
    fun agNeutral(n: Int): String
    fun agNegativ(n: Int): String
    val agDafuer: String
    val agDagegen: String
    val agHinweis: String
    val agEinzelTitel: String
    val meKernTitel: String
    fun meKern(zins: Int?, rezession: Int?): String
    fun meFalls(schluessel: String): String
    fun meErreicht(schwellen: String): String
    fun fiDeutung(schluessel: String): String

    // 5.93 stimmung2
    val sbSicher: String
    fun sbSicherMehr(unterschied: String): String

    // 5.93 selbstabruf
    fun saWartet(ordner: String): String
    val saSeiteUebernehmen: String
    val saFehler: String

    // 5.93 nacharbeit
    fun trRangAenderung(plaetze: Int, hoeher: Boolean): String

    // 5.94
    val anHochgestuft: String
    val anAbgestuft: String
    fun lvStufe(stufe: Int): String
    fun trHeuteKurz(rang: Int, veraenderung: String?): String
    fun trSummenKurz(woche: String, monat: String?, jahr: String, seit: String?, spitze: Pair<String, String>?): String
    fun ersMastodonKurz(beitraege: Int): String
    val nwEinordnung: String
    val nwZurAktie: String
    val nwErwaehnt: String
    val ampelGut: String
    val ampelAchtung: String
    val ampelKritisch: String
    val ampelHinweis: String
    val wlSortMarktwert: String
    fun epWasBranche(markt: String): String
    fun epBranche(markt: String, abstand: String, besser: Boolean): String
    fun epErsatzSatz(schluessel: String, markt: String, urteil: Int): String
    fun ipoReiter(reiter: Int): String
    fun ipoStatusTag(status: Int, datum: String): String
    val ipoReiterLeer: String
    val ipoEckdaten: String
    val ipoPreis: String
    val ipoAktien: String
    val ipoVolumen: String
    val ipoEinschaetzung: String
    val ipoRitter: String
    val ipoBrancheTitel: String
    fun ipoBranche(b: Int): String
    fun ipoBrancheText(b: Int): String
    val ipoUeber: String
    val ipoDiskussion: String
    val ipoKeineSchlagzeilen: String
    val ipoDokumente: String
    fun ipoFormular(f: String): String
    val ipoEdgarLink: String
    val ipoKeineEckdaten: String
    val ipoEdgarSuche: String
    fun ipoKatalogGeplant(stand: String): String
    fun bkBankSatz(kbv: String, roe: String, angemessen: String?): String
    fun bkBankUnter(kapitalkosten: String, wachstum: String): String
    fun bkBankBilanzSatz(roa: String?, ekQuote: String?): String
    val bkBankBilanzUnter: String
    val heatmapAlle: String
    val heatmapZoomZurueck: String
    fun skKnoten(id: String): String
    val skGeldfluss: String
    val skVerkaeufe: String
    fun skGuvTitel(jahr: String): String
    fun afVorlage(id: String): String
    fun afVorlageIdee(id: String): String
    fun afVorlageFehlt(id: String): String?
    fun afFeld(id: String): String
    // [0.72] Waage und Indikatoren in der Zeile eines Werts
    fun afWaage(wert: Int): String
    fun afRsi(wert: String): String
    fun afLinieKurz(tage: Int, abstand: String): String
    val afVorlageKeine: String
    val afVorlageNurUs: String
    val afVorlageHinweis: String
    fun afVorlageStand(offen: Int): String
    val afVorlageWeiter: String
    val afAlarm: String
    val afAlarmHinweis: String
    fun afAlarmTitel(vorlage: String): String
    fun afAlarmText(namen: List<String>, weitere: Int): String
    fun afFeldAb(feld: String, wert: String): String
    fun afFeldBis(feld: String, wert: String): String
    fun afFeldZwischen(feld: String, von: String, bis: String): String
    fun afFeldPositiv(feld: String): String
    fun afFeldNegativ(feld: String): String
    fun afUeberLinie(id: String): String
    fun afUnterLinie(id: String): String
    fun afNeuesHoch(tage: Int): String
    fun afNahHoch52(abstand: String): String
    fun afZahlenIn(tage: Int): String
    val afKreuzung: String
    fun afSortFeld(feld: String, aufsteigend: Boolean): String
    val widgetNewsInhalt: String
    val widgetNewsDepot: String
    val widgetNewsWichtig: String
    val widgetNewsWichtigTitel: String
    val widgetNewsWichtigKeine: String
    val widgetNewsAlle: String
    val widgetNewsAlleTitel: String
    fun widgetNewsFeed(name: String): String
    val widgetNewsListeKeine: String
    val widgetSchnellWahl: String
    val widgetSchnellHinweis: String
    val widgetSchnellVerlauf: String
    val widgetEinzelSuche: String
    fun apTitel(id: String): String
    fun apWert(id: String, a: String, b: String, zahl: Int): String
    fun apErklaerung(id: String): String
    val apUeberschrift: String
    val apZuWenig: String
    val apHinweis: String
    val scTitel: String
    val scFragen: List<String>
    fun scStand(ja: Int, alle: Int): String
    fun anHaelt(anteil: String, wert: String, stichtag: String, seit: String?): String
    val anHalterHinweis: String
    fun anHalterKeiner(n: Int): String
    val hrTitel: String
    val hrTrefferTitel: String
    val hrVorsprung: String
    val hrHausLabel: String
    val hrKeine: String
    fun hrUnter(n: Int, von: String, bis: String): String
    fun hrUrteile(n: String): String
    fun hrStand(stand: String, von: String, bis: String, aktien: Int, urteile: Int): String
    fun hrInfo(min: Int): String
    fun hrSortierung(name: String): String
    fun hrAnzahl(n: Int): String
    fun hrUrteileTreffer(urteile: String, treffer: String): String
    val finJahreTitel: String
    val finLangfristSchulden: String
    val finInvestitionen: String
    fun umsatzSchaetzung(quartal: String): String
    val qzTitel: String
    val qzBekannte: String
    val qzAlle: String
    val qzFehlt: String
    val qzTagFehlt: String
    val qzKeine: String
    fun qzKeineBekannten(n: Int): String
    fun qzZeit(zeit: Int): String
    val qzHeute: String
    fun qzVon(gezeigt: Int, gesamt: Int): String
    val qzEigen: String
    fun qzErwartet(wert: String): String
    fun qzVorjahr(wert: String): String
    fun qzUmsatz(wert: String): String
    // 5.95 Lesemodus
    val lesemodusLeer: String
    // 5.95 Gesamtstimmung
    val sgTitel: String
    val sgKachel: String
    val sgAnbieter: String
    fun sgAnzahl(n: Int, von: Int): String
    val sgLaedtNoch: String
    val sgGewichte: String
    fun sgGruppe(schluessel: String): String
    fun sgGruppeWarum(schluessel: String): String
    fun sgTeil(schluessel: String): String
    fun sgMethode(schluessel: String): String
    fun sgRoh(schluessel: String, zahl: String): String
    fun sgHochTief(hochs: Int, tiefs: Int): String
    fun sgGewicht(prozent: String): String
    fun sgStand(datum: String): String
    fun sgFehlen(namen: String): String

    // 5.95 Lizenzen
    val lizenzenTitel: String
    val lizenzenHinweis: String
    val lizenzenFehlt: String
    // 5.95 Nasdaq
    val nkNichtVerfuegbar: String
    val nkIpoFehlt: String
    val nkQzHinweis: String
    val nkDivTitel: String
    fun nkZahltag(datum: String): String
    fun nkJeJahr(wert: String): String
    val nkDivKeine: String
    fun nkDivKeineBekannten(n: Int): String
    val nkDivHinweis: String
    val nkSplits: String
    val nkSplit: String
    val nkZusammenlegung: String
    fun nkSplitText(neu: Int, alt: Int): String
    val nkWiTitel: String
    val nkWichtige: String
    val nkGanztags: String
    fun nkVorher(wert: String): String
    val nkEuroraum: String
    val nkWiKeine: String
    val nkWiHinweis: String

    // 5.95 Quellen
    val q5Nicht: String
    val q5VxTitel: String
    fun q5VxVerhaeltnis(q: String): String
    val q5VxMehr: String
    val q5IgPrivat: String
    val q5IgJa: String
    val q5IgNein: String
    val q5IgKeine: String
    val q5IgSeite: String
    fun q5IgQuelleMeldung(quelle: String): String
    val q5IgOeffnen: String
    val q5IgAlleTitel: String
    val q5IgAlleUnter: String
    val q5IgKategorie: String
    val q5NnKeine: String
    val q5MnbKeine: String
    val q5EodTitel: String
    val q5EodKeine: String
    val q5EodQuelle: String
    val q5EodLimit: String
    val q5IgQuelleOhne: String
    val q5StandardArtikel: String
    // 5.95 Long/Short
    fun opUsNotierung(kuerzel: String): String

    // 5.95 Stimmrechte GB
    val srTitel: String
    val srKeine: String
    val srOhne: String
    fun srNachher(p: String): String
    fun srVorher(p: String): String
    fun srUnter(p: String): String
    fun srAktien(p: String): String
    fun srInstrumente(p: String): String
    fun srSchwelleUeber(p: String): String
    fun srSchwelleUnter(p: String): String
    fun srStichtag(d: String): String
    fun srPunkte(z: String): String
    val srHinweis: String

    // 5.96 Labor
    val slRegelWaehlen: String
    val slKeineLinie: String
    fun slLinieSchalter(regel: String): String

    // 5.96 Daten
    fun hlUnter(p: String): String
    val hlUnveraendert: String
    fun hlMitNamen(n: Int, gesamt: Int): String
    val hlGemeldet: String
    val hlKeineNamen: String
    val hlFonds: String
    val hlFondsHinweis: String
    // 0.77 13F-Gesamtbild
    fun g13Kopf(quartal: String): String
    val g13Halter: String
    fun g13Netto(kaeufer: Boolean): String
    val g13Stueck: String
    val g13Bestand: String
    val g13Groesste: String
    fun g13KernHalter(n: String): String
    fun g13KernNetto(n: String, kaeufer: Boolean): String
    fun g13Stand(melder: String, bis: String): String
    val g13Hinweis: String
    val g13Titel: String
    fun g13KernMelder(n: String, quartal: String): String
    val g13Gekauft: String
    val g13Verkauft: String
    fun g13Weitere(n: Int): String
    val g13Weniger: String
    fun g13Unten(zufluss: Boolean, betrag: String, halter: String): String
    val vmWerte: String
    val vmSchulden: String
    val vmNetto: String
    val vmKeineSchulden: String
    fun vmImMinus(spanne: String): String
    fun vmPosten(werte: Int): String
    fun vmNettoOffen(schlecht: String?, gut: String?): String
    val vmRangErklaerung: String

    // 5.96 Vergleich
    fun vgMax(n: Int): String
    val vgSuche: String
    val vgAktienEtfs: String
    val vgPolitiker: String
    val vgPolitikerMehr: String
    val vgLabor: String
    fun vgLaborUnter(name: String): String
    val vgHaus: String
    val vgSenat: String
    val vgRegierung: String
    val vgKeineTreffer: String
    fun vgZeitraum(von: String, bis: String): String
    val vgHeute: String
    fun vgKuerzer(name: String, ab: String): String
    val vgLaedt: String
    val vgKeineDaten: String
    val vgZuKurz: String
    fun vgGeschaefte(n: Int): String
    val vgWaehrungHinweis: String
    val vgPlanNote: String
    val vgGleicheRaten: String
    val vgLaborHinweis: String
    fun vgOhneDaten(namen: String): String
    fun vgWenigGeschaefte(namen: String): String
    val vgVergleichIntro: String

    // 5.96 Feed
    val feedErstellen: String
    val feedBearbeiten: String
    val feedName: String
    val feedZugaenge: String
    val feedQuellenApp: String
    val feedPruefen: String
    fun feedZugangOk(titel: String, n: Int): String
    val feedNichtErreichbar: String
    val feedKeinFeed: String
    fun feedFehler(f: de.troetenrepublik.app.engine.EigenerFeed.Fehler): String
    fun feedSchonInApp(name: String): String
    val feedSchonDrin: String
    val feedQuelleAus: String
    fun feedAusgeschaltet(namen: String): String
    val feedLeer: String
    val feedOhneQuelle: String
    val feedLoeschen: String
    fun feedLoeschenFrage(name: String): String
    val feedLoeschenText: String
    fun feedZuViele(n: Int): String
    fun feedMeldeOption(stufe: String): String
    fun feedMeldeOptionHint(stufe: String): String
    val feedMelden: String
    val feedMeldeQuellen: String
    val feedOhneFeeds: String
    val feedBuendel: String

    // 5.96 Kongress
    val copyFormer: String
    val copyMitgliederNote: String
    fun kammerZeitraum(senat: Boolean, von: Int, bis: Int): String
    fun amtZeitraum(von: Int, bis: Int): String

    // 5.97 Aufklappen
    val aufZahlenHerkunft: String

    // 5.97 Finanzen
    val finDivTitel: String
    val finDivJeAktie: String
    val finDivExTag: String
    val finDivZahltag: String
    val finDivBestand: String
    val finVerlust: String
    fun finWechsel(art: String): String
    val trErkReddit: String
    val trErkRedditDeutsch: String
    fun trErkVerlauf(seit: String?): String
    val trErkBeitraege: String
    val trErkYahoo: String
    val trErkWiki: String
    val trErkHn: String
    fun trErkMastodon(tag: String): String
    val trErkBluesky: String

    // 5.97 Quartalszahlen
    fun qzUhrKurz(art: Int, von: String?, bis: String?): String?
    fun qzUhrLang(art: Int, von: String?, bis: String?): String
    fun qzZeitTag(zeit: String, versatz: Int): String
    val qzGeschaetzt: String
    fun qzZeitzone(zone: String): String
    val qzbZurAktie: String
    fun qzbKalenderNotierung(symbol: String): String
    val qzbKalenderNotierungText: String
    fun qzbQuartal(quartal: String): String
    val qzbTermin: String
    val qzbTag: String
    val qzbUhrzeit: String
    val qzbKonferenz: String
    fun qzbTerminSpanne(von: String, bis: String): String
    val qzbGeschaetztText: String
    val qzbUhrText: String
    val qzbZahlen: String
    val qzbGewinn: String
    val qzbUmsatz: String
    fun qzbErwartetVon(quelle: Int, analysten: Int?): String
    val qzbErwartet: String
    val qzbSpanne: String
    fun qzbVonBis(von: String, bis: String): String
    val qzbVorjahr: String
    val qzbGemeldet: String
    val qzbAusblick: String
    val qzbAusblickText: String
    val qzbKeineErwartung: String
    val qzbBewertung: String
    fun qzbGewinnWachstum(a: String, b: String, p: String): String
    fun qzbGewinnRueckgang(a: String, b: String, p: String): String
    fun qzbGewinnAusVerlust(a: String, b: String): String
    fun qzbVerlustErwartet(a: String, b: String?): String
    fun qzbUmsatzVeraenderung(a: String, b: String, p: String): String
    fun qzbSpanneWeit(a: String, b: String, n: Int?): String
    fun qzbSpanneEng(a: String, b: String, n: Int?): String
    fun qzbSchaetzungHoch(a: String, b: String, p: String, n: Int?, m: Int?): String
    fun qzbSchaetzungRunter(a: String, b: String, p: String, n: Int?, m: Int?): String
    fun qzbSchaetzungGleich(a: String): String
    fun qzbTreffer(n: Int, m: Int, p: String): String
    fun qzbGemeldetUeber(a: String, b: String, p: String): String
    fun qzbGemeldetUnter(a: String, b: String, p: String): String
    fun qzbGemeldetWie(a: String, b: String): String
    val qzbKeineBewertung: String
    val qzbVerlauf: String
    fun qzbQuartalBis(datum: String): String
    val qzbVonWem: String
    fun qzbKalenderZeile(schaetzungen: Int?): String
    fun qzbAnalystenZeile(gewinn: Int?, umsatz: Int?): String
    fun qzbHaeuser(namen: String): String
    val qzbVonWemText: String
    val qzbYahooFehlt: String
    // 0.68 Finanzen
    fun finErklaerung(label: String): String?
    val finUmsatzSchaetzungErklaerung: String
    val finQuartal: String
    val finJahr: String
    val finAbweichung: String
    // 0.68 Stimmung und Lager
    val sgKurz: String
    val sgRechnungTitel: String
    val sgRechnung: String
    val sgBarometerTitel: String
    val sgTeileTitel: String
    fun fiRichtung(schluessel: String, mehr: Boolean): String
    fun fiLegendeSchnitt(wert: String): String
    fun fiLegendeStand(datum: String, wert: String): String
    fun smMarkt(symbol: String): String
    fun smSeit(datum: String, markt: String, bewegung: String): String
    fun smLauf(lauf: Int): String
    val smNichtMessbar: String
    val smKeineKurse: String
    fun smNochNicht(datum: String): String

    // 0.68 Strategietest
    fun slRegelKurz(art: String, a: Int, b: Int, n: Int): String

    // 0.68 Suchverlauf
    val svKuerzlich: String
    val svLoeschen: String
    val svVerlauf: String
    val svAus: String
    val svAusEinstellung: String
    val kgvVerlust: String
    fun wdReicht(jahre: String): String
    fun wdReichtFolgen(n: Int, von: Int): String
    fun wdReichtVerlaeufe(pct: String): String
    fun svEntfernen(name: String): String

    // 0.68 Tagesrueckblick und Mitteilungen
    val rbUhrzeit: String
    fun rbEinleitungDepot(ton: String, hoch: Boolean, prozent: String): String
    val rbVeraenderung: String
    val rbWert: String
    val mtAlle: String
    fun mtProfil(an: Boolean, aktiv: Int, gesamt: Int): String
    val mtAlleAus: String
    val mtDepot: String
    val mtDepotHinweis: String
    val mtAlarme: String
    val mtAlarmeHinweis: String
    val mtCopy: String

    // 0.68 ipo
    fun ipoSchritt(i: Int): String
    val ipoSchrittZurueck: String
    val ipoPreisSpanne: String
    val ipoSpanneMitte: String
    val ipoSeitAusgabeTitel: String
    fun ipoKursJetzt(p: String): String
    fun ipoAusgabe(p: String): String
    fun ipoPunktKurz(merkmal: Int): String
    fun ipoPunktErklaerung(merkmal: Int): String
    val ipoSchlagzeilen: String
    fun ipoKopfLabel(i: Int): String
    val ipoKopfTage: String
    val ipoGrosse: String
    val ipoBericht: String
    fun ipoLautProspekt(betrag: String): String
    val ipoLautProspektKurz: String
    val ipoVolumenProspekt: String
    fun ipoWeitere(n: Int): String
    fun ipoDavonSpac(n: Int): String
    // Paket q78b (0.76): weitere Alarmarten, Bewegung zu den Zahlen, Dividendenserie
    fun alertArtText(tag: String, extra: String, price: String): String
    val zrTitel: String
    fun zrErwartet(straddle: Boolean): String
    val zrKursNach: String
    fun zrUeblichZeile(pct: String): String
    val zrUeblichBand: String
    val zrEingepreistBand: String
    val zrInfo: String
    val zrEpsReihe: String
    val zrEpsUeberraschung: String
    val zrUmsatzUeberraschung: String
    fun zrEpsStatt(ist: String, schaetzung: String): String
    fun zrSaeule(ist: String, schaetzung: String): String
    val finDivJahreErhoeht: String
    val finDivKauftag: String
    val finDiv5J: String
    fun nkErhoeht(pct: String): String

    // 0.76 q78a: Signale, Meldeverzug, Kongress-Flow, Insiderliste, POTUS
    val sigTitel: String
    val sigKeine: String
    val sgInsider: String
    val sgKongress: String
    val sgStufungen: String
    val sgStufungenKurz: String
    val sgTrends: String
    fun sgTrendWert(rang: Int, aenderung: String?): String
    val sgOptionen: String
    fun sgInsiderWert(kaeufer: Int, verkaeufer: Int): String
    fun sgKaeufeVerkaeufe(kaeufe: Int, verkaeufe: Int): String
    fun sgHochAb(hoch: Int, ab: Int): String
    fun sgHochAbKurz(hoch: Int, ab: Int): String
    fun sgOptionenWert(anzahl: Int, calls: Int, puts: Int): String
    fun sgNetto(betrag: String): String
    fun sgPraemie(betrag: String): String
    val mvMedian: String
    val mvSpaet: String
    val mvGeschaefte90: String
    val mvVolumen90: String
    fun mvTage(n: Int): String
    fun mvUnter(spaet: String, geschaefte: Int, volumen: String): String
    val flTitel: String
    val flNav: String
    val flSektoren: String
    val flKammern: String
    val flEigeneFamilie: String
    val flHaus: String
    val flSenat: String
    val flEigene: String
    val flFamilie: String
    fun flKaeufe(n: Int): String
    fun flVerkaeufe(n: Int): String
    fun flOhneSektor(n: Int): String
    fun usSeitKauf(p: String): String
    fun usVomMarktwert(p: String): String
    val usPlanLabel: String
    fun poStunden(h: Int): String
    // q78h
    val vbKomponenten: String
    val vbKursziele: String
    val vbTief: String
    val vbMedian: String
    val vbHoch: String
    val vbKurs: String
    fun vbStufen(tage: Int, hoch: Int, ab: Int): String
    fun vbAbweichung(wert: String): String
    // richtung: -1 Kurs unter dem Median, 0 am Median, 1 darueber
    fun vbKursMedian(prozent: String, richtung: Int): String
    fun vbImPlus(n: Int): String
    fun vbImMinus(n: Int): String
    val vbPaare: String
    val ifNurFuehrung: String
    // q78i (0.78)
    val akMinVolumen: String
    val akVolumen: String
    val akOffen: String
    val akVerhaeltnis: String
    val akLeer: String
    val stLeer: String
    val stErst: String
    val opNasdaqGamma: String
    fun opGexMischung(echt: Int, naeher: Int): String
    val zmTitel: String
    val zmInhalt: String
    val zmOhneKontakt: String
    val zmKeine: String
    val zmOriginal: String
    val zmAusblick: String
    fun zmVom(tag: String): String
    fun zmVorquartal(wert: String): String
    fun zmArt(key: String): String
    val bhLeihgebuehr: String
    val kgvSchnitt: String
    fun kgvSchnittHinweis(jahr: Int, prozent: String, jahre: Int, ueber: Boolean): String
    // Marktmacht, Branchen, Vorschlagsdepot (0.77)
    val mmTitel: String
    fun mmStufe(s: String): String
    val mmErklaerung: String
    fun mmGrund(k: String, gut: Boolean, wert: String, vergleich: String, jahre: String): String
    fun mmJahre(ueber: Int, alle: Int): String
    fun mmKurz(stufe: String): String
    val afAnsichtBranchen: String
    val afAnsichtDepot: String
    fun fbrZahl(n: Int): String
    val fbrSammel: String
    fun fbrWerte(n: Int): String
    val fbrAlle: String
    val vsdTitel: String
    fun vsdWerte(n: Int): String
    fun vsdSektoren(n: Int): String
    fun vsdLaender(n: Int): String
    fun vsdAuswahl(punkte: Int, bereich: Int, jeBranche: Int, jeSektor: Int, jeLand: Int, deckel: Int): List<String>
    val vsdAuswahlKopf: String
    val vsdPositionen: String
    val vsdGewicht: String
    val vsdLeer: String
    fun vsdSektor(key: String): String
    val vsdSektorKopf: String
    val vsdLandKopf: String
    val vsdNachbilden: String
    val vgTitel: String
    fun vgCeo(jahr: Int): String
    val vgZugeflossen: String
    fun vgUebrige(jahr: Int): String
    val vgRendite: String
    fun vgMio(zahl: String): String
    fun vgMrd(zahl: String): String
    fun vgTsd(zahl: String): String
    val vgInfo: String
    val pdTitel: String
    fun pdArt(art: String): String
    fun pdThema(id: String): String
    fun pdImTitel(was: String): String
    fun pdNeu(anzahl: Int): String
    fun pdZuletzt(datum: String): String
    fun pdAnzahl(n: Int): String
    // Offene US-Register: Studien, Fahrzeug- und Produktrueckrufe (0.77)
    val rgStudienTitel: String
    fun rgStudienLaufend(n: Int): String
    fun rgPhase(n: Int): String
    val rgStudienBald: String
    fun rgStudienBaldKern(datum: String): String
    val rgStudienFertig: String
    fun rgStudienFertigKern(fertig: Int, abgebrochen: Int): String
    fun rgStudieEnde(phase: Int, datum: String): String
    fun rgStudieBeendet(abgebrochen: Boolean, phase: Int, datum: String): String
    val rgStudienInfo: String
    val rgFahrzeugTitel: String
    val rgRueckrufeTitel: String
    fun rgRueckrufeKern(n: Int): String
    fun rgFahrzeuge(zahl: String): String
    val rgUntersuchungenTitel: String
    fun rgUntersuchungenKern(n: Int): String
    fun rgUntersuchungSeit(art: String, datum: String): String
    val rgFahrzeugInfo: String
    val rgProdukteTitel: String
    fun rgProdukteKern(n: Int): String
    val rgProdukteInfo: String
    val rgBeschwerdenTitel: String
    // 0.77 Paket AD5: Begriffe, seltene Kaeufe, Konvergenz, Insider-Simulator
    val wdClusterMedianInfo: String
    val wdPutErklaerung: String
    val skTitel: String
    fun skAussage(art: Int, monate: Int, beginn: Boolean, zahl: Int, faktor: String, jahr: Int): String
    fun skKopf(bis: String, seit: String): String
    val konTitel: String
    val kvNav: String
    fun kvErklaerung(bis: String, quartal: String): String
    val kvDrei: String
    val kvZwei: String
    val kvInsider: String
    val kvKongress: String
    val kvFonds: String
    fun kvInsiderZahl(n: Int): String
    fun kvKongressZahl(n: Int): String
    fun kvFondsZahl(n: String): String
    fun skKern(n: Int): String
    val kvLeer: String
    fun kvWert(insider: Boolean, kongress: Boolean, fonds: Boolean): String
    fun wsMindest(n: Int): String
    fun wsFenster(tage: Int): String
    fun wsBetrag(je: Int, dollar: String): String
    fun wsRolle(nurFuehrung: Boolean): String
    fun wsHalten(tage: Int): String
    fun wsKosten(prozent: String): String
    val wsStandard: String
    val wsKeine: String
    fun wsHinweis(seit: String, kosten: Boolean): String
    val guideKonvergenz: String

    // 0.77 Warnzeichen und Übernahmen aus den 8-K-Meldungen
    val achtInfoWarn: String
    val achtInfoDeal: String
    fun achtWarnTitel(n: Int): String
    fun achtDealTitel(n: Int): String
    fun achtArt(key: String): String
    fun achtErkl(key: String): String
    fun achtRolle(rolle: String): String
    fun achtInsider(n: Int): String
    fun achtGegenseite(name: String): String
    fun achtPreis(preis: String): String
    val achtKursTag: String
    val achtKurs5: String
    fun achtGrund(key: String, zusatz: String): String
    fun achtBericht(form: String, zeitraum: String): String
    fun achtFristDatum(datum: String): String
    fun achtFristTage(tage: Int): String
    fun achtFuehrungDetail(person: String, ereignis: String, sofort: Boolean, wirksam: String, nachfolge: Boolean?): String
    fun achtPrueferDetail(pruefer: String, neu: String, ereignis: String, streit: Boolean, maengel: Boolean): String
    fun achtKorrekturDetail(von: Int, bis: Int): String
    fun achtInsolvenzDetail(kapitel: Int): String
    fun achtAbschreibungDetail(betrag: String): String
    // Seit 0.77 (AD8): Widgets Meistgekauft (13F) und Haus-Rangliste
    fun widgetZufluss(betrag: String): String
    fun widgetGekauftStand(quartal: String): String
    val widgetGekauftLeer: String
    val widgetTrefferanteil: String
    // Seit 0.77: Widgets zu Konvergenz, seltenen Kaeufen, Insider-Simulator, Vorschlagsdepot, Branchen, Warnzeichen, Praesidialdokumenten
    fun widgetKonvergenzStand(bis: String): String
    fun widgetSeltenStand(bis: String): String
    val widgetSeltenLeer: String
    fun widgetSimStand(n: String, jahr: String): String
    val widgetSimLeer: String
    val widgetFinderUngeladen: String
    val widgetVorschlagLeer: String
    val widgetBrancheLeer: String
    val widgetWarnTitel: String
    val widgetWarnUnter: String
    val widgetWarnKeine: String
    val widgetWarnOhneKennung: String
    val widgetWarnNurUs: String
    val widgetWarnFehler: String
    val widgetPraesidialLeer: String
    val widgetBrancheBeste: String
    fun widgetFonds(n: String): String
    fun widgetPunkte(wert: String): String
    val widgetSimVorsprung: String
    val widgetSimFaelle: String
    fun widgetSimSeit(jahr: String): String
    val widgetSimSchlechtester: String

    // Optionsrechner: Erwartung, Kernaussage, Kauf
    val orErwartung: String
    fun orErw(key: String): String
    val orAnpassen: String
    val orGekauft: String
    val orWertAm: String
    val orBerechnet: String
    val orDatumWahl: String
    val orSumme: String
    fun orKaufHint(stueck: String, preis: String, tag: String): String
    fun orKlar(key: String, a: String, b: String, c: String): String
    fun orKernKosten(b: String): String
    fun orKernEinnahme(b: String): String
    fun orKernGewinnUeber(name: String, tag: String, a: String, pct: String): String
    fun orKernGewinnUnter(name: String, tag: String, a: String, pct: String): String
    fun orKernGewinnZwischen(name: String, tag: String, a: String, b: String): String
    fun orKernGewinnAusserhalb(name: String, tag: String, a: String, b: String): String
    val orKernGewinnImmer: String
    val orKernGewinnNie: String
    fun orKernMaxGewinn(b: String): String
    fun orKernMaxVerlust(b: String): String
    val orKernVerlustUnbegrenzt: String
    fun orErkl(key: String): String

    // Glocke je Wertpapier (0.77)
    fun glockeGruppe(gruppe: String): String
    fun glockeArt(art: String): String
    fun glockeArtKurz(art: String): String
    val glockeAlle: String
    val glockePreismarken: String
    val glockeNeueMarke: String
    fun glockeBewegung(pct: String?): String
    fun glockeMarkeKurz(preis: String): String
    fun glockeMarkenKurz(n: Int): String
    fun glockeBewegungKurz(pct: String): String
    val glockeListe: String
    val glockeLeer: String
    val glockeAus: String
    fun alertNeuText(tag: String, extra: String, price: String): String
    fun glockeWeitere(n: Int): String
    // Feste, kurze Bezeichnungen der Widgets (Zeilen brechen dort nie um)
    fun widgetGrundName(schluessel: String, name: String): String
    fun widgetLageName(key: String, zusatz: String): String
    fun widgetCheckName(regel: String): String
    fun widgetBlasenName(schluessel: String, name: String): String
    val widgetStufeGut: String
    val widgetKeineDaten: String
    val widgetZuWenigPositionen: String
    // Verkaufte Optionen (0.77)
    val shVerkauft: String
    fun shGeschaeft(eroeffnung: Boolean): String
    val shAusgleichJeStueck: String
    val shStillhalterErtraege: String
    fun shRueckkaufDone(qty: String, price: String): String
    val shGruppeGeschlossen: String
    fun shAktienGebunden(n: String): String
    fun shVerfallText(name: String, betrag: String, wertlos: Boolean): String
    fun shGebundenZeile(betrag: String): String
    fun shFreiZeile(betrag: String): String
    val shPraemie: String
    fun shJeStueck(preis: String): String
    val shRueckkaufpreis: String
    val shBindung: String
    val shGebundeneAktien: String
    val shStrategie: String
    val shGruppeSchliessen: String
    val shZurueckkaufen: String
    fun shNurGebunden(n: String): String
    val orAusfuehren: String
    // Selbst anstossen (Profil, Zugaenge)
    val anTitel: String
    val anUntertitel: String
    val anGruppeLaden: String
    val anGruppeDateien: String
    val anGruppeBrowser: String
    val anMeldungen: String
    val anKongress: String
    val anFinder: String
    val anRadar: String
    val anNie: String
    val anKeineDatei: String
    val anAlle: String
    val anHeute: String
    val anHeuteNie: String
    fun anLaeuft(prozent: Int?): String
    fun anAm(datum: String): String
    fun anBis(datum: String): String
    fun anMonate(n: Int): String
    fun anOffen(n: Int): String
    // Finder-Rueckblick: Abdeckung der damaligen Mitglieder (jahre: Jahr zu Anteil, erstes und letztes Jahr)
    fun afRueckblickAbdeckung(schnitt: String, jahre: List<Pair<String, String>>, voll: Boolean): String
    // [Laender Historie 0.78]
    val histTitel: String
    val histAktien: String
    val histAnleihen: String
    val histImmobilien: String
    val histZins: String
    fun histReal(titel: String): String
    fun histJeJahrAb(jahr: Int): String
    val histKernInfo: String
    val histOhneRenditen: String
    fun histLueckeInfo(von: Int, bis: Int): String
    val histVermoegen: String
    fun histKrisen(n: Int): String
    val histKrisenInfo: String
    val histSchlecht: String
    val histSchlechtInfo: String
    fun histZeitraum(von: Int, bis: Int): String
    fun histJe(wert: String): String
    val histInflation: String
    fun histInflationInfo(ab: Int): String
    fun histKredit(jahr: Int): String
    val histKreditInfo: String
    val histZitatKopf: String
    val histLizenzJst: String
    fun histAbruf(datum: String): String
    fun hpTitel(ab: Int): String
    val hpPreise: String
    val hpVerhaeltnis: String
    fun hpSeit(ab: Int): String
    fun hpSchnitt(abweichung: Int, ab: Int): String
    fun hpInfo(bis: String): String
    fun hpQuartal(jahr: Int, quartal: Int): String
    fun hpVerlauf(ab: Int): String
    val hpPreisLinie: String
    val hpEinkommenLinie: String
    val hpZitatKopf: String
    val hpNutzung: String
    val globusHaus: String
    val globusAktien1870: String
    val globusAktien1950: String
    val globusLegHaus: String
    val globusLegAktien1870: String
    val globusLegAktien1950: String
    // Rezessionsbaender in Langzeitcharts
    val rzBand: String
    fun rzBandAb(jahr: Int): String
    // Rezessionswahrscheinlichkeit der New York Fed (Fruehsignale im Ausblick)
    val nyName: String
    fun nySatz(stufe: Int, monat: String): String
    fun nyBilanz(schwelle: String, treffer: Int, von: Int, fehlalarme: Int, abJahr: Int): String
    fun nyStand(monat: String): String
    val nyErklaerung: String
    val ensoKernNeutral: String
    fun ensoKern(hoch: String, tief: String): String
    val ensoKurz: String
    val ensoBeiNino: String
    val ensoBeiNina: String
    val ensoMuster: String
    val ensoBullish: String
    val ensoBearish: String
    fun ensoWirkName(k: String): String
    fun ensoWirkGrund(phase: Int, k: String): String
    val tlTitel: String
    val tlMedian: String
    val tlBeispiele: String
    fun tlAktien(n: Int): String
    fun tlNeu(n: Int): String
    // Terminmaerkte gegen das ganze Archiv der CFTC
    fun cotSeit(hoch: Boolean, long: Boolean, seit: String, rekord: Boolean): String
    fun cotRang(rang: Int, abJahr: Int): String
    fun cotWeitere(n: Int): String
    fun cotWeitereUnter(auffaellig: Int): String
    // Hoechster oder tiefster Stand seit (alle langen Reihen)
    fun standSeit(hoch: Boolean, seit: String, rekord: Boolean): String
    // Muster der Vergangenheit im Ausblick
    val vmTitel: String
    fun vmIndex(id: String): String
    fun vmZyklusName(jahr: Int): String
    fun vmZyklusKurz(jahr: Int): String
    fun vmZyklusSatz(positiv: Int, anzahl: Int, jahre: Int, von: Int): String
    fun vmBalkenKopf(name: String, mittel: String, positiv: Int, anzahl: Int): String
    fun vmGrundlage(id: String, von: Int, bis: Int): String
    fun vmMonat(monat: Int): String
    fun vmSaisonName(index: String, monat: String): String
    fun vmSaisonSatz(positiv: Int, anzahl: Int, von: Int): String
    val vmBaerName: String
    fun vmBaerSatz(n: Int, von: Int, tiefe: String, monate: Int): String
    val vmBaerBeginn: String
    val vmBaerTiefe: String
    val vmBaerTief: String
    val vmBaerErholt: String
    val vmBaerJetzt: String
    val vmBaerOffen: String
    val vmBaerEinheit: String

    // [0.78] Historische Annahmen der Rechner, Messung des Rezessionssignals und des Kreditanstiegs
    val hrAnnahmen: String
    val hrEigene: String
    val hrWelt: String
    fun hrLand(name: String): String
    val hrWeltName: String
    val hrRendite: String
    fun hrSpanne(name: String, von: Int, bis: Int): String
    fun hrSchlecht(jahre: Int): String
    fun hrBest(jahre: Int): String
    fun hrZeitraumReal(von: Int, bis: Int): String
    val hrVerlaeufe: String
    val hrZufall: String
    val hrFolgen: String
    val hrStartjahre: String
    fun hrSchlechtStart(jahr: Int): String
    fun hrBestStart(jahr: Int): String
    fun hrVon(n: Int, von: Int): String
    fun hrReichte(n: Int, von: Int, jahre: String): String
    fun hrLeerNach(jahre: Int): String
    fun hrNoteZufall(name: String, von: Int, bis: Int): String
    fun hrNoteFolgen(name: String, von: Int, bis: Int, jahre: Int): String
    fun hrWeltInfo(laender: Int): String
    fun hrRateInflation(infl: String): String
    val hrNoteEnde: String
    fun nyEinbrueche(schwelle: String, treffer: Int, von: Int, abJahr: Int, grund: String): String
    fun hrKreditAnstieg(jahre: Int): String
    fun hrPunkte(wert: String): String
    fun hrKreditInfo(schwelle: String, jahre: Int, mitKrise: Int, faelle: Int, quote: String, grund: String, laender: Int, von: Int, bis: Int): String
    fun lbStandJahr(jahr: Int): String
    fun lbUmgerechnet(waehrung: String): String
    fun heatmapKgv(wert: String): String
    val lbTreemapHinweisKgv: String
    val kursOhneLauf: String
    fun kursLetzterStand(datum: String): String
    fun kursKennzahlenStand(datum: String): String
    // [Waehrung und Kaufkraft 0.78]
    val kkTitel: String
    val kkKaufkraft: String
    val kkPreisniveau: String
    val kkDeutschland: String
    val kkDollar: String
    val kkEuro: String
    fun kkVon(land: String): String
    fun kkZum(code: String): String
    val kkUnterKkp: String
    val kkUeberKkp: String
    val kkAufKkp: String
    fun kkBasisJahr(land: String, jahr: Int): String
    fun kkKorb(betrag: String): String
    fun kkKorbHinweis(land: String, betrag: String): String
    fun kkReise(betrag: String): String
    fun kkReiseHinweis(land: String): String
    val kkBipKopf: String
    fun kkBipKopfHinweis(jahr: Int): String
    val kkBipKaufkraft: String
    fun kkBipKaufkraftHinweis(land: String): String
    fun kkNiveauVerlauf(ab: Int, land: String): String
    fun kkStaerke(waehrung: String): String
    val kkZeitraum: String
    val kkNominal: String
    val kkReal: String
    fun kkJahre(n: Int): String
    fun kkSchnitt(jahr: Int): String
    val kkBindung: String
    val kkFestDollar: String
    val kkFestEuro: String
    val kkBindungHinweis: String
    val kkReer: String
    fun kkReerHinweis(jahr: Int): String
    val kkReerVerlauf: String
    fun kkInfoNiveau(land: String): String
    val kkInfoKkp: String
    fun kkInfoKorb(land: String): String
    fun kkInfoBip(land: String): String
    val kkInfoStaerke: String
    val kkInfoBindung: String
    val kkInfoReer: String
    val kkVglBip: String
    val kkVglKaufkraft: String
    fun kkVglUmrechnung(betrag: String, a: String, b: String, wert: String): String
    val bmTitel: String
    val bmPreis: String
    fun bmStandEuroraum(monat: String): String
    val bmUmgerechnet: String
    val bmKursStichtag: String
    val bmGegenEuro: String
    val bmGegenDollar: String
    val bmRoh: String
    val bmBereinigt: String
    fun bmVerlauf(ab: Int): String
    val bmInfo: String
    val rlTitel: String
    fun rlName(x: de.troetenrepublik.app.engine.Ranglisten.Index): String
    fun rlKurz(x: de.troetenrepublik.app.engine.Ranglisten.Index): String
    fun rlInfo(x: de.troetenrepublik.app.engine.Ranglisten.Index): String
    fun rlPlatz(rang: Int): String
    fun rlVon(n: Int): String
    fun rlRang(rang: Int, von: Int, jahr: Int): String
    fun rlHdiVerlauf(ab: Int): String
    val globusKkp: String
    val globusHdi: String
    val globusGini: String
    val globusLegPreisniveau: String
    val globusLegKkp: String
    val globusLegHdi: String
    val globusLegGini: String
    // Optionen verkaufen und Optionszeilen
    val ozErklaerung: String
    fun ozFaellig(tag: String): String
    val ozErtragJahr: String
    val ozAbstandKurs: String
    val ozGehandelt: String
    fun ozSeite(seite: String): String
    // Suche: Gruppen der Rohstoffe, Waehrungspaare und Kryptowerte (Schluessel aus Suchgruppen)
    fun suGruppe(key: String): String
    val suGruppeTitel: String
    // Krypto entdecken: Listen (Schluessel aus Suchgruppen.KryptoListe)
    fun kyListe(key: String): String
    fun kyListeTitel(key: String, woche: Boolean): String
    val kyTag: String
    val kyWoche: String
    val kyUnterHoch: String
    val kyBitcoinAnteil: String
    fun kyBitcoinAnteilHinweis(anzahl: Int): String
    // 0.78 Rohstoffe: Fuer und Wider, Signale, Optionshandel, Terminkurve, Oel weltweit (i18n/RohstoffLageTexte.kt)
    fun roh(key: String, w: List<String> = emptyList()): String
    // 0.78 Umschichten mit Schwelle, Rueckgaenge der Performance, Stabilitaet der Bilanz
    val umJede: String
    val umAbSchwelle: String
    val umNoetig: String
    val umAllesImRahmen: String
    val umRahmen: String
    val umKeinZiel: String
    val umNochKeine: String
    val umJetzt: String
    val umZiel: String
    val ppfZeitUnterHoch: String
    val ppfMittlererRueckgang: String
    val ppfMittlererRueckgangHinweis: String
    val stabTitel: String
    val stabKeine: String
    fun stabName(key: String): String
    fun stabSatz(key: String, wert: String): String
    fun stabNegativ(key: String): String
    fun stabSchwelle(key: String): String
    // 0.78 Aktienfinder: Auswahlraster (Anteil des Markts, die n hoechsten oder niedrigsten, mindestens n von m, Rangsumme)
    val afRangsumme: String
    fun afMindestens(n: Int, m: Int): String
    fun afFeldAnteil(feld: String, anteil: String, oben: Boolean): String
    fun afFeldSpitze(feld: String, n: Int, oben: Boolean): String
    fun afUnterHoch52(abstand: String): String
    // 0.78 Marktzeichen: Vierer-Indikator, Bodenzeichen, VIX-Abstand, Squeeze (i18n/MarktzeichenTexte.kt)
    fun mz(key: String, w: List<String> = emptyList()): String
    // [0.78] Levermann-Punkte, Typ nach Lynch, faires KGV und Ertragswert ohne Wachstum
    val levTitel: String
    fun levSatz(punkte: String, von: Int): String
    fun levKaufAb(ab: Int, gross: Boolean): String
    fun levKaufAbKurz(ab: Int): String
    fun levOhne(n: Int): String
    val levFinanz: String
    val levKeine: String
    fun levName(key: String): String
    fun levVon(n: Int): String
    fun levGruppe(key: String): String
    fun levKurz(key: String, variante: Boolean): String
    fun levWert(key: String, punkte: Int, variante: Boolean): String
    val lyTitel: String
    fun lyTyp(key: String): String
    fun lyGrund(key: String, wert: String): String
    fun lyDividende(wert: String): String
    fun lySchulden(wert: String, ok: Boolean): String
    val lyWachstumWarnung: String
    val fkTitel: String
    fun fkSatz(fair: String, aktuell: String?): String
    fun fkAbstand(prozent: String, ueber: Boolean): String
    val fkSockel: String
    val fkSockelInfo: String
    val fkStabilInfo: String
    val fkMarktInfo: String
    val fkRenditeInfo: String
    val fkStabil: String
    fun fkStabilHinweis(schwankung: String?, jahre: Int): String
    val fkMarkt: String
    fun fkMarktHinweis(marktmacht: Boolean, wert: String): String
    val fkRendite: String
    fun fkRenditeHinweis(rendite: String): String
    val ewTitel: String
    fun ewSatz(prozent: String, ueber: Boolean): String
    fun ewUnter(ueber: Boolean): String
    val ewNegativ: String
    val ewJeAktie: String
    fun ewJeAktieHinweis(kosten: String): String
    val ewGewinn: String
    fun ewGewinnHinweis(marge: String, jahre: Int, steuer: String): String
    val ewKurs: String
    val ewBuchwert: String
    fun ewBuchwertHinweis(prozent: String, ueber: Boolean): String
    fun mtZustand(zustand: String): String
    fun mtMarke(zustand: String, kurs: String): String
    val mtStopp: String
    fun mtStoppHinweis(abstand: String): String
    val fmTitel: String
    fun fmName(art: String): String
    fun fmBedeutung(art: String, auf: Boolean): String
    fun fmDurchbruch(datum: String): String
    val rhTitel: String
    fun rhDruck(imGeld: Boolean): String
    val rhGutschrift: String
    val rhKosten: String
    val rhNeuePraemie: String
    val rhKeine: String
    val rhRollen: String
    val rhGerollt: String
    val rhNachAndienung: String
    fun rhFrage(alt: String, neu: String, datum: String): String
    // 0.78 Phasenzaehler, Kurs und Umsatz, 16-Wochen-Rhythmus, Schuldentragfaehigkeit (i18n/MarktLaenderTexte.kt)
    fun ml(key: String, w: List<String> = emptyList()): String
    // 0.78 Zusatz der Mitteilung zu einer nachgeholten Order oder Sparplan-Ausfuehrung
    fun nhAm(zeit: String): String
    // 0.78 Signale und Blaetter mit gleichem Fenster: Insider-Trend der Zeile, aeltere Meldungen, Fenster der Konvergenz
    fun itTitelTage(tage: Int): String
    fun vdZusammenfassungAelter(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String): String
    fun kvFensterBis(tage: Int, bis: String): String
    val whZwoelfMonate: String
    // 0.78 Verweise: News-Reiter „Trump“, „Was wäre, wenn“ vom Wert, Glocke an Terminzeilen
    val trumpMarktreaktion: String
    val abhWasWaereDepot: String
    val glockeTermin: String
    // 0.78 Angleichungen der Wertseite: Optionshandel aus offenen Kontrakten, Volatilitaet eines Hausscheins
    fun crOptionenOffen(long: String): String
    val modellVol: String
    val gsrTrend: String
    fun stabGrund(key: String): String
    // 0.78: Bollinger und RSI nennen in der Trefferbilanz, welches Ereignis gezaehlt wird
    fun p1BilanzGemessen(art: String): String
    // 0.78 Depot, Analytics und Depot-Werkzeuge (i18n/DepotTexte.kt)
    fun dp(key: String, w: List<String> = emptyList()): String
    // 0.78: Schalter in der Quellenliste der Nachrichten, nur auf Englisch sichtbar
    val newsNurEnglisch: String
    // 0.78 Strategietest: Ausstieg, Kennzahlen je Geschaeft, weitere Regeln, andere Werte
    fun slRegelInfo(art: String): String
    val slAusstieg: String
    fun slAusstiegName(art: String, wert: Int, stopp: Int): String
    fun slAusstiegHinweis(art: String, wert: Int, stopp: Int): String
    val slTrendfilter: String
    val slMonatsende: String
    fun slMehrRegeln(n: Int): String
    val slGv: String
    val slErwartung: String
    val slGewinnfaktor: String
    val slHaltedauer: String
    val slVerlustserie: String
    val slKennzahlen: String
    val slKennzahlenText: String
    val slAndere: String
    val slAndereText: String
    fun slAndereKern(besser: Int, alle: Int): String
    val slAndereSpalte: String
    val slAndereKeine: String
    // 0.78 Regel-Glocke, ATR-Abstand, Stopp mit Ziel, Auswertung der Geschaefte (i18n/StrategieTexte.kt)
    fun st(key: String, w: List<String> = emptyList()): String
    // 0.78 Prognosemaerkte: Auswahl „Wichtigste“
    val pmWichtigste: String
    val pmWichtigsteAus: String
    // 0.78 Werkzeug Neue Analystenstufen (i18n/StufenTexte.kt)
    fun su(key: String, w: List<String> = emptyList()): String
    // 0.78 Kursmuster im Aktienfinder (i18n/KursmusterTexte.kt) und Risikoregeln (i18n/RisikoTexte.kt)
    fun km(key: String, w: List<String> = emptyList()): String
    fun ri(key: String, w: List<String> = emptyList()): String
    // 0.78 Abgleich mit dem Flex-Bericht (i18n/FlexTexte.kt)
    fun flex(key: String, w: List<String> = emptyList()): String
}
