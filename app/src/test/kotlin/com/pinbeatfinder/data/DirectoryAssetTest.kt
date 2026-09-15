package com.pinbeatfinder.data

import com.pinbeatfinder.data.directory.DirectoryAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class DirectoryAssetTest {
    private val line = "Connaught Place SO\t110001\tPO\tNon Delivery\tNEW DELHI\tDELHI\tNew Delhi Central Division\tDelhi Region\tDelhi Circle\tkonogt place\tKNKT PLS\tKNKT PLS"

    @Test
    fun `parses a line into an entity`() {
        val e = DirectoryAsset.parseLine(line)!!
        assertEquals("Connaught Place SO", e.name)
        assertEquals("110001", e.pincode)
        assertEquals("PO", e.officeType)
        assertEquals("DELHI", e.state)
        assertEquals("KNKT PLS", e.phoneticPrimary)
        assertNull(DirectoryAsset.parseLine("too\tfew\tcolumns"))
    }

    @Test
    fun `batches stream and malformed lines are counted`() {
        val text = (1..5).joinToString("\n") { line } + "\nbroken\n\n" + line
        val batches = ArrayList<Int>()
        val skipped = DirectoryAsset.readBatches(BufferedReader(StringReader(text)), batchSize = 4) { batches += it.size }
        assertEquals(listOf(4, 2), batches)
        assertEquals(1, skipped)
    }

    @Test
    fun `gzip is detected by magic bytes, plain text passes through`() {
        val plain = line.toByteArray()
        val gz = java.io.ByteArrayOutputStream().also { java.util.zip.GZIPOutputStream(it).use { g -> g.write(plain) } }.toByteArray()
        assertEquals(line, DirectoryAsset.openMaybeGzip(java.io.ByteArrayInputStream(gz)).bufferedReader().readText())
        assertEquals(line, DirectoryAsset.openMaybeGzip(java.io.ByteArrayInputStream(plain)).bufferedReader().readText())
    }

    @Test
    fun `labels and meta`() {
        assertEquals("Sub Post Office", DirectoryAsset.officeTypeLabel("PO"))
        assertEquals("Branch Office", DirectoryAsset.officeTypeLabel("bo"))
        assertEquals("Non-Delivery", DirectoryAsset.deliveryLabel("Non Delivery"))
        val meta = DirectoryAsset.parseMeta("""{"version":"2026-09","rows":165627,"source":"x","columns":[]}""")
        assertEquals("2026-09", meta.version)
        assertEquals(165627, meta.rows)
    }
}
