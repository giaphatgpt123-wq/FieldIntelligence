package vn.duongodau.app.data

import android.content.Context
import vn.duongodau.app.core.AdminLevel
import vn.duongodau.app.core.AdminTransition
import vn.duongodau.app.core.AdminUnit
import vn.duongodau.app.core.VerificationState

/** Offline-first administrative repository for the current two-level system plus history. */
class AdminRepository(private val context: Context) {

    companion object {
        const val EXPECTED_PROVINCES = 34
        const val EXPECTED_COMMUNES = 3321
    }

    data class AdminDataState(
        val provinces: List<AdminUnit>,
        val communesByProvince: Map<String, List<AdminUnit>>,
        val transitions: List<AdminTransition>,
        val nationwideReady: Boolean,
        val sourceLabel: String
    )

    fun load(): AdminDataState {
        val current = runCatching { loadCurrentAsset() }.getOrNull()
        val transitions = runCatching { loadTransitionsAsset() }.getOrDefault(emptyList())
        val provinceCount = current?.first?.map { it.code }?.distinct()?.size ?: 0
        val communeCount = current?.second?.values?.flatten()?.map { it.code }?.distinct()?.size ?: 0
        val completeNationwide = provinceCount == EXPECTED_PROVINCES && communeCount == EXPECTED_COMMUNES

        return if (current != null && completeNationwide) {
            AdminDataState(
                provinces = current.first,
                communesByProvince = current.second,
                transitions = transitions,
                nationwideReady = true,
                sourceLabel = "QĐ 19/2025/QĐ-TTg – admin_current.csv ($provinceCount tỉnh, $communeCount xã/phường/đặc khu)"
            )
        } else {
            // Partial commune files are never silently promoted as nationwide data.
            AdminDataState(
                provinces = if (current?.first?.size == EXPECTED_PROVINCES) current.first else fallbackProvinces(),
                communesByProvince = current?.second.orEmpty(),
                transitions = transitions,
                nationwideReady = false,
                sourceLabel = if (current != null)
                    "Dữ liệu hành chính một phần: $provinceCount/34 tỉnh, $communeCount/3321 cấp xã"
                else
                    "QĐ 19/2025/QĐ-TTg – fallback 34 tỉnh/thành"
            )
        }
    }

    private fun loadCurrentAsset(): Pair<List<AdminUnit>, Map<String, List<AdminUnit>>> {
        val provinceMap = linkedMapOf<String, AdminUnit>()
        val communeMap = linkedMapOf<String, MutableList<AdminUnit>>()
        context.assets.open("admin_current.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val c = parseCsvLine(line)
                if (c.size < 4) return@forEach
                val pCode = c[0].trim().padStart(2, '0')
                val pName = c[1].trim()
                val cCode = c[2].trim().padStart(5, '0')
                val cName = c[3].trim()
                if (pCode.isBlank() || pName.isBlank() || cCode.isBlank() || cName.isBlank()) return@forEach

                provinceMap.putIfAbsent(
                    pCode,
                    AdminUnit(code = pCode, name = pName, level = AdminLevel.PROVINCE, effectiveFrom = "2025-07-01")
                )
                communeMap.getOrPut(pCode) { mutableListOf() }.add(
                    AdminUnit(
                        code = cCode,
                        name = cName,
                        level = AdminLevel.COMMUNE,
                        parentCode = pCode,
                        effectiveFrom = "2025-07-01",
                        isCurrent = true
                    )
                )
            }
        }
        return provinceMap.values.sortedBy { it.name } to
            communeMap.mapValues { (_, v) -> v.distinctBy { it.code }.sortedBy { it.name } }
    }

    private fun loadTransitionsAsset(): List<AdminTransition> {
        val output = mutableListOf<AdminTransition>()
        context.assets.open("admin_transitions.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val c = parseCsvLine(line)
                if (c.size < 5) return@forEach
                output += AdminTransition(
                    oldCode = c[0].trim(),
                    newCode = c[1].trim(),
                    effectiveFrom = c[2].trim(),
                    legalSource = c[3].trim(),
                    verification = runCatching { VerificationState.valueOf(c[4].trim()) }
                        .getOrDefault(VerificationState.UNVERIFIED)
                )
            }
        }
        return output
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    result += current.toString(); current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }

    /** Official province-level codes from Decision 19/2025/QĐ-TTg, effective 2025-07-01. */
    private fun fallbackProvinces(): List<AdminUnit> {
        val units = listOf(
            "01" to "Hà Nội", "04" to "Cao Bằng", "08" to "Tuyên Quang", "11" to "Điện Biên",
            "12" to "Lai Châu", "14" to "Sơn La", "15" to "Lào Cai", "19" to "Thái Nguyên",
            "20" to "Lạng Sơn", "22" to "Quảng Ninh", "24" to "Bắc Ninh", "25" to "Phú Thọ",
            "31" to "Hải Phòng", "33" to "Hưng Yên", "37" to "Ninh Bình", "38" to "Thanh Hóa",
            "40" to "Nghệ An", "42" to "Hà Tĩnh", "44" to "Quảng Trị", "46" to "Huế",
            "48" to "Đà Nẵng", "51" to "Quảng Ngãi", "52" to "Gia Lai", "56" to "Khánh Hòa",
            "66" to "Đắk Lắk", "68" to "Lâm Đồng", "75" to "Đồng Nai", "79" to "Thành phố Hồ Chí Minh",
            "80" to "Tây Ninh", "82" to "Đồng Tháp", "86" to "Vĩnh Long", "91" to "An Giang",
            "92" to "Cần Thơ", "96" to "Cà Mau"
        )
        return units.map { (code, name) ->
            AdminUnit(code, name, AdminLevel.PROVINCE, effectiveFrom = "2025-07-01", isCurrent = true)
        }
    }
}
