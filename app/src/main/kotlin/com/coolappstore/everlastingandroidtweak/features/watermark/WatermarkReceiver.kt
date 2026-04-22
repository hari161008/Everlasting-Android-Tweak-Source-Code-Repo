package com.coolappstore.everlastingandroidtweak.features.watermark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

class WatermarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val path = uri.path ?: return
        if (!path.endsWith(".jpg", true) && !path.endsWith(".jpeg", true) &&
            !path.endsWith(".png", true)) return

        CoroutineScope(Dispatchers.IO).launch {
            val enabled = AppPreferences.get(AppPreferences.WATERMARK_ENABLED, false).first()
            if (!enabled) return@launch
            val text = AppPreferences.get(AppPreferences.WATERMARK_TEXT, "© My Photo").first()
            val position = AppPreferences.get(AppPreferences.WATERMARK_POSITION, "Bottom Right").first()
            val fontSize = AppPreferences.get(AppPreferences.WATERMARK_FONT_SIZE, 3.5f).first()
            val colorHex = AppPreferences.get(AppPreferences.WATERMARK_COLOR, "#FFFFFF").first()
            val opacity = AppPreferences.get(AppPreferences.WATERMARK_OPACITY, 0.78f).first()
            val bold = AppPreferences.get(AppPreferences.WATERMARK_BOLD, false).first()
            val shadow = AppPreferences.get(AppPreferences.WATERMARK_SHADOW, true).first()
            applyWatermark(path, text, position, fontSize, colorHex, opacity, bold, shadow)
        }
    }

    private fun applyWatermark(
        path: String, text: String, position: String,
        fontSizePercent: Float, colorHex: String, opacity: Float, bold: Boolean, shadow: Boolean
    ) {
        try {
            val file = File(path)
            if (!file.exists()) return
            val original = BitmapFactory.decodeFile(path) ?: return
            val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)
            val baseColor = try { Color.parseColor(colorHex) } catch (e: Exception) { Color.WHITE }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = baseColor
                alpha = (opacity * 255).toInt().coerceIn(0, 255)
                textSize = mutable.width * (fontSizePercent / 100f)
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                if (shadow) setShadowLayer(8f, 3f, 3f, Color.BLACK)
            }
            val textWidth = paint.measureText(text)
            val margin = 48f
            val (x, y) = when (position) {
                "Bottom Left"  -> Pair(margin, mutable.height - margin)
                "Top Right"    -> Pair(mutable.width - textWidth - margin, paint.textSize + margin)
                "Top Left"     -> Pair(margin, paint.textSize + margin)
                "Center"       -> Pair((mutable.width - textWidth) / 2f, mutable.height / 2f)
                "Top Center"   -> Pair((mutable.width - textWidth) / 2f, paint.textSize + margin)
                "Bottom Center"-> Pair((mutable.width - textWidth) / 2f, mutable.height - margin)
                else           -> Pair(mutable.width - textWidth - margin, mutable.height - margin)
            }
            canvas.drawText(text, x, y, paint)
            file.outputStream().use { mutable.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            original.recycle(); mutable.recycle()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
