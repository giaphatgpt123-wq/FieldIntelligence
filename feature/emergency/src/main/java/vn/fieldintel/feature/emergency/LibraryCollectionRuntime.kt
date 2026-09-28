package vn.fieldintel.feature.emergency

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

enum class LibraryCollectionRuntimeState {
    IDLE,
    LOADING,
    SQLITE_READY,
    FALLBACK
}

/**
 * Small runtime bridge between the validated specialist-evidence pack and taxonomy pack.
 *
 * It never creates medicinal/toxic labels from taxonomy. Instead it starts from an explicit
 * evidence domain, resolves canonical scientific names, then looks up taxonomy records. Loading
 * happens on a background thread and the published snapshot is observable by Compose.
 */
object LibraryCollectionRuntime {
    private var recordsByCollection by mutableStateOf<Map<String, List<SpeciesRecord>>>(emptyMap())
    var state by mutableStateOf(LibraryCollectionRuntimeState.IDLE)
        private set

    private val activeFingerprint = AtomicReference<String?>(null)

    fun recordsFor(collectionId: String): List<SpeciesRecord>? = recordsByCollection[collectionId]

    fun bind(context: Context, taxonomyStore: ScientificLibraryStore) {
        val appContext = context.applicationContext
        val evidenceFile = File(File(appContext.filesDir, SpecialistEvidenceStore.DIRECTORY_NAME), SpecialistEvidenceStore.DATABASE_NAME)
        val taxonomyFile = taxonomyStore.databasePath()
        val fingerprint = listOf(
            evidenceFile.absolutePath,
            evidenceFile.length().toString(),
            evidenceFile.lastModified().toString(),
            taxonomyFile.absolutePath,
            taxonomyFile.length().toString(),
            taxonomyFile.lastModified().toString()
        ).joinToString("|")

        if (!activeFingerprint.compareAndSet(null, fingerprint)) {
            val current = activeFingerprint.get()
            if (current == fingerprint) return
            activeFingerprint.set(fingerprint)
        }

        Snapshot.withMutableSnapshot {
            state = LibraryCollectionRuntimeState.LOADING
        }

        thread(name = "fieldintel-evidence-collections", isDaemon = true) {
            val evidenceStore = SpecialistEvidenceStore(appContext)
            // This code already runs on the dedicated loader thread, so use the blocking taxonomy
            // status check here. UI callers use ScientificLibraryStore.status(), which is async.
            val taxonomyReady = taxonomyStore.isInstalledBlocking()
            val evidenceReady = evidenceStore.status().installed && taxonomyReady
            val resolved = if (!taxonomyReady) {
                emptyMap()
            } else {
                val evidence = if (!evidenceReady) emptyMap() else LibraryCollections.evidenceCollections().associate { collection ->
                    val domain = requireNotNull(collection.evidenceDomain)
                    val names = evidenceStore.scientificNamesFor(domain, limit = 5000)
                    val external = taxonomyStore.findByScientificNames(names, limit = 1000)
                    val starter = LibraryCollections.starterRecordsFor(collection.id)
                    collection.id to (starter + external)
                        .distinctBy { it.scientificName.trim().lowercase() }
                        .sortedBy { it.scientificName.lowercase() }
                }
                val reviewed = LibraryCollections.reviewedCollections().associate { collection ->
                    collection.id to (taxonomyStore.reviewedCollectionRecords(collection.id) +
                        LibraryCollections.starterRecordsFor(collection.id))
                        .distinctBy { it.scientificName.split(' ').take(2).joinToString(" ").lowercase() }
                        .sortedBy { it.vietnameseName.lowercase() }
                }
                evidence + reviewed
            }

            Snapshot.withMutableSnapshot {
                recordsByCollection = resolved
                state = if (evidenceReady) {
                    LibraryCollectionRuntimeState.SQLITE_READY
                } else {
                    LibraryCollectionRuntimeState.FALLBACK
                }
            }
        }
    }
}
