package com.pinbeatfinder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pinbeatfinder.core.phonetic.PhoneticKeys
import com.pinbeatfinder.domain.model.BeatRecord

/**
 * Persisted row of the offline beat directory.
 *
 * Phonetic keys are pre-computed on every write (see [BeatDirectoryRepository]) so that fuzzy
 * search is a plain indexed prefix scan rather than an O(n) distance computation.
 *
 * Text columns that are searched with `LIKE 'prefix%'` are declared `NOCASE` so SQLite's LIKE
 * optimisation can use their indexes (it only applies to NOCASE-collated indexed columns).
 */
@Entity(
    tableName = BeatDirectoryEntity.TABLE,
    indices = [
        Index(value = ["localityName"]),
        Index(value = ["phoneticPrimary", "phoneticAlternate"]),
        Index(value = ["phoneticAlternate"]),   // compound index above cannot serve alternate-only lookups
        Index(value = ["state", "district"]),
        Index(value = ["beatNumber"]),
        Index(value = ["pincode"]),
    ],
)
data class BeatDirectoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val localityName: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val phoneticPrimary: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val phoneticAlternate: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val branchOffice: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val subPostOffice: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val beatNumber: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val district: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val state: String,
    val pincode: String,
    val remarks: String = "",
    val updatedAt: Long,
) {
    fun toDomain() = BeatRecord(
        id = id,
        localityName = localityName,
        branchOffice = branchOffice,
        subPostOffice = subPostOffice,
        beatNumber = beatNumber,
        district = district,
        state = state,
        pincode = pincode,
        remarks = remarks,
        updatedAt = updatedAt,
    )

    companion object {
        const val TABLE = "local_beat_directory"

        fun fromDomain(record: BeatRecord, keys: PhoneticKeys) = BeatDirectoryEntity(
            id = record.id,
            localityName = record.localityName,
            phoneticPrimary = keys.primary,
            phoneticAlternate = keys.alternate,
            branchOffice = record.branchOffice,
            subPostOffice = record.subPostOffice,
            beatNumber = record.beatNumber,
            district = record.district,
            state = record.state,
            pincode = record.pincode,
            remarks = record.remarks,
            updatedAt = record.updatedAt,
        )
    }
}
