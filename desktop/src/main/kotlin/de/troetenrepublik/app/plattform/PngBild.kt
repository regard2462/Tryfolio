package de.troetenrepublik.app.plattform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

// PNG- oder JPEG-Daten als Bild; null, wenn sie sich nicht lesen lassen.
fun pngBild(daten: ByteArray): ImageBitmap? = runCatching { org.jetbrains.skia.Image.makeFromEncoded(daten).toComposeImageBitmap() }.getOrNull()
