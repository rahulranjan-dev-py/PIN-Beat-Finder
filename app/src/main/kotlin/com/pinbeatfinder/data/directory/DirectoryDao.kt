package com.pinbeatfinder.data.directory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectoryDao {
    @Query("SELECT * FROM india_post_offices WHERE pincode = :pincode ORDER BY officeType DESC, name")
    suspend fun byPincode(pincode: String): List<IndiaPostOfficeEntity>

    /**
     * Candidate retrieval for name search: normalised-prefix and phonetic-prefix hit indexes;
     * the raw substring match is the safety net (full scan, still ~100 ms on 165k rows).
     */
    @Query(
        """
        SELECT * FROM india_post_offices
        WHERE (:normPattern != '' AND normalizedName LIKE :normPattern)
           OR (:primaryPattern != '' AND (phoneticPrimary LIKE :primaryPattern OR phoneticAlternate LIKE :primaryPattern))
           OR (:alternatePattern != '' AND (phoneticPrimary LIKE :alternatePattern OR phoneticAlternate LIKE :alternatePattern))
           OR (:textPattern != '' AND name LIKE :textPattern)
        LIMIT :limit
        """,
    )
    suspend fun searchByName(
        normPattern: String,
        primaryPattern: String,
        alternatePattern: String,
        textPattern: String,
        limit: Int,
    ): List<IndiaPostOfficeEntity>

    @Query("SELECT COUNT(*) FROM india_post_offices")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM india_post_offices")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insertAll(rows: List<IndiaPostOfficeEntity>)

    @Query("DELETE FROM india_post_offices")
    suspend fun deleteAll()
}
