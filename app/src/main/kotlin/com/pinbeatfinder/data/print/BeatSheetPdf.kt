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

    /** Every piece of fixed text on the sheet, so the caller can hand in the app language. */
    data class Labels(
        val serial: String,
        val locality: String,
        val beat: String,
        val pin: String,
        val remarks: String,
        /** "12 localities" for the count under the subtitle. */
        val localities: (Int) -> String,
        /** Footer; receives the formatted date-time. */
        val printed: (String) -> String,
        /** "Page 1 of 3". */
        val page: (Int, Int) -> String,
    ) {
        companion object {
            val ENGLISH = Labels(
                serial = "Sl.", locality = "Locality / Village", beat = "Beat", pin = "PIN", remarks = "Remarks",
                localities = { n -> "$n localit${if (n == 1) "y" else "ies"}" },
                printed = { stamp -> "Printed $stamp • PIN Beat Finder" },
                page = { p, n -> "Page $p of $n" },
            )
        }
    }

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
    fun write(
        title: String,
        subtitle: String,
        records: List<BeatRecord>,
        out: OutputStream,
        now: Date = Date(),
        labels: Labels = Labels.ENGLISH,
    ): Int {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        val fillPaint = Paint().apply { color = 0xFFEFEFEF.toInt() }
        val footerText = labels.printed(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(now))

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
                    c.drawText(labels.localities(records.size), MARGIN, y + 10f, subPaint)
                    y += 28f

                    // header row
                    c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + HEADER_H, fillPaint)
                    val headers = listOf(labels.serial, labels.locality, labels.beat, labels.pin, labels.remarks)
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
                    val pageLabel = labels.page(pageIndex + 1, pages)
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

    /** One office on the summary sheet: its beats and the villages of each. */
    data class OfficeSection(val title: String, val subtitle: String, val beats: List<Pair<String, List<String>>>)

    data class SummaryLabels(
        val heading: String,
        /** "Beat 2 • 12 villages". */
        val beatLine: (String, Int) -> String,
        val printed: (String) -> String,
        val page: (Int, Int) -> String,
    )

    /**
     * Writes one section per office, each starting on a new page: a heading, the office line,
     * then every beat as a bold line followed by its village names flowed across the width.
     */
    fun writeOfficeSummary(sections: List<OfficeSection>, out: OutputStream, labels: SummaryLabels, now: Date = Date()): Int {
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val beatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val footerText = labels.printed(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(now))
        val width = PAGE_W - 2 * MARGIN
        val bottom = PAGE_H - MARGIN - 20f

        // Pass 1: lay every line out into pages (text + paint + indent), breaking pages as the text grows.
        data class Line(val text: String, val paint: Paint, val indent: Float, val height: Float)
        val pages = ArrayList<ArrayList<Line>>()
        var current = ArrayList<Line>()
        var y = MARGIN
        fun newPage() { if (current.isNotEmpty()) pages += current; current = ArrayList(); y = MARGIN }
        fun line(text: String, paint: Paint, indent: Float = 0f, height: Float = ROW_H * 0.75f) {
            if (y + height > bottom) newPage()
            current += Line(text, paint, indent, height)
            y += height
        }

        for (section in sections) {
            newPage()
            line(labels.heading, headingPaint, height = 14f)
            line(section.title, titlePaint, height = 22f)
            line(section.subtitle, subPaint, height = 20f)
            for ((beat, villages) in section.beats) {
                if (y + 32f > bottom) newPage()
                line(labels.beatLine(beat, villages.size), beatPaint, height = 18f)
                // Flow the names across the width, comma separated.
                var buffer = StringBuilder()
                for ((i, name) in villages.withIndex()) {
                    val piece = if (i == villages.size - 1) name else "$name,"
                    val candidate = if (buffer.isEmpty()) piece else "$buffer $piece"
                    if (buffer.isNotEmpty() && bodyPaint.measureText(candidate) > width - 12f) {
                        line(buffer.toString(), bodyPaint, indent = 12f)
                        buffer = StringBuilder(piece)
                    } else {
                        buffer = StringBuilder(candidate)
                    }
                }
                if (buffer.isNotEmpty()) line(buffer.toString(), bodyPaint, indent = 12f)
                y += 6f
            }
        }
        newPage()
        if (pages.isEmpty()) pages += arrayListOf(Line(labels.heading, headingPaint, 0f, 14f))

        // Pass 2: draw.
        val doc = PdfDocument()
        out.use { stream ->
            try {
                pages.forEachIndexed { index, lines ->
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create())
                    val c = page.canvas
                    var py = MARGIN
                    for (l in lines) {
                        py += l.height
                        drawClipped(c, l.text, MARGIN + l.indent, py - 4f, width - l.indent, l.paint)
                    }
                    c.drawText(footerText, MARGIN, PAGE_H - MARGIN + 12f, subPaint)
                    val pageLabel = labels.page(index + 1, pages.size)
                    c.drawText(pageLabel, PAGE_W - MARGIN - subPaint.measureText(pageLabel), PAGE_H - MARGIN + 12f, subPaint)
                    doc.finishPage(page)
                }
                doc.writeTo(stream)
            } finally {
                doc.close()
            }
        }
        return pages.size
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
