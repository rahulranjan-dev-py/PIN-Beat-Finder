package com.pinbeatfinder.data

import com.pinbeatfinder.data.excel.ExcelCodec
import com.pinbeatfinder.data.excel.ExcelFormatException
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.OfficeType
import org.dhatim.fastexcel.Workbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelCodecTest {

    private val records = listOf(
        BeatRecord(1, "Rampur Kalan", OfficeType.BO, "Rampur", "Sitapur SO", "3", "Sitapur", "Uttar Pradesh", "261001", "Near temple", 1L),
        BeatRecord(2, "Bhilwara", OfficeType.SO, "Bhilwara", "", "1", "Bhilwara", "Rajasthan", "311001", "", 1L),
    )

    @Test
    fun `template has the mandated headers`() {
        assertEquals(
            listOf(
                "Locality/Village Name*", "Office Type*", "Office Name*", "Account Office (SO/HO)", "Beat Number*",
                "District*", "State*", "Pincode*", "Remarks",
            ),
            ExcelCodec.HEADERS,
        )
        val bytes = ByteArrayOutputStream().also { ExcelCodec.writeTemplate(it) }.toByteArray()
        assertTrue(bytes.size > 1000)
        val parsed = ExcelCodec.read(ByteArrayInputStream(bytes))
        assertEquals(0, parsed.rows.size)
    }

    @Test
    fun `export then import round-trips every field`() {
        val bytes = ByteArrayOutputStream().also { ExcelCodec.writeRecords(records, it) }.toByteArray()
        val parsed = ExcelCodec.read(ByteArrayInputStream(bytes))
        assertEquals(2, parsed.rows.size)
        val first = parsed.rows[0]
        assertEquals(2, first.rowNumber) // 1-based, row 1 is the header
        assertEquals("Rampur Kalan", first.draft.localityName)
        assertEquals("BO", first.draft.officeType)
        assertEquals("Rampur", first.draft.officeName)
        assertEquals("Sitapur SO", first.draft.accountOffice)
        assertEquals("3", first.draft.beatNumber)
        assertEquals("Sitapur", first.draft.district)
        assertEquals("Uttar Pradesh", first.draft.state)
        assertEquals("261001", first.draft.pincode)
        assertEquals("Near temple", first.draft.remarks)
        assertEquals("", parsed.rows[1].draft.remarks)
        assertEquals("SO", parsed.rows[1].draft.officeType)
        assertEquals("", parsed.rows[1].draft.accountOffice)
    }

    @Test
    fun `legacy BO and SO columns are folded into type, name and account office`() {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "test", "1.0")
        val ws = wb.newWorksheet("Sheet1")
        listOf("Locality/Village Name*", "Branch Office (BO)*", "Sub Post Office (SO)*", "Beat Number*", "District*", "State*", "Pincode*", "Remarks")
            .forEachIndexed { i, h -> ws.value(0, i, h) }
        // classic row: BO + its SO
        listOf("Rampur Kalan", "Rampur B.O", "Sitapur SO", "3", "Sitapur", "Uttar Pradesh", "261001", "").forEachIndexed { i, v -> ws.value(1, i, v) }
        // SO-only row (village served directly by the sub office)
        listOf("Sitapur Town", "", "Sitapur S.O", "1", "Sitapur", "Uttar Pradesh", "261001", "").forEachIndexed { i, v -> ws.value(2, i, v) }
        wb.finish()

        val parsed = ExcelCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, parsed.rows.size)
        val bo = parsed.rows[0].draft
        assertEquals("BO", bo.officeType); assertEquals("Rampur", bo.officeName); assertEquals("Sitapur SO", bo.accountOffice)
        val so = parsed.rows[1].draft
        assertEquals("SO", so.officeType); assertEquals("Sitapur", so.officeName); assertEquals("", so.accountOffice)
    }

    @Test
    fun `numeric cells, loose headers and blank rows are handled`() {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "test", "1.0")
        val ws = wb.newWorksheet("Sheet1")
        // Headers typed by a human: different case, no asterisks, reordered, extra column.
        listOf("state", "district", "locality / village name", "office type", "office name", "beat number", "PINCODE", "remarks", "ignored")
            .forEachIndexed { i, h -> ws.value(0, i, h) }
        ws.value(1, 0, "Rajasthan"); ws.value(1, 1, "Bhilwara"); ws.value(1, 2, "Bhilwara")
        ws.value(1, 3, "bo"); ws.value(1, 4, "Bhilwara"); ws.value(1, 5, 2) // numeric beat
        ws.value(1, 6, 311001) // numeric PIN
        // row 2: formatted but empty (how Excel serialises a row someone cleared with Delete)
        ws.value(2, 0, ""); ws.value(2, 2, "   ")
        ws.value(3, 0, "Rajasthan"); ws.value(3, 2, "Only partially filled")
        wb.finish()

        val parsed = ExcelCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, parsed.rows.size)
        assertEquals(1, parsed.blankRowsSkipped)
        val row = parsed.rows[0].draft
        assertEquals("Bhilwara", row.localityName)
        assertEquals("2", row.beatNumber)
        assertEquals("311001", row.pincode)
        assertEquals("Rajasthan", row.state)
        assertEquals("bo", row.officeType)
        assertEquals("Bhilwara", row.officeName)
        assertEquals(4, parsed.rows[1].rowNumber)
    }

    @Test
    fun `missing mandatory column is a format error`() {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "test", "1.0")
        val ws = wb.newWorksheet("Sheet1")
        listOf("Locality", "State").forEachIndexed { i, h -> ws.value(0, i, h) }
        ws.value(1, 0, "X")
        wb.finish()
        try {
            ExcelCodec.read(ByteArrayInputStream(out.toByteArray()))
            fail("expected ExcelFormatException")
        } catch (e: ExcelFormatException) {
            assertTrue(e.message!!.contains("Pincode"))
            assertTrue(e.message!!.contains("Office Name"))
        }
    }

    @Test
    fun `backup file name is timestamped xlsx`() {
        val name = ExcelCodec.backupFileName()
        assertTrue(name.startsWith("beat_directory_backup_"))
        assertTrue(name.endsWith(".xlsx"))
    }

    @Test
    fun `export file name is sanitised from the label`() {
        val name = ExcelCodec.exportFileName("Nirsa Chatti SO beat 2/A")
        assertTrue(name, name.startsWith("beats_Nirsa_Chatti_SO_beat_2_A_"))
        assertTrue(name.endsWith(".xlsx"))
        assertTrue(ExcelCodec.exportFileName("   ").startsWith("beats_export_"))
    }
}
