package vn.fieldintel.feature.emergency

/** Runtime state for camera-based search in the surrounding environment. */
enum class LiveVisualSearchPhase {
    IDLE,
    REQUESTING_CAMERA,
    SCANNING,
    MODEL_NOT_READY,
    CANDIDATE,
    STABLE_CANDIDATE,
    UNKNOWN,
    ERROR
}

data class LiveVisualSearchTarget(
    val query: String,
    val speciesId: String? = null,
    val scientificName: String? = null
) {
    val normalizedQuery: String = query.trim()
    val isValid: Boolean get() = normalizedQuery.length >= 2
}

data class LiveFrameInfo(
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

data class LiveVisualCandidate(
    val label: String,
    val scientificName: String? = null,
    val score: Float? = null,
    val stableFrames: Int = 0,
    val evidenceReady: Boolean = false
)

data class LiveVisualSearchUiState(
    val phase: LiveVisualSearchPhase = LiveVisualSearchPhase.IDLE,
    val target: LiveVisualSearchTarget? = null,
    val lastFrame: LiveFrameInfo? = null,
    val candidate: LiveVisualCandidate? = null,
    val message: String = "Nhập loài cần tìm rồi bắt đầu quét."
)

/**
 * Conservative state reducer for live visual search.
 *
 * Camera frames alone never produce a taxon, edibility, toxicity or treatment conclusion.
 * A future verified model runner must explicitly submit candidates into this reducer.
 */
object LiveVisualSearchReducer {
    fun start(target: LiveVisualSearchTarget, modelReady: Boolean): LiveVisualSearchUiState {
        require(target.isValid) { "Mục tiêu tìm kiếm phải có ít nhất 2 ký tự" }
        val resolvedTarget = if (target.speciesId == null && target.scientificName == null) {
            LiveVisualTargetResolver.resolveStarter(target.query)
        } else {
            target
        }
        val targetMessage = resolvedTarget.scientificName?.let { " Mục tiêu chuẩn hóa: $it." }.orEmpty()
        return if (modelReady) {
            LiveVisualSearchUiState(
                phase = LiveVisualSearchPhase.SCANNING,
                target = resolvedTarget,
                message = "Đang quét môi trường xung quanh. Lia camera chậm và giữ đủ sáng.$targetMessage"
            )
        } else {
            LiveVisualSearchUiState(
                phase = LiveVisualSearchPhase.MODEL_NOT_READY,
                target = resolvedTarget,
                message = "Camera có thể quét trực tiếp, nhưng mô hình nhận dạng đã kiểm chứng chưa được cài. Không tạo kết quả giả.$targetMessage"
            )
        }
    }

    fun frame(state: LiveVisualSearchUiState, frame: LiveFrameInfo): LiveVisualSearchUiState =
        state.copy(lastFrame = frame)

    fun candidate(
        state: LiveVisualSearchUiState,
        candidate: LiveVisualCandidate,
        stableThreshold: Int = 4
    ): LiveVisualSearchUiState {
        require(stableThreshold >= 2)
        val stable = candidate.stableFrames >= stableThreshold
        return state.copy(
            phase = if (stable) LiveVisualSearchPhase.STABLE_CANDIDATE else LiveVisualSearchPhase.CANDIDATE,
            candidate = candidate,
            message = if (stable) {
                "Phát hiện ứng viên ổn định qua nhiều khung hình. Cần đối chiếu chi tiết trước khi xác nhận."
            } else {
                "Phát hiện ứng viên. Tiếp tục lia chậm để kiểm tra qua nhiều khung hình."
            }
        )
    }

    fun unknown(state: LiveVisualSearchUiState, reason: String): LiveVisualSearchUiState = state.copy(
        phase = LiveVisualSearchPhase.UNKNOWN,
        candidate = null,
        message = reason.ifBlank { "Chưa đủ bằng chứng để nhận dạng." }
    )
}
