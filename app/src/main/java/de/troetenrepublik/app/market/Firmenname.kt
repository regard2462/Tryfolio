package de.troetenrepublik.app.market

// Firmennamen verschiedener Quellen vergleichbar machen (Gattung, Notierungszusatz, Nennwert und Rechtsform
// weg); genutzt fuer Suchbegriffe bei Yahoo und Google News und fuer Namensvergleiche.
object Firmenname {

    // Zusatz am Ende eines Yahoo- oder Nasdaq-Namens, der nicht zur Firma gehoert: ADR- und Notierungszusaetze
    // ("Sponsored", "Registered", "NY Reg", "- New York Re...", "ADR"/"ADS"), "ORD ..." samt Nennwert und
    // "Common Stock", "Capital Stock", "Ordinary Shares".
    private val US_ZUSATZ = Regex(
        "(?i)\\s*-\\s*new york re.*$|\\s+ny reg.*$|\\s+sponsored.*$|\\s+registered.*$|\\s+ad[rs]\\b.*$|\\s+ord\\b.*$" +
            "|,?\\s+(?:common|capital|ordinary)\\s+(?:stock|shares?)\\b.*$",
    )

    // Nennwert am Ende: nur mit Waehrungszeichen oder -kuerzel oder Dezimaltrenner ("$0.25", "EUR0.07", "3.5P",
    // "0,10"); blosse ganze Zahlen wie bei "Phillips 66" gehoeren zum Namen.
    private val NENNWERT = Regex("(?i)[\\s,]+(?:\\$|eur|usd|gbp)\\d[\\d.,]*[a-z]?\\.?$|[\\s,]+\\d+[.,]\\d+[a-z]?\\.?$")

    // Gattung am Ende: "Class B", "Cl. B", "Series B", "ser. B", "ser.", "Vz", "Vzo", "St", "Namen-Akt.",
    // "Inhaber", "Inh.", "Nam." und einzelner Gattungsbuchstabe (A, B, C, I, N, P, R, S), nicht aber nach
    // "&" oder "and" ("Seven & i").
    private val GATTUNG = Regex(
        "(?i),?\\s+(?:class|cl|series|ser)\\.?\\s+\\w\\.?(?=\\s*$)|,?\\s+(?:vzo|vz\\.?|st\\.?|ser\\.?)\\s*$" +
            "|,?\\s+(?:namen-akt\\.?|inhaber|inh\\.?|nam\\.?)\\s*$|,?\\s+(?<!&\\s)(?<!\\band\\s)[abcinprs]\\s*$",
    )

    // Rechtsform am Ende: Inc, Corp, Co, Ltd, PLC, AG, SE, SA, N.V., AB, AS, Oyj und aehnliche, dazu
    // Aktiengesellschaft, p.l.c. und "& Co. KGaA".
    private val US_FORM = Regex(
        "(?i)(,?\\s+(?:incorporated|inc\\.?|corp\\.?|corporation|co\\.?|ltd\\.?|limited|plc|p\\.l\\.c\\.|ag|se|sa|s\\.a\\.|n\\.v\\.|nv|" +
            "asa|as|a[ /]*s|ab|oyj|a/s|s\\.p\\.a\\.|spa|aktiengesellschaft|vz\\.?|vzo|st\\.?|holdings?|& co\\. kgaa))+\\.?\\s*$",
    )

    // Rechtsform AB am Anfang ("AB Volvo" -> "Volvo").
    private val AB_ANFANG = Regex("(?i)^ab\\s+")

    // Name ohne Klammern, Notierungszusatz, Nennwert, Gattung und Rechtsform: am Ende wiederholt abtragen,
    // solange ein Wort uebrig bleibt.
    fun bereinigt(name: String): String {
        var s = name.replace(Regex("\\(.*?\\)"), " ")
        while (true) {
            val t = s.replace(US_ZUSATZ, " ").replace(NENNWERT, " ").replace(GATTUNG, " ").replace(US_FORM, " ")
                .replace(AB_ANFANG, "").replace(Regex("\\s+"), " ").trim().trimEnd(',', ' ')
            if (t == s || t.isBlank()) break
            s = t
        }
        return s.trim()
    }

    // Suchbegriffe fuer die Suche nach der US-Notierung: der bereinigte Name und, falls er ein Komma enthaelt,
    // zusaetzlich der Teil davor (Yahoo-Kurznamen wie "Ericsson, Telefonab. L M ser. B" -> "Ericsson").
    fun suchbegriffe(name: String): List<String> {
        val s = bereinigt(name)
        val vorKomma = if (s.contains(',')) bereinigt(s.substringBefore(',')) else null
        return listOfNotNull(s, vorKomma).filter { it.isNotEmpty() }.distinct()
    }

    // Name zum Vergleichen: wie bereinigt, dann ø als o, æ als ae, scharfes s als ss, ohne Akzente und ohne
    // Rechtsform AS/ASA, in Kleinbuchstaben als Woerter; Bindestriche und Schraegstriche trennen Woerter,
    // "&" zaehlt als eigenes Wort.
    fun worte(name: String): List<String> {
        var s = bereinigt(name).replace("ø", "o").replace("Ø", "o").replace("æ", "ae").replace("Æ", "ae")
            .replace("ß", "ss")
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(de.troetenrepublik.app.engine.Muster.re("\\p{M}+"), "")
        s = s.lowercase().replace("&", " & ").replace(Regex("\\ba[ /]?s\\b|\\basa\\b"), " ")
        return s.replace(Regex("[\\-/]"), " ").split(Regex("[\\s,]+")).map { it.trimEnd('.') }.filter { it.isNotEmpty() }
    }

    // Endet der Name nach Abtragen von Klammern, Notierungszusatz, Nennwert und Gattung (ohne Rechtsform) auf
    // "Inc"/"Inc.": solche Firmen sind fast immer US-Firmen, fuer sie gelten keine Zusatzwoerter.
    fun endetAufInc(name: String): Boolean {
        var s = name.replace(Regex("\\(.*?\\)"), " ").replace(Regex("\\s+"), " ").trim()
        while (true) {
            val t = s.replace(US_ZUSATZ, " ").replace(NENNWERT, " ").replace(GATTUNG, " ")
                .replace(Regex("\\s+"), " ").trim().trimEnd(',', ' ')
            if (t == s || t.isBlank()) break
            s = t
        }
        return Regex("(?i)\\binc\\.?$").containsMatchIn(s)
    }
}
