package vn.local.queryrouter.core

import java.text.Normalizer

object TextNormalizer {
    fun normalize(input: String): String {
        val lowered = input.lowercase().trim()
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").replace('đ', 'd').replace(Regex("[^a-z0-9]+"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
