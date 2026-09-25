package vn.fieldintel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapPackTest {
    @Test fun regionContainsUsesBoundingBox() {
        val r = OfflineMapRegion("test", "Test", 9.0, 104.0, 11.0, 106.0, java.io.File("test.region"))
        assertTrue(r.contains(10.0, 105.0))
        assertFalse(r.contains(12.0, 105.0))
    }
}