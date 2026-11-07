package de.troetenrepublik.app.engine

// Boersen, deren Kurse die App nicht selbst abruft (Bedingungen verbieten automatische Abrufe, Bot-Pruefung oder nicht erreichbar):
// Das Laenderblatt zeigt dort eine Zeile "Kurse an der ...", die die Seite der Boerse fuer Menschen im eingebauten Browser oeffnet.
// Quelle der Adressen: eigene Pruefung vom 06.11.2026 (Startseite oder Kurs- bzw. Marktseite der Boerse).
object Boersenlinks {
    // selbst: Die Person oeffnet die Seite im Browser der App (loest eine Pruefung der Seite selbst), die App liest danach die angezeigten Werte
    // (SeitenLeser) und speichert sie eine Woche; waehrung = Waehrung der Seite, wenn sie keine nennt
    data class Link(val name: String, val adresse: String, val waehrung: String = "", val selbst: Boolean = false)

    const val SELBST_GUELTIG_MS = 7 * 24 * 3_600_000L

    val NACH_LAND: Map<String, Link> = mapOf(
        "NG" to Link("Nigerian Exchange", "https://ngxgroup.com/exchange/data/equities-price-list/"),
        "KE" to Link("Nairobi Securities Exchange", "https://www.nse.co.ke/dataservices/market-statistics/"),
        "TZ" to Link("Dar es Salaam Stock Exchange", "https://dse.co.tz/"),
        "RO" to Link("Bukarester Börse", "https://www.bvb.ro/FinancialInstruments/Markets/Shares"),
        "LU" to Link("Luxemburger Börse", "https://www.luxse.com/market-overview/market-statistics"),
        "OM" to Link("Muscat Stock Exchange", "https://www.msx.om/"),
        "BW" to Link("Botswana Stock Exchange", "https://www.bse.co.bw/"),
        "GH" to Link("Ghana Stock Exchange", "https://gse.com.gh/", "GHS", selbst = true),
        "MU" to Link("Stock Exchange of Mauritius", "https://www.stockexchangeofmauritius.com/products-market-data/equities-board/market-watch/official"),
        "JO" to Link("Amman Stock Exchange", "https://www.ase.com.jo/en/daily_summary"),
        "PH" to Link("Philippine Stock Exchange", "https://edge.pse.com.ph/"),
        "BG" to Link("Bulgarische Börse Sofia", "https://www.bse-sofia.bg/"),
        "GE" to Link("Georgische Börse", "https://www.gse.ge/en/capitalizations", "GEL", selbst = true),
        "KZ" to Link("Astana International Exchange", "https://market.aixkz.com/", "USD", selbst = true),
        "AR" to Link("Bolsas y Mercados Argentinos", "https://open.bymadata.com.ar/"),
        "BH" to Link("Bahrain Bourse", "https://bahrainbourse.com/en/Quotes%20and%20Market/Stocks/daily-trading-summary", "BHD", selbst = true),
        "TN" to Link("Börse Tunis", "https://tunis-stockexchange.com/marche-principal", "TND", selbst = true),
        "MN" to Link("Mongolische Börse", "https://open.mse.mn/securities", "MNT", selbst = true),
        "SK" to Link("Börse Bratislava", "https://www.bsse.sk/bcpb/en/market-data/statistics/monthly/", "EUR", selbst = true),
        "RW" to Link("Rwanda Stock Exchange", "https://rse.rw/equities-market", "RWF", selbst = true),
        "UZ" to Link("Börse Taschkent", "https://uzse.uz/isu_infos/STK?locale=en", "UZS", selbst = true),
        // Seiten mit Bot-Pruefung oder Sperre (Domains stehen in Rss.OHNE_LESEMODUS)
        "IS" to Link("Nasdaq Island", "https://www.nasdaq.com/european-market-activity/shares"),
        "CL" to Link("Bolsa de Santiago", "https://www.bolsadesantiago.com/"),
        "KW" to Link("Boursa Kuwait", "https://www.boursakuwait.com.kw/"),
        "CO" to Link("Bolsa de Valores de Colombia", "https://www.bvc.com.co/"),
        "JM" to Link("Jamaica Stock Exchange", "https://www.jamstockex.com/"),
        "ZM" to Link("Lusaka Securities Exchange", "https://www.luse.co.zm/"),
        "ZW" to Link("Zimbabwe Stock Exchange", "https://www.zse.co.zw/", "ZWG", selbst = true),
        "AM" to Link("Armenische Börse", "https://amx.am/en", "AMD", selbst = true),
        // nicht erreichbar
        "EG" to Link("Egyptian Exchange", "https://www.egx.com.eg/en/homepage.aspx", "EGP", selbst = true),
        "QA" to Link("Qatar Stock Exchange", "https://www.qe.com.qa/listed-companies", "QAR", selbst = true),
        "LB" to Link("Börse Beirut", "https://www.bse.com.lb/", "USD", selbst = true),
        "RS" to Link("Belgrader Börse", "https://www.belex.rs/", "RSD", selbst = true),
        "UA" to Link("PFTS Ukraine", "https://www.pfts.ua/", "UAH", selbst = true),
    )

    // Boersen mit Selbstabruf: gesperrt, mit Pruefung oder ohne freie Schnittstelle (BYMA und Bahrain nur mit Schluessel: Bahrain ebenfalls Selbstabruf,
    // Argentinien nur Link). Am Desktop und auf iOS gibt es keinen eingebauten Browser, dort oeffnet der Link extern.
    fun selbstFuer(a2: String): Link? = NACH_LAND[a2]?.takeIf { it.selbst }

    // Abgerufene Boersen brauchen keinen Link (Ausnahme Kasachstan: KASE wird abgerufen, die AIX nicht)
    fun fuer(a2: String): Link? = NACH_LAND[a2]
}
