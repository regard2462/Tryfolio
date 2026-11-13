package de.troetenrepublik.app.engine

// [0.78] Rollstand einer Seite oder eines Blatts, das nach einer daraus geoeffneten Seite wieder aufgebaut wird. Der Inhalt
// waechst beim Wiederaufbau erst nach (Abschnitte laden); ohne Stuetze kuerzt die Rollflaeche den gemerkten Stand auf die
// vorlaeufige Hoehe, und die Seite springt. Solange der Stand gehalten wird, bekommt der Inhalt deshalb eine Mindesthoehe.
object Rollstand {
    // So lange stuetzt die Mindesthoehe den gemerkten Stand, danach gilt die echte Hoehe.
    const val STUETZE_MS = 1_200L

    // So lange wird der Stand noch einmal gesetzt, wenn der Inhalt erst spaeter lang genug ist.
    const val NACHHOLEN_MS = 8_000L

    // Hoehe, mit der der Inhalt gelegt wird: die echte, solange gehalten wird aber mindestens ziel plus Sichtfenster,
    // damit der Stand ziel erreichbar bleibt. sicht: Hoehe des Sichtfensters, ist sie noch unbekannt (0), ersatz.
    fun hoehe(inhalt: Int, ziel: Int, sicht: Int, ersatz: Int, halten: Boolean): Int {
        if (!halten || ziel <= 0) return inhalt
        return maxOf(inhalt, ziel + (if (sicht > 0) sicht else ersatz))
    }

    // Der echte Inhalt traegt den Stand: Die Stuetze kann weg, ohne dass sich etwas bewegt.
    fun traegt(inhalt: Int, ziel: Int, sicht: Int): Boolean = ziel <= 0 || (sicht > 0 && inhalt >= ziel + sicht)

    // Die Stuetze endet, sobald der Inhalt traegt oder die Person selbst rollt.
    fun stuetzeEnde(inhalt: Int, ziel: Int, sicht: Int, rolltSelbst: Boolean): Boolean = rolltSelbst || traegt(inhalt, ziel, sicht)

    // Nach der Stuetze: Stand noch einmal setzen, wenn der Inhalt inzwischen traegt, die Person nicht gerollt hat und die
    // Seite nicht schon dort steht.
    fun nachholen(inhalt: Int, ziel: Int, sicht: Int, stand: Int, gerollt: Boolean): Boolean =
        ziel > 0 && !gerollt && stand < ziel && traegt(inhalt, ziel, sicht)

    // Liste mit einzelnen Eintraegen: Der gemerkte Eintrag ist erreichbar, sobald die Liste ihn (und einen weiteren) hat.
    fun eintragErreichbar(index: Int, anzahl: Int): Boolean = index >= 0 && anzahl > index + 1

    // Stand einer Liste noch einmal setzen: Eintrag erreichbar, nicht selbst gerollt, und die Liste steht woanders.
    fun listeNachholen(zielIndex: Int, zielVersatz: Int, index: Int, versatz: Int, anzahl: Int, gerollt: Boolean): Boolean =
        (zielIndex > 0 || zielVersatz > 0) && !gerollt && eintragErreichbar(zielIndex, anzahl) && (index != zielIndex || versatz != zielVersatz)
}
