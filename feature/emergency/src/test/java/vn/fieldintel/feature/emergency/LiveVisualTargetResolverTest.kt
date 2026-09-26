package vn.fieldintel.feature.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveVisualTargetResolverTest {
    @Test
    fun vietnameseNameResolvesToCanonicalScientificTarget() {
        val target = LiveVisualTargetResolver.resolveStarter("Rau má")

        assertEquals("centella-asiatica", target.speciesId)
        assertEquals("Centella asiatica", target.scientificName)
    }

    @Test
    fun scientificNameWithoutAuthorityResolves() {
        val target = LiveVisualTargetResolver.resolveStarter("Curcuma longa")

        assertEquals("curcuma-longa", target.speciesId)
        assertEquals("Curcuma longa", target.scientificName)
    }

    @Test
    fun unknownFreeTextStaysUnresolvedInsteadOfGuessing() {
        val target = LiveVisualTargetResolver.resolveStarter("rau ABC")

        assertNull(target.speciesId)
        assertNull(target.scientificName)
        assertEquals("rau ABC", target.query)
    }
}
