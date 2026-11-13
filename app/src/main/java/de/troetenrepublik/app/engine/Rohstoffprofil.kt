package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild

// [0.76] Rohstoffe je Unternehmen statt je Branche. Quellen in dieser Reihenfolge (bei Streit gewinnt die weiter oben):
//  1. SEC: Form SD (Konfliktmineralien Zinn, Tantal, Wolfram, Gold) und Rohstoffabschnitt im Jahresbericht (SecAngaben)
//  2. Geschaeftsbeschreibung (Yahoo), gegen Regeln je Rohstoff mit Verwendung geprueft
//  3. gebuendelte Zuordnung je Unternehmen (FIRMEN), wenn die Branche das Unternehmen falsch beschreibt
//  4. feine Industrie (Yahoo "industry", INDUSTRIEN)
//  5. grobe Branche des Katalogs (BRANCHE_KERN, sonst die Liste aus welt/rohstoffe.json)
// Fabless-Firmen (Chipentwickler ohne eigene Fertigung) zeigen von der Fertigung nur den Wafer, gekennzeichnet "ueber
// Auftragsfertiger"; Helium, Flussspat, Kupfer der Fab, Gallium und Germanium bleiben weg, weil sie erst beim Fertiger
// anfallen. Nichts davon ist gemessen: alles ist abgeleitet oder bei der SEC gemeldet.
object Rohstoffprofil {
    // Stand der gebuendelten Zuordnung
    const val STAND = "2026-11-10"

    // Wie der Rohstoff das Unternehmen betrifft
    enum class Weg { EIGEN, ZULIEFERER, FERTIGER, PRODUKT }

    // Woher die Zuordnung kommt; je weiter vorn, desto naeher am Unternehmen
    enum class Quelle { SEC, BESCHREIBUNG, FIRMA, INDUSTRIE, BRANCHE }

    // verwendung: Schluessel der Verwendung beim Unternehmen (null: unbekannt); weg null: unbekannt
    // sec: Bits der SEC-Angaben (SD = Form SD, BERICHT = Rohstoffabschnitt des Jahresberichts), auch wenn eine genauere Quelle gilt
    class Treffer(val id: String, val verwendung: String?, val weg: Weg?, val quelle: Quelle, val sec: Int = 0) {
        val firmenbezogen: Boolean get() = quelle != Quelle.INDUSTRIE && quelle != Quelle.BRANCHE

        // [0.77] Der Rohstoff ist ein Erzeugnis der Firma (sie sucht, foerdert, erzeugt ihn oder gewinnt ihn zurueck), kein
        // Einkauf. Sie haengt am Preis, nicht an der Lieferung aus den grossen Foerderlaendern. Die Zahlen fuer Aluminium
        // messen die Huettenerzeugung: Eine Aluminiumhuette ist dort selbst Erzeuger. Kupfer und Zink messen die Foerderung
        // der Minen: Eine Kupfer- oder Zinkhuette kauft Konzentrat ein und bleibt abhaengig.
        val selbst: Boolean get() = verwendung in EIGENE || (id == "ALUMINIUM" && verwendung == "HUETTE")

        // Rolle des Rohstoffs beim Unternehmen; gilt fuer jede Branche. Eine blosse Nennung in der Beschreibung ohne
        // erkennbare Verwendung belegt keinen Bedarf.
        val rolle: Rolle get() = when {
            selbst -> Rolle.SELBST
            verwendung == null && quelle == Quelle.BESCHREIBUNG && sec == 0 -> Rolle.GENANNT
            else -> Rolle.EINSATZ
        }
        val einsatz: Boolean get() = rolle == Rolle.EINSATZ
    }

    // Angaben aus Einreichungen bei der SEC: sd = Datum der juengsten Form SD ("" ohne), rohstoffe = im Rohstoffabschnitt des
    // Jahresberichts genannte Rohstoffe
    class SecAngaben(val sd: String = "", val rohstoffe: List<String> = emptyList())

    class Ergebnis(val treffer: List<Treffer>, val fabless: Boolean) {
        val ids: List<String> get() = treffer.map { it.id }
        // Mindestens eine Zeile stammt aus Angaben zum Unternehmen selbst (nicht nur aus Industrie oder Branche)
        val firmenbezogen: Boolean get() = treffer.any { it.firmenbezogen }
    }

    enum class Rolle { SELBST, EINSATZ, GENANNT }

    const val FOERDERUNG = "FOERDERUNG"
    const val ERKUNDUNG = "ERKUNDUNG"
    const val ERZEUGUNG = "ERZEUGUNG"
    const val RECYCLING = "RECYCLING"
    // [0.77] Die Firma haelt Royalties und Streams (Lizenzen und Abnahmerechte auf fremde Foerderung): Sie haengt am Preis, nicht an einem Foerderland
    const val LIZENZ = "LIZENZ"
    // Einsatzstoff ohne naehere Verwendung: Die Beschreibung sagt, dass die Firma den Rohstoff verarbeitet oder einkauft
    const val EINSATZSTOFF = "EINSATZ"
    private val EIGENE = setOf(FOERDERUNG, ERKUNDUNG, ERZEUGUNG, RECYCLING, LIZENZ)

    const val SEC_SD = 1
    const val SEC_BERICHT = 2

    private class E(val id: String, val verwendung: String?, val weg: Weg?)

    private fun eintraege(text: String): List<E> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { t ->
        val p = t.split(':')
        E(p[0], p.getOrNull(1)?.takeIf { it.isNotEmpty() && it != "-" }, when (p.getOrNull(2)) {
            "E" -> Weg.EIGEN; "Z" -> Weg.ZULIEFERER; "F" -> Weg.FERTIGER; "P" -> Weg.PRODUKT; else -> null
        })
    }

    // ------------------------------------------------------------------ Profile
    // Name = Eintraege (Rohstoff:Verwendung:Weg; Weg E selbst, Z Zulieferer, F Auftragsfertiger, P im Produkt)
    private val PROFILE: Map<String, List<E>> = """
KEINE =
FABLESS = SILIZIUM:WAFER:F
FABLESS_FUNK = SILIZIUM:WAFER:F GALLIUM:GAN:F
CHIPFERTIGER = SILIZIUM:WAFER:E HELIUM:WAFERGAS:E FLUSSSPAT:AETZ:E KUPFER:LEITER:E
LEISTUNGSCHIP = SILIZIUM:WAFER:E GALLIUM:GAN:E HELIUM:WAFERGAS:E KUPFER:LEITER:E
FUNKCHIP = GALLIUM:GAN:E SILIZIUM:WAFER:E
HALBLEITER_ALLG = SILIZIUM:WAFER:-
WAFER = SILIZIUM:WAFER:E
POLYSILIZIUM = SILIZIUM:POLY:E
SUBSTRAT = GALLIUM:SUBSTRAT:E GERMANIUM:SUBSTRAT:E
LASER = GALLIUM:OPTIK:E GERMANIUM:OPTIK:E
LASER_GA = GALLIUM:OPTIK:E
GLASFASER = GERMANIUM:FASER:E
KABEL = KUPFER:KABEL:E ALUMINIUM:KABEL:E
KABEL_FASER = KUPFER:KABEL:E ALUMINIUM:KABEL:E GERMANIUM:FASER:E
BAUTEILE = NICKEL:KONDENSATOR:E TANTAL:KONDENSATOR:E KUPFER:LEITER:E ZINN:LOT:E
BAUTEILE_MAGNET = NICKEL:KONDENSATOR:E TANTAL:KONDENSATOR:E KUPFER:LEITER:E SELTENE_ERDEN:MAGNET:E
STECKVERBINDER = KUPFER:LEITER:E GOLD:LOT:E ZINN:LOT:E SILBER:LOT:E
EMS = ZINN:LOT:E KUPFER:LEITER:Z GOLD:LOT:Z TANTAL:KONDENSATOR:Z
LEITERPLATTE = KUPFER:LEITER:E GOLD:LOT:E
GERAETE = ALUMINIUM:GEHAEUSE:Z LITHIUM:AKKU:Z KOBALT:AKKU:Z SELTENE_ERDEN:MAGNET:Z ZINN:LOT:Z TANTAL:KONDENSATOR:Z
SERVER = KUPFER:LEITER:Z ALUMINIUM:GEHAEUSE:Z ZINN:LOT:Z
FESTPLATTE = SELTENE_ERDEN:MAGNET:Z ALUMINIUM:SPEICHER:Z PLATIN:SPEICHER:Z
FUNKAUSRUESTER = GALLIUM:GAN:Z KUPFER:LEITER:Z ZINN:LOT:Z
SUPRALEITER = NIOB:SUPRALEITER:E
SOLAR_MODUL = SILIZIUM:SOLAR:E SILBER:SOLAR:E ALUMINIUM:GEHAEUSE:E
ELEKTROTECHNIK = KUPFER:KABEL:E ALUMINIUM:LEITUNG:Z
MOTOREN = KUPFER:KABEL:E SELTENE_ERDEN:MAGNET:Z
ENERGIETECHNIK = KUPFER:KABEL:E ALUMINIUM:LEITUNG:Z NICKEL:TRIEBWERK:Z SELTENE_ERDEN:WIND:Z
WIND = SELTENE_ERDEN:WIND:Z KUPFER:KABEL:Z EISENERZ:STAHL:Z
BATTERIE = LITHIUM:AKKU:E NICKEL:KATHODE:E KOBALT:KATHODE:E GRAPHIT:ANODE:E KUPFER:AKKU:E ALUMINIUM:AKKU:E
WASSERSTOFF = PLATIN:ELEKTROLYSE:E
EAUTO = LITHIUM:AKKU:Z NICKEL:KATHODE:Z KOBALT:KATHODE:Z GRAPHIT:ANODE:Z SELTENE_ERDEN:MAGNET:Z KUPFER:KABEL:P ALUMINIUM:LEICHTBAU:P
EAUTO_ZELLE = LITHIUM:AKKU:E GRAPHIT:ANODE:E KUPFER:AKKU:E SELTENE_ERDEN:MAGNET:Z ALUMINIUM:LEICHTBAU:P
AUTOHERSTELLER = LITHIUM:AKKU:Z KOBALT:AKKU:Z NICKEL:AKKU:Z GRAPHIT:AKKU:Z SELTENE_ERDEN:MAGNET:Z ALUMINIUM:LEICHTBAU:P EISENERZ:KAROSSERIE:P KUPFER:KABEL:P PLATIN:KATALYSATOR:P
LKW = EISENERZ:KAROSSERIE:P ALUMINIUM:LEICHTBAU:P KUPFER:KABEL:P PLATIN:KATALYSATOR:P
AUTOZULIEFERER = KUPFER:KABEL:E ALUMINIUM:LEICHTBAU:E EISENERZ:STAHL:E
REIFEN = EISENERZ:STAHL:Z ZINK:ADDITIV:Z
FLUGZEUGBAU = TITAN:STRUKTUR:Z ALUMINIUM:STRUKTUR:Z
FLUGZEUG_ALLG = TITAN:STRUKTUR:Z ALUMINIUM:STRUKTUR:Z NICKEL:TRIEBWERK:Z KOBALT:TRIEBWERK:Z
TRIEBWERK = NICKEL:TRIEBWERK:E KOBALT:TRIEBWERK:E TITAN:TRIEBWERK:E
MUNITION = WOLFRAM:MUNITION:E KUPFER:MUNITION:E ANTIMON:MUNITION:Z EISENERZ:STAHL:E
RADAR = GALLIUM:RADAR:Z SELTENE_ERDEN:MAGNET:Z
RUESTUNG = TITAN:STRUKTUR:Z ALUMINIUM:STRUKTUR:Z NICKEL:TRIEBWERK:Z WOLFRAM:MUNITION:Z GALLIUM:RADAR:Z SELTENE_ERDEN:MAGNET:Z
SCHIFFBAU = EISENERZ:STAHL:E KUPFER:KABEL:Z
RAUMFAHRT = ALUMINIUM:STRUKTUR:E TITAN:STRUKTUR:E NICKEL:TRIEBWERK:E
EVTOL = LITHIUM:AKKU:Z NICKEL:KATHODE:Z GRAPHIT:ANODE:Z SELTENE_ERDEN:MAGNET:Z
NETZ_VERSORGER = KUPFER:LEITUNG:E ALUMINIUM:LEITUNG:E
ERNEUERBARE = SELTENE_ERDEN:WIND:Z SILIZIUM:SOLAR:Z SILBER:SOLAR:Z KUPFER:LEITUNG:Z
BETREIBER = KUPFER:LEITUNG:E
TURM = EISENERZ:STAHL:P ZINK:VERZINKUNG:P
MRT = HELIUM:MRT:P
IMPLANTAT = TITAN:IMPLANTAT:P KOBALT:IMPLANTAT:P
DUENGER = PHOSPHAT:DUENGER:E
PHOSPHAT_FOERDERER = PHOSPHAT:FOERDERUNG:E
DUENGER_Z = PHOSPHAT:DUENGER:Z
GASHANDEL = HELIUM:GASHANDEL:E
FLUOR = FLUSSSPAT:FLUOR:E
FARBEN =
GRAPHITWERK = GRAPHIT:ELEKTRODE:E
LITHIUMCHEMIE = LITHIUM:FOERDERUNG:E
MASCHINEN = EISENERZ:STAHL:E MANGAN:STAHL:Z NICKEL:STAHL:Z KUPFER:KABEL:Z WOLFRAM:HARTMETALL:Z VANADIUM:STAHL:Z
HARTMETALL = WOLFRAM:HARTMETALL:E KOBALT:HARTMETALL:E EISENERZ:STAHL:E
KLIMA = FLUSSSPAT:FLUOR:Z KUPFER:LEITUNG:E ALUMINIUM:LEICHTBAU:E
HVAC = KUPFER:LEITUNG:E ALUMINIUM:LEICHTBAU:E
SCHLOSS = ZINK:GUSS:E KUPFER:MESSING:E
FREILEITUNG = KUPFER:LEITUNG:P ALUMINIUM:LEITUNG:P EISENERZ:STAHL:P
STAHL = EISENERZ:STAHL:E MANGAN:STAHL:Z
EDELSTAHL = EISENERZ:STAHL:E NICKEL:STAHL:Z MANGAN:STAHL:Z
DOSE = ALUMINIUM:VERPACKUNG:Z
ALU = ALUMINIUM:HUETTE:E
FOERDER_EISEN_KUPFER_NICKEL = EISENERZ:FOERDERUNG:E KUPFER:FOERDERUNG:E NICKEL:FOERDERUNG:E
FOERDER_EISEN_ALU_KUPFER_LI = EISENERZ:FOERDERUNG:E ALUMINIUM:HUETTE:E KUPFER:FOERDERUNG:E LITHIUM:FOERDERUNG:E
FOERDER_EISEN_NICKEL_KUPFER = EISENERZ:FOERDERUNG:E NICKEL:FOERDERUNG:E KUPFER:FOERDERUNG:E
FOERDER_KUPFER_EISEN = KUPFER:FOERDERUNG:E EISENERZ:FOERDERUNG:E
FOERDER_KUPFER_KOBALT = KUPFER:FOERDERUNG:E KOBALT:FOERDERUNG:E NICKEL:FOERDERUNG:E ZINK:FOERDERUNG:E
FOERDER_KUPFER_GOLD = KUPFER:FOERDERUNG:E GOLD:FOERDERUNG:E
FOERDER_KUPFER_ZINK = KUPFER:FOERDERUNG:E ZINK:FOERDERUNG:E SILBER:FOERDERUNG:E GOLD:FOERDERUNG:E
FOERDER_GOLD = GOLD:FOERDERUNG:E
FOERDER_GOLD_KUPFER = GOLD:FOERDERUNG:E KUPFER:FOERDERUNG:E
FOERDER_SILBER = SILBER:FOERDERUNG:E GOLD:FOERDERUNG:E
FOERDER_SILBER_GOLD_ONLY = SILBER:FOERDERUNG:E
FOERDER_KUPFER_NICKEL_GOLD = KUPFER:HUETTE:E NICKEL:HUETTE:E GOLD:HUETTE:E
HUETTE_KUPFER = KUPFER:HUETTE:E SILBER:HUETTE:E GOLD:HUETTE:E
KUPFER_WOLFRAM = KUPFER:HUETTE:E WOLFRAM:HARTMETALL:E
KUPFER_ZINK_VERARB = KUPFER:HUETTE:E ZINK:HUETTE:E
ZINK_RECYCLING = ZINK:RECYCLING:E
UMICORE = KOBALT:KATHODE:E NICKEL:KATHODE:E LITHIUM:KATHODE:E PLATIN:KATALYSATOR:E GOLD:RECYCLING:E SILBER:RECYCLING:E GERMANIUM:SUBSTRAT:E
FOERDER_KUPFER = KUPFER:FOERDERUNG:E
FOERDER_PLATIN = PLATIN:FOERDERUNG:E
FOERDER_ZINK = ZINK:FOERDERUNG:E
FOERDER_EISEN = EISENERZ:FOERDERUNG:E
FOERDER_BHP = EISENERZ:FOERDERUNG:E KUPFER:FOERDERUNG:E NICKEL:FOERDERUNG:E URAN:FOERDERUNG:E
FOERDER_KUPFER_SILBER = KUPFER:FOERDERUNG:E SILBER:FOERDERUNG:E
SOUTH32 = ALUMINIUM:HUETTE:E MANGAN:FOERDERUNG:E ZINK:FOERDERUNG:E NICKEL:FOERDERUNG:E
MAADEN = PHOSPHAT:FOERDERUNG:E ALUMINIUM:HUETTE:E GOLD:FOERDERUNG:E
URAN_FOERDERER = URAN:FOERDERUNG:E
URAN_ANREICHERUNG = URAN:ANREICHERUNG:E
KERNKRAFT_BAUER = URAN:BRENNSTOFF:Z
KERNKRAFT_VERSORGER = URAN:BRENNSTOFF:Z KUPFER:LEITUNG:E ALUMINIUM:LEITUNG:E
KERNTECHNIK_MASCHINEN = EISENERZ:STAHL:E MANGAN:STAHL:Z NICKEL:STAHL:Z KUPFER:KABEL:Z WOLFRAM:HARTMETALL:Z VANADIUM:STAHL:Z URAN:BRENNSTOFF:Z
ZWEIRAD = EISENERZ:KAROSSERIE:P ALUMINIUM:LEICHTBAU:P KUPFER:KABEL:P PLATIN:KATALYSATOR:P
BATTERIECHEMIE = NICKEL:KATHODE:E KOBALT:KATHODE:E LITHIUM:KATHODE:Z PLATIN:KATALYSATOR:E
SILIKONE = SILIZIUM:SILIKON:E
KATALYSATORTRAEGER = PLATIN:KATALYSATOR:Z
BAU_GROSS = EISENERZ:STAHL:Z KUPFER:KABEL:Z ALUMINIUM:LEICHTBAU:Z
HAUSBAU = KUPFER:KABEL:Z ALUMINIUM:LEICHTBAU:Z
FAHRRAD = ALUMINIUM:LEICHTBAU:E
""".trim().lines().filter { it.contains('=') }.associate { z ->
        z.substringBefore('=').trim() to eintraege(z.substringAfter('=').trim())
    }

    // ------------------------------------------------------------------ Unternehmen
    // Profil: Kuerzel des Werts (Katalog, Grossschreibung); gilt vor Industrie und Branche
    private val FIRMEN: Map<String, String> = """
FABLESS: NVDA AMD MRVL ALAB CBRS 2454.TW 6526.T NOD.OL ELG.DE MPWR SNDK
FABLESS_FUNK: QCOM AVGO
CHIPFERTIGER: INTC TSM 2330.TW MU TXN 005930.KS 000660.KS SKHY 285A.T MCHP GFS UMC
LEISTUNGSCHIP: IFX.DE STMPA.PA NXPI ON ADI 6723.T 6963.T WOLF
FUNKCHIP: SWKS QRVO
KEINE: FTV ASML.AS AMAT LRCX KLAC TER 8035.T 6857.T 6146.T 6920.T 7735.T ASM.AS BESI.AS SMHN.DE TPE.DE AIXA.DE ARM FSLR 6861.T KEYS HLMA HEXA-B.ST BSL.DE JEN.DE ZBRA TDY IONQ QNT FFIV CF K+S SDF.DE CTVA
WAFER: WAF.DE 3436.T Q 4063.T
POLYSILIZIUM: WCH.DE 4043.T
SUBSTRAT: AXTI
LASER: COHR
LASER_GA: LITE
GLASFASER: GLW
KABEL_FASER: PRY.MI 5803.T 5801.T 5802.T
KABEL: NKT.CO
BAUTEILE: 6981.T 6976.T 6971.T
BAUTEILE_MAGNET: 6762.T 6479.T 6770.T
STECKVERBINDER: APH TEL
EMS: FLEX JBL 2317.TW
LEITERPLATTE: ATS.VI
GERAETE: AAPL 6758.T 1810.HK GRMN LOGN.SW 6753.T 6724.T 7751.T 4902.T 7752.T 7731.T SLYG.DE AXON
SERVER: DELL HPQ HPE SMCI NTAP P ANET CSCO CIEN
FESTPLATTE: STX WDC
FUNKAUSRUESTER: ERIC-B.ST NOKIA.HE MSI 6701.T ADV.DE
SUPRALEITER: QBTS RGTI
ELEKTROTECHNIK: SIE.DE ABBN.SW ETN HUBB LR.PA SU.PA 6501.T 6503.T 6504.T 6841.T ROK EMR AME VRT GNRC EIN.DE PFSE.DE 1AST.DE S92.DE
MOTOREN: 6954.T 6506.T 6594.T 6645.T
ENERGIETECHNIK: ENR.DE GEV
WIND: VWS.CO NDX1.DE
BATTERIE: 3750.HK
WASSERSTOFF: PLUG NEL.OL F3C.DE
EAUTO: TSLA RIVN LCID NIO LI XPEV
EAUTO_ZELLE: 1211.HK
LKW: DTG.DE 8TRA.DE PCAR IVG.MI VOLV-B.ST
AUTOZULIEFERER: APTV AMV0.DE CON.DE HLE.DE 6902.T SHA0.DE KBX.DE JST.DE SFQ.DE
REIFEN: ML.PA 5108.T 5101.T TYRES.HE
FLUGZEUGBAU: BA AIR.DE AM.PA TXT
TRIEBWERK: GE RR.L SAF.PA MTX.DE HWM MRO.L
MUNITION: RHM.DE CSG.AS
RADAR: HAG.DE HO.PA
RUESTUNG: LMT NOC RTX LHX GD BA.L LDO.MI SAAB-B.ST KOG.OL IDR.MC
SCHIFFBAU: HII TKMS.DE BAB.L
RAUMFAHRT: RKLB SPCX OHB.DE AVIO.MI
EVTOL: ACHR JOBY
TURM: AMT CCI SBAC CLNX.MC INW.MI
MRT: SHL.DE PHIA.AS GEHC
IMPLANTAT: ZBH SYK SN.L OBCK.DE
PHOSPHAT_FOERDERER: MOS
DUENGER_Z: YAR.OL
GASHANDEL: LIN AI.PA APD
FLUOR: 5201.T 6367.T 4042.T
FARBEN: SHW PPG AKZA.AS
GRAPHITWERK: 4004.T 5301.T
LITHIUMCHEMIE: ALB
UMICORE: UMI.BR
HARTMETALL: SAND.ST
HVAC: TT CARR LII JCI AOS
SCHLOSS: ASSA-B.ST ALLE
FREILEITUNG: PWR
KEINE: HEI.DE HOLN.SW CRH BZU.MI MLM VMC AMRZ.SW 5233.T WIE.VI SGO.PA SIKA.SW ROCK-B.CO
ZINK_RECYCLING: BFSA.DE
FOERDER_BHP: BHP BHP.AX
FOERDER_EISEN_ALU_KUPFER_LI: RIO.L
FOERDER_EISEN_NICKEL_KUPFER: VALE
FOERDER_KUPFER_EISEN: AAL.L
FOERDER_KUPFER_KOBALT: GLEN.L
FOERDER_KUPFER_GOLD: FCX ANTO.L B
FOERDER_KUPFER_ZINK: BOL.ST
FOERDER_GOLD: NEM EDV.L
FOERDER_SILBER: FRES.L
FOERDER_KUPFER_NICKEL_GOLD: 5713.T
HUETTE_KUPFER: NDA.DE
KUPFER_WOLFRAM: 5711.T
KUPFER_ZINK_VERARB: 5706.T 5714.T
ALU: NHY.OL
DOSE: BALL
STAHL: MT.AS 5401.T 5411.T 5406.T NUE STLD SZG.DE TKA.DE VOE.VI
EDELSTAHL: ACX.MC OUT1V.HE
URAN_FOERDERER: CCJ CCO.TO KAP.L KAP.IL KAZ.IL NXE NXE.TO DNN DNN.TO DML.TO UEC UUUU PDN.AX PDN.TO BOE.AX DYL.AX URG EU 1164.HK FCU.TO ISO.TO
URAN_ANREICHERUNG: LEU
KERNKRAFT_BAUER: OKLO SMR BWXT LTBR NNE
KERNTECHNIK_MASCHINEN: 7011.T 034020.KS
KERNKRAFT_VERSORGER: CEG D DUK ETR EXC PEG PCG PNW XEL AEE AEP EVRG DTE NEE SO VST TLN FORTUM.HE ENGI.PA ELE.MC IBE.MC 9501.T 9502.T 9503.T 9504.T 9506.T 9507.T 9508.T 9509.T CEZ.PR 015760.KS 1816.HK EDF.PA
NETZ_VERSORGER: A2A.MI AES LNT CMS ED CNP CNA.L EOAN.DE EIX ENEL.MI EVN.VI ES FE SRE HER.MI NG.L NI NRG PPL RED.MC SSE.L TRN.MI VER.VI WEC AGL.AX ORG.AX COLBUN.SN 0002.HK 1038.HK 0006.HK ELI.BR PGE.WA EDP.LS RENE.LS TEL.RO 5347.KL IGN1L.VS GNE.NZ CEN.NZ MNILY U96.SI SEG.SI EGCO.BK PAZ.TA PPC.AT BHEL.NS
ERNEUERBARE: ANE.MC EKT.DE PNE3.DE SLR.MC RWE.DE
WIND: ORSTED.CO SUZLON.NS
ALU: MTLN.L HINDALCO.NS
KEINE: ENG.MC SRG.MI IG.MI ATO 9531.T 9532.T 0003.HK AWK SVT.L UU.L SBSP3.SA COALINDIA.NS JSW.WA ORI.AX RSG WM VIE.PA VLTO XYL TOM.OL ASTS WILD.TO THCOM.BK CAE.TO GIAA.JK 0069.HK 3182.KL 2445.KL SOLB.BR WSP.TO TRP.RO LPK.DE FDR.MC S63.SI 034220.KS 2323.TW 2349.TW 5214.T GEBN.SW STO3.DE HWDN.L 5332.T CEMEXCPO.MX PINFRA.MX FBU.NZ SCC.BK
KEINE: ACT0.DE 3407.T AVY CRDA.L 4061.T DSFIR.AS DD ECL FPE3.DE 4901.T GIVN.SW IFF KEMIRA.HE 3405.T LXS.DE LNZ.VI LYB 4188.T 4183.T 4021.T 6988.T 4005.T SY1.DE 3401.T 3402.T 4208.T
KEINE: ABT A ALC.SW ALGN AMBU-B.CO AMP.MI BAX BDX TECH CAH AFX.DE COR CNC CRL CI COLO-B.CO CTEC.L COO CVS DHR DVA DEMANT.CO DXCM DIA.MI DRW3.DE EUZ.DE ELV EL.PA ERF.PA FRE.DE FME.DE GN.CO HCA HSIC HIMS 7741.T HUM IDXX ILMN PODD ISRG IQV LH MCK MDLN MTD 7733.T DGX RMD RVTY SRT3.DE 1SXP.DE SOLV STE SBS.DE 4543.T TMO UNH UHS WAT WST
IMPLANTAT: BSX EW MDT
BATTERIECHEMIE: BAS.DE
SILIKONE: DOW EVK.DE
DOSE: AMCR
FLUOR: ORBIA.MX
DUENGER: ATT.WA
AUTOHERSTELLER: MBG.DE BMW.DE VOW3.DE P911.DE RACE.MI F GM 7267.T 005380.KS 000270.KS 7261.T 7211.T 7201.T RNO.PA STLAM.MI 7270.T 7269.T 7203.T VOLCAR-B.ST 7272.T MARUTI.NS M&M.NS TMPV.NS TATAMOTORS.NS 600104.SS 601633.SS 002594.SZ 0175.HK 2333.HK 0489.HK
ZWEIRAD: BAJAJ-AUTO.NS HEROMOTOCO.NS TVSMOTOR.NS EICHERMOT.NS
LKW: 7202.T TMCV.NS ASHOKLEY.NS POMO3.SA OTKAR.IS
REIFEN: 161390.KS 2105.TW
AUTOZULIEFERER: 012330.KS MG.TO RABA.BD
EAUTO: NIO.SI
SCHIFFBAU: FCT.MI ASB.AX ISHI.TA 042660.KS
HVAC: NIBE-B.ST
SCHLOSS: MAS
BAU_GROSS: ANA.MC ACS.MC EN.PA FER.MC VH2.DE HOT.DE J 1963.T 1812.T 1802.T 1803.T 1801.T SKA-B.ST STR.VI POS.VI SCYR.MC DG.PA 1721.T
HAUSBAU: DHI LEN NVR PHM BTRW.L 1925.T 1928.T 1808.T FIX EME
MASCHINEN: ALFA.ST 6113.T ANDR.VI ATCO-A.ST CAT CMI DE DEZ.DE DOV DUE.DE 6361.T EPI-A.ST FLS.CO G1A.DE HDD.DE HIAB.HE 6305.T IEX 7013.T ITW IMI.L IR INIO 5631.T 6473.T JUN3.DE KALMAR.HE 7004.T 7012.T KGX.DE 6301.T KNEBV.HE KCR.HE KRN.DE KSB3.DE 6326.T METSO.HE NDSN NOEJ.DE 6471.T 6472.T 6103.T OTIS PH PNR RAA.DE SKF-B.ST SMIN.L SNA SPX.L STM.DE SWK 6302.T VALMT.HE VOS.DE WAB WAC.DE WEIR.L WRT1V.HE AKSO.OL AMC.WA SKL.NZ R3NK.DE NWG.WA
MOTOREN: WEGE3.SA V1NC.DE
FAHRRAD: 9921.TW
FLUGZEUG_ALLG: HONA TDG HAL.NS
FLUGZEUGBAU: EMBJ3.SA BBD-B.TO 047810.KS
MUNITION: TASA4.SA COLT.PR
RUESTUNG: 000065.SZ ESLT.TA BDL.NS
RADAR: BEL.NS
ELEKTROTECHNIK: BE HON
KATALYSATORTRAEGER: 5333.T
BATTERIE: 6752.T
GERAETE: 0669.HK 600060.SS 000100.SZ 600690.SS 2357.TW 2377.TW 2498.TW
CHIPFERTIGER: TSEM.TA 2303.TW 2344.TW
FABLESS: C76.SI 2388.TW
EMS: 4938.TW 3231.TW
SERVER: KTN.DE 2356.TW 3515.TW 2397.TW 2331.TW 2465.TW 3046.TW 3015.TW
FUNKAUSRUESTER: 000063.SZ 2332.TW 3704.TW
BETREIBER: DTE.DE 1U1.DE AAF.L T BT-A.L CHTR CMCSA ECHO ELISA.HE FNTN.DE 9433.T KPN.AS 9432.T ORA.PA 9434.T SCMN.SW TMUS TEL2-B.ST TIT.MI TEF.MC TEL.OL TELIA.ST UTDI.DE VZ VOD.L TLS.AX TECO2.BA 600941.SS PROX.BR OIBR3.SA VIVT3.SA T.TO RCI-B.TO 0008.HK 4IG.BD TLKM.JK ISAT.JK EXCL.JK CEL.TA BEZQ.TA PTNR.TA BHARTIARTL.NS PHI GTMEY 7010.SR 2412.TW 4904.TW AMXB.MX 6888.KL 4863.KL 6012.KL 6947.KL OPL.WA Z74.SI TTKOM.IS 017670.KS 032640.KS HTO.AT TRUE.BK ADVANC.BK EGS74081C018-EGP.CA EGS48031C016.CA MTN.JO VOD.JO TKG.JO
FOERDER_EISEN: FMG.AX CSNA3.SA CAP.SN
STAHL: BSL.AX GGBR3.SA USIM3.SA KRAS.JK TATASTEEL.NS SAIL.NS 005490.KS 2002.TW
FOERDER_KUPFER_ZINK: GMEXICOB.MX
FOERDER_KUPFER_SILBER: KGH.WA
FOERDER_KUPFER: FM.TO
FOERDER_EISEN_ALU_KUPFER_LI: RIO.AX
FOERDER_GOLD: K.TO CG.TO AEM.TO MINEROS.CL
SOUTH32: S32.AX
MAADEN: 1211.SR
KABEL: VIO.BR VIO.AT
""".trim().lines().filter { it.contains(':') }.flatMap { z ->
        val name = z.substringBefore(':').trim()
        z.substringAfter(':').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.uppercase() to name }
    }.toMap()

    // Profile, deren Firmen keine eigene Fertigung haben
    private val FABLESS_PROFILE = setOf("FABLESS", "FABLESS_FUNK")

    // ------------------------------------------------------------------ Industrien (Yahoo "industry", Kleinschreibung, enthalten)
    // Reihenfolge zaehlt: die erste passende Zeile gilt. Profil: Teile der Industrie getrennt durch |. "Ohne Blick" heisst:
    // keine Rohstoffe und die Beschreibung wird nicht durchsucht.
    private class Industrie(val profil: String, val teile: List<String>, val ohneBlick: Boolean)

    private val INDUSTRIEN: List<Industrie> = """
KEINE! banks|insurance|asset management|capital markets|credit services|financial|mortgage|software|information technology services|internet|reit|real estate|advertising|broadcasting|publishing|gambling|staffing|consulting|education|drug manufacturers|biotechnology|diagnostics|health information|medical care|healthcare plans|pharmaceutical|medical distribution|conglomerates|shell companies
KEINE: semiconductor equipment|semiconductor materials|scientific & technical instruments|electronics & computer distribution|airlines|airports|railroads|coking coal|thermal coal|oil & gas|building materials|specialty chemicals|chemicals|medical devices|medical instruments|waste management|pollution|regulated gas|regulated water|water utilities|gas utilities|packaged foods|beverages|tobacco|restaurants|grocery|department stores|apparel|footwear|lodging|resorts|leisure|entertainment|travel
BETREIBER: telecom services
FOERDER_GOLD: gold
URAN_FOERDERER: uranium
HALBLEITER_ALLG: semiconductors
SOLAR_MODUL: solar
GERAETE: consumer electronics
SERVER: computer hardware
BAUTEILE: electronic components
SERVER: communication equipment
ELEKTROTECHNIK: electrical equipment
AUTOHERSTELLER: auto manufacturers
AUTOZULIEFERER: auto parts
MASCHINEN: farm & heavy
FLUGZEUG_ALLG: aerospace
ERNEUERBARE: renewable
NETZ_VERSORGER: utilities
DUENGER: agricultural inputs
MASCHINEN: specialty industrial machinery|metal fabrication|tools & accessories
STAHL: steel
ALU: aluminum
FOERDER_KUPFER: copper
FOERDER_SILBER_GOLD_ONLY: silver
FOERDER_PLATIN: other precious metals
LITHIUMCHEMIE: lithium
""".trim().lines().filter { it.isNotBlank() }.map { z ->
        val kopf = z.substringBefore(' ')
        Industrie(kopf.trimEnd('!', ':'), z.substringAfter(' ').trim().split('|').map { it.trim().lowercase() }.filter { it.isNotEmpty() }, kopf.endsWith("!"))
    }

    // "gold" steht oben unter KEINE nur als Warnung gegen Fehlgriffe; Goldfoerderer haben ihr eigenes Profil
    private fun industrie(name: String): Industrie? {
        val n = name.lowercase()
        if (n.contains("gold") && n.contains("mining")) return Industrie("FOERDER_GOLD", emptyList(), false)
        return INDUSTRIEN.firstOrNull { i -> i.teile.any { n.contains(it) } }
    }

    // ------------------------------------------------------------------ Grobe Branche als Rueckfall
    // Wo die Liste aus welt/rohstoffe.json eine ganze Branche ueber einen Kamm schert, steht hier der gemeinsame Kern.
    // Auch dieser Rueckfall nennt je Zeile die in der Branche uebliche Verwendung (ohne Weg); die Anzeige beschriftet sie
    // als Branche.
    private val BRANCHE_KERN: Map<String, List<E>> = mapOf(
        "HALBLEITER" to eintraege("SILIZIUM:WAFER:-"),
        "TELEKOM" to eintraege("KUPFER:LEITUNG:E"),
        "VERSORGER" to eintraege("KUPFER:LEITUNG:E ALUMINIUM:LEITUNG:E"),
        "GESUNDHEIT" to emptyList(),
        "UMWELT" to emptyList(),
        "CHEMIE" to emptyList(),
    )

    // Uebliche Verwendung je Rohstoff und Branche; was hier fehlt, bekommt die Standardverwendung des Rohstoffs
    private val BRANCHE_VERWENDUNG: Map<String, Map<String, String>> = """
HALBLEITER = SILIZIUM:WAFER GALLIUM:GAN GERMANIUM:SUBSTRAT KUPFER:LEITER HELIUM:WAFERGAS FLUSSSPAT:AETZ
COMPUTER = ZINN:LOT GOLD:LOT TANTAL:KONDENSATOR KUPFER:LEITER SELTENE_ERDEN:MAGNET
ELEKTRO = KUPFER:KABEL ZINN:LOT SILBER:LOT TANTAL:KONDENSATOR SELTENE_ERDEN:MAGNET LITHIUM:AKKU GRAPHIT:ANODE
FAHRZEUGE = LITHIUM:AKKU KOBALT:KATHODE NICKEL:KATHODE GRAPHIT:ANODE SELTENE_ERDEN:MAGNET ALUMINIUM:LEICHTBAU EISENERZ:KAROSSERIE KUPFER:KABEL PLATIN:KATALYSATOR
LUFTFAHRT = TITAN:STRUKTUR ALUMINIUM:STRUKTUR NICKEL:TRIEBWERK KOBALT:TRIEBWERK
MASCHINEN = EISENERZ:STAHL MANGAN:STAHL WOLFRAM:HARTMETALL VANADIUM:STAHL NICKEL:STAHL KUPFER:KABEL
BAU = EISENERZ:STAHL MANGAN:STAHL KUPFER:KABEL ALUMINIUM:LEICHTBAU ZINK:VERZINKUNG
VERSORGER = KUPFER:LEITUNG ALUMINIUM:LEITUNG SELTENE_ERDEN:WIND SILIZIUM:SOLAR SILBER:SOLAR
UMWELT = SELTENE_ERDEN:WIND SILIZIUM:SOLAR SILBER:SOLAR KUPFER:KABEL
CHEMIE = PHOSPHAT:DUENGER FLUSSSPAT:FLUOR SILIZIUM:SILIKON
GESUNDHEIT = HELIUM:MRT
TELEKOM = GALLIUM:FUNKCHIP GERMANIUM:FASER KUPFER:LEITUNG
""".trim().lines().filter { it.contains('=') }.associate { z ->
        z.substringBefore('=').trim() to z.substringAfter('=').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.associate { t -> t.substringBefore(':') to t.substringAfter(':') }
    }

    // Verwendung, wenn ein Rohstoff in einer Branche ueblich ist und keine genauere Angabe vorliegt
    private val STANDARD_VERWENDUNG: Map<String, String> = """
LITHIUM:AKKU KOBALT:KATHODE SELTENE_ERDEN:MAGNET KUPFER:KABEL NICKEL:STAHL GRAPHIT:ANODE GALLIUM:GAN GERMANIUM:OPTIK ZINN:LOT
WOLFRAM:HARTMETALL ANTIMON:MUNITION ALUMINIUM:LEICHTBAU MANGAN:STAHL EISENERZ:STAHL SILIZIUM:WAFER VANADIUM:STAHL TITAN:STRUKTUR
NIOB:STAHL TANTAL:KONDENSATOR HELIUM:MRT PHOSPHAT:DUENGER FLUSSSPAT:FLUOR ZINK:VERZINKUNG SILBER:LOT GOLD:LOT PLATIN:KATALYSATOR
URAN:BRENNSTOFF
""".trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.associate { t -> t.substringBefore(':') to t.substringAfter(':') }

    // Zeilen der Branche; jede nennt eine Verwendung
    private fun brancheZeilen(i: Einflussverzeichnis.Index, branche: String): List<E> =
        BRANCHE_KERN[branche] ?: i.branchenRohstoffe[branche].orEmpty().map { id ->
            E(id, BRANCHE_VERWENDUNG[branche]?.get(id) ?: STANDARD_VERWENDUNG[id], null)
        }

    // ------------------------------------------------------------------ Regeln fuer die Beschreibung (Kleinschreibung)
    private class Regel(val muster: Regex, val eintraege: List<E>)

    private val REGELN: List<Regel> = """
lithium (mine|mining|brine|production|producer|resources?|deposits?|hydroxide|carbonate|chemicals?)|spodumene => LITHIUM:FOERDERUNG:-
lithium[- ]ion|li-ion|lithium[- ]metal|battery cells?|battery packs?|battery manufactur\w* => LITHIUM:AKKU:-
\blithium\b => LITHIUM
(designs?|develops?|manufactur\w*|produc\w*|sells?)\W+((?!for\b|in\b|to\b|used\b|of\b|with\b)\w+\W+){0,4}electric (vehicles?|cars?|trucks?|buses|bikes?)(?! (supply|industr|market|batter|charging|component|manufacturers|makers)) => LITHIUM:AKKU:Z NICKEL:KATHODE:Z GRAPHIT:ANODE:Z SELTENE_ERDEN:MAGNET:Z
cobalt (mine|mining|refin\w*|hydroxide|production)|cobalt resources => KOBALT:FOERDERUNG:-
\bcobalt\b => KOBALT
nickel (mine|mining|refin\w*|sulph\w+|production|pig iron) => NICKEL:FOERDERUNG:-
\bnickel\b => NICKEL
graphite electrodes? => GRAPHIT:ELEKTRODE:-
anode materials? => GRAPHIT:ANODE:-
\bgraphite\b => GRAPHIT
rare[- ]earth (mine|mining|oxides?|elements?|production) => SELTENE_ERDEN:FOERDERUNG:-
permanent magnets?|neodymium|ndfeb => SELTENE_ERDEN:MAGNET:-
rare[- ]earths? => SELTENE_ERDEN
copper (mine|mining|smelt\w*|cathodes?|concentrates?|production) => KUPFER:FOERDERUNG:-
copper (wire|cable|tubing|rods?|foil)|wire and cable|power cables?|cables? and wires?|magnet wire|power transformers?|distribution transformers?|switchgear|electric motors?|electrical wiring => KUPFER:KABEL:-
\bcopper\b => KUPFER
bauxite|alumina|(aluminium|aluminum) smelt\w*|primary alumin\w+ => ALUMINIUM:HUETTE:-
(aluminium|aluminum) (extrusions?|rolled|sheets?|castings?|wheels)|alumin\w+ (body|frames?) => ALUMINIUM:LEICHTBAU:-
(aluminium|aluminum) cans? => ALUMINIUM:VERPACKUNG:-
\b(aluminium|aluminum)\b => ALUMINIUM
tin (plate|mine|mining|smelt\w*)|tin and tungsten => ZINN:FOERDERUNG:-
\bsolder\w* => ZINN:LOT:-
zinc (mine|mining|smelt\w*|production|recycl\w*) => ZINK:FOERDERUNG:-
galvaniz\w*|hot-dip|zinc coat\w* => ZINK:VERZINKUNG:-
\bzinc\b => ZINK
iron ore (mine|mining|pellets?|production|producer)|iron ore resources => EISENERZ:FOERDERUNG:-
blast furnaces?|steelmak\w*|steel mills?|crude steel|rebar|(produc\w*|manufactur\w*) (of )?steel => EISENERZ:STAHL:-
armou?red vehicles?|battle tanks?|military vehicles? => EISENERZ:STAHL:-
\biron ore\b => EISENERZ
manganese (mine|mining|ore) => MANGAN:FOERDERUNG:-
\bmanganese\b => MANGAN
tungsten|cemented carbides?|carbide (tools?|inserts?)|hard ?metals? => WOLFRAM:HARTMETALL:-
\bvanadium\b => VANADIUM
titanium (sponge|alloys?|parts|components?|castings?|forgings?)|aerostructures? => TITAN:STRUKTUR:-
titanium implants? => TITAN:IMPLANTAT:-
rutile|ilmenite|titanium[- ](minerals?|feedstocks?|slag|bearing) => TITAN
\btitanium\b(?! (dioxide|pigments?)) => TITAN
gallium nitride|\bgan\b|gallium arsenide|\bgaas\b => GALLIUM:GAN:-
rf (power amplifiers?|front[- ]end|filters?)|power amplifiers?|radio[- ]frequency (devices?|components?)|base stations?|radio access|wireless infrastructure => GALLIUM:FUNKCHIP:-
vcsels?|laser diodes?|photonic\w* => GALLIUM:OPTIK:-
radar|electronic warfare => GALLIUM:RADAR:-
\bgallium\b => GALLIUM
optical fib(er|re)s?(?! sens)|fib(er|re)[- ]optic (cables?|cabling|components|products|connectivity) => GERMANIUM:FASER:-
infrared (optics|imaging|lenses|cameras?|sensors?)|thermal imaging => GERMANIUM:OPTIK:-
\bgermanium\b => GERMANIUM
polysilicon|silicon wafers?|semiconductor wafers?|wafer (fabrication|production) => SILIZIUM:WAFER:-
manufactur\w* (and \w+ )?(of )?(integrated circuits|semiconductors?|semiconductor devices|memory (chips|products|devices))|semiconductor foundr(y|ies)|pure[- ]play foundry|wafer foundry|semiconductor manufacturing => SILIZIUM:WAFER:-
silicon carbide|\bsic\b => SILIZIUM:WAFER:-
solar (cells?|modules?|panels?|wafers?)|photovoltaic (cells?|modules?|panels?) => SILIZIUM:SOLAR:- SILBER:SOLAR:-
silicones? => SILIZIUM:SILIKON:-
\bhelium\b|industrial gases => HELIUM:GASHANDEL:-
magnetic resonance|\bmri\b => HELIUM:MRT:-
phosphate (rock )?(mines?|mining)|(mines|mining)( and \w+)? (of )?phosphate|phosphate rock (production|reserves|deposits?) => PHOSPHAT:FOERDERUNG:-
phosphate|phosphoric acid|\bdap\b|\bnpk\b => PHOSPHAT:DUENGER:-
fluorspar|fluorite|hydrofluoric|fluoro\w+|fluorine|refrigerants?|aluminum fluoride => FLUSSSPAT:FLUOR:-
silver (mine|mining|production|bullion|coins?) => SILBER:FOERDERUNG:-
silver (paste|alloy|plated|brazing|contacts?) => SILBER:LOT:-
gold (mine|mining|producer|production|resources?|deposits?|exploration|ounces?)|precious metals? (mining|production) => GOLD:FOERDERUNG:-
jewel(l)?ery|fine watches|luxury watches => GOLD:SCHMUCK:-
gold (bonding|plating|contacts?) => GOLD:LOT:-
platinum group metals? (mine|mining|production)|pgm (mine|mining|production) => PLATIN:FOERDERUNG:-
catalytic converters?|autocatalysts?|emissions? (catalysts?|control) => PLATIN:KATALYSATOR:-
^(?s)(?!.*(solid[- ]oxide|carbonate fuel|molten carbonate)).*?(electrolyz\w+|fuel cells?) => PLATIN:ELEKTROLYSE:-
\b(platinum|palladium|rhodium)\b => PLATIN
ammunition|munitions?|artillery shells? => WOLFRAM:MUNITION:- KUPFER:MUNITION:- ANTIMON:MUNITION:-
\bantimony\b => ANTIMON
superconduct\w+ => NIOB:SUPRALEITER:-
\bniobium\b|ferroniobium => NIOB
\btantalum\b => TANTAL
wind turbines? => SELTENE_ERDEN:WIND:-
hard disk drives?|\bhdds?\b|data storage devices? => SELTENE_ERDEN:MAGNET:- PLATIN:SPEICHER:- ALUMINIUM:SPEICHER:-
(designs?|manufactur\w*|develops?)\W+(\w+\W+){0,6}(smartphones?|laptops?|personal computers?|wearables?|consumer electronics) => ALUMINIUM:GEHAEUSE:- LITHIUM:AKKU:- SELTENE_ERDEN:MAGNET:-
(manufactur(es|ed|ing|ers? of)|produc(es|ed|ing|ers? of)|designs?|builds?|develops?|repairs?|overhauls?|suppl(ies|ier of))\W+((?!leas|rent)\w+\W+){0,6}(aircraft|jet|aero|turbofan)[- ]?engines?|gas turbines? => NICKEL:TRIEBWERK:- KOBALT:TRIEBWERK:-
(manufactur(es|ed|ing|ers? of)|produc(es|ed|ing|ers? of)|designs?|builds?|develops?)\W+((?!leas|rent)\w+\W+){0,5}(aircraft|jets|airplanes|helicopters)\b(?! (engines?|leasing|parts|components|maintenance|interiors?|seats?))|aerostructures? => TITAN:STRUKTUR:- ALUMINIUM:STRUKTUR:-
printed circuit boards?|\bpcbs?\b|electronics manufacturing services? => KUPFER:LEITER:- ZINN:LOT:- GOLD:LOT:-
(manufactur\w*|produc\w*|design\w*)\W+(\w+\W+){0,6}(connectors?|interconnects?)\b|electronic connectors?|interconnect (products|solutions|components|devices|systems) => KUPFER:LEITER:- GOLD:LOT:-
\buranium\b|\bnuclear fuel\b => URAN
nuclear (power|energy|generation|plants?|fleet|reactors?)|small modular reactors?|advanced reactors?|\bsmrs?\b|reactor (designs?|vendors?|technology) => URAN:BRENNSTOFF:Z
(uranium|nuclear fuel) (enrichment|conversion|fabrication)|enrich\w* uranium|\bhaleu\b => URAN:ANREICHERUNG:-
uranium (mine|mining|exploration|resources?|deposits?|production|projects?)|in[- ]situ (recovery|leach\w*)|yellowcake|\bu3o8\b => URAN:FOERDERUNG:-
(automobile|automotive|car|vehicle|motorcycle) (manufacturer|maker)s?|manufacturers? of (passenger )?(cars|automobiles|motor vehicles|motorcycles)|(produces?|builds?|makes?|manufactures?) (passenger )?(cars|automobiles|motorcycles|trucks) => LITHIUM:AKKU:Z KOBALT:AKKU:Z NICKEL:AKKU:Z GRAPHIT:AKKU:Z SELTENE_ERDEN:MAGNET:Z ALUMINIUM:LEICHTBAU:P EISENERZ:KAROSSERIE:P KUPFER:KABEL:P PLATIN:KATALYSATOR:P
automobilhersteller|fahrzeughersteller|autohersteller|automobilkonzern|kraftfahrzeuge?|personenkraftwagen|\bpkw\b|nutzfahrzeuge? => LITHIUM:AKKU:Z KOBALT:AKKU:Z NICKEL:AKKU:Z GRAPHIT:AKKU:Z SELTENE_ERDEN:MAGNET:Z ALUMINIUM:LEICHTBAU:P EISENERZ:KAROSSERIE:P KUPFER:KABEL:P PLATIN:KATALYSATOR:P
lithium-ionen|batteriezellen?|akkuzellen?|batteriehersteller => LITHIUM:AKKU:-
lithium(vorkommen|abbau)|lithiumbergbau => LITHIUM:FOERDERUNG:-
halbleiter(fertigung|hersteller)|chipfertigung|chiphersteller|auftragsfertiger => SILIZIUM:WAFER:-
kupfer(bergbau|mine|minen|hütte|hütten|förderung) => KUPFER:FOERDERUNG:-
(kabel und leitungen|stromkabel|energiekabel|kupferkabel|kupferdraht|transformatoren|schaltanlagen|elektromotoren) => KUPFER:KABEL:-
stahl(werke?|erzeugung|produktion|hersteller)|rohstahl|hochöfen => EISENERZ:STAHL:-
eisenerz(bergbau|förderung|mine|minen) => EISENERZ:FOERDERUNG:-
seltene erden|permanentmagnete? => SELTENE_ERDEN:MAGNET:-
windkraftanlagen|windturbinen|windenergieanlagen => SELTENE_ERDEN:WIND:-
solarmodule?|photovoltaik(module|anlagen)?|solarzellen? => SILIZIUM:SOLAR:- SILBER:SOLAR:-
aluminium(hütten?|produktion|erzeugung)|bauxit => ALUMINIUM:HUETTE:-
flugzeug(bau|hersteller)|triebwerke? => TITAN:STRUKTUR:- ALUMINIUM:STRUKTUR:-
munition|rüstungs(konzern|unternehmen|güter)|panzer(fahrzeuge)?|waffensysteme => WOLFRAM:MUNITION:- KUPFER:MUNITION:- ANTIMON:MUNITION:-
elektrolyseure?|brennstoffzellen? => PLATIN:ELEKTROLYSE:-
(gold|silber)(bergbau|minen?|förderung) => GOLD:FOERDERUNG:-
uran(bergbau|förderung|minen?|vorkommen)|yellowcake => URAN:FOERDERUNG:-
kernkraftwerke?|kernenergie|atomkraft\w*|kernreaktoren?|kernbrennstoffe? => URAN:BRENNSTOFF:Z
urananreicherung|brennelemente => URAN:ANREICHERUNG:-
""".trim().lines().filter { it.contains(" => ") }.map { z ->
        Regel(Regex(z.substringBefore(" => ").trim()), eintraege(z.substringAfter(" => ")))
    }

    // Beschreibung eines Chipentwicklers ohne eigene Fertigung
    private val FABLESS_MUSTER = Regex("\\bfabless\\b|(does not|doesn't|do not) (own|operate) (any )?(wafer )?fabs?|third[- ]party foundr(y|ies)|outsourc\\w+ (wafer )?(fabrication|manufacturing)")

    // Firmen, bei denen eine Form SD nichts ueber Konfliktmineralien im Produkt sagt: Foerderer melden dort Zahlungen an Staaten
    private fun foerderer(w: Wertbild, industrie: String): Boolean {
        val n = industrie.lowercase()
        return w.branche == "BERGBAU" || w.branche == "OEL_GAS" || listOf("mining", "gold", "silver", "copper", "coal", "uranium", "oil & gas", "metals").any { n.contains(it) }
    }

    // [0.77] Beschreibung einer Bergbau- oder Erkundungsfirma (Kleinschreibung)
    private val ERKUNDER_MUSTER = Regex(
        "explores? for\\b|\\b(mineral|resource|metals?|gold|silver|copper|uranium|lithium|nickel|cobalt|zinc|graphite|rare[- ]earths?) explor(ation|er)\\w*|" +
            "exploration[- ]stage|exploration,? (and|&) (development|evaluation|acquisition|mining)|acquisition,? (and )?exploration|exploration,? (and )?development|" +
            "\\bmineral (properties|property|claims|projects?|deposits?)\\b|\\b(mining|exploration) (company|properties|claims)\\b|junior (miner|mining|explor\\w+)|" +
            "explorationsunternehmen|rohstoffexplor\\w*|bergbauunternehmen|bergbaugesellschaft|explorationsgesellschaft",
    )

    // Die Firma sucht oder foerdert Rohstoffe. Ist die Industrie bekannt, entscheidet sie: Stahlwerke, Aluminiumhuetten und
    // Metallverarbeiter stehen im Katalog unter Bergbau, kaufen ihre Rohstoffe aber ein. Ohne Industrie zaehlen die Branche
    // Bergbau oder eine Beschreibung als Erkunder ("bitcoin mining company" ist keine).
    internal fun bergbau(w: Wertbild, industrie: String, text: String): Boolean {
        val n = industrie.lowercase()
        if (n.isNotBlank()) return listOf("mining", "gold", "silver", "copper", "uranium").any { n.contains(it) }
        return w.branche == "BERGBAU" || (ERKUNDER_MUSTER.containsMatchIn(text) && !KRYPTO.containsMatchIn(text))
    }

    private val KRYPTO = Regex("bitcoin|crypto|blockchain|digital assets?")

    // [0.77] Handel, Software und Reifenbau in der Industrie "Auto Parts": Nur Hersteller von Bauteilen brauchen Kupfer, Aluminium
    // und Stahl fuer Fahrzeugteile. Die Beschreibung trennt: Reifenhersteller, Haendler (Verkauf, Vertrieb, Laeden) und
    // Softwarefirmen ohne eigene Hardware.
    private val REIFENBAU = Regex("\\b(manufactur(es|ed|ing|ers?)|produc(es|ed|ing|ers?)|develops?|makes?)\\W+((?!retail|repair)\\w+\\W+){0,6}(tires|tyres)\\b")
    private val AUTO_SOFTWARE = Regex("self[- ]driving|autonomous (driving|vehicles?)|\\bsoftware\\b")
    private val AUTO_HARDWARE = Regex("\\b(chips?|sensors?|cameras?|lidars?|radars?|modules?|semiconductors?)\\b")
    private val AUTO_HANDEL = Regex("\\b(retail\\w*|distribut(or|ors|es|ion)|wholesale|stores|aftermarket parts|replacement parts|e-?commerce)\\b")
    private val AUTO_HERSTELLER = Regex("\\b(re)?(manufactur\\w*|fabricat\\w*|assembl(es|ed|ing)|foundr\\w*|casting)\\b")

    private fun autoProfil(text: String): String = when {
        text.isBlank() -> "AUTOZULIEFERER"
        REIFENBAU.containsMatchIn(text) -> "REIFEN"
        AUTO_SOFTWARE.containsMatchIn(text) && !AUTO_HARDWARE.containsMatchIn(text) && !AUTO_HERSTELLER.containsMatchIn(text) -> "KEINE"
        AUTO_HANDEL.containsMatchIn(text) && !AUTO_HERSTELLER.containsMatchIn(text) -> "KEINE"
        else -> "AUTOZULIEFERER"
    }

    // [0.77] Firmen, die nach der Beschreibung nur Royalties und Streams halten (keine eigene Mine): "royalty and stream company",
    // "acquires and manages streams and royalties". Gilt fuer jede Branche und jedes Metall.
    private val LIZENZ_FIRMA = Regex(
        "\\b(royalt(y|ies)|stream(s|ing)?)( (and|&) (royalt(y|ies)|stream(s|ing)?))? (company|corporation|business)\\b|" +
            "(acquires?|manages?|holds?|owns?|purchases?)[^.;]{0,80}(stream\\w*[, ]+(and |& )?royalt|royalt\\w*[, ]+(and |& )?stream)",
    )
    private val LIZENZ_BETRIEB = Regex("operates? (\\w+ ){0,3}mines?\\b|mining operations|(owns?|operates?) and (operates?|owns?)|\\bproduc(es|ing) (gold|silver|copper|zinc|nickel|lithium|uranium)")

    // [0.77] Ein Duenger- oder Chemiehersteller mit eigenem Bergbau und Phosphatgeschaeft gewinnt das Phosphat selbst
    // ("Potash segment includes mining and processing of potash", "Phosphate segment manufactures ... phosphate fertilizers")
    private val PHOSPHAT_GESCHAEFT = Regex("phosphates? (segment|business|division)|(manufactur\\w*|produc\\w*) [^.;]{0,60}phosphate (fertili\\w*|feed|rock|products?)|purified phosphoric acid")
    private val EIGENER_BERGBAU = Regex("\\b(includes?|involves?|segment) mining|mining and (processing|production|refining)|(owns?|operates?) (and operates? )?(\\w+ ){0,3}mines?\\b")

    // Ein Rohstoff, den die Beschreibung nur als Ausgangsstoff einer Verarbeitung nennt ("antimony trioxide from base forms of
    // antimony metal"): Der Hersteller erzeugt das Folgeprodukt, den Rohstoff kauft er ein.
    private val AUSGANGSSTOFF = Regex("\\bfrom\\s+(?:(?:base|raw|crude|primary|refined|scrap)\\s+){0,2}(?:forms?\\s+of\\s+|sources?\\s+of\\s+)?$")

    internal fun ausgangsstoff(text: String, stelle: IntRange): Boolean {
        val satzAnfang = maxOf(text.lastIndexOf(". ", stelle.first) + 1, text.lastIndexOf("; ", stelle.first) + 1, stelle.first - 80, 0)
        val vor = text.substring(satzAnfang, stelle.first)
        val m = AUSGANGSSTOFF.find(vor) ?: return false
        // "from copper concentrate" ist keine Verarbeitung des Metalls: ohne Zusatzwort vor dem Rohstoff zaehlt nur die Formulierung "forms of"
        return m.value.contains("forms") || m.value.contains("source") || Regex("(base|raw|crude|primary|refined|scrap)").containsMatchIn(m.value)
    }

    // Hinter dem Rohstoff steht Mine, Raffinerie, Projekt und Aehnliches: die Beschreibung nennt ihn als Erzeugnis
    private fun erzeugtDirekt(text: String, stelle: IntRange): Boolean {
        val ende = stelle.last + 1
        return ERZEUGT_NACH.containsMatchIn(text.substring(ende, minOf(ende + 80, text.length)))
    }

    // Alle Fundstellen des Rohstoffs in der Beschreibung sind Ausgangsstoffe
    private fun nurAusgangsstoff(muster: Regex, text: String): Boolean {
        val stellen = muster.findAll(text).map { it.range }.toList()
        return stellen.isNotEmpty() && stellen.any { ausgangsstoff(text, it) } && stellen.none { erzeugtDirekt(text, it) }
    }

    // Name des Rohstoffs in einer Beschreibung (Kleinschreibung). Ein Foerderer nennt, was er sucht oder foerdert; was nur
    // aus einem Erzeugnis abgeleitet waere ("for electric vehicles", "wind turbines"), ist bei ihm kein Bedarf.
    private val NAMEN: Map<String, Regex> = """
LITHIUM = lithium|spodumene|lepidolite
KOBALT = cobalt|kobalt
NICKEL = nickel
GRAPHIT = graphite|graphit
SELTENE_ERDEN = rare[- ]earths?|neodymium|praseodymium|ndpr|dysprosium|terbium|monazite|seltenen? erden
KUPFER = copper|kupfer
ALUMINIUM = alumin(i)?um|bauxite?|alumina
ZINN = \btin\b|zinn
ZINK = zinc|zink
EISENERZ = iron[- ]ore|hematite|magnetite|eisenerz
MANGAN = mangan(ese)?
WOLFRAM = tungsten|wolfram(ite)?|scheelite
VANADIUM = vanadium
TITAN = titanium|rutile|ilmenite
GALLIUM = gallium
GERMANIUM = germanium
SILIZIUM = silicon metal|ferrosilicon|polysilicon|silizium
HELIUM = helium
PHOSPHAT = phosphat
FLUSSSPAT = fluorspar|fluorite|flussspat
SILBER = \bsilver\b|silber
GOLD = \bgold\b
PLATIN = platin|palladium|rhodium|\bpgms?\b
ANTIMON = antimon
NIOB = niob
TANTAL = tantal|coltan
URAN = uranium|\buran\b|u3o8|yellowcake
""".trim().lines().filter { it.contains(" = ") }.associate { it.substringBefore(" = ").trim() to Regex(it.substringAfter(" = ").trim()) }

    // [0.77] Jede Branche: Die Beschreibung nennt den Rohstoff als eigenes Erzeugnis ("produces nickel, cobalt and copper",
    // "cobalt refinery", "recycling of lithium"). Davor steht ein Wort fuer Gewinnung oder Erzeugung und bis zum Rohstoff nur
    // eine Aufzaehlung; oder dahinter steht Mine, Projekt, Lagerstaette, Raffinerie, Rueckgewinnung.
    private val ERZEUGT_VOR = Regex(
        "\\b(?:explor\\w+ for|prospect\\w* for|mines|mining(?: of)?|produc(?:es|ed|ing)|(?:production|producers?|suppliers?|sale|refining|smelting|recycling|recovery|extraction|processing) of|" +
            "production and (?:sale|marketing|distribution) of|refin(?:es|ed|ing)|smelts?|smelting|recycl(?:es|ing)|recover(?:s|ing)|extract(?:s|ing)|" +
            "(?:deposits?|reserves|resources) of|royalt(?:y|ies) (?:on|in|from)|streams? (?:on|of|in))\\s+",
    )
    private val ERZEUGT_LISTE = Regex("(?:[\\w'-]+(?:,|\\s+and|\\s+or)?\\s+){0,10}")
    private val ERZEUGT_SPERRE = Regex("\\b(using|use[sd]?|with|from|containing|contains?|that|which|by|into|made|based|for|to|in|at|on|its|their|through|via)\\b")
    private val ERZEUGT_NACH = Regex(
        "^(?:[ ,/-]+(?:and |or )?[\\w-]+){0,3}?[ -]+(?:mines?|mining|miners?|projects?|deposits?|propert(?:y|ies)|producers?|production|refiner(?:y|ies)|refining|smelters?|smelting|" +
            "concentrates?|royalt(?:y|ies)|streams?|streaming|recycling|recovery|exploration|claims|reserves|resources?|ores?)\\b" +
            "(?! (?:equipment|machinery|industry|industries|companies|customers|sectors?|markets?|services|software|trucks|tools))",
    )
    // Dahinter steht ein anderes Erzeugnis: "cobalt alloys", "copper wire", "cobalt-free"
    private val ERZEUGT_NICHT = Regex("^[- ](?:free|based|containing|alloys?|batter\\w+|cathodes?|anodes?|wires?|cables?|foils?|tub\\w+|plat\\w+|coat\\w+|clad|chemistr\\w+|magnets?|components?|parts|fittings|electrodes?|products)\\b")

    internal fun erzeugt(text: String, stelle: IntRange): Boolean {
        val satzAnfang = maxOf(text.lastIndexOf(". ", stelle.first) + 1, text.lastIndexOf("; ", stelle.first) + 1, stelle.first - 200, 0)
        val ende = stelle.last + 1
        val satzEnde = listOf(text.indexOf(". ", ende), text.indexOf("; ", ende)).filter { it >= 0 }.minOrNull() ?: text.length
        val nach = text.substring(ende, minOf(satzEnde, ende + 80, text.length))
        if (ERZEUGT_NACH.containsMatchIn(nach)) return true
        if (ERZEUGT_NICHT.containsMatchIn(nach)) return false
        val vor = text.substring(satzAnfang, stelle.first)
        return ERZEUGT_VOR.findAll(vor).any { m ->
            val zwischen = vor.substring(m.range.last + 1)
            ERZEUGT_LISTE.matches(zwischen) && !ERZEUGT_SPERRE.containsMatchIn(zwischen)
        }
    }

    // [0.77] Jede Branche: Die Beschreibung nennt den Rohstoff als Einsatzstoff ("contain cobalt", "made from titanium",
    // "raw materials such as nickel", "cobalt alloys", "copper-based").
    private val EINSATZ_VOR = Regex(
        "\\b(?:contain(?:s|ing)?|us(?:es|ing)|made (?:of|from|with)|based on|consum(?:es|ing)|purchas(?:es|ing)|buys?|sourc(?:es|ing)|procur(?:es|ing)|requir(?:es|ing)|" +
            "raw materials?,? (?:such as|including|like|are|is)|relies on|depends on|dependent on)\\s+",
    )
    private val EINSATZ_SPERRE = Regex("\\b(that|which|by|into|for|to|in|at|its|their|through|via|no|not|without)\\b")
    private val EINSATZ_NACH = Regex("^[- ](?:based|containing|alloys?|superalloys?|batter\\w+|cathodes?|anodes?|wires?|cables?|foils?|tub\\w+|plat\\w+|coat\\w+|clad|chemistr\\w+|magnets?|components?|parts|fittings|electrodes?|products|content)\\b")

    internal fun eingesetzt(text: String, stelle: IntRange): Boolean {
        val satzAnfang = maxOf(text.lastIndexOf(". ", stelle.first) + 1, text.lastIndexOf("; ", stelle.first) + 1, stelle.first - 200, 0)
        val ende = stelle.last + 1
        if (EINSATZ_NACH.containsMatchIn(text.substring(ende, minOf(ende + 40, text.length)))) return true
        val vor = text.substring(satzAnfang, stelle.first)
        return EINSATZ_VOR.findAll(vor).any { m ->
            val zwischen = vor.substring(m.range.last + 1)
            ERZEUGT_LISTE.matches(zwischen) && !EINSATZ_SPERRE.containsMatchIn(zwischen)
        }
    }

    // ------------------------------------------------------------------ Rechnung
    fun fuer(i: Einflussverzeichnis.Index, w: Wertbild, sec: SecAngaben? = null): Ergebnis {
        val sym = w.symbol.uppercase()
        val ind = w.industrie.orEmpty()
        val text = w.beschreibung.orEmpty().lowercase()
        val aus = LinkedHashMap<String, Treffer>()

        fun ablegen(e: E, q: Quelle, weg: Weg? = e.weg, bit: Int = 0) {
            if (e.id !in i.rohstoffe) return
            val alt = aus[e.id]
            if (alt == null) {
                aus[e.id] = Treffer(e.id, e.verwendung ?: if (q == Quelle.BRANCHE) STANDARD_VERWENDUNG[e.id] else null, weg, q, bit)
                return
            }
            // Je genauer die Quelle, desto eher gilt ihre Verwendung; die SEC nennt nur das Mineral und laesst Verwendung und Weg stehen
            val neuVor = q != Quelle.SEC && q.ordinal <= alt.quelle.ordinal
            aus[e.id] = Treffer(
                e.id, if (neuVor) e.verwendung ?: alt.verwendung else alt.verwendung ?: e.verwendung,
                if (neuVor) weg ?: alt.weg else alt.weg ?: weg, if (q.ordinal < alt.quelle.ordinal) q else alt.quelle, alt.sec or bit,
            )
        }

        val firma = FIRMEN[sym]
        val fabless = (firma != null && firma in FABLESS_PROFILE) || (firma == null && FABLESS_MUSTER.containsMatchIn(text))
        var suchen = true
        if (firma != null) {
            PROFILE[firma].orEmpty().forEach { ablegen(it, Quelle.FIRMA) }
            suchen = false
        } else if (fabless) {
            PROFILE.getValue("FABLESS").forEach { ablegen(it, Quelle.BESCHREIBUNG) }
        } else {
            val fein = if (ind.isNotBlank()) industrie(ind) else null
            if (fein != null) {
                val profil = if (fein.profil == "AUTOZULIEFERER") autoProfil(text) else fein.profil
                PROFILE[profil].orEmpty().forEach { ablegen(it, Quelle.INDUSTRIE) }
                if (fein.ohneBlick) suchen = false
            } else {
                val kern = w.branche?.let { brancheZeilen(i, it) }.orEmpty()
                kern.forEach { ablegen(it, Quelle.BRANCHE) }
            }
        }

        // Beschreibung: nur Rohstoffe mit Regel und Fundstelle; bei Fabless-Firmen alles ueber den Auftragsfertiger
        if (suchen && text.isNotBlank()) {
            // Bergbau und Erkundung: Ein Rohstoff, den die Beschreibung nur nennt, ist das eigene Ziel der Firma und kein Einkauf
            // Wer nur Royalties und Streams haelt, sucht und foerdert nichts selbst
            val lizenz = !fabless && firma == null && LIZENZ_FIRMA.containsMatchIn(text) && !LIZENZ_BETRIEB.containsMatchIn(text)
            val selbst = !fabless && !lizenz && bergbau(w, ind, text)
            // Jede andere Firma: Nennt die Beschreibung den Rohstoff als eigenes Erzeugnis, gilt dasselbe
            for (r in REGELN) if (r.muster.containsMatchIn(text)) for (it in r.eintraege) {
                val alt = aus[it.id]
                // Was die Beschreibung als eigenes Erzeugnis erkannt hat, bleibt es ("lithium deposits ... for lithium-ion batteries")
                if (!fabless && alt != null && alt.selbst && alt.quelle.ordinal <= Quelle.BESCHREIBUNG.ordinal) continue
                val offen = it.verwendung == null && (alt == null || alt.verwendung == null || alt.quelle == Quelle.BRANCHE)
                val e = when {
                    fabless -> it
                    selbst -> when {
                        it.verwendung == FOERDERUNG || it.verwendung == RECYCLING || it.verwendung == "HUETTE" -> it
                        // Die Industrie nennt ihn schon als gefoerdert: dabei bleibt es
                        alt != null && alt.selbst -> continue
                        NAMEN[it.id]?.let { n -> n.containsMatchIn(text) && nurAusgangsstoff(n, text) } == true -> E(it.id, EINSATZSTOFF, null)
                        NAMEN[it.id]?.containsMatchIn(text) == true -> E(it.id, ERKUNDUNG, Weg.EIGEN)
                        else -> continue
                    }
                    !offen -> it
                    NAMEN[it.id]?.let { n -> nurAusgangsstoff(n, text) } == true -> E(it.id, EINSATZSTOFF, null)
                    r.muster.findAll(text).any { m -> erzeugt(text, m.range) } -> E(it.id, ERZEUGUNG, Weg.EIGEN)
                    r.muster.findAll(text).any { m -> eingesetzt(text, m.range) } -> E(it.id, EINSATZSTOFF, null)
                    else -> it
                }
                ablegen(e, Quelle.BESCHREIBUNG, if (fabless) Weg.FERTIGER else e.weg)
            }
            // Ein Foerderer nennt auch Rohstoffe, fuer die es keine Regel braucht ("explores for gold, silver, copper")
            if (selbst) for ((id, muster) in NAMEN) if (id !in aus && muster.containsMatchIn(text)) {
                ablegen(E(id, if (nurAusgangsstoff(muster, text)) EINSATZSTOFF else ERKUNDUNG, if (nurAusgangsstoff(muster, text)) null else Weg.EIGEN), Quelle.BESCHREIBUNG)
            }
            // Lizenzhalter: nur die Metalle, die die Beschreibung nennt; Zeilen aus Industrie oder Branche fallen weg
            if (lizenz) {
                val genannt = NAMEN.filter { (_, muster) -> muster.containsMatchIn(text) }.keys.filter { it in i.rohstoffe }
                aus.clear()
                for (id in genannt) aus[id] = Treffer(id, LIZENZ, Weg.EIGEN, Quelle.BESCHREIBUNG)
            }
            // Eigener Bergbau und Phosphatgeschaeft: das Phosphat stammt aus eigenen Minen
            if (!lizenz && firma == null && PHOSPHAT_GESCHAEFT.containsMatchIn(text) && EIGENER_BERGBAU.containsMatchIn(text)) {
                ablegen(E("PHOSPHAT", ERZEUGUNG, Weg.EIGEN), Quelle.BESCHREIBUNG)
            }
        }

        // SEC: Form SD nennt Zinn, Tantal, Wolfram und Gold; der Rohstoffabschnitt des Jahresberichts nennt weitere
        if (sec != null) {
            if (sec.sd.isNotBlank() && !foerderer(w, ind)) {
                for (id in KONFLIKT) ablegen(E(id, "KONFLIKT", Weg.ZULIEFERER), Quelle.SEC, bit = SEC_SD)
            }
            for (id in sec.rohstoffe) ablegen(E(id, null, null), Quelle.SEC, bit = SEC_BERICHT)
        }
        return Ergebnis(aus.values.toList(), fabless)
    }

    // Konfliktmineralien der Form SD
    val KONFLIKT = listOf("ZINN", "TANTAL", "WOLFRAM", "GOLD")

    fun betrifft(i: Einflussverzeichnis.Index, w: Wertbild, id: String, sec: SecAngaben? = null): Boolean = fuer(i, w, sec).treffer.any { it.id == id }

    // Rohstoffe, die in der Beschreibung oder im Rohstoffabschnitt eines Jahresberichts vorkommen (Kennungen, ohne Doppelte)
    fun ausText(text: String): List<String> {
        val t = text.lowercase()
        val ids = LinkedHashSet<String>()
        for (r in REGELN) if (r.muster.containsMatchIn(t)) r.eintraege.forEach { ids += it.id }
        return ids.toList()
    }

    // Klartext eines Jahresberichts (HTML): ohne Skripte und Auszeichnung, Leerraum zusammengezogen, klein geschrieben
    fun klartext(html: String): String =
        html.replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&").replace("&#8217;", "'").replace(Regex("\\s+"), " ").lowercase()

    private val RISIKO_KOPF = Regex("item\\s*1a\\W{0,12}risk\\s+factors")
    private val VERWEIS_DAVOR = Regex("(see|in|under|and|to|of|refer to|within|part i,?)\\W*$")

    // Beginn des Abschnitts "Item 1A. Risk Factors" im Klartext (der Rohstoffabschnitt steht davor, in Item 1); -1, wenn er
    // noch nicht erreicht ist. Zeilen des Inhaltsverzeichnisses (gleich dahinter folgt Item 1B) und Verweise im Fliesstext
    // (davor "see", "in", ein Anfuehrungszeichen) zaehlen nicht.
    fun risikoBeginn(klar: String): Int {
        for (m in RISIKO_KOPF.findAll(klar)) {
            val s = m.range.first
            val davor = klar.substring(maxOf(0, s - 20), s)
            if (davor.trimEnd().let { it.endsWith("\"") || it.endsWith("'") || it.endsWith("“") || it.endsWith("‘") } || VERWEIS_DAVOR.containsMatchIn(davor)) continue
            if (klar.substring(m.range.last + 1, minOf(klar.length, m.range.last + 201)).contains("item 1b")) continue
            return s
        }
        return -1
    }

    // Rohstoffabschnitt eines Jahresberichts (HTML oder Text): Fenster hinter "raw material" nach Rohstoffen absuchen, nur
    // vor dem Abschnitt Risk Factors. Nur die Rohstoffe der Fenster zaehlen, nicht der ganze Bericht.
    fun ausJahresbericht(html: String): List<String> {
        val voll = klartext(html)
        val schnitt = risikoBeginn(voll)
        val roh = if (schnitt > 0) voll.substring(0, schnitt) else voll
        val ids = LinkedHashSet<String>()
        var von = 0
        var n = 0
        while (n < MAX_FENSTER) {
            val p = roh.indexOf("raw material", von)
            if (p < 0) break
            val ende = minOf(roh.length, p + FENSTER)
            ids += ausText(roh.substring(maxOf(0, p - 200), ende))
            von = ende
            n++
        }
        return ids.filter { it !in ALLGEMEIN }
    }

    // Zu viele Treffer waeren Rauschen; nur bestimmte Rohstoffe zaehlen aus dem Bericht
    private val ALLGEMEIN = setOf("EISENERZ", "ALUMINIUM")
    private const val FENSTER = 700
    private const val MAX_FENSTER = 40
}
