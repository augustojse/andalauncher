package com.anda.launcher.feature.search

import com.anda.launcher.feature.search.ContactSearchProvider.PhoneRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPhoneTest {

    @Test
    fun theDefaultNumberWins() {
        val rows = listOf(
            PhoneRow(1, "+1 555 0100", superPrimary = false, primary = true),
            PhoneRow(1, "+1 555 0199", superPrimary = true, primary = true),
        )
        assertEquals(mapOf(1L to "+1 555 0199"), ContactSearchProvider.primaryNumbers(rows))
    }

    @Test
    fun thePrimaryNumberComesNext() {
        val rows = listOf(
            PhoneRow(1, "+1 555 0100", superPrimary = false, primary = false),
            PhoneRow(1, "+1 555 0150", superPrimary = false, primary = true),
        )
        assertEquals(mapOf(1L to "+1 555 0150"), ContactSearchProvider.primaryNumbers(rows))
    }

    @Test
    fun otherwiseTheFirstListedNumber() {
        val rows = listOf(
            PhoneRow(1, "+1 555 0100", superPrimary = false, primary = false),
            PhoneRow(1, "+1 555 0101", superPrimary = false, primary = false),
        )
        assertEquals(mapOf(1L to "+1 555 0100"), ContactSearchProvider.primaryNumbers(rows))
    }

    @Test
    fun eachContactGetsItsOwnNumber() {
        val rows = listOf(
            PhoneRow(1, "+1 555 0100", superPrimary = false, primary = false),
            PhoneRow(2, "+44 20 7946 0000", superPrimary = false, primary = false),
            PhoneRow(1, "+1 555 0199", superPrimary = true, primary = false),
        )
        assertEquals(mapOf(1L to "+1 555 0199", 2L to "+44 20 7946 0000"), ContactSearchProvider.primaryNumbers(rows))
    }

    @Test
    fun contactsWithoutNumbersAreLeftOut() {
        assertTrue(ContactSearchProvider.primaryNumbers(emptyList()).isEmpty())
    }
}
