package vn.fieldintel.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class OfflineMapPackTest {
    @Test fun regionContainsUsesBoundingBox() {
        val r = OfflineMapRegion("test", "Test", 9.0, 104.0, 11.0, 106.0, java.io.File("test.region"))
        assertTrue(r.contains(10.0, 105.0))
        assertFalse(r.contains(12.0, 105.0))
    }
    @Test fun validGeometryIsAccepted() {
        val region = OfflineMapRegion("test", "Test", 9.0, 104.0, 11.0, 106.0, java.io.File("test.region"))
        val file = kotlin.io.path.createTempFile("map", ".lines").toFile()
        try { file.writeText("10.0,105.0;10.5,105.5\n"); MapRecordValidator.validate(region, file) }
        finally { file.delete() }
    }

    @Test fun malformedGeometryCannotBeSilentlyDropped() {
        val region = OfflineMapRegion("test", "Test", 9.0, 104.0, 11.0, 106.0, java.io.File("test.region"))
        val bad = listOf("10.0,105.0;NaN,105.5", "10.0,105.0;10.5,999", "10.0,105.0;", "10.0,105.0")
        val file = kotlin.io.path.createTempFile("map", ".lines").toFile()
        try { bad.forEach { sample -> file.writeText(sample); try { MapRecordValidator.validate(region, file); fail("Accepted: $sample") } catch (_: IllegalArgumentException) { } } }
        finally { file.delete() }
    }
}
