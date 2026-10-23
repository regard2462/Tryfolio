package de.troetenrepublik.app.plattform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.UriHandler

// Oeffnet nur Webadressen im Browser. Andere Schemata wie intent: oder file: aus Feeds und fremden Seiten
// werden still ignoriert.
class WebLinks(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        val u = runCatching { Uri.parse(uri.trim()) }.getOrNull() ?: return
        val schema = u.scheme?.lowercase()
        if (schema != "https" && schema != "http") return
        context.startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
