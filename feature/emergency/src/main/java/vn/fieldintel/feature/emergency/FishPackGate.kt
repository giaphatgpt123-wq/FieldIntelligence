package vn.fieldintel.feature.emergency

/**
 * Release/import gate for a taxonomy SQLite that explicitly declares freshwater-fish coverage.
 * Generic plant/taxonomy packs that do not declare fishCoverageGateVersion are intentionally left
 * unchanged. A declared fish pack must prove both checklist coverage and locally cached media.
 */
object FishPackGate {
    const val BASELINE_REPORTED = 772L
    const val BASELINE_PRESENT = 736L

    fun validateIfDeclared(
        meta: Map<String, String>,
        hasLocalMediaTables: Boolean,
        observedFishTaxa: Long,
        observedFishTaxaWithLocalMedia: Long
    ) {
        if (!meta.containsKey("fishCoverageGateVersion")) return

        val version = requiredLong(meta, "fishCoverageGateVersion")
        require(version == 0L || version == 1L) { "Fish coverage gate version không được hỗ trợ: $version" }

        val reported = requiredLong(meta, "fishChecklistReported")
        val present = requiredLong(meta, "fishChecklistPresent")
        val review = requiredLong(meta, "fishChecklistReview")
        val excluded = requiredLong(meta, "fishChecklistExcluded")
        val resolved = requiredLong(meta, "fishPresentChecklistResolved")
        val unresolved = requiredLong(meta, "fishPresentChecklistUnresolved")
        val acceptedTaxa = requiredLong(meta, "fishPresentAcceptedTaxa")
        val acceptedWithMedia = requiredLong(meta, "fishPresentTaxaWithLocalMedia")
        val fishTaxa = requiredLong(meta, "fishTaxa")
        val fishWithLocalMedia = requiredLong(meta, "fishWithLocalMedia")

        require(reported >= BASELINE_REPORTED) {
            "Fish checklist thiếu dữ liệu: $reported < $BASELINE_REPORTED"
        }
        require(present >= BASELINE_PRESENT) {
            "Fish present checklist thiếu dữ liệu: $present < $BASELINE_PRESENT"
        }
        require(review >= 0L && excluded >= 0L) { "Fish checklist có số lượng âm" }
        require(reported == present + review + excluded) {
            "Fish checklist không nhất quán: $reported != $present + $review + $excluded"
        }
        require(resolved > 0L && unresolved >= 0L && resolved + unresolved == present) {
            "Fish checklist không nhất quán: resolved=$resolved, present=$present, unresolved=$unresolved"
        }
        require(acceptedTaxa in 1..resolved) {
            "Số accepted fish taxa không hợp lệ: $acceptedTaxa"
        }
        require(fishTaxa == acceptedTaxa) {
            "fishTaxa không khớp accepted taxa: $fishTaxa != $acceptedTaxa"
        }
        require(acceptedWithMedia in 0..acceptedTaxa) {
            "Số accepted fish taxa có ảnh không hợp lệ: $acceptedWithMedia/$acceptedTaxa"
        }
        require(fishWithLocalMedia in 1..fishTaxa && fishWithLocalMedia >= acceptedWithMedia) {
            "fishWithLocalMedia không hợp lệ: $fishWithLocalMedia/$fishTaxa"
        }
        require(hasLocalMediaTables) { "Fish pack thiếu bảng media offline bắt buộc" }
        require(observedFishTaxa == fishTaxa) {
            "Số taxon cá thực tế không khớp meta: $observedFishTaxa != $fishTaxa"
        }
        require(observedFishTaxaWithLocalMedia == fishWithLocalMedia) {
            "SQLite cá thực tế không khớp số ảnh offline: $observedFishTaxaWithLocalMedia/$fishWithLocalMedia"
        }
        if (version == 1L) {
            require(resolved == present && unresolved == 0L) { "Fish checklist chưa resolve đầy đủ" }
            require(acceptedWithMedia == acceptedTaxa && fishWithLocalMedia == fishTaxa) {
                "Fish coverage gate hoàn chỉnh nhưng thiếu ảnh offline"
            }
        }
    }

    private fun requiredLong(meta: Map<String, String>, key: String): Long =
        requireNotNull(meta[key]?.toLongOrNull()) { "Fish pack thiếu/không hợp lệ meta: $key" }
}
