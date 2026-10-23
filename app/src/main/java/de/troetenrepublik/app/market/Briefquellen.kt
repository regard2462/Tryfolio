package de.troetenrepublik.app.market

// Briefe von Investoren und Unternehmen. Die Herausgeber haben keinen Feed, nur eine Uebersichtsseite. Je Quelle
// beschreibt eine Regel, wie dort die Links der Briefe aussehen; die neuesten werden zu Schlagzeilen. Gelesen wird
// der Brief selbst (HTML oder PDF) mit dem Leser der Nachrichten.
// Nur Herausgeber, deren Seiten ohne Anmeldung und ohne Zustimmungsseite erreichbar sind. Pershing Square und
// Bridgewater verlangen eine Zustimmung zu ihren Bedingungen und fehlen deshalb.
object Briefquellen {

    // muster: regulaerer Ausdruck ueber die Seite; link, jahr, titel, datum sind die Nummern seiner Gruppen
    // (0: gibt es nicht). titelDe/titelEn: Vorlage mit {jahr} und {titel}. Mit Jahr gewinnen die neuesten, sonst
    // die ersten auf der Seite. ohne: Links mit diesem Teil zaehlen nicht (etwa die chinesische Fassung).
    data class Regel(
        val muster: String,
        val link: Int,
        val basis: String,
        val titelDe: String,
        val titelEn: String,
        val jahr: Int = 0,
        val titel: Int = 0,
        val datum: Int = 0,
        val anzahl: Int = 1,
        val ohne: String? = null,
    )

    // Text einer Adresse ohne Prozentzeichen-Kodierung (Dateinamen der PDFs).
    fun dekodieren(s: String): String {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && s.substring(i + 1, i + 3).toIntOrNull(16) != null) {
                out += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
            } else {
                c.toString().encodeToByteArray().forEach { out += it }; i++
            }
        }
        return out.toByteArray().decodeToString()
    }

    // Titel aus einem Adressteil: „a-catalyst-for-the-ai-bubble-break“ wird zu „A Catalyst for the AI Bubble Break“.
    fun titelAusTeil(teil: String): String {
        val klein = setOf("a", "an", "the", "of", "for", "to", "in", "on", "at", "and", "or", "by", "is")
        val w = dekodieren(teil).replace(Regex("[-_]+"), " ").replace(Regex("\\(?\\d{8}\\)?"), " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return w.mapIndexed { i, x ->
            when {
                x.length == 2 && x.lowercase() !in klein && x.all { it.isLetter() } -> x.uppercase()
                i > 0 && x.lowercase() in klein -> x.lowercase()
                else -> x.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }

    private fun absolut(link: String, basis: String): String = when {
        link.startsWith("http") -> link
        link.startsWith("/") -> Regex("^https?://[^/]+").find(basis)?.value.orEmpty() + link
        else -> basis.trimEnd('/') + "/" + link
    }

    private fun datum(text: String): Long =
        runCatching { java.time.LocalDate.parse(text.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() + 12 * 3_600_000L }.getOrDefault(0L)

    // Schlagzeilen aus der Uebersichtsseite html nach der Regel der Quelle.
    fun lesen(html: String, q: Rss.Quelle, deutsch: Boolean): List<Rss.Eintrag> {
        val r = q.regel ?: return emptyList()
        val treffer = Regex(r.muster, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).mapNotNull { m ->
            val link = Sonderleser.entitaeten(m.groupValues[r.link]).replace("&amp;", "&")
            if (r.ohne != null && link.contains(r.ohne, true)) return@mapNotNull null
            val jahr = if (r.jahr > 0) m.groupValues[r.jahr].toIntOrNull() else null
            val titel = if (r.titel > 0) Rss.klartext(Sonderleser.entitaeten(m.groupValues[r.titel])).replace('�', '–').trim() else ""
            val zeit = if (r.datum > 0) datum(m.groupValues[r.datum]) else 0L
            Triple(absolut(link, r.basis), jahr, titel to zeit)
        }.distinctBy { it.first }.toList()
        val geordnet = if (treffer.any { it.second != null }) treffer.sortedByDescending { it.second ?: 0 } else treffer
        return geordnet.take(r.anzahl).map { (link, jahr, tz) ->
            val vorlage = if (deutsch) r.titelDe else r.titelEn
            val titel = vorlage.replace("{jahr}", jahr?.toString().orEmpty()).replace("{titel}", tz.first.ifBlank { titelAusTeil(link.trimEnd('/').substringAfterLast('/').substringBeforeLast('.').substringBefore("_gmo")) })
            Rss.Eintrag(
                titel = titel.trim(), link = link, zeit = tz.second, herausgeber = q.name, quelle = q.name,
                kategorien = setOf(q.kategorie), quellen = setOf(q.schluessel),
            )
        }
    }
}
