package com.alhaq.amniquest.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationModelTest {

    @Test
    fun validationResult_equalityAndProperties_behaveAsExpected() {
        val valid = TaskValidationClient.ValidationResult(
            isValid = true,
            reason = "Desk is clean and organized."
        )
        val invalid = TaskValidationClient.ValidationResult(
            isValid = false,
            reason = "Coffee mug is still visible on the table."
        )

        assertTrue(valid.isValid)
        assertEquals("Desk is clean and organized.", valid.reason)
        assertFalse(invalid.isValid)
        assertEquals("Coffee mug is still visible on the table.", invalid.reason)
    }

    @Test
    fun buildConfig_hasNonBlankBackendConfiguration() {
        assertNotNull(BuildConfig.SUPABASE_URL)
        assertNotNull(BuildConfig.SUPABASE_API_KEY)
        assertNotNull(BuildConfig.API_URL)
        assertTrue(BuildConfig.SUPABASE_URL.isNotBlank())
    }
}

