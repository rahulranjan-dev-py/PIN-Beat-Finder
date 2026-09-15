package com.pinbeatfinder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pinbeatfinder.core.phonetic.PhoneticKeys
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.OfficeType

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
    /** Office type code (BO/SO/HO/GPO/IDC). Added in schema v2; v1 rows default to BO. */
    @ColumnInfo(defaultValue = "BO") val officeType: String = OfficeType.BO.code,
    /** Serving office name. Column keeps its v1 name (`branchOffice`) so no table rebuild was needed. */
    @ColumnInfo(name = "branchOffice", collate = ColumnInfo.NOCASE) val officeName: String,
    /** Account (reporting) office. Column keeps its v1 name (`subPostOffice`). */
    @ColumnInfo(name = "subPostOffice", collate = ColumnInfo.NOCASE) val accountOffice: String,
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
        officeType = OfficeType.parse(officeType) ?: OfficeType.BO,
        officeName = officeName,
        accountOffice = accountOffice,
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
            officeType = record.officeType.code,
            officeName = record.officeName,
            accountOffice = record.accountOffice,
            beatNumber = record.beatNumber,
            district = record.district,
            state = record.state,
            pincode = record.pincode,
            remarks = record.remarks,
            updatedAt = record.updatedAt,
        )
    }
}
