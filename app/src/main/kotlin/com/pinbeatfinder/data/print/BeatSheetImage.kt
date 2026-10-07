package com.pinbeatfinder.data.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.pinbeatfinder.domain.model.BeatRecord
import java.io.OutputStream

/**
 * Renders a beat list as a single PNG for chat groups where a PDF is awkward to open: a title
 * block, one row per village with its PIN, and a footer. Width is fixed; height grows with the
 * list (capped so a huge beat still produces a usable image).
 */
object BeatSheetImage {
    const val MIME_TYPE = "image/png"

    private const val WIDTH = 1080
    private const val MARGIN = 48f
    private const val ROW_H = 64f
    private const val MAX_ROWS = 400

    fun write(title: String, subtitle: String, records: List<BeatRecord>, footer: String, out: OutputStream, columnBeat: String, columnPin: String) {
        val rows = records.take(MAX_ROWS)
        val headerH = 190f
        val footerH = 70f
        val height = (headerH + rows.size * ROW_H + footerH + MARGIN).toInt()
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = Color.DKGRAY }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; color = Color.BLACK }
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = Color.DKGRAY }
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF7A5600.toInt() }
        val linePaint = Paint().apply { color = 0xFFE0E0E0.toInt(); strokeWidth = 2f }
        val bandPaint = Paint().apply { color = 0xFFB3261E.toInt() }

        c.drawRect(0f, 0f, WIDTH.toFloat(), 16f, bandPaint)
        var y = MARGIN + 60f
        c.drawText(ellipsize(title, titlePaint, WIDTH - 2 * MARGIN), MARGIN, y, titlePaint)
        y += 44f
        c.drawText(ellipsize(subtitle, subPaint, WIDTH - 2 * MARGIN), MARGIN, y, subPaint)
        y += 30f
        c.drawText("$columnBeat • $columnPin", MARGIN, y, subPaint)
        y = headerH

        val pinX = WIDTH - MARGIN
        rows.forEachIndexed { i, r ->
            val baseline = y + ROW_H * 0.65f
            c.drawText("${i + 1}.", MARGIN, baseline, numPaint)
            val pinWidth = pinPaint.measureText(r.pincode)
            c.drawText(r.pincode, pinX - pinWidth, baseline, pinPaint)
            val nameMax = (pinX - pinWidth - 24f) - (MARGIN + 70f)
            c.drawText(ellipsize(r.localityName, namePaint, nameMax), MARGIN + 70f, baseline, namePaint)
            y += ROW_H
            c.drawLine(MARGIN, y, WIDTH - MARGIN, y, linePaint)
        }
        if (records.size > rows.size) {
            c.drawText("+${records.size - rows.size}", MARGIN, y + 40f, subPaint)
        }
        c.drawText(ellipsize(footer, subPaint, WIDTH - 2 * MARGIN), MARGIN, height - MARGIN, subPaint)

        out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
