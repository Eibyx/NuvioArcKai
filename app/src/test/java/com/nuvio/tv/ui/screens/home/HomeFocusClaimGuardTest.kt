package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFocusClaimGuardTest {
    @Test
    fun `the two-row loop from the tv log is broken on its third claim`() {
        val guard = HomeFocusClaimGuard()
        assertTrue(guard.tryClaim("mdblist.227980", 8_047))
        assertTrue(guard.tryClaim("continue_watching", 8_183))
        assertFalse(guard.tryClaim("mdblist.227980", 8_316))
        // Still refused while the other row keeps trying straight after.
        assertTrue(guard.tryClaim("continue_watching", 8_362))
        assertFalse(guard.tryClaim("mdblist.227980", 8_406))
    }

    @Test
    fun `moving up and back down at human speed is allowed`() {
        val guard = HomeFocusClaimGuard()
        assertTrue(guard.tryClaim("a", 0))
        assertTrue(guard.tryClaim("b", 400))
        assertTrue(guard.tryClaim("a", 800))
    }

    @Test
    fun `moving through new rows quickly is allowed`() {
        val guard = HomeFocusClaimGuard()
        assertTrue(guard.tryClaim("a", 0))
        assertTrue(guard.tryClaim("b", 50))
        assertTrue(guard.tryClaim("c", 100))
        assertTrue(guard.tryClaim("d", 150))
    }

    @Test
    fun `the same row claiming again is allowed`() {
        val guard = HomeFocusClaimGuard()
        assertTrue(guard.tryClaim("a", 0))
        assertTrue(guard.tryClaim("a", 10))
        assertTrue(guard.tryClaim("a", 20))
    }

    @Test
    fun `a refused row may claim again once it has been quiet`() {
        val guard = HomeFocusClaimGuard()
        guard.tryClaim("a", 0); guard.tryClaim("b", 50)
        assertFalse(guard.tryClaim("a", 100))
        assertTrue(guard.tryClaim("a", 1_000))
    }
}
