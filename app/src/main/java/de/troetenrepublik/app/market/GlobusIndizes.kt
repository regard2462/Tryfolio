package de.troetenrepublik.app.market

// Leitindex je Land fuer den Globus (0.69): Kuerzel des Landes (ISO 3166 alpha-2) -> Yahoo-Kuerzel und Name.
// Alle Kuerzel am 06.11.2026 mit der Sammelabfrage geprueft; ^MERV (Argentinien), ^IPSA (Chile), ^PX (Tschechien),
// ^COLCAP und ^VNINDEX lieferten keinen brauchbaren Kurs und stehen deshalb nicht darin.
object GlobusIndizes {
    // etf: Laender-ETF in US-Dollar als Ersatz, wo Yahoo keinen brauchbaren Leitindex fuehrt
    data class Index(val symbol: String, val name: String, val etf: Boolean = false)

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
        "EE" to Index("^OMXTGI", "OMX Tallinn GI"),
        "LV" to Index("^OMXRGI", "OMX Riga GI"),
        "LT" to Index("^OMXVGI", "OMX Vilnius GI"),
        // Ersatz ueber Laender-ETF in US-Dollar, wo Yahoo keinen Leitindex mit Tageskurs fuehrt (am 06.11.2026 geprueft;
        // ^MERV, ^IPSA, ^COLCAP, ^PX, ^VNINDEX u. a. liefern keine Tagesreihe, DFM, Kuwait und Island nur einen Kurs)
        "AR" to Index("ARGT", "Global X MSCI Argentina", etf = true),
        "CL" to Index("ECH", "iShares MSCI Chile", etf = true),
        "PE" to Index("EPU", "iShares MSCI Peru", etf = true),
        "AE" to Index("UAE", "iShares MSCI UAE", etf = true),
        "QA" to Index("QAT", "iShares MSCI Qatar", etf = true),
        "KW" to Index("KWT", "iShares MSCI Kuwait", etf = true),
        "VN" to Index("VNM", "VanEck Vietnam", etf = true),
    )

    val SYMBOLE: List<String> = NACH_LAND.values.map { it.symbol }
}
