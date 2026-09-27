package vn.fieldintel.feature.emergency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificNameResolverTest {
    @Test
    fun canonicalBinomialMatchesParenthesizedAuthorship() {
        assertTrue(
            ScientificNameResolver.matchesCanonical(
                "Channa striata",
                "Channa striata (Bloch, 1793)"
            )
        )
    }

    @Test
    fun canonicalBinomialMatchesUnparenthesizedAuthorship() {
        assertTrue(
            ScientificNameResolver.matchesCanonical(
                "Anabas testudineus",
                "Anabas testudineus Bloch, 1792"
            )
        )
    }

    @Test
    fun exactAuthoredNameStillMatches() {
        assertTrue(
            ScientificNameResolver.matchesCanonical(
                "Channa striata (Bloch, 1793)",
                "Channa striata (Bloch, 1793)"
            )
        )
    }

    @Test
    fun canonicalBinomialDoesNotCrossIntoSubspecies() {
        assertFalse(
            ScientificNameResolver.matchesCanonical(
                "Channa striata",
                "Channa striata siamensis"
            )
        )
    }

    @Test
    fun canonicalBinomialDoesNotMatchDifferentSpecies() {
        assertFalse(
            ScientificNameResolver.matchesCanonical(
                "Channa striata",
                "Channa micropeltes"
            )
        )
    }

    @Test
    fun genusOnlyDoesNotExpand() {
        assertFalse(
            ScientificNameResolver.matchesCanonical(
                "Channa",
                "Channa striata"
            )
        )
    }
}
