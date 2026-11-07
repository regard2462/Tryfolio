package okhttp3

/** Ersatz fuer okhttp3.HttpUrl: nur http und https, Host in Kleinbuchstaben, Pfad und Abfrage wie angegeben (kodiert). */
class HttpUrl private constructor(
    val scheme: String,
    val encodedUsername: String,
    val encodedPassword: String,
    val host: String,
    val port: Int,
    val encodedPath: String,
    val encodedQuery: String?,
    val encodedFragment: String?,
) {
    val isHttps: Boolean get() = scheme == "https"
    val username: String get() = java.net.URLDecoder.decode(encodedUsername, "UTF-8")
    val pathSegments: List<String> get() = encodedPath.removePrefix("/").split('/').map { java.net.URLDecoder.decode(it, "UTF-8") }
    val query: String? get() = encodedQuery?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    fun queryParameter(name: String): String? = encodedQuery?.split('&')?.map { it.substringBefore('=') to it.substringAfter('=', "") }
        ?.firstOrNull { java.net.URLDecoder.decode(it.first, "UTF-8") == name }?.second?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    /** Registrierbare Domain (eine Stufe unter der oeffentlichen Endung), etwa yahoo.com fuer finance.yahoo.com. */
    fun topPrivateDomain(): String? {
        if (host.all { it.isDigit() || it == '.' } || ':' in host) return null
        val teile = host.split('.')
        if (teile.size < 2) return null
        val zwei = teile.takeLast(2).joinToString(".")
        val n = if (zwei in ZWEISTUFIG && teile.size >= 3) 3 else if (zwei in ZWEISTUFIG) return null else 2
        return teile.takeLast(n).joinToString(".")
    }

    fun newBuilder(): Builder = Builder().also {
        it.schema = scheme; it.nutzer = encodedUsername; it.passwort = encodedPassword; it.rechner = host; it.tor = port
        it.pfad = encodedPath; it.abfrage = encodedQuery; it.anker = encodedFragment
    }

    override fun toString(): String = buildString {
        append(scheme).append("://")
        if (encodedUsername.isNotEmpty() || encodedPassword.isNotEmpty()) {
            append(encodedUsername)
            if (encodedPassword.isNotEmpty()) append(':').append(encodedPassword)
            append('@')
        }
        append(if (':' in host) "[$host]" else host)
        if (port != standardTor(scheme)) append(':').append(port)
        append(encodedPath)
        if (encodedQuery != null) append('?').append(encodedQuery)
        if (encodedFragment != null) append('#').append(encodedFragment)
    }

    override fun equals(other: Any?): Boolean = other is HttpUrl && other.toString() == toString()
    override fun hashCode(): Int = toString().hashCode()

    class Builder {
        internal var schema = "https"
        internal var nutzer = ""
        internal var passwort = ""
        internal var rechner: String? = null
        internal var tor = -1
        internal var pfad = "/"
        internal var abfrage: String? = null
        internal var anker: String? = null

        fun scheme(scheme: String) = apply { schema = scheme.lowercase() }
        fun host(host: String) = apply { rechner = host.lowercase() }
        fun port(port: Int) = apply { tor = port }
        fun encodedPath(encodedPath: String) = apply {
            require(encodedPath.startsWith("/")) { "unexpected encodedPath: $encodedPath" }
            pfad = encodedPath
        }
        fun encodedQuery(encodedQuery: String?) = apply { abfrage = encodedQuery }
        fun query(query: String?) = apply { abfrage = query?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("%3D", "=").replace("%26", "&") } }
        fun addQueryParameter(name: String, value: String?) = apply {
            val teil = java.net.URLEncoder.encode(name, "UTF-8") + (value?.let { "=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
            abfrage = if (abfrage.isNullOrEmpty()) teil else "$abfrage&$teil"
        }
        fun fragment(fragment: String?) = apply { anker = fragment }

        fun build(): HttpUrl {
            val h = rechner ?: throw IllegalStateException("host == null")
            return HttpUrl(schema, nutzer, passwort, h, if (tor > 0) tor else standardTor(schema), pfad, abfrage, anker)
        }
    }

    companion object {
        // Oeffentliche Endungen mit zwei Stufen, die bei den Quellen der App vorkommen
        private val ZWEISTUFIG = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "ne.jp", "or.jp", "co.nz", "com.br",
            "com.cn", "com.hk", "co.in", "co.kr", "com.sg", "com.tw", "co.za", "com.mx", "com.tr", "gv.at", "co.at", "or.at",
        )

        fun defaultPort(scheme: String): Int = standardTor(scheme)

        private fun standardTor(scheme: String): Int = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }

        fun String.toHttpUrl(): HttpUrl = toHttpUrlOrNull() ?: throw IllegalArgumentException("Expected URL scheme 'http' or 'https' but was '$this'")

        fun String.toHttpUrlOrNull(): HttpUrl? {
            val s = trim()
            val trenner = s.indexOf("://")
            if (trenner <= 0) return null
            val schema = s.substring(0, trenner).lowercase()
            if (schema != "http" && schema != "https") return null
            var rest = s.substring(trenner + 3)
            var anker: String? = null
            rest.indexOf('#').takeIf { it >= 0 }?.let { anker = rest.substring(it + 1); rest = rest.substring(0, it) }
            var abfrage: String? = null
            val ende = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
            var autoritaet = rest.substring(0, ende)
            var weg = rest.substring(ende)
            weg.indexOf('?').takeIf { it >= 0 }?.let { abfrage = weg.substring(it + 1); weg = weg.substring(0, it) }
            if (weg.isEmpty()) weg = "/"
            var nutzer = ""
            var passwort = ""
            autoritaet.lastIndexOf('@').takeIf { it >= 0 }?.let {
                val info = autoritaet.substring(0, it)
                nutzer = info.substringBefore(':')
                passwort = info.substringAfter(':', "")
                autoritaet = autoritaet.substring(it + 1)
            }
            val host: String
            var tor = standardTor(schema)
            if (autoritaet.startsWith("[")) {
                val zu = autoritaet.indexOf(']')
                if (zu < 0) return null
                host = autoritaet.substring(1, zu)
                autoritaet.substring(zu + 1).removePrefix(":").takeIf { it.isNotEmpty() }?.let { tor = it.toIntOrNull() ?: return null }
            } else {
                host = autoritaet.substringBefore(':')
                autoritaet.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { tor = it.toIntOrNull() ?: return null }
            }
            if (host.isEmpty() || tor !in 1..65535) return null
            if (host.any { it.isWhitespace() || it == '%' || it == '/' || it == '\\' }) return null
            // Leerzeichen und andere unerlaubte Zeichen in Pfad und Abfrage kodieren wie OkHttp
            return HttpUrl(schema, nutzer, passwort, host.lowercase(), tor, kodieren(weg), abfrage?.let { kodieren(it) }, anker)
        }

        fun parse(url: String): HttpUrl? = url.toHttpUrlOrNull()
        fun get(url: String): HttpUrl = url.toHttpUrl()

        private fun kodieren(s: String): String {
            if (s.all { it.code in 0x21..0x7E && it != '"' && it != '<' && it != '>' && it != '`' && it != '{' && it != '}' && it != '|' && it != '^' }) return s
            val sb = StringBuilder()
            for (ch in s) {
                if (ch.code in 0x21..0x7E && ch != '"' && ch != '<' && ch != '>' && ch != '`' && ch != '{' && ch != '}' && ch != '|' && ch != '^') sb.append(ch)
                else for (b in ch.toString().encodeToByteArray()) {
                    val c = b.toInt() and 0xFF
                    sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
                }
            }
            return sb.toString()
        }
    }
}

/** Ersatz fuer okhttp3.Cookie mit Lesen aus Set-Cookie und dem Abgleich zu einer Adresse. */
class Cookie private constructor(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val persistent: Boolean,
    val hostOnly: Boolean,
) {
    fun matches(url: HttpUrl): Boolean {
        val domainPasst = if (hostOnly) url.host == domain else url.host == domain || url.host.endsWith(".$domain")
        if (!domainPasst) return false
        val p = url.encodedPath
        val pfadPasst = p == path || (p.startsWith(path) && (path.endsWith("/") || p[path.length] == '/'))
        if (!pfadPasst) return false
        return !secure || url.isHttps
    }

    override fun toString(): String = "$name=$value; path=$path; domain=$domain"

    companion object {
        private const val MAX_DATUM = 253402300799999L

        fun parse(url: HttpUrl, setCookie: String): Cookie? {
            val teile = setCookie.split(';')
            val paar = teile.first()
            val gleich = paar.indexOf('=')
            if (gleich <= 0) return null
            val name = paar.substring(0, gleich).trim()
            val wert = paar.substring(gleich + 1).trim().removeSurrounding("\"")
            if (name.isEmpty()) return null
            var ablauf = MAX_DATUM
            var maxAlter: Long? = null
            var domain: String? = null
            var pfad: String? = null
            var sicher = false
            var nurHttp = false
            var dauerhaft = false
            for (t in teile.drop(1)) {
                val k = t.substringBefore('=').trim().lowercase()
                val v = t.substringAfter('=', "").trim()
                when (k) {
                    "expires" -> datum(v)?.let { ablauf = it; dauerhaft = true }
                    "max-age" -> v.toLongOrNull()?.let { maxAlter = it; dauerhaft = true }
                    "domain" -> domain = v.removePrefix(".").lowercase().takeIf { it.isNotEmpty() }
                    "path" -> pfad = v.takeIf { it.startsWith("/") }
                    "secure" -> sicher = true
                    "httponly" -> nurHttp = true
                }
            }
            maxAlter?.let { s ->
                val jetzt = de.troetenrepublik.app.ios.jetztMs()
                ablauf = if (s <= 0) Long.MIN_VALUE else if (s > (MAX_DATUM - jetzt) / 1000) MAX_DATUM else jetzt + s * 1000
            }
            val d = domain
            if (d != null && url.host != d && !url.host.endsWith(".$d")) return null
            val standardPfad = url.encodedPath.substringBeforeLast('/').ifEmpty { "/" }
            return Cookie(name, wert, ablauf, d ?: url.host, pfad ?: standardPfad, sicher, nurHttp, dauerhaft, d == null)
        }

        fun parseAll(url: HttpUrl, headers: Headers): List<Cookie> = headers.values("Set-Cookie").mapNotNull { parse(url, it) }

        private val MONATE = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        // Datumsangaben in Set-Cookie: "Wed, 21 Oct 2026 07:28:00 GMT" und Varianten mit Bindestrichen; immer UTC
        private fun datum(s: String): Long? {
            var tag = -1; var monat = -1; var jahr = -1; var h = -1; var m = 0; var sek = 0
            for (t in s.split(' ', '-', ',').filter { it.isNotBlank() }) {
                when {
                    ':' in t && h < 0 -> t.split(':').let { z -> h = z[0].toIntOrNull() ?: return null; m = z.getOrNull(1)?.toIntOrNull() ?: 0; sek = z.getOrNull(2)?.toIntOrNull() ?: 0 }
                    t.length >= 3 && MONATE.indexOf(t.take(3).lowercase()) >= 0 && monat < 0 -> monat = MONATE.indexOf(t.take(3).lowercase()) + 1
                    t.all { it.isDigit() } && t.length <= 2 && tag < 0 -> tag = t.toInt()
                    t.all { it.isDigit() } && (t.length == 4 || t.length == 2) && jahr < 0 -> jahr = t.toInt().let { if (it < 70) it + 2000 else if (it < 100) it + 1900 else it }
                }
            }
            if (tag < 1 || monat < 1 || jahr < 1601 || h < 0) return null
            return runCatching {
                java.time.LocalDateTime.of(jahr, monat, tag, h, m, sek).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
        }
    }
}
