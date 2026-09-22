package com.pinbeatfinder.data.print

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.pinbeatfinder.domain.model.BeatRecord
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a beat list as a printable A4 PDF with the platform `PdfDocument` (no library): a
 * title block, then a table of Sl. No. / Locality / Beat / PIN / Remarks that continues over as
 * many pages as needed. Page numbers and the print date sit in the footer.
 */
object BeatSheetPdf {
    const val MIME_TYPE = "application/pdf"

    // A4 at 72 dpi.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val ROW_H = 20f
    private const val HEADER_H = 24f

    // Column x-offsets (points) and widths: Sl | Locality | Beat | PIN | Remarks
    private val COL_X = floatArrayOf(0f, 36f, 236f, 296f, 356f)
    private val COL_W = floatArrayOf(36f, 200f, 60f, 60f, PAGE_W - 2 * MARGIN - 356f)

    /** Writes [records] under [title] / [subtitle] to [out] and closes it. Returns the page count. */
    fun write(title: String, subtitle: String, records: List<BeatRecord>, out: OutputStream, now: Date = Date()): Int {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        val fillPaint = Paint().apply { color = 0xFFEFEFEF.toInt() }
        val footerText = "Printed ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(now)} • PIN Beat Finder"

        val doc = PdfDocument()
        val rowsPerPage = ((PAGE_H - 2 * MARGIN - 70f - HEADER_H - 24f) / ROW_H).toInt()
        val pages = maxOf(1, (records.size + rowsPerPage - 1) / rowsPerPage)
        out.use { stream ->
            try {
                for (pageIndex in 0 until pages) {
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create())
                    val c = page.canvas
                    var y = MARGIN + 16f
                    c.drawText(title, MARGIN, y, titlePaint)
                    y += 16f
                    c.drawText(subtitle, MARGIN, y, subPaint)
                    y += 10f
                    c.drawText("${records.size} localit${if (records.size == 1) "y" else "ies"}", MARGIN, y + 10f, subPaint)
                    y += 28f

                    // header row
                    c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + HEADER_H, fillPaint)
                    val headers = listOf("Sl.", "Locality / Village", "Beat", "PIN", "Remarks")
                    headers.forEachIndexed { i, h -> c.drawText(h, MARGIN + COL_X[i] + 4f, y + 16f, headPaint) }
                    y += HEADER_H

                    val from = pageIndex * rowsPerPage
                    val slice = records.subList(from, minOf(records.size, from + rowsPerPage))
                    slice.forEachIndexed { i, r ->
                        val cells = listOf("${from + i + 1}", r.localityName, r.beatNumber, r.pincode, r.remarks)
                        cells.forEachIndexed { col, text -> drawClipped(c, text, MARGIN + COL_X[col] + 4f, y + 14f, COL_W[col] - 8f, cellPaint) }
                        y += ROW_H
                        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
                    }
                    // column rules
                    var x = MARGIN
                    COL_W.forEach { w -> c.drawLine(x, y - slice.size * ROW_H - HEADER_H, x, y, linePaint); x += w }
                    c.drawLine(PAGE_W - MARGIN, y - slice.size * ROW_H - HEADER_H, PAGE_W - MARGIN, y, linePaint)

                    c.drawText(footerText, MARGIN, PAGE_H - MARGIN + 12f, subPaint)
                    val pageLabel = "Page ${pageIndex + 1} of $pages"
                    c.drawText(pageLabel, PAGE_W - MARGIN - subPaint.measureText(pageLabel), PAGE_H - MARGIN + 12f, subPaint)
                    doc.finishPage(page)
                }
                doc.writeTo(stream)
            } finally {
                doc.close()
            }
        }
        return pages
    }

    /** Draws [text] truncated with an ellipsis so it never spills into the next column. */
    private fun drawClipped(c: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (text.isEmpty()) return
        if (paint.measureText(text) <= maxWidth) { c.drawText(text, x, y, paint); return }
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        c.drawText(text.substring(0, end) + "…", x, y, paint)
    }
}
