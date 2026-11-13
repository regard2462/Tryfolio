package de.troetenrepublik.app.engine

// Hintergrund eines Firmenlogos entfernen: Logos mit eigener einfarbiger Flaeche werden freigestellt. Reine Rechnung auf
// ARGB-Werten (Zeile fuer Zeile), ohne Oberflaeche und ohne Plattformaufrufe.
object LogoFreistellen {

    // Pixel, deren Farbe in keinem Kanal mehr als TOLERANZ von der Randfarbe abweicht, zaehlen zur Flaeche.
    const val TOLERANZ = 40

    // Mindestanteil deckender Randpixel (abgerundete Ecken duerfen durchsichtig sein) und Anteil davon, der der
    // Randfarbe gleichen muss, damit der Rand als einheitlich gilt.
    private const val RAND_DECKEND = 0.6
    private const val RAND_EINHEITLICH = 0.92

    // Hoechstanteil der Flaeche, die entfernt werden darf; sonst war das Bild fast einfarbig und bleibt, wie es ist.
    private const val MAX_ENTFERNT = 0.97

    private fun alpha(p: Int) = (p ushr 24) and 0xFF
    private fun rot(p: Int) = (p shr 16) and 0xFF
    private fun gruen(p: Int) = (p shr 8) and 0xFF
    private fun blau(p: Int) = p and 0xFF

    private fun abstand(p: Int, ref: Int): Int =
        maxOf(kotlin.math.abs(rot(p) - rot(ref)), kotlin.math.abs(gruen(p) - gruen(ref)), kotlin.math.abs(blau(p) - blau(ref)))

    // Randfarbe, wenn der Rand deckend und einheitlich ist, sonst null (Verlauf, Foto, freistehendes Zeichen).
    fun randfarbe(px: IntArray, breite: Int, hoehe: Int): Int? {
        if (breite < 4 || hoehe < 4 || px.size < breite * hoehe) return null
        val rand = ArrayList<Int>()
        for (x in 0 until breite) { rand += px[x]; rand += px[(hoehe - 1) * breite + x] }
        for (y in 1 until hoehe - 1) { rand += px[y * breite]; rand += px[y * breite + breite - 1] }
        val deckend = rand.filter { alpha(it) > 200 }
        if (deckend.size < rand.size * RAND_DECKEND) return null
        // Haeufigste Farbe als Bezug (Mittelwert wuerde bei zwei Farben dazwischen landen)
        val bezug = deckend.groupingBy { (rot(it) shr 3 shl 10) or (gruen(it) shr 3 shl 5) or (blau(it) shr 3) }.eachCount()
            .maxByOrNull { it.value }?.key ?: return null
        val kandidaten = deckend.filter { ((rot(it) shr 3 shl 10) or (gruen(it) shr 3 shl 5) or (blau(it) shr 3)) == bezug }
        val r = kandidaten.sumOf { rot(it) } / kandidaten.size
        val g = kandidaten.sumOf { gruen(it) } / kandidaten.size
        val b = kandidaten.sumOf { blau(it) } / kandidaten.size
        val ref = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val gleich = deckend.count { abstand(it, ref) <= TOLERANZ }
        if (gleich < deckend.size * RAND_EINHEITLICH) return null
        return ref
    }

    // Flaeche vom Bildrand aus transparent machen. Pixel im Zeichen, die nicht mit dem Rand verbunden sind, bleiben.
    // Pixel nahe der Toleranzgrenze werden teiltransparent. Ohne einheitlichen Rand kommt das Bild unveraendert zurueck.
    fun freistellen(px: IntArray, breite: Int, hoehe: Int, toleranz: Int = TOLERANZ): IntArray {
        val ref = randfarbe(px, breite, hoehe) ?: return px
        val n = breite * hoehe
        val flaeche = BooleanArray(n)
        val stapel = IntArray(n)
        var oben = 0
        fun gehoert(i: Int): Boolean {
            val p = px[i]
            return alpha(p) <= 200 || abstand(p, ref) <= toleranz
        }
        fun aufnehmen(i: Int) {
            if (!flaeche[i] && gehoert(i)) { flaeche[i] = true; stapel[oben++] = i }
        }
        for (x in 0 until breite) { aufnehmen(x); aufnehmen((hoehe - 1) * breite + x) }
        for (y in 0 until hoehe) { aufnehmen(y * breite); aufnehmen(y * breite + breite - 1) }
        while (oben > 0) {
            val i = stapel[--oben]
            val x = i % breite; val y = i / breite
            if (x > 0) aufnehmen(i - 1)
            if (x < breite - 1) aufnehmen(i + 1)
            if (y > 0) aufnehmen(i - breite)
            if (y < hoehe - 1) aufnehmen(i + breite)
        }
        val entfernt = flaeche.count { it }
        if (entfernt > n * MAX_ENTFERNT) return px
        val aus = px.copyOf(n)
        val halb = toleranz / 2
        for (i in 0 until n) {
            if (!flaeche[i]) continue
            val p = px[i]
            val d = if (alpha(p) <= 200) 0 else abstand(p, ref)
            val a = when {
                d <= halb -> 0
                else -> (alpha(p) * (d - halb) / (toleranz - halb).coerceAtLeast(1)).coerceIn(0, 255)
            }
            aus[i] = (a shl 24) or (p and 0xFFFFFF)
        }
        return aus
    }

    // Durchsichtigen Rand abschneiden (mit etwas Luft), damit das freigestellte Zeichen den Platz ausfuellt.
    // Gibt Pixel, Breite und Hoehe zurueck; ohne sichtbaren Inhalt das Bild unveraendert.
    fun zuschneiden(px: IntArray, breite: Int, hoehe: Int, luft: Double = 0.04): Triple<IntArray, Int, Int> {
        var x0 = breite; var y0 = hoehe; var x1 = -1; var y1 = -1
        for (y in 0 until hoehe) for (x in 0 until breite) {
            if (alpha(px[y * breite + x]) > 24) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        if (x1 < 0) return Triple(px, breite, hoehe)
        val l = (maxOf(x1 - x0, y1 - y0) * luft).toInt()
        x0 = (x0 - l).coerceAtLeast(0); y0 = (y0 - l).coerceAtLeast(0)
        x1 = (x1 + l).coerceAtMost(breite - 1); y1 = (y1 + l).coerceAtMost(hoehe - 1)
        val nb = x1 - x0 + 1; val nh = y1 - y0 + 1
        if (nb == breite && nh == hoehe) return Triple(px, breite, hoehe)
        val aus = IntArray(nb * nh)
        for (y in 0 until nh) for (x in 0 until nb) aus[y * nb + x] = px[(y0 + y) * breite + x0 + x]
        return Triple(aus, nb, nh)
    }

    // Einfaches Symbol statt Foto oder Flagge (Bilder fremder Quellen wie Prognosemaerkte): fast quadratisch, keine
    // Streifen, und hoechstens fuenf Farben decken fast alle deckenden Pixel, entweder im ganzen Bild bei durchsichtigem
    // oder einheitlichem Rand (auch farbig wie bei Kategorie-Kacheln) oder ohne einen weich auslaufenden Saum von 12 %.
    // Gemessen an Bildern von Polymarket und Kalshi: Symbole ab 0,92 (ohne Saum ab 0,95), Fotos hoechstens 0,75.
    // Flaggen im Querformat fallen ueber das Seitenverhaeltnis heraus, Streifenflaggen ueber die Streifen.
    fun einfachesSymbol(px: IntArray, breite: Int, hoehe: Int): Boolean {
        if (breite < 8 || hoehe < 8 || px.size < breite * hoehe) return false
        if (maxOf(breite, hoehe) > minOf(breite, hoehe) * 1.25) return false
        if (streifen(px, breite, hoehe)) return false
        if (flaggeMitScheibe(px, breite, hoehe)) return false
        val rand = ArrayList<Int>()
        for (x in 0 until breite) { rand += px[x]; rand += px[(hoehe - 1) * breite + x] }
        for (y in 1 until hoehe - 1) { rand += px[y * breite]; rand += px[y * breite + breite - 1] }
        val durchsichtig = rand.count { alpha(it) < 40 } >= rand.size * RAND_DECKEND
        if ((durchsichtig || randfarbe(px, breite, hoehe) != null) && farbAnteil(px.asList()) >= 0.9) return true
        val m = (minOf(breite, hoehe) * 0.12).toInt()
        val innen = ArrayList<Int>()
        for (y in m until hoehe - m) for (x in m until breite - m) innen += px[y * breite + x]
        // Ohne Saum mit acht Farben und etwas mehr Spielraum, damit auch Kacheln mit sanftem Verlauf zaehlen
        return farbAnteil(innen, 8, 32) >= 0.93
    }

    // Flache Flagge mit einfarbigem Feld und einer mittigen Scheibe (Japan): Das Feld reicht bis an den Rand, die Scheibe
    // ist rund (Rahmen quadratisch, Flaeche etwa pi/4 des Rahmens), sitzt in der Mitte und misst gut die Haelfte der Seite.
    // Symbole auf Kacheln haben kleinere Zeichen oder andere Formen.
    fun flaggeMitScheibe(px: IntArray, breite: Int, hoehe: Int): Boolean {
        val ref = randfarbe(px, breite, hoehe) ?: return false
        var x0 = breite; var y0 = hoehe; var x1 = -1; var y1 = -1; var zahl = 0
        for (y in 0 until hoehe) for (x in 0 until breite) {
            val p = px[y * breite + x]
            if (alpha(p) > 200 && abstand(p, ref) > TOLERANZ) {
                zahl++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        if (zahl == 0) return false
        val bw = (x1 - x0 + 1).toDouble(); val bh = (y1 - y0 + 1).toDouble()
        if (kotlin.math.abs(bw - bh) > 0.06 * maxOf(bw, bh)) return false
        val seite = minOf(breite, hoehe).toDouble()
        if (bw < 0.5 * seite || bw > 0.7 * seite) return false
        val mx = (x0 + x1 + 1) / 2.0; val my = (y0 + y1 + 1) / 2.0
        if (kotlin.math.abs(mx - breite / 2.0) > 0.05 * breite || kotlin.math.abs(my - hoehe / 2.0) > 0.05 * hoehe) return false
        val fuellung = zahl / (bw * bh)
        return fuellung in 0.70..0.86
    }

    // Symbol aus fremder Quelle zum Umfaerben vorbereiten. Bei weich auslaufendem Rand (weder einheitlich noch durchsichtig)
    // faellt ein Saum von 12 % weg; Grund ist dann der Rand des Inneren oder seine haeufigste Farbe. Bei durchsichtigem
    // Rand ist der Grund null.
    class SymbolTeile(val px: IntArray, val breite: Int, val hoehe: Int, val grund: Int?)

    fun symbolTeile(px: IntArray, breite: Int, hoehe: Int): SymbolTeile {
        randfarbe(px, breite, hoehe)?.let { return SymbolTeile(px, breite, hoehe, it) }
        var rand = 0; var durch = 0
        for (x in 0 until breite) { rand += 2; if (alpha(px[x]) < 40) durch++; if (alpha(px[(hoehe - 1) * breite + x]) < 40) durch++ }
        for (y in 1 until hoehe - 1) { rand += 2; if (alpha(px[y * breite]) < 40) durch++; if (alpha(px[y * breite + breite - 1]) < 40) durch++ }
        if (durch >= rand * RAND_DECKEND) return SymbolTeile(px, breite, hoehe, null)
        val m = (minOf(breite, hoehe) * 0.12).toInt()
        val ib = breite - 2 * m; val ih = hoehe - 2 * m
        if (ib < 4 || ih < 4) return SymbolTeile(px, breite, hoehe, null)
        val innen = IntArray(ib * ih) { px[(it / ib + m) * breite + it % ib + m] }
        val grund = randfarbe(innen, ib, ih) ?: run {
            val deckend = innen.filter { alpha(it) > 200 }
            if (deckend.isEmpty()) null
            else stufenFarbe(deckend.groupingBy { stufe(it) }.eachCount().maxByOrNull { it.value }!!.key)
        }
        return SymbolTeile(innen, ib, ih, grund)
    }

    // Zeichen eines zweitonigen Symbols ohne Flaeche: die deckenden Pixel werden nach Helligkeit in zwei Gruppen geteilt
    // (Otsu-Schwelle), die kleinere Gruppe ist das Zeichen (weisses Zeichen auf farbiger Kachel, auch mit Verlauf).
    // Weiche Kante ueber ein Band zwischen den Gruppen. Weiss mit Deckkraft = Tinte, wie tinteMaske.
    fun zeichenNachHelligkeit(px: IntArray): IntArray = helligkeitsGruppen(px).first

    // Maske des Zeichens und ob es die hellere Gruppe ist. Bei mehreren Farbtoenen (gelbe Flaeche, gruener Winkel, weisser
    // Kranz) gibt es kein einzelnes Zeichen: dann ist Weiss Papier und alles Farbige Tinte, wie in tintenMatrix.
    private fun helligkeitsGruppen(px: IntArray): Pair<IntArray, Boolean> {
        fun hell(p: Int) = (299 * rot(p) + 587 * gruen(p) + 114 * blau(p)) / 1000
        if (mehrereFarbtoene(px)) return IntArray(px.size) { i ->
            val p = px[i]
            val t = (8.0 * (0.97 - hell(p) / 255.0)).coerceIn(0.0, 1.0)
            ((t * alpha(p)).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        } to false
        val verteilung = IntArray(256)
        var n = 0
        for (p in px) if (alpha(p) > 200) { verteilung[hell(p)]++; n++ }
        if (n == 0) return IntArray(px.size) to false
        val summe = (0 until 256).sumOf { it.toLong() * verteilung[it] }
        var unten = 0L; var nUnten = 0; var besteZwischen = -1.0; var schwelle = 128
        for (t in 0 until 256) {
            nUnten += verteilung[t]; unten += t.toLong() * verteilung[t]
            val nOben = n - nUnten
            if (nUnten == 0 || nOben == 0) continue
            val mu = unten.toDouble() / nUnten; val mo = (summe - unten).toDouble() / nOben
            val zwischen = nUnten.toDouble() * nOben * (mu - mo) * (mu - mo)
            if (zwischen > besteZwischen) { besteZwischen = zwischen; schwelle = t }
        }
        var nU = 0; var sU = 0L; var sO = 0L
        for (t in 0 until 256) if (t <= schwelle) { nU += verteilung[t]; sU += t.toLong() * verteilung[t] } else sO += t.toLong() * verteilung[t]
        val nO = n - nU
        val mittelU = if (nU > 0) sU / nU.toDouble() else 0.0
        val mittelO = if (nO > 0) sO / nO.toDouble() else 255.0
        val zeichenOben = nO <= nU
        val band = maxOf(12.0, (mittelO - mittelU) * 0.25)
        // Kante mittig zwischen beiden Gruppen (die Otsu-Schwelle liegt bei einer Luecke am Rand der unteren)
        val mitte = (mittelU + mittelO) / 2
        return IntArray(px.size) { i ->
            val p = px[i]
            val l = hell(p).toDouble()
            val t = ((if (zeichenOben) l - mitte else mitte - l) / band + 0.5).coerceIn(0.0, 1.0)
            ((t * alpha(p)).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        } to zeichenOben
    }

    // Schwarz-weiss fuer Logos mit eigener Flaeche ohne einheitlichen Rand (Verlauf, zweifarbiger Rand): Zeichen und Grund
    // nach Helligkeitsgruppen, das Zeichen im Gegenton zum Grund. Ist das Zeichen heller, wird der Grund schwarz und das
    // Zeichen weiss, sonst umgekehrt. Deckkraft bleibt.
    fun schwarzWeissNachHelligkeit(px: IntArray): IntArray {
        val (maske, zeichenHell) = helligkeitsGruppen(px)
        return IntArray(px.size) { i ->
            val t = (maske[i] ushr 24) / 255.0
            val v = (if (zeichenHell) 255 * t else 255 * (1 - t)).toInt().coerceIn(0, 255)
            (alpha(px[i]) shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    // Mindestens zwei deutlich verschiedene Farbtoene mit je mindestens 10 % der deckenden Pixel (Farbkreis in 30-Grad-
    // Schritten, benachbarte Schritte zaehlen zusammen). Verlaeufe in einem Ton und Weiss oder Schwarz zaehlen nicht.
    fun mehrereFarbtoene(px: IntArray): Boolean {
        val stufen = IntArray(12)
        var n = 0
        for (p in px) {
            if (alpha(p) <= 200) continue
            n++
            val r = rot(p); val g = gruen(p); val b = blau(p)
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            if (mx < 40 || mx - mn < 0.25 * mx) continue
            val d = (mx - mn).toDouble()
            val ton = when (mx) {
                r -> ((g - b) / d).let { if (it < 0) it + 6 else it }
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            } * 60
            stufen[(ton / 30).toInt().coerceIn(0, 11)]++
        }
        if (n == 0) return false
        val stark = BooleanArray(12) { stufen[it] >= n * 0.1 }
        // Gruppen benachbarter starker Stufen auf dem Farbkreis zaehlen
        if (stark.all { it }) return false
        var gruppen = 0
        for (i in 0 until 12) if (stark[i] && !stark[(i + 11) % 12]) gruppen++
        return gruppen >= 2
    }

    // Anteil der deckenden Pixel nahe an einer der n haeufigsten Farben (Stufen von 16 je Kanal).
    private fun farbAnteil(px: List<Int>, n: Int = 5, nah: Int = 24): Double {
        val deckend = px.filter { alpha(it) > 200 }
        if (deckend.size < px.size * 0.04) return 0.0
        val stufen = deckend.groupingBy { stufe(it) }.eachCount()
        val palette = stufen.entries.sortedByDescending { it.value }.take(n).map { stufenFarbe(it.key) }
        return deckend.count { p -> palette.any { abstand(p, it) <= nah } }.toDouble() / deckend.size
    }

    private fun stufe(p: Int) = ((rot(p) shr 4) shl 8) or ((gruen(p) shr 4) shl 4) or (blau(p) shr 4)
    private fun stufenFarbe(k: Int) =
        (0xFF shl 24) or ((((k shr 8) and 15) * 16 + 8) shl 16) or ((((k shr 4) and 15) * 16 + 8) shl 8) or ((k and 15) * 16 + 8)

    // Streifenflagge: mindestens drei Viertel der Zeilen (oder Spalten) sind einfarbig, in mindestens zwei Farben.
    private fun streifen(px: IntArray, breite: Int, hoehe: Int): Boolean {
        fun einfarbig(linie: List<Int>): Int? {
            val deckend = linie.filter { alpha(it) > 200 }
            if (deckend.size < linie.size * 0.9) return null
            val ref = stufenFarbe(deckend.groupingBy { stufe(it) }.eachCount().maxByOrNull { it.value }!!.key)
            return if (deckend.count { abstand(it, ref) <= TOLERANZ } >= deckend.size * RAND_EINHEITLICH) ref else null
        }
        val zeilen = (0 until hoehe).map { y -> (0 until breite).map { x -> px[y * breite + x] } }
        val spalten = (0 until breite).map { x -> (0 until hoehe).map { y -> px[y * breite + x] } }
        for (linien in listOf(zeilen, spalten)) {
            val farben = linien.mapNotNull { einfarbig(it) }
            if (farben.size < linien.size * 0.75) continue
            val verschieden = ArrayList<Int>()
            for (f in farben) if (verschieden.all { abstand(f, it) > 60 }) verschieden += f
            if (verschieden.size >= 2) return true
        }
        return false
    }

    // Ist die Flaeche hell genug fuer weissen Grund (Helligkeit ab 0.6)? Sonst schwarzer Grund mit weissem Zeichen.
    fun grundHell(ref: Int): Boolean = (0.299 * rot(ref) + 0.587 * gruen(ref) + 0.114 * blau(ref)) / 255.0 >= 0.6

    // Schwarz-weiss fuer Logos mit eigener Flaeche: was der Flaechenfarbe ref aehnelt, wird Grund, alles andere Zeichen
    // im Gegenton. Der Uebergang reicht von halber Toleranz bis 60 % des groessten Abstands im Bild, damit Kanten kleiner
    // Bilder weich bleiben statt treppig. Deckkraft bleibt.
    fun schwarzWeiss(px: IntArray, ref: Int, toleranz: Int = TOLERANZ): IntArray {
        val grund = if (grundHell(ref)) 255 else 0
        val zeichen = 255 - grund
        val halb = toleranz / 2
        val groesster = px.maxOfOrNull { if (alpha(it) > 200) abstand(it, ref) else 0 } ?: 0
        val voll = maxOf(toleranz, (groesster * 0.6).toInt())
        return IntArray(px.size) { i ->
            val p = px[i]
            val d = abstand(p, ref)
            val t = when {
                d <= halb -> 0.0
                d >= voll -> 1.0
                else -> (d - halb).toDouble() / (voll - halb)
            }
            val v = (grund + (zeichen - grund) * t).toInt().coerceIn(0, 255)
            (alpha(p) shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    // Schwarz-weiss ohne Hintergrund: Maske des Zeichens (weiss, Deckkraft = Tinte), beim Zeichnen schwarz oder weiss
    // eingefaerbt. Mit Flaechenfarbe grund (Logo mit eigener Flaeche) faellt alles weg, was ihr aehnelt, auch
    // eingeschlossene Stellen wie das Innere eines o. Ohne grund wird eine freistehende Scheibe oder ein Plaettchen
    // erkannt (plattenfarbe) und ebenso entfernt. Sonst werden weisse Stellen, die ganz vom Zeichen umschlossen sind,
    // durchsichtig; weisse Teile am Aussenrand gehoeren zum Zeichen (weisses a mit orangem Bogen).
    fun tinteMaske(px: IntArray, breite: Int, hoehe: Int, grund: Int?, toleranz: Int = TOLERANZ): IntArray {
        val n = breite * hoehe
        val tinte = IntArray(n) { alpha(px[it]) }
        fun abziehen(ref: Int) {
            val halb = toleranz / 2
            for (i in 0 until n) {
                val d = abstand(px[i], ref)
                val t = when {
                    d <= halb -> 0.0
                    d >= toleranz -> 1.0
                    else -> (d - halb).toDouble() / (toleranz - halb)
                }
                tinte[i] = (tinte[i] * t).toInt()
            }
        }
        if (grund != null) abziehen(grund)
        // Schichten schaelen: solange die uebrige Form ein einfarbiges Plaettchen ist (Scheibe, Rahmen, Oval), faellt es weg
        var geschaelt = false
        for (runde in 0 until 3) {
            val jetzt = IntArray(n) { (tinte[it].coerceIn(0, 255) shl 24) or (px[it] and 0xFFFFFF) }
            val platte = platte(jetzt, breite, hoehe, toleranz) ?: break
            geschaelt = true
            if (!platte.second) {
                val vorher = tinte.copyOf()
                abziehen(platte.first)
                // Bleibt eine volle Scheibe ohne Zeichen, war das Plaettchen selbst das Zeichen (Ring um weisse Mitte)
                if (vollerKlumpen(tinte, breite, hoehe)) {
                    for (i in 0 until n) tinte[i] = if (fastWeiss(px[i])) 0 else vorher[i]
                    break
                }
                continue
            }
            // Das Zeichen ist ausgespart (weisse Wellen im gruenen Quadrat): Loecher werden Tinte, das Plaettchen faellt weg
            val aussen = aussen(jetzt, breite, hoehe)
            for (i in 0 until n) tinte[i] = if (aussen[i]) 0 else (255 - 2 * tinte[i].coerceIn(0, 255)).coerceAtLeast(0)
            break
        }
        if (grund == null && !geschaelt) {
            val jetzt = IntArray(n) { (tinte[it].coerceIn(0, 255) shl 24) or (px[it] and 0xFFFFFF) }
            val aussen = aussen(jetzt, breite, hoehe)
            val weiss = BooleanArray(n) { tinte[it] > 128 && fastWeiss(px[it]) }
            // Weisse Gebiete einzeln ablaufen; beruehrt keines seiner Pixel das Aeussere oder den Bildrand, ist es ein Loch
            val gesehen = BooleanArray(n)
            val stapel = IntArray(n)
            val gebiet = ArrayList<Int>()
            for (s in 0 until n) {
                if (!weiss[s] || gesehen[s]) continue
                gebiet.clear()
                var oben = 0; stapel[oben++] = s; gesehen[s] = true
                var offen = false
                while (oben > 0) {
                    val i = stapel[--oben]; gebiet += i
                    val x = i % breite; val y = i / breite
                    if (x == 0 || y == 0 || x == breite - 1 || y == hoehe - 1) offen = true
                    for (j in nachbarn(i, x, y, breite, hoehe)) {
                        if (j < 0) continue
                        if (aussen[j]) offen = true
                        if (weiss[j] && !gesehen[j]) { gesehen[j] = true; stapel[oben++] = j }
                    }
                }
                if (!offen) for (i in gebiet) tinte[i] = 0
            }
            // Bleibt eine volle, kompakte Scheibe ohne Loecher (mehrfarbige Muenze mit weissem Zeichen bis an den Rand),
            // ist das Weisse das Zeichen und wird ausgespart
            if (vollerKlumpen(tinte, breite, hoehe)) {
                val weissZahl = (0 until n).count { tinte[it] > 128 && fastWeiss(px[it]) }
                if (weissZahl >= (0 until n).count { tinte[it] > 128 } * 0.05) for (i in 0 until n) if (fastWeiss(px[i])) tinte[i] = 0
            }
        }
        if (geschaelt) kruemelWeg(tinte, breite, hoehe)
        return IntArray(n) { (tinte[it].coerceIn(0, 255) shl 24) or 0xFFFFFF }
    }

    // Kleine Tintenreste nach dem Abschaelen (Lichtsaum, Kantenpunkte) unter 0,4 % der Zeichenflaeche entfernen.

    // Eine Form aus einem Stueck, kompakt wie Kreis oder Quadrat, ohne nennenswerte Loecher.
    private fun vollerKlumpen(tinte: IntArray, breite: Int, hoehe: Int): Boolean {
        val n = breite * hoehe
        val jetzt = IntArray(n) { tinte[it].coerceIn(0, 255) shl 24 }
        val aussen = aussen(jetzt, breite, hoehe)
        var innen = 0; var deckend = 0; var umfang = 0
        for (i in 0 until n) {
            if (aussen[i]) continue
            innen++
            if (tinte[i] > 128) deckend++
            if (nachbarn(i, i % breite, i / breite, breite, hoehe).any { it < 0 || aussen[it] }) umfang++
        }
        if (innen == 0 || umfang == 0) return false
        return innen - deckend < innen * 0.02 && 4 * Math.PI * innen / (umfang.toDouble() * umfang) >= 0.9 &&
            teile(aussen, breite, hoehe, innen / 100) == 1
    }
    private fun kruemelWeg(tinte: IntArray, breite: Int, hoehe: Int) {
        val n = breite * hoehe
        val voll = BooleanArray(n) { tinte[it] > 64 }
        val gesamt = voll.count { it }
        if (gesamt == 0) return
        val grenze = maxOf(3, gesamt / 250)
        val gesehen = BooleanArray(n)
        val stapel = IntArray(n)
        val gebiet = ArrayList<Int>()
        for (s in 0 until n) {
            if (!voll[s] || gesehen[s]) continue
            gebiet.clear()
            var oben = 0; stapel[oben++] = s; gesehen[s] = true
            while (oben > 0) {
                val i = stapel[--oben]; gebiet += i
                for (j in nachbarn(i, i % breite, i / breite, breite, hoehe)) if (j >= 0 && voll[j] && !gesehen[j]) { gesehen[j] = true; stapel[oben++] = j }
            }
            if (gebiet.size < grenze) for (i in gebiet) {
                tinte[i] = 0
                for (j in nachbarn(i, i % breite, i / breite, breite, hoehe)) if (j >= 0 && !voll[j]) tinte[j] = 0
            }
        }
    }

    // Zahl der zusammenhaengenden Stuecke ausserhalb des Aeusseren (ohne Krumen bis mindestens Pixel).
    private fun teile(aussen: BooleanArray, breite: Int, hoehe: Int, mindestens: Int): Int {
        val n = breite * hoehe
        val gesehen = BooleanArray(n)
        val stapel = IntArray(n)
        var zahl = 0
        for (s in 0 until n) {
            if (aussen[s] || gesehen[s]) continue
            var oben = 0; stapel[oben++] = s; gesehen[s] = true
            var groesse = 0
            while (oben > 0) {
                val i = stapel[--oben]; groesse++
                for (j in nachbarn(i, i % breite, i / breite, breite, hoehe)) if (j >= 0 && !aussen[j] && !gesehen[j]) { gesehen[j] = true; stapel[oben++] = j }
            }
            if (groesse > mindestens) zahl++
        }
        return zahl
    }

    private fun nachbarn(i: Int, x: Int, y: Int, breite: Int, hoehe: Int) = intArrayOf(
        if (x > 0) i - 1 else -1, if (x < breite - 1) i + 1 else -1,
        if (y > 0) i - breite else -1, if (y < hoehe - 1) i + breite else -1,
    )

    private fun fastWeiss(p: Int): Boolean {
        val r = rot(p); val g = gruen(p); val b = blau(p)
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 >= 0.8 && (mx - mn) <= 0.25 * mx
    }

    // Durchsichtiges Aeusseres: vom Bildrand aus ueber alle kaum deckenden Pixel erreichbar.
    private fun aussen(px: IntArray, breite: Int, hoehe: Int): BooleanArray {
        val n = breite * hoehe
        val aussen = BooleanArray(n)
        val stapel = IntArray(n)
        var oben = 0
        fun auf(i: Int) { if (!aussen[i] && alpha(px[i]) <= 128) { aussen[i] = true; stapel[oben++] = i } }
        for (x in 0 until breite) { auf(x); auf((hoehe - 1) * breite + x) }
        for (y in 0 until hoehe) { auf(y * breite); auf(y * breite + breite - 1) }
        while (oben > 0) {
            val i = stapel[--oben]
            for (j in nachbarn(i, i % breite, i / breite, breite, hoehe)) if (j >= 0) auf(j)
        }
        return aussen
    }

    // Farbe einer freistehenden Scheibe oder eines Plaettchens: die Form fuellt ihren Rahmen weitgehend (ab 72 %, eine
    // Scheibe hat 79 %), ihr Aussenrand ist einfarbig, und es bleibt genug Zeichen in anderer Farbe uebrig. Sonst null
    // (die Form selbst ist das Zeichen).
    fun plattenfarbe(px: IntArray, breite: Int, hoehe: Int, toleranz: Int = TOLERANZ): Int? = platte(px, breite, hoehe, toleranz)?.first

    // Plaettchenfarbe und ob das Zeichen nur aus Loechern im Plaettchen besteht (dann umkehren statt abziehen).
    private fun platte(px: IntArray, breite: Int, hoehe: Int, toleranz: Int): Pair<Int, Boolean>? {
        val n = breite * hoehe
        if (n == 0 || px.size < n) return null
        val aussen = aussen(px, breite, hoehe)
        var x0 = breite; var y0 = hoehe; var x1 = -1; var y1 = -1; var deckend = 0
        // Aeusserster Ring ist kantengeglaettet (Mischfarbe); die Randfarbe kommt aus dem Ring direkt dahinter
        val ring1 = BooleanArray(n)
        var innen = 0
        for (i in 0 until n) {
            if (aussen[i]) continue
            innen++
            val x = i % breite; val y = i / breite
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            if (alpha(px[i]) <= 128) continue
            deckend++
            if (nachbarn(i, x, y, breite, hoehe).any { it < 0 || aussen[it] }) ring1[i] = true
        }
        val rand = ArrayList<Int>()
        for (i in 0 until n) {
            if (aussen[i] || ring1[i] || alpha(px[i]) <= 128) continue
            if (nachbarn(i, i % breite, i / breite, breite, hoehe).any { it >= 0 && ring1[it] }) rand += px[i]
        }
        if (deckend == 0 || rand.isEmpty()) return null
        val rahmen = (x1 - x0 + 1) * (y1 - y0 + 1)
        // Loecher im Plaettchen zaehlen zur Form. Ein Plaettchen ist gross, ein Stueck und kompakt (Kreis, Quadrat),
        // Schrift und Glyphen sind gezackt oder zerfallen in Teile.
        if (innen < rahmen * 0.62 || innen < n * 0.25) return null
        val umfang = ring1.count { it }
        if (4 * Math.PI * innen / (umfang.toDouble() * umfang) < 0.7) return null
        if (teile(aussen, breite, hoehe, innen / 100) != 1) return null
        val bezug = rand.groupingBy { (rot(it) shr 3 shl 10) or (gruen(it) shr 3 shl 5) or (blau(it) shr 3) }.eachCount()
            .maxByOrNull { it.value }?.key ?: return null
        val kandidaten = rand.filter { ((rot(it) shr 3 shl 10) or (gruen(it) shr 3 shl 5) or (blau(it) shr 3)) == bezug }
        val ref = (0xFF shl 24) or ((kandidaten.sumOf { rot(it) } / kandidaten.size) shl 16) or
            ((kandidaten.sumOf { gruen(it) } / kandidaten.size) shl 8) or (kandidaten.sumOf { blau(it) } / kandidaten.size)
        if (rand.count { abstand(it, ref) <= toleranz } < rand.size * 0.8) return null
        var anders = 0; var loecher = 0
        for (i in 0 until n) {
            if (aussen[i]) continue
            if (alpha(px[i]) <= 128) loecher++ else if (abstand(px[i], ref) > toleranz) anders++
        }
        // Mehr Loecher als Andersfarbiges: das Zeichen ist ausgespart (der Saum um die Loecher zaehlt sonst als anders)
        if (loecher >= (deckend + loecher) * 0.02 && loecher >= anders) {
            // Ein einziges rundes Loch ist kein ausgespartes Zeichen, sondern die Mitte eines Rings (Ring ist das Zeichen)
            val loch = IntArray(n) { if (!aussen[it] && alpha(px[it]) <= 128) 255 else 0 }
            if (vollerKlumpen(loch, breite, hoehe)) return null
            return ref to true
        }
        if (anders >= deckend * 0.04) return ref to false
        return null
    }

    // Auf hoechstens n mal n verkleinert (Mittel je Block, Seitenverhaeltnis bleibt), fuer die Analyse und kleine Anzeigen.
    fun verkleinern(px: IntArray, breite: Int, hoehe: Int, n: Int): Triple<IntArray, Int, Int> {
        if (breite <= n && hoehe <= n) return Triple(px, breite, hoehe)
        val f = maxOf(breite, hoehe).toDouble() / n
        val nb = (breite / f).toInt().coerceAtLeast(1)
        val nh = (hoehe / f).toInt().coerceAtLeast(1)
        val aus = IntArray(nb * nh)
        for (y in 0 until nh) for (x in 0 until nb) {
            val x0 = (x * f).toInt(); val x1 = ((x + 1) * f).toInt().coerceIn(x0 + 1, breite)
            val y0 = (y * f).toInt(); val y1 = ((y + 1) * f).toInt().coerceIn(y0 + 1, hoehe)
            var a = 0; var r = 0; var g = 0; var b = 0; var c = 0
            for (yy in y0 until y1) for (xx in x0 until x1) {
                val p = px[yy * breite + xx]
                val pa = alpha(p)
                // nach Deckkraft gewichtet, damit durchsichtige Pixel die Farbe nicht verfaelschen
                a += pa; r += rot(p) * pa; g += gruen(p) * pa; b += blau(p) * pa; c++
            }
            aus[y * nb + x] = if (a == 0) 0 else ((a / c) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (b / a)
        }
        return Triple(aus, nb, nh)
    }
}
