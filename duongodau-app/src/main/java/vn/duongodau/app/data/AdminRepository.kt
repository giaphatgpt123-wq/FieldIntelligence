package vn.duongodau.app.data

import android.content.Context
import vn.duongodau.app.core.AdminLevel
import vn.duongodau.app.core.AdminTransition
import vn.duongodau.app.core.AdminUnit
import vn.duongodau.app.core.VerificationState

/**
 * Offline-first administrative repository.
 *
 * Preferred assets:
 * - admin_current.csv: province_code,province_name,commune_code,commune_name
 * - admin_transitions.csv: old_code,new_code,effective_from,legal_source,verification
 *
 * If the full commune asset is not bundled yet, the app falls back to the 34 current
 * province-level units and explicitly reports nationwideReady=false.
 */
class AdminRepository(private val context: Context) {

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

        return if (current != null && current.second.values.sumOf { it.size } > 0) {
            AdminDataState(
                provinces = current.first,
                communesByProvince = current.second,
                transitions = transitions,
                nationwideReady = true,
                sourceLabel = "admin_current.csv"
            )
        } else {
            AdminDataState(
                provinces = fallbackProvinces(),
                communesByProvince = emptyMap(),
                transitions = transitions,
                nationwideReady = false,
                sourceLabel = "fallback_34_provinces"
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
                val pCode = c[0].trim()
                val pName = c[1].trim()
                val cCode = c[2].trim()
                val cName = c[3].trim()
                if (pCode.isBlank() || pName.isBlank() || cCode.isBlank() || cName.isBlank()) return@forEach

                provinceMap.putIfAbsent(
                    pCode,
                    AdminUnit(code = pCode, name = pName, level = AdminLevel.PROVINCE)
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

    private fun fallbackProvinces(): List<AdminUnit> {
        val names = listOf(
            "An Giang", "Bắc Ninh", "Cà Mau", "Cao Bằng", "Cần Thơ", "Đà Nẵng",
            "Đắk Lắk", "Điện Biên", "Đồng Nai", "Đồng Tháp", "Gia Lai", "Hà Nội",
            "Hà Tĩnh", "Hải Phòng", "Huế", "Hưng Yên", "Khánh Hòa", "Lai Châu",
            "Lâm Đồng", "Lạng Sơn", "Lào Cai", "Nghệ An", "Ninh Bình", "Phú Thọ",
            "Quảng Ngãi", "Quảng Ninh", "Quảng Trị", "Sơn La", "Tây Ninh",
            "Thái Nguyên", "Thanh Hóa", "Thành phố Hồ Chí Minh", "Tuyên Quang", "Vĩnh Long"
        )
        return names.mapIndexed { index, name ->
            AdminUnit(
                code = "P${(index + 1).toString().padStart(2, '0')}",
                name = name,
                level = AdminLevel.PROVINCE,
                effectiveFrom = "2025-07-01",
                isCurrent = true
            )
        }
    }
}
