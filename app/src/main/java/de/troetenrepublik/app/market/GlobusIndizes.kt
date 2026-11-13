package de.troetenrepublik.app.market

// Leitindex je Land fuer den Globus: Kuerzel des Landes (ISO 3166 alpha-2) -> Yahoo-Kuerzel und Name. Alle Kuerzel am
// 09.11.2026 mit dem Chart-Endpunkt geprueft und mit Kursen von diesem Tag; Indizes mit nur einem Tageskurs sind brauchbar
// (der Vortag steht in chartPreviousClose). Nicht aufgenommen: Venezuela (IBC.CR meldet +64,7 % gegen einen veralteten
// Vortagswert), Argentinien und Peru nur als ETF (^IPSA und Schreibweisen liefern bei Yahoo nichts; ^MERV fuehrt am 12.11.2026 nur
// einen Kurs von Mitte Oktober).
object GlobusIndizes {
    // etf: Laender-ETF in US-Dollar als Ersatz, wo Yahoo keinen brauchbaren Index fuehrt. laenderIndex: MSCI- oder FTSE-Laenderindex
    // statt des Leitindex der Boerse (echter Preisindex mit Tageskurs, aber nicht der Index der Boerse selbst)
    data class Index(val symbol: String, val name: String, val etf: Boolean = false, val laenderIndex: Boolean = false)

    val NACH_LAND: Map<String, Index> = linkedMapOf(
        "US" to Index("^GSPC", "S&P 500"),
        "DE" to Index("^GDAXI", "DAX"),
        "GB" to Index("^FTSE", "FTSE 100"),
        "FR" to Index("^FCHI", "CAC 40"),
        "JP" to Index("^N225", "Nikkei 225"),
        "CN" to Index("000001.SS", "SSE Composite"),
        "HK" to Index("^HSI", "Hang Seng"),
        "IN" to Index("^BSESN", "BSE Sensex"),
        "BR" to Index("^BVSP", "Ibovespa"),
        "CA" to Index("^GSPTSE", "S&P/TSX Composite"),
        "AU" to Index("^AXJO", "S&P/ASX 200"),
        "KR" to Index("^KS11", "KOSPI"),
        "TW" to Index("^TWII", "TAIEX"),
        "MX" to Index("^MXX", "IPC"),
        "CH" to Index("^SSMI", "SMI"),
        "NL" to Index("^AEX", "AEX"),
        "ES" to Index("^IBEX", "IBEX 35"),
        "IT" to Index("FTSEMIB.MI", "FTSE MIB"),
        "SE" to Index("^OMX", "OMX Stockholm 30"),
        "SG" to Index("^STI", "STI"),
        "ID" to Index("^JKSE", "IDX Composite"),
        "TR" to Index("XU100.IS", "BIST 100"),
        "ZA" to Index("^J203.JO", "FTSE/JSE All Share"),
        "NZ" to Index("^NZ50", "S&P/NZX 50"),
        "BE" to Index("^BFX", "BEL 20"),
        "AT" to Index("^ATX", "ATX"),
        "NO" to Index("OSEBX.OL", "Oslo Børs Benchmark"),
        "DK" to Index("^OMXC25", "OMX Copenhagen 25"),
        "FI" to Index("^OMXH25", "OMX Helsinki 25"),
        "PT" to Index("PSI20.LS", "PSI"),
        "PL" to Index("WIG20.WA", "WIG20"),
        "TH" to Index("^SET.BK", "SET"),
        "MY" to Index("^KLSE", "FTSE Bursa Malaysia KLCI"),
        "PH" to Index("PSEI.PS", "PSEi"),
        "SA" to Index("^TASI.SR", "Tadawul All Share"),
        "EG" to Index("^CASE30", "EGX 30"),
        "IL" to Index("^TA125.TA", "TA-125"),
        "IE" to Index("^ISEQ", "ISEQ All Share"),
        "GR" to Index("GD.AT", "Athex Composite"),
        "HU" to Index("^BUX.BD", "BUX"),
        // Nasdaq Baltic: Gesamtindizes (am 06.11.2026 mit aktuellen Tageskursen geprueft)
        // Island (am 07.11.2026 geprueft, Yahoo fuehrt ihn wieder mit Tageskurs)
        "IS" to Index("^OMXI15", "OMX Iceland 15"),
        "EE" to Index("^OMXTGI", "OMX Tallinn GI"),
        "LV" to Index("^OMXRGI", "OMX Riga GI"),
        "LT" to Index("^OMXVGI", "OMX Vilnius GI"),
        // Leitindizes mit nur einem Tageskurs (Vortag ueber chartPreviousClose)
        "CZ" to Index("FPXAA.PR", "PX"),
        "RO" to Index("^BET.RO", "BET"),
        "AE" to Index("DFMGI.AE", "DFM General Index"),
        "QA" to Index("^GNRI.QA", "QE Index"),
        "KW" to Index("^BKP.KW", "Boursa Kuwait Premier"),
        "VN" to Index("^VNINDEX.VN", "VN-Index"),
        // MSCI- und FTSE-Laenderindizes fuer Laender ohne Leitindex bei Yahoo
        "CO" to Index("^737809-COP-STRD", "MSCI COLCAP", laenderIndex = true),
        "MA" to Index("^105765-MAD-STRD", "MSCI Morocco", laenderIndex = true),
        "JO" to Index("^940000-JOD-STRD", "MSCI Jordan", laenderIndex = true),
        "OM" to Index("^133714-OMR-STRD", "MSCI Oman", laenderIndex = true),
        "NA" to Index("N098.FGI", "FTSE/JSE Namibia", laenderIndex = true),
        "CL" to Index("WICHL.FGI", "FTSE Chile", laenderIndex = true),
        // Ersatz ueber Laender-ETF in US-Dollar, wo es keinen Index mit Tageskurs gibt
        "AR" to Index("ARGT", "Global X MSCI Argentina", etf = true),
        "PE" to Index("EPU", "iShares MSCI Peru", etf = true),
    )

    // Leitindizes ohne Yahoo-Kurs, direkt von der Boerse abgerufen (BoersenDirekt); das Symbol ist die Kennung der Quelle.
    val DIREKT: Map<String, Index> = BoersenDirekt.laender().mapValues { (_, q) -> Index(q.id, q.index) }

    val SYMBOLE: List<String> = NACH_LAND.values.map { it.symbol }
}
