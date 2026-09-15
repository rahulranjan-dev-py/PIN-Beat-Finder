package com.pinbeatfinder.data.directory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Metadata shipped next to the TSV asset. */
@Serializable
data class DirectoryMeta(val version: String, val rows: Int, val source: String = "")

/**
 * Pure parser for the bundled `india_post_directory.tsv.gz` (12 tab-separated columns, see the
 * generator in the repo's scratch tooling). Kept Android-free so it is unit-testable.
 */
object DirectoryAsset {
    /**
     * Gzip-compressed TSV. The extension is deliberately NOT ".gz": the Android Gradle plugin
     * treats `.gz` assets specially (it decompresses them and strips the extension at packaging
     * time), which made the file vanish under its expected name in v0.8.0/v0.9.0.
     */
    const val TSV_NAME = "india_post_directory.bin"
    const val META_NAME = "india_post_directory.json"
    private const val COLUMNS = 12

    fun parseMeta(text: String): DirectoryMeta = Json { ignoreUnknownKeys = true }.decodeFromString(DirectoryMeta.serializer(), text)

    /** Parses one TSV line; null for malformed lines (which the seeder skips and counts). */
    fun parseLine(line: String): IndiaPostOfficeEntity? {
        val f = line.split('\t')
        if (f.size < COLUMNS) return null
        return IndiaPostOfficeEntity(
            name = f[0], pincode = f[1], officeType = f[2], delivery = f[3], district = f[4], state = f[5],
            division = f[6], region = f[7], circle = f[8], normalizedName = f[9], phoneticPrimary = f[10], phoneticAlternate = f[11],
        )
    }

    /**
     * Wraps [raw] in a [GZIPInputStream] when it starts with the gzip magic bytes, otherwise
     * returns it as-is — so the seeder works whether or not the build tooling decompressed it.
     */
    fun openMaybeGzip(raw: InputStream): InputStream {
        val buffered = if (raw.markSupported()) raw else BufferedInputStream(raw, 1 shl 16)
        buffered.mark(2)
        val b1 = buffered.read(); val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 1 shl 16) else buffered
    }

    /** Streams entities in [batchSize] chunks so 165k rows never sit in memory at once. */
    inline fun readBatches(reader: BufferedReader, batchSize: Int, onBatch: (List<IndiaPostOfficeEntity>) -> Unit): Int {
        val batch = ArrayList<IndiaPostOfficeEntity>(batchSize)
        var skipped = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) continue
            val row = parseLine(line)
            if (row == null) { skipped++; continue }
            batch += row
            if (batch.size == batchSize) { onBatch(batch); batch.clear() }
        }
        if (batch.isNotEmpty()) onBatch(batch)
        return skipped
    }

    /** Dataset office-type codes to the labels used by the online providers. */
    fun officeTypeLabel(code: String): String = when (code.trim().uppercase()) {
        "BO" -> "Branch Office"
        "PO", "SO" -> "Sub Post Office"
        "HO", "HPO" -> "Head Post Office"
        "GPO" -> "General Post Office"
        else -> code
    }

    fun deliveryLabel(raw: String): String = when (raw.trim().lowercase()) {
        "delivery" -> "Delivery"
        "non delivery", "non-delivery", "nondelivery" -> "Non-Delivery"
        else -> raw
    }
}
