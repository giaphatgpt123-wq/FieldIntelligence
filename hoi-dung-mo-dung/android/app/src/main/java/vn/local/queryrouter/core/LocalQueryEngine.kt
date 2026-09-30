package vn.local.queryrouter.core

object LocalQueryEngine {
    private val moneyRegex = Regex("(?<!\\d)(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,9})(?:\\s*(?:d|đ|dong|nghin|ngan|trieu))?")
    private val phoneRegex = Regex("(?:\\+?84|0)\\d{8,10}")
    private val codeRegex = Regex("\\b[A-Z0-9]{6,20}\\b", RegexOption.IGNORE_CASE)
    private val tax03Regex = Regex("(?:trang thai|ma|mst)?\\s*0?3\\b")

    fun resolve(raw: String, extraFunctions: List<FunctionNode> = emptyList()): QueryResult {
        val q = TextNormalizer.normalize(raw)
        if (q.isBlank()) return QueryResult(ConfidenceState.NO_MATCH, shortAnswer = "Hãy nhập nội dung cần tra cứu.")
        val entities = extract(raw, q)
        val allFunctions = LocalKnowledge.functions + extraFunctions
        val ranked = allFunctions.map { fn -> fn to score(q, fn) }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (ranked.isEmpty()) return QueryResult(ConfidenceState.NO_MATCH, entities = entities, shortAnswer = "Chưa xác định được chức năng phù hợp trong thư viện cục bộ.")
        val best = ranked.first(); val next = ranked.getOrNull(1)
        if (next != null && best.second - next.second <= 1 && best.second < 5) {
            return QueryResult(ConfidenceState.AMBIGUOUS, best.first, entities, "Có nhiều khả năng phù hợp với yêu cầu này.", "Hãy chọn đúng việc bạn muốn thực hiện.", ranked.take(3).map { it.first }, sources(best.first))
        }
        return postProcess(best.first, entities, q)
    }

    private fun sources(fn: FunctionNode) = LocalKnowledge.sourcesFor(fn)
    private fun score(q: String, fn: FunctionNode): Int {
        var score = 0
        val title = TextNormalizer.normalize(fn.title)
        if (q == title) score += 8
        if (q.contains(title) || title.contains(q)) score += 3
        fn.keywords.forEach { rawKeyword ->
            val kw = TextNormalizer.normalize(rawKeyword)
            if (q == kw) score += 7 else if (q.contains(kw)) score += when { kw.length >= 14 -> 5; kw.length >= 7 -> 3; else -> 1 }
        }
        if (fn.id == "tax_status_03" && tax03Regex.containsMatchIn(q)) score += 6
        if (fn.id == "mobile_topup" && (q.split(' ').contains("nap") || phoneRegex.containsMatchIn(q))) score += 2
        if (fn.id == "etc_topup" && (q.contains("etc") || q.contains("thu phi") || q.contains("vetc") || q.contains("epass"))) score += 8
        if (fn.id == "mobile_topup" && (q.contains("etc") || q.contains("thu phi") || q.contains("vetc") || q.contains("epass"))) score -= 6
        if (fn.id == "eye_hospital" && (q.contains("mat") || q.contains("nhan khoa"))) score += 4
        if (fn.id == "health_facility" && (q.contains("benh vien") || q.contains("phong kham"))) score += 2
        return score
    }

    private fun extract(raw: String, q: String): QueryEntities {
        val money = moneyRegex.find(raw)?.value
        val phone = phoneRegex.find(raw.replace(" ", ""))?.value
        val code = codeRegex.find(raw)?.value
        val location = listOf("tp hcm", "ho chi minh", "ha noi", "da nang", "can tho", "hue", "hai phong", "dong nai", "tay ninh", "an giang", "ca mau", "khanh hoa").firstOrNull { q.contains(it) }
        val specialty = when { q.contains("mat") || q.contains("nhan khoa") -> "Mắt/Nhãn khoa"; q.contains("tim mach") -> "Tim mạch"; q.contains("nhi") -> "Nhi"; q.contains("san") -> "Sản"; q.contains("da lieu") -> "Da liễu"; q.contains("rang ham mat") -> "Răng Hàm Mặt"; else -> null }
        val taxStatus = if (tax03Regex.containsMatchIn(q)) "03" else null
        val organizationType = when { q.contains("doanh nghiep") -> "Doanh nghiệp"; q.contains("ho kinh doanh") -> "Hộ/cá nhân kinh doanh"; q.contains("ca nhan") -> "Cá nhân"; else -> null }
        return QueryEntities(money, phone, code, taxStatus, location, specialty, organizationType)
    }

    private fun postProcess(fn: FunctionNode, entities: QueryEntities, q: String): QueryResult {
        val refs = sources(fn)
        if (fn.dataStatus != DataStatus.CURRENT && refs.isNotEmpty()) {
            return QueryResult(ConfidenceState.NEED_MORE_INFO, fn, entities, "Đã nhận diện đúng nội dung, nhưng dữ liệu căn cứ trong thư viện chưa được xác minh là hiện hành.", "Cần nạp/cập nhật gói dữ liệu chính thức trước khi trả lời kết luận.", sourceRefs = refs)
        }
        return when (fn.id) {
            "water_payment" -> if (entities.customerCode == null && !q.contains("ma kh") && !q.contains("ma khach hang")) QueryResult(ConfidenceState.NEED_MORE_INFO, fn, entities, "Bạn muốn thanh toán tiền nước.", "Nhập mã khách hàng. Nếu mã không đủ nhận diện, app sẽ hỏi Tỉnh/Thành và đơn vị cấp nước.", sourceRefs = refs) else QueryResult(ConfidenceState.CONFIRMED, fn, entities, "Đã xác định yêu cầu thanh toán tiền nước. App sẽ đối chiếu mã bằng dữ liệu local trước khi chọn kênh thực hiện.", sourceRefs = refs)
            "mobile_topup" -> if (entities.phone == null || entities.money == null) QueryResult(ConfidenceState.NEED_MORE_INFO, fn, entities, "Bạn muốn nạp tiền điện thoại.", when { entities.phone == null && entities.money == null -> "Cần số điện thoại và số tiền cần nạp."; entities.phone == null -> "Cần số điện thoại nhận tiền."; else -> "Cần số tiền muốn nạp." }, sourceRefs = refs) else QueryResult(ConfidenceState.CONFIRMED, fn, entities, "Đã nhận diện yêu cầu nạp ${entities.money} cho ${entities.phone}. App chỉ tìm kênh phù hợp và mở ứng dụng giao dịch chính chủ.", sourceRefs = refs)
            "eye_hospital" -> QueryResult(if (entities.location == null) ConfidenceState.NEED_MORE_INFO else ConfidenceState.CONFIRMED, fn, entities, "Bạn đang tìm cơ sở y tế chuyên khoa Mắt${entities.location?.let { " tại $it" } ?: ""}.", if (entities.location == null) "Chọn Tỉnh/Thành để thu hẹp kết quả." else null, sourceRefs = refs)
            "tax_close" -> if (entities.organizationType == null) QueryResult(ConfidenceState.NEED_MORE_INFO, fn, entities, "Bạn đang muốn chấm dứt hiệu lực mã số thuế.", "Chọn đối tượng: Cá nhân, Hộ/cá nhân kinh doanh hoặc Doanh nghiệp.", sourceRefs = refs) else QueryResult(ConfidenceState.CONFIRMED, fn, entities, "Đã xác định đối tượng: ${entities.organizationType}. Tiếp tục đối chiếu trạng thái và thủ tục tương ứng trong thư viện local.", sourceRefs = refs)
            "traffic_fine" -> QueryResult(ConfidenceState.NEED_MORE_INFO, fn, entities, "Bạn đang xử lý tình huống phạt nguội.", "Chọn việc cần làm: Xem lỗi vi phạm, Xem mức phạt, Nơi xử lý, Nộp phạt hoặc Kiểm tra đã hoàn tất.", sourceRefs = refs)
            else -> QueryResult(ConfidenceState.CONFIRMED, fn, entities, fn.description, sourceRefs = refs)
        }
    }
}
