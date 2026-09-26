package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TfliteRegionModelRunnerTest {
    @Test
    fun parsesCommonAndScientificNamePair() {
        val parsed = ModelClassNameParser.parse("Rau má|Centella asiatica")
        assertEquals("Rau má", parsed.displayLabel)
        assertEquals("Centella asiatica", parsed.scientificName)
    }

    @Test
    fun treatsBinomialLabelAsScientificName() {
        val parsed = ModelClassNameParser.parse("Centella asiatica")
        assertEquals("Centella asiatica", parsed.displayLabel)
        assertEquals("Centella asiatica", parsed.scientificName)
    }

    @Test
    fun leavesOrdinaryDetectorLabelWithoutScientificClaim() {
        val parsed = ModelClassNameParser.parse("leafy plant")
        assertEquals("leafy plant", parsed.displayLabel)
        assertNull(parsed.scientificName)
    }
}
