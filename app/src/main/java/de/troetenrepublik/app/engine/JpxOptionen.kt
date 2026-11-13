package de.troetenrepublik.app.engine

// Ersatz fuer Aktien aus Japan ohne US-Optionen [0.76]: Tageskurse der Aktienoptionen der Osaka Exchange (JPX), eine
// kleine CSV mit allen gehandelten Kontrakten. Der Link steht in einer JSON-Datei, die die Seite der JPX selbst
// einliest. Nur Umsatz; offene Kontrakte liegen dort nur als Excel-Datei. Japanische Aktienoptionen werden duenn
// gehandelt, viele Werte bleiben unter dem Mindestumsatz.
object JpxOptionen {

    const val VERZEICHNIS_URL = "https://www.jpx.co.jp/automation/english/markets/derivatives/trading-volume/json/e_derivatives_market_data_eve.json"
    const val SERVER = "https://www.jpx.co.jp"
    const val VERZEICHNIS_MAX = 50_000
    const val DATEI_MAX = 600_000

    private val SYMBOL = Regex("^([0-9]{3}[0-9A-Z])\\.T$")

    // Wertpapiercode der JPX zum Yahoo-Kuerzel (7974.T wird 7974); null fuer andere Kuerzel.
    fun code(yahooSymbol: String): String? = SYMBOL.matchEntire(yahooSymbol.uppercase())?.groupValues?.get(1)

    // Pfad der Kurs-CSV aus dem Verzeichnis; null, wenn keine verlinkt ist.
    fun dateiPfad(verzeichnis: String): String? =
        Regex("\"File\"\\s*:\\s*\"([^\"]*individual_options_quotes\\.csv)\"").find(verzeichnis)?.groupValues?.get(1)

    private val MUSTER = Regex("^[0-9]+,([0-9]{3}[0-9A-Z]) OOP ([CP])[0-9]+-[0-9]+,")

    // Umsatz je Wertpapiercode als Zahlen (Calls, Puts); offene Kontrakte sind 0. Die Zeilen heissen
    // "32414797,7974 OOP C2612-9250,...,total volume,total value,count,date".
    fun lesen(csv: String): Map<String, OptionsZahlen> {
        val zeilen = csv.lineSequence().iterator()
        if (!zeilen.hasNext()) return emptyMap()
        val kopf = zeilen.next().split(',').map { it.trim().lowercase() }
        val spalte = kopf.indexOf("total volume")
        if (spalte < 0) return emptyMap()
        val summen = HashMap<String, LongArray>()
        for (z in zeilen) {
            val m = MUSTER.find(z) ?: continue
            val v = z.split(',').getOrNull(spalte)?.trim()?.toLongOrNull() ?: continue
            val s = summen.getOrPut(m.groupValues[1]) { LongArray(2) }
            s[if (m.groupValues[2] == "C") 0 else 1] += v
        }
        return summen.mapValues { (_, s) -> OptionsZahlen(s[0], s[1], 0, 0) }
    }
}
