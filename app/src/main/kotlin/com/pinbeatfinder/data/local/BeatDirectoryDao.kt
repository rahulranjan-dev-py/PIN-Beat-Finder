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

/** Projection for [BeatDirectoryDao.officeStats]: how much local data an office already has. */
data class OfficeStats(val officeName: String, val villages: Int, val beats: Int)

/** Projection for [BeatDirectoryDao.observeFacets]: one distinct filter combination. */
data class FacetRow(
    val state: String,
    val district: String,
    val officeType: String,
    val officeName: String,
    val beatNumber: String,
    val pincode: String,
)

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
     * Exact and prefix matches on the typed text sort first so a common query ("Ram") whose
     * candidates exceed [limit] never drops the best rows before the re-rank sees them.
     */
    @Query(
        """
        SELECT * FROM local_beat_directory
        WHERE (:state IS NULL OR state = :state)
          AND (:district IS NULL OR district = :district)
          AND (:officeType IS NULL OR officeType = :officeType)
          AND (:officeName IS NULL OR branchOffice = :officeName)
          AND (:beatNumber IS NULL OR beatNumber = :beatNumber)
          AND (:pincode IS NULL OR pincode = :pincode)
          AND (
                (:textPattern != '' AND localityName LIKE :textPattern ESCAPE '\')
             OR (:exactText != '' AND (beatNumber = :exactText OR pincode = :exactText))
             OR (:primaryPattern != '' AND (phoneticPrimary LIKE :primaryPattern OR phoneticAlternate LIKE :primaryPattern))
             OR (:alternatePattern != '' AND (phoneticPrimary LIKE :alternatePattern OR phoneticAlternate LIKE :alternatePattern))
          )
        ORDER BY (localityName = :exactText) DESC,
                 (:prefixPattern != '' AND localityName LIKE :prefixPattern ESCAPE '\') DESC,
                 localityName ASC
        LIMIT :limit
        """,
    )
    suspend fun searchCandidates(
        textPattern: String,
        prefixPattern: String,
        exactText: String,
        primaryPattern: String,
        alternatePattern: String,
        state: String?,
        district: String?,
        officeType: String?,
        officeName: String?,
        beatNumber: String?,
        pincode: String?,
        limit: Int,
    ): List<BeatDirectoryEntity>

    /** Browse mode (no query): filtered listing ordered by name. */
    @Query(
        """
        SELECT * FROM local_beat_directory
        WHERE (:state IS NULL OR state = :state)
          AND (:district IS NULL OR district = :district)
          AND (:officeType IS NULL OR officeType = :officeType)
          AND (:officeName IS NULL OR branchOffice = :officeName)
          AND (:beatNumber IS NULL OR beatNumber = :beatNumber)
          AND (:pincode IS NULL OR pincode = :pincode)
        ORDER BY localityName ASC
        LIMIT :limit
        """,
    )
    suspend fun browse(
        state: String?,
        district: String?,
        officeType: String?,
        officeName: String?,
        beatNumber: String?,
        pincode: String?,
        limit: Int,
    ): List<BeatDirectoryEntity>

    @Query("SELECT * FROM local_beat_directory ORDER BY state, district, branchOffice, beatNumber, localityName")
    suspend fun getAll(): List<BeatDirectoryEntity>

    /** Every row matching the optional filters; used by the "By beat" view, which groups in memory. */
    @Query(
        """
        SELECT * FROM local_beat_directory
        WHERE (:state IS NULL OR state = :state)
          AND (:district IS NULL OR district = :district)
          AND (:officeType IS NULL OR officeType = :officeType)
          AND (:officeName IS NULL OR branchOffice = :officeName)
          AND (:beatNumber IS NULL OR beatNumber = :beatNumber)
          AND (:pincode IS NULL OR pincode = :pincode)
        ORDER BY branchOffice, beatNumber, localityName
        """,
    )
    suspend fun listAll(
        state: String?,
        district: String?,
        officeType: String?,
        officeName: String?,
        beatNumber: String?,
        pincode: String?,
    ): List<BeatDirectoryEntity>

    /** Every distinct filter combination; small (one row per office/beat/PIN), drives the filter sheet. */
    @Query(
        """
        SELECT DISTINCT state, district, officeType, branchOffice AS officeName, beatNumber, pincode
        FROM local_beat_directory
        """,
    )
    fun observeFacets(): Flow<List<FacetRow>>

    @Query("DELETE FROM local_beat_directory WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

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

    /**
     * Per-office record and beat counts under one PIN. `branchOffice` is NOCASE-collated, so the
     * grouping is case-insensitive.
     */
    @Query(
        """
        SELECT branchOffice AS officeName, COUNT(*) AS villages, COUNT(DISTINCT beatNumber) AS beats
        FROM local_beat_directory WHERE pincode = :pincode GROUP BY branchOffice
        """,
    )
    suspend fun officeStats(pincode: String): List<OfficeStats>

    /** Bulk office-type fix for one office (name + its PINs); returns the number of rows changed. */
    @Query(
        """
        UPDATE local_beat_directory SET officeType = :officeType, updatedAt = :now
        WHERE branchOffice = :officeName AND pincode IN (:pincodes)
        """,
    )
    suspend fun updateOfficeType(officeName: String, pincodes: List<String>, officeType: String, now: Long): Int

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
