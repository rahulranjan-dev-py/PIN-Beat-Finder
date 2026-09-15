package com.pinbeatfinder.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Projection for [BeatDirectoryDao.observePincodeCounts]. */
data class PincodeCount(val pincode: String, val count: Int)

@Dao
interface BeatDirectoryDao {

    /**
     * Candidate retrieval for the offline search.
     *
     * The caller pre-builds the LIKE patterns (`%text%`, `PHONETIC%`) so SQLite receives plain
     * bound parameters — a requirement for the LIKE-prefix index optimisation. Passing an empty
     * string for a pattern disables that branch. State/district narrowing is optional (`NULL`).
     *
     * Beat number and PIN are matched exactly so staff can type "3" or "110001" directly.
     * The result is a small candidate set (bounded by [limit]) that the repository re-ranks.
     */
    @Query(
        """
        SELECT * FROM local_beat_directory
        WHERE (:state IS NULL OR state = :state)
          AND (:district IS NULL OR district = :district)
          AND (
                (:textPattern != '' AND localityName LIKE :textPattern)
             OR (:exactText != '' AND (beatNumber = :exactText OR pincode = :exactText))
             OR (:primaryPattern != '' AND (phoneticPrimary LIKE :primaryPattern OR phoneticAlternate LIKE :primaryPattern))
             OR (:alternatePattern != '' AND (phoneticPrimary LIKE :alternatePattern OR phoneticAlternate LIKE :alternatePattern))
          )
        ORDER BY localityName ASC
        LIMIT :limit
        """,
    )
    suspend fun searchCandidates(
        textPattern: String,
        exactText: String,
        primaryPattern: String,
        alternatePattern: String,
        state: String?,
        district: String?,
        limit: Int,
    ): List<BeatDirectoryEntity>

    /** Browse mode (no query): filtered listing ordered by name. */
    @Query(
        """
        SELECT * FROM local_beat_directory
        WHERE (:state IS NULL OR state = :state)
          AND (:district IS NULL OR district = :district)
        ORDER BY localityName ASC
        LIMIT :limit
        """,
    )
    suspend fun browse(state: String?, district: String?, limit: Int): List<BeatDirectoryEntity>

    @Query("SELECT * FROM local_beat_directory ORDER BY state, district, branchOffice, beatNumber, localityName")
    suspend fun getAll(): List<BeatDirectoryEntity>

    @Query("SELECT * FROM local_beat_directory WHERE id = :id")
    suspend fun getById(id: Long): BeatDirectoryEntity?

    @Query("SELECT COUNT(*) FROM local_beat_directory")
    fun observeCount(): Flow<Int>

    /** Reactive so filter chips refresh after an import or edit. */
    @Query("SELECT DISTINCT state FROM local_beat_directory ORDER BY state COLLATE NOCASE")
    fun observeStates(): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT district FROM local_beat_directory
        WHERE (:state IS NULL OR state = :state)
        ORDER BY district COLLATE NOCASE
        """,
    )
    fun observeDistricts(state: String?): Flow<List<String>>

    /** How many directory rows exist per PIN; drives the "N local beats for this PIN" bridge on online results. */
    @Query("SELECT pincode AS pincode, COUNT(*) AS count FROM local_beat_directory GROUP BY pincode")
    fun observePincodeCounts(): Flow<List<PincodeCount>>

    /** Natural keys already present, used for duplicate detection during import. */
    @Query("SELECT localityName || '|' || branchOffice || '|' || beatNumber || '|' || pincode FROM local_beat_directory")
    suspend fun naturalKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BeatDirectoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<BeatDirectoryEntity>): List<Long>

    @Update
    suspend fun update(entity: BeatDirectoryEntity)

    @Delete
    suspend fun delete(entity: BeatDirectoryEntity)

    @Query("DELETE FROM local_beat_directory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_beat_directory")
    suspend fun deleteAll()

    /**
     * Bulk import inside one transaction: either every chunk lands or none does, and the
     * on-disk journal is written once instead of once per row.
     */
    @Transaction
    suspend fun importBatch(entities: List<BeatDirectoryEntity>, replaceExisting: Boolean) {
        if (replaceExisting) deleteAll()
        entities.chunked(IMPORT_CHUNK).forEach { insertAll(it) }
    }

    companion object {
        /** Keeps each statement under SQLite's bound-variable ceiling. */
        const val IMPORT_CHUNK = 400
    }
}
