package vn.fieldintel.feature.emergency

import java.text.Normalizer
import java.util.Locale

/** Resolves a typed target to a canonical starter-catalog taxon only when the match is unambiguous. */
object LiveVisualTargetResolver {
    fun resolveStarter(query: String): LiveVisualSearchTarget {
        val trimmed = query.trim()
        if (trimmed.length < 2) return LiveVisualSearchTarget(trimmed)
        val needle = normalize(trimmed)
        val exact = SpeciesCatalog.records.filter { record ->
            normalize(record.vietnameseName) == needle ||
                normalize(record.scientificName) == needle ||
                normalize(stripAuthority(record.scientificName)) == needle
        }
        if (exact.size != 1) return LiveVisualSearchTarget(trimmed)
        val record = exact.single()
        return LiveVisualSearchTarget(
            query = trimmed,
            speciesId = record.id,
            scientificName = stripAuthority(record.scientificName)
        )
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd')
        .trim()

    /**
     * Keeps the binomial/trinomial name used for strict model identity matching while dropping
     * nomenclatural authorship, which visual models generally do not emit.
     */
    private fun stripAuthority(scientificName: String): String {
        val tokens = scientificName.trim().split(Regex("\\s+"))
        if (tokens.size < 2) return scientificName.trim()
        val genus = tokens[0]
        val species = tokens[1]
        val third = tokens.getOrNull(2)
        return if (third != null && third.firstOrNull()?.isLowerCase() == true) {
            "$genus $species $third"
        } else {
            "$genus $species"
        }
    }
}
