package com.pinbeatfinder.data.repository

import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.data.local.BeatDirectoryDao
import com.pinbeatfinder.data.local.BeatDirectoryEntity
import com.pinbeatfinder.data.local.OfficeStats
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.BeatSearchHit
import com.pinbeatfinder.domain.model.MatchKind
import com.pinbeatfinder.domain.model.OfficeType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Result of a bulk import. */
data class ImportOutcome(val inserted: Int, val duplicatesSkipped: Int)

/**
 * Offline beat directory: the only place that knows rows carry phonetic keys.
 *
 * Search pipeline:
 *  1. Encode the query once ([PhoneticSearchEngine.encode]).
 *  2. Ask the DAO for an indexed candidate set (substring OR phonetic prefix OR exact beat/PIN).
 *  3. Re-rank the candidates in memory with [PhoneticSearchEngine.score].
 * Steps 1–3 stay well under the 300 ms budget for directories of tens of thousands of rows
 * because step 2 never scans the whole table and step 3 touches at most [CANDIDATE_LIMIT] rows.
 */
class BeatDirectoryRepository(
    private val dao: BeatDirectoryDao,
    private val phonetic: PhoneticSearchEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(
        query: String,
        filters: BeatSearchFilters = BeatSearchFilters(),
        limit: Int = RESULT_LIMIT,
    ): List<BeatSearchHit> = withContext(ioDispatcher) {
        val q = query.trim()
        val state = filters.state?.takeIf { it.isNotBlank() }
        val district = filters.district?.takeIf { it.isNotBlank() }

        if (q.isEmpty()) {
            return@withContext dao.browse(state, district, limit).map { BeatSearchHit(it.toDomain(), 0.0) }
        }

        val keys = phonetic.encode(q)
        val candidates = dao.searchCandidates(
            textPattern = "%${escapeLike(q)}%",
            exactText = q,
            primaryPattern = if (keys.isEmpty) "" else "${keys.primary}%",
            alternatePattern = if (keys.isEmpty || keys.alternate == keys.primary) "" else "${keys.alternate}%",
            state = state,
            district = district,
            limit = CANDIDATE_LIMIT,
        )

        candidates.asSequence()
            .map { entity ->
                val record = entity.toDomain()
                val score = when {
                    record.pincode == q || record.beatNumber.equals(q, ignoreCase = true) -> 1.0
                    else -> phonetic.score(q, record.localityName)
                }
                BeatSearchHit(record, score, MatchKind.fromScore(score, hasQuery = true))
            }
            .sortedWith(compareByDescending<BeatSearchHit> { it.score }.thenBy { it.record.localityName })
            .take(limit)
            .toList()
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    /** PIN -> number of local records, kept live so online cards update after an add/import. */
    fun observePincodeCounts(): Flow<Map<String, Int>> =
        dao.observePincodeCounts().map { rows -> rows.associate { it.pincode to it.count } }
    fun observeStates(): Flow<List<String>> = dao.observeStates()
    fun observeDistricts(state: String?): Flow<List<String>> = dao.observeDistricts(state?.takeIf { it.isNotBlank() })

    suspend fun getAll(): List<BeatRecord> = withContext(ioDispatcher) { dao.getAll().map { it.toDomain() } }

    /** Unbounded filtered listing for the "By beat" view. */
    suspend fun listAll(filters: BeatSearchFilters = BeatSearchFilters()): List<BeatRecord> = withContext(ioDispatcher) {
        dao.listAll(filters.state?.takeIf { it.isNotBlank() }, filters.district?.takeIf { it.isNotBlank() }).map { it.toDomain() }
    }

    /** Local data per office under [pincode], keyed by lower-cased office name. */
    suspend fun officeStats(pincode: String): Map<String, OfficeStats> = withContext(ioDispatcher) {
        dao.officeStats(pincode).associateBy { it.officeName.trim().lowercase() }
    }

    /** Sets the type of every record of one office (name, case-insensitive, within [pincodes]). */
    suspend fun setOfficeType(officeName: String, pincodes: List<String>, type: OfficeType): Int = withContext(ioDispatcher) {
        if (pincodes.isEmpty()) 0
        else dao.updateOfficeType(officeName.trim(), pincodes, type.code, System.currentTimeMillis())
    }

    suspend fun deleteMany(ids: Collection<Long>) = withContext(ioDispatcher) {
        ids.chunked(BeatDirectoryDao.IMPORT_CHUNK).forEach { dao.deleteByIds(it) }
    }

    /**
     * Splits [records] into rows that would be inserted and the count that would be skipped as
     * duplicates of existing rows (or of each other). Used for the import preview so the numbers
     * shown before confirming are exactly what [importRecords] will do.
     */
    suspend fun partitionForImport(records: List<BeatRecord>, replaceExisting: Boolean): Pair<List<BeatRecord>, Int> =
        withContext(ioDispatcher) {
            val existing: MutableSet<String> = if (replaceExisting) HashSet() else HashSet(dao.naturalKeys().map { it.lowercase() })
            val fresh = ArrayList<BeatRecord>(records.size)
            var duplicates = 0
            for (record in records) {
                if (!existing.add(record.dedupeKey)) duplicates++ else fresh += record
            }
            fresh to duplicates
        }

    suspend fun getById(id: Long): BeatRecord? = withContext(ioDispatcher) { dao.getById(id)?.toDomain() }

    /** Inserts or updates; phonetic keys are always recomputed so edits never leave stale keys. */
    suspend fun save(record: BeatRecord): Long = withContext(ioDispatcher) {
        val entity = toEntity(record.copy(updatedAt = System.currentTimeMillis()))
        if (entity.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            entity.id
        }
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) { dao.deleteById(id) }

    suspend fun importRecords(records: List<BeatRecord>, replaceExisting: Boolean): ImportOutcome =
        withContext(ioDispatcher) {
            val existing: MutableSet<String> = if (replaceExisting) HashSet() else HashSet(dao.naturalKeys().map { it.lowercase() })
            val fresh = ArrayList<BeatDirectoryEntity>(records.size)
            var duplicates = 0
            for (record in records) {
                if (!existing.add(record.dedupeKey)) {
                    duplicates++
                    continue
                }
                fresh += toEntity(record.copy(id = 0L))
            }
            dao.importBatch(fresh, replaceExisting)
            ImportOutcome(inserted = fresh.size, duplicatesSkipped = duplicates)
        }

    suspend fun clear() = withContext(ioDispatcher) { dao.deleteAll() }

    private fun toEntity(record: BeatRecord) =
        BeatDirectoryEntity.fromDomain(record, phonetic.encode(record.localityName))

    companion object {
        const val RESULT_LIMIT = 200
        const val CANDIDATE_LIMIT = 400

        /** `%` and `_` are LIKE wildcards; a village literally named "100%" should still work. */
        fun escapeLike(raw: String): String = raw.replace("%", "").replace("_", " ")
    }
}
