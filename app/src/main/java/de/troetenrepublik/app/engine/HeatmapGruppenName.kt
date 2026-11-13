package de.troetenrepublik.app.engine

// Namensform einer Gruppe (Branche oder Region) der Heatmap: volle Bezeichnung, kurze Form, zweizeilig; passt keine,
// steht der Name erst, wenn hineingezoomt ist.
object HeatmapGruppenName {
    enum class Form { VOLL, KURZ, ZWEIZEILIG }

    // Ein gesetzter Text in Pixeln; inhalt ist das Layout der Oberflaeche.
    class Kandidat<T>(val form: Form, val schrift: Float, val breite: Float, val hoehe: Float, val zeilen: Int, val inhalt: T)

    // Der erste Kandidat der Folge (bevorzugte zuerst), der in eine Gruppe von gruppeB x gruppeH passt, sonst null.
    // rand ist der Rand je Achse zusammen, kopf die Hoehe einer Kopfzeile: einzeilig braucht die Gruppe drei, zweizeilig
    // fuenf Kopfzeilen Hoehe, damit unter dem Namen noch Kacheln bleiben; schmaler als zwei Kopfzeilen bekommt keinen Namen.
    fun <T> waehlen(kandidaten: Sequence<Kandidat<T>>, gruppeB: Float, gruppeH: Float, rand: Float, kopf: Float): Kandidat<T>? {
        val platz = gruppeB - rand
        if (gruppeB < kopf * 2 || platz <= 0f) return null
        return kandidaten.firstOrNull { k ->
            k.breite <= platz && gruppeH >= kopf * (if (k.zeilen <= 1) 3 else 5)
        }
    }

    // Die kurze Form nur, wenn sie sich vom vollen Namen unterscheidet.
    fun kurzNoetig(voll: String, kurz: String): Boolean = kurz.isNotBlank() && kurz != voll
}
