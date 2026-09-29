package vn.duongodau.app.interop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalShareParserTest {
    @Test
    fun parsesGoogleMapsQueryCoordinates() {
        val parsed = ExternalShareParser.parse(
            "Đường thử nghiệm https://www.google.com/maps/search/?api=1&query=11.12345,106.54321"
        )
        assertNotNull(parsed)
        assertEquals(11.12345, parsed!!.latitude!!, 0.000001)
        assertEquals(106.54321, parsed.longitude!!, 0.000001)
        assertTrue(parsed.sourceType.contains("google_maps"))
    }

    @Test
    fun parsesGoogleMapsAtCoordinates() {
        val parsed = ExternalShareParser.parse(
            "https://www.google.com/maps/@10.7769,106.7009,17z"
        )
        assertNotNull(parsed)
        assertEquals(10.7769, parsed!!.latitude!!, 0.000001)
        assertEquals(106.7009, parsed.longitude!!, 0.000001)
    }

    @Test
    fun parsesWazeCoordinates() {
        val parsed = ExternalShareParser.parse(
            "https://www.waze.com/ul?ll=10.8001%2C106.6502&navigate=yes"
        )
        assertNotNull(parsed)
        assertEquals(10.8001, parsed!!.latitude!!, 0.000001)
        assertEquals(106.6502, parsed.longitude!!, 0.000001)
        assertEquals("waze", parsed.sourceType)
    }

    @Test
    fun parsesPlainCoordinateText() {
        val parsed = ExternalShareParser.parse("Cầu A 10.1234, 106.5678")
        assertNotNull(parsed)
        assertEquals("Cầu A", parsed!!.label)
        assertEquals(10.1234, parsed.latitude!!, 0.000001)
        assertEquals(106.5678, parsed.longitude!!, 0.000001)
    }

    @Test
    fun preservesPlainRoadTextWithoutInventingCoordinates() {
        val parsed = ExternalShareParser.parse("Đường liên xã A - B")
        assertNotNull(parsed)
        assertEquals("Đường liên xã A - B", parsed!!.label)
        assertNull(parsed.latitude)
        assertNull(parsed.longitude)
        assertEquals("plain_text", parsed.sourceType)
    }

    @Test
    fun blankInputReturnsNull() {
        assertNull(ExternalShareParser.parse("   "))
    }
}
