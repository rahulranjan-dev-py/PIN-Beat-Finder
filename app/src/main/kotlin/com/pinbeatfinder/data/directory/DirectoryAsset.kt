package com.pinbeatfinder.data.directory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader

/** Metadata shipped next to the TSV asset. */
@Serializable
data class DirectoryMeta(val version: String, val rows: Int, val source: String = "")

/**
 * Pure parser for the bundled `india_post_directory.tsv.gz` (12 tab-separated columns, see the
 * generator in the repo's scratch tooling). Kept Android-free so it is unit-testable.
 */
object DirectoryAsset {
    const val TSV_NAME = "india_post_directory.tsv.gz"
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
