package vn.fieldintel.feature.emergency

import android.content.Context

/**
 * Resolves high-risk library collections from the validated specialist-evidence pack and then
 * links those scientific names back to the taxonomy pack. This keeps medicinal/toxic labels
 * evidence-driven while still returning normal SpeciesRecord items for the library UI.
 */
class EvidenceBackedCollectionResolver(context: Context) {
    private val appContext = context.applicationContext
    private val evidenceStore = SpecialistEvidenceStore(appContext)
    private val taxonomyStore = ScientificLibraryStore(appContext)

    fun isReady(): Boolean = evidenceStore.status().installed && taxonomyStore.status().installed

    fun recordsFor(collection: LibraryCollection, limit: Int = 500): List<SpeciesRecord> {
        val starter = LibraryCollections.recordsFor(collection.id)
        val domain = collection.evidenceDomain ?: return starter
        if (!isReady()) return starter

        val names = evidenceStore.scientificNamesFor(domain, limit)
        if (names.isEmpty()) return starter
        val external = taxonomyStore.findByScientificNames(names, limit)

        return (starter + external)
            .distinctBy { it.scientificName.trim().lowercase() }
            .sortedBy { it.scientificName.lowercase() }
    }

    fun countFor(collection: LibraryCollection, limit: Int = 5000): Int {
        val domain = collection.evidenceDomain ?: return collection.recordIds.size
        if (!isReady()) return collection.recordIds.size
        return evidenceStore.scientificNamesFor(domain, limit).size
    }
}
