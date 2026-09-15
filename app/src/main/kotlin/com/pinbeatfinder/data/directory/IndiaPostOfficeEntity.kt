package com.pinbeatfinder.data.directory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of the bundled All-India directory (Department of Posts dataset). Phonetic keys are
 * precomputed into the asset with the app's own engine, so seeding is pure inserts and name
 * search behaves exactly like the local beat directory.
 */
@Entity(
    tableName = IndiaPostOfficeEntity.TABLE,
    indices = [
        Index(value = ["pincode"]),
        Index(value = ["normalizedName"]),
        Index(value = ["phoneticPrimary"]),
        Index(value = ["phoneticAlternate"]),
        Index(value = ["state", "district"]),
    ],
)
data class IndiaPostOfficeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val pincode: String,
    val officeType: String,
    val delivery: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val district: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val state: String,
    val division: String,
    val region: String,
    val circle: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val normalizedName: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val phoneticPrimary: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val phoneticAlternate: String,
    /** Reporting ("account") office from the India Post facility master, e.g. "Nirsa Chatti SO". Blank when unknown. */
    @ColumnInfo(defaultValue = "") val accountOffice: String = "",
) {
    companion object {
        const val TABLE = "india_post_offices"
    }
}
