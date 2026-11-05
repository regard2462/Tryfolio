package de.troetenrepublik.app.plattform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

// PNG- oder JPEG-Daten als Bild; null, wenn sie sich nicht lesen lassen.
fun pngBild(daten: ByteArray): ImageBitmap? = runCatching { BitmapFactory.decodeByteArray(daten, 0, daten.size)?.asImageBitmap() }.getOrNull()
