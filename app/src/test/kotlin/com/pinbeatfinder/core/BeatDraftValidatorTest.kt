package com.pinbeatfinder.core

import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.core.util.PinCodeValidator
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.DraftValidation
import com.pinbeatfinder.domain.model.FieldError
import com.pinbeatfinder.domain.model.OfficeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDraftValidatorTest {

    private val good = BeatDraft(
        localityName = " Rampur Kalan ",
        officeType = "BO",
        officeName = "Rampur",
        accountOffice = "Sitapur SO",
        beatNumber = "3.0",
        district = "Sitapur",
        state = "Uttar Pradesh",
        pincode = " 261001 ",
        remarks = "",
    )

    @Test
    fun `pin regex accepts six digits not starting with zero`() {
        assertTrue(PinCodeValidator.isValid("110001"))
        assertFalse(PinCodeValidator.isValid("010001"))
        assertFalse(PinCodeValidator.isValid("11000"))
        assertFalse(PinCodeValidator.isValid("1100011"))
        assertFalse(PinCodeValidator.isValid("11000a"))
        assertEquals("110001", PinCodeValidator.normalize("110001.0"))
        assertNull(PinCodeValidator.normalize("abc"))
    }

    @Test
    fun `valid draft is trimmed and normalised`() {
        val result = BeatDraftValidator.validate(good, now = 42L)
        assertTrue(result is DraftValidation.Valid)
        val record = (result as DraftValidation.Valid).record
        assertEquals("Rampur Kalan", record.localityName)
        assertEquals("3", record.beatNumber)
        assertEquals("261001", record.pincode)
        assertEquals(42L, record.updatedAt)
        assertEquals(OfficeType.BO, record.officeType)
        assertEquals("Rampur", record.officeName)
        assertEquals("Rampur BO", record.officeDisplay)
        assertEquals("Sitapur SO", record.accountOffice)
    }

    @Test
    fun `office type accepts codes and spellings, rejects junk, account office optional`() {
        assertEquals(OfficeType.SO, (BeatDraftValidator.validate(good.copy(officeType = "Sub Post Office", accountOffice = "")) as DraftValidation.Valid).record.officeType)
        assertEquals(OfficeType.HO, (BeatDraftValidator.validate(good.copy(officeType = " h.o ")) as DraftValidation.Valid).record.officeType)
        assertEquals(OfficeType.GPO, (BeatDraftValidator.validate(good.copy(officeType = "gpo")) as DraftValidation.Valid).record.officeType)
        assertEquals(OfficeType.IDC, (BeatDraftValidator.validate(good.copy(officeType = "IDC")) as DraftValidation.Valid).record.officeType)
        assertEquals(OfficeType.SO, OfficeType.parse("PO"))
        assertNull(OfficeType.parse("XYZ"))

        val bad = BeatDraftValidator.validate(good.copy(officeType = "XYZ")) as DraftValidation.Invalid
        assertEquals(FieldError.INVALID_OFFICE_TYPE, bad.errors[BeatField.OFFICE_TYPE])
        val blank = BeatDraftValidator.validate(good.copy(officeType = "", officeName = " ")) as DraftValidation.Invalid
        assertEquals(setOf(BeatField.OFFICE_TYPE, BeatField.OFFICE_NAME), blank.errors.keys)
        assertEquals(FieldError.REQUIRED, blank.errors[BeatField.OFFICE_TYPE])
    }

    @Test
    fun `office suffix stripping`() {
        assertEquals("Rampur", OfficeType.stripSuffix("Rampur B.O"))
        assertEquals("Nirsa Chatti", OfficeType.stripSuffix("Nirsa Chatti SO"))
        assertEquals("Kolkata", OfficeType.stripSuffix("Kolkata GPO"))
        assertEquals("Dhanbad", OfficeType.stripSuffix("Dhanbad H.O."))
        assertEquals("Bo", OfficeType.stripSuffix("Bo"))  // never strips the whole name
        assertEquals(OfficeType.GPO, OfficeType.fromDirectory("Head Post Office", "Kolkata GPO"))
        assertEquals(OfficeType.BO, OfficeType.fromDirectory("BO", "Barbendia BO"))
    }

    @Test
    fun `missing mandatory fields are reported per field`() {
        val result = BeatDraftValidator.validate(good.copy(localityName = "", state = "  ", pincode = ""))
        assertTrue(result is DraftValidation.Invalid)
        val errors = (result as DraftValidation.Invalid).errors
        assertEquals(setOf(BeatField.LOCALITY, BeatField.STATE, BeatField.PINCODE), errors.keys)
    }

    @Test
    fun `bad pincode is rejected`() {
        val result = BeatDraftValidator.validate(good.copy(pincode = "012345"))
        assertTrue(result is DraftValidation.Invalid)
        assertTrue((result as DraftValidation.Invalid).errors.containsKey(BeatField.PINCODE))
    }

    @Test
    fun `alphanumeric beat numbers are kept verbatim`() {
        val result = BeatDraftValidator.validate(good.copy(beatNumber = "2A"))
        assertEquals("2A", (result as DraftValidation.Valid).record.beatNumber)
    }
}
