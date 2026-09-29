package vn.duongodau.app.interop

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin parser for content shared from map/navigation apps.
 * It never assumes a URL is valid road data; it only extracts a query/coordinate candidate.
 */
object ExternalShareParser {
    private val coordinateRegex = Regex("(?<!\\d)(-?(?:[0-8]?\\d(?:\\.\\d+)?|90(?:\\.0+)?))\\s*[, ]\\s*(-?(?:1[0-7]\\d(?:\\.\\d+)?|(?:[0-9]?\\d)(?:\\.\\d+)?|180(?:\\.0+)?))(?!\\d)")
    private val urlRegex = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val atCoordinateRegex = Regex("@(-?\\d{1,2}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?)")

    fun parse(text: String): ParsedExternalShare? {
        val clean = text.trim().take(4_000)
        if (clean.isBlank()) return null

        val firstUrl = urlRegex.find(clean)?.value?.trimEnd('.', ',', ';', ')', ']')
        if (firstUrl != null) {
            parseUrl(firstUrl)?.let { fromUrl ->
                return fromUrl.copy(label = chooseLabel(clean, firstUrl, fromUrl.label))
            }
        }

        coordinateRegex.find(clean)?.let { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lon = match.groupValues[2].toDoubleOrNull()
            if (validCoordinate(lat, lon)) {
                return ParsedExternalShare(
                    label = clean.removeRange(match.range).trim().ifBlank { "Vị trí được chia sẻ" }.take(500),
                    latitude = lat,
                    longitude = lon,
                    sourceType = "coordinate_text",
                    originalUrl = null
                )
            }
        }

        return ParsedExternalShare(
            label = clean.take(500),
            latitude = null,
            longitude = null,
            sourceType = "plain_text",
            originalUrl = null
        )
    }

    private fun parseUrl(url: String): ParsedExternalShare? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        val params = parseQuery(uri.rawQuery)

        if (host.contains("google.")) {
            atCoordinateRegex.find(uri.rawPath.orEmpty())?.let { m ->
                val lat = m.groupValues[1].toDoubleOrNull()
                val lon = m.groupValues[2].toDoubleOrNull()
                if (validCoordinate(lat, lon)) {
                    return ParsedExternalShare("Vị trí Google Maps", lat, lon, "google_maps", url)
                }
            }
            for (key in listOf("query", "destination", "q", "ll")) {
                parseCoordinateValue(params[key])?.let { (lat, lon) ->
                    return ParsedExternalShare(params["query"]?.takeUnless { looksLikeCoordinates(it) } ?: "Vị trí Google Maps", lat, lon, "google_maps", url)
                }
            }
            val label = params["query"] ?: params["q"] ?: params["destination"]
            if (!label.isNullOrBlank()) {
                return ParsedExternalShare(label.take(500), null, null, "google_maps", url)
            }
            return ParsedExternalShare("Liên kết Google Maps", null, null, "google_maps", url)
        }

        if (host.contains("waze.com")) {
            parseCoordinateValue(params["ll"])?.let { (lat, lon) ->
                return ParsedExternalShare("Vị trí Waze", lat, lon, "waze", url)
            }
            return ParsedExternalShare("Liên kết Waze", null, null, "waze", url)
        }

        parseCoordinateValue(params["ll"] ?: params["q"] ?: params["query"])?.let { (lat, lon) ->
            return ParsedExternalShare("Vị trí được chia sẻ", lat, lon, "web_map", url)
        }

        return ParsedExternalShare(
            label = host.ifBlank { "Liên kết được chia sẻ" }.take(500),
            latitude = null,
            longitude = null,
            sourceType = "web_link",
            originalUrl = url
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.isEmpty()) return@mapNotNull null
            val key = decode(parts[0]).lowercase()
            val value = decode(parts.getOrElse(1) { "" })
            key to value
        }.toMap()
    }

    private fun parseCoordinateValue(value: String?): Pair<Double, Double>? {
        if (value.isNullOrBlank()) return null
        val match = coordinateRegex.find(value) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        return if (validCoordinate(lat, lon)) lat!! to lon!! else null
    }

    private fun looksLikeCoordinates(value: String): Boolean = parseCoordinateValue(value) != null

    private fun validCoordinate(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun chooseLabel(fullText: String, url: String, parsedLabel: String): String {
        val withoutUrl = fullText.replace(url, " ")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        return when {
            withoutUrl.isNotBlank() -> withoutUrl.take(500)
            parsedLabel.isNotBlank() -> parsedLabel.take(500)
            else -> "Vị trí được chia sẻ"
        }
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}

data class ParsedExternalShare(
    val label: String,
    val latitude: Double?,
    val longitude: Double?,
    val sourceType: String,
    val originalUrl: String?
)
