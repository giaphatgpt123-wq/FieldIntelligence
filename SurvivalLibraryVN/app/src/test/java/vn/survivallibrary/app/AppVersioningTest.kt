package vn.survivallibrary.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersioningTest {
    @Test
    fun newerSemanticVersionIsDetected() {
        assertTrue(AppVersioning.isNewer("0.7.0", "0.6.0"))
        assertTrue(AppVersioning.isNewer("1.0.0", "0.99.9"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(AppVersioning.isNewer("0.6.0", "0.6.0"))
        assertFalse(AppVersioning.isNewer("0.5.9", "0.6.0"))
    }
}
