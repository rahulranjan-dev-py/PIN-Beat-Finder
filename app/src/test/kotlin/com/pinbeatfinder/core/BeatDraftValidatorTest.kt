package com.pinbeatfinder.core

import com.pinbeatfinder.core.util.BeatDraftValidator
import com.pinbeatfinder.core.util.PinCodeValidator
import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.BeatField
import com.pinbeatfinder.domain.model.DraftValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDraftValidatorTest {

    private val good = BeatDraft(
        localityName = " Rampur Kalan ",
        branchOffice = "Rampur BO",
        subPostOffice = "Sitapur SO",
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
