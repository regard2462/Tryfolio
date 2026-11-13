package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

// Sammelt die Quellen der Karten einer Seite (0.73), damit am Seitenende ein einziger Anhang steht, der nur nennt, was
// die Seite wirklich zeigt. Karten melden ihre Quelle mit QuellenMelden, sobald sie Daten der Quelle darstellen.
class SeitenQuellen {
    val liste = mutableStateListOf<QuelleLink>()

    fun melden(q: QuelleLink) {
        if (liste.none { it.name == q.name }) liste.add(q)
    }
}

val LocalSeitenQuellen = staticCompositionLocalOf<SeitenQuellen?> { null }

// Meldet Quellen an den Sammler der Seite; ohne Sammler geschieht nichts.
@Composable
fun QuellenMelden(vararg quellen: QuelleLink) {
    val sammler = LocalSeitenQuellen.current ?: return
    LaunchedEffect(quellen.map { it.name }) { quellen.forEach { sammler.melden(it) } }
}

// Stellt einen Sammler bereit und zeigt den Anhang nach dem Inhalt; fuer Blaetter und Seiten ohne eigene Liste.
@Composable
fun MitQuellenAnhang(modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    val sammler = remember { SeitenQuellen() }
    CompositionLocalProvider(LocalSeitenQuellen provides sammler) {
        inhalt()
        QuellenAnhang(sammler.liste.toList(), modifier)
    }
}

// Quellen, die an mehreren Stellen vorkommen und deren Bedingungen eine Nennung verlangen.
object NennQuellen {
    val BAFIN = QuelleLink("© Bundesanstalt für Finanzdienstleistungsaufsicht (BaFin)", "https://www.bafin.de")
    val AMF = QuelleLink("© Autorité des marchés financiers (Licence Ouverte 2.0)", "https://www.amf-france.org")
    val FCA = QuelleLink("Financial Conduct Authority (Open Government Licence v3.0)", "https://www.fca.org.uk")
    val FINANSINSPEKTIONEN = QuelleLink("Finansinspektionen", "https://www.fi.se")
    val OSHA = QuelleLink("U.S. Department of Labor, OSHA Enforcement Data", "https://enforcedata.dol.gov")
    val WARN = QuelleLink("WARN Feed (CC BY 4.0)", "https://github.com/APVentureEngine/warn-act-notices")
    val WIKIMEDIA_COMMONS = QuelleLink("Wikimedia Commons", "https://commons.wikimedia.org")
    val GIE = QuelleLink("GIE AGSI+", "https://agsi.gie.eu")
    val DROUGHT = QuelleLink("U.S. Drought Monitor", "https://droughtmonitor.unl.edu")
    val CLINICALTRIALS = QuelleLink("ClinicalTrials.gov (U.S. National Library of Medicine)", "https://clinicaltrials.gov")

    // Aufsicht eines Landes, soweit ihre Bedingungen eine Nennung verlangen oder unklar sind.
    fun aufsicht(land: String): QuelleLink? = when (land) {
        "FR" -> AMF
        "GB" -> FCA
        "SE" -> FINANSINSPEKTIONEN
        "DE" -> BAFIN
        else -> null
    }
}
