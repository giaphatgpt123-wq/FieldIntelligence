package vn.duongodau.app.interop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface InteropResult {
    data class Opened(val target: String) : InteropResult
    data class Failed(val message: String) : InteropResult
}

object AppInterop {
    fun openGoogleMaps(context: Context, latitude: Double, longitude: Double, label: String): InteropResult {
        val encoded = Uri.encode(label)
        val native = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encoded)"))
            .setPackage("com.google.android.apps.maps")
        val generic = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encoded)"))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
        )
        return openWithFallback(context, "Google Maps", native, generic, web)
    }

    fun openWaze(context: Context, latitude: Double, longitude: Double): InteropResult {
        val native = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$latitude,$longitude&navigate=yes"))
            .setPackage("com.waze")
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.waze.com/ul?ll=$latitude%2C$longitude&navigate=yes")
        )
        return openWithFallback(context, "Waze", native, web)
    }

    fun openBrowser(context: Context, url: String): InteropResult {
        if (!url.startsWith("https://")) return InteropResult.Failed("Chỉ cho phép liên kết HTTPS.")
        return openWithFallback(context, "Trình duyệt", Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun shareRoad(context: Context, roadName: String, latitude: Double?, longitude: Double?): InteropResult {
        val location = if (latitude != null && longitude != null) "\nTọa độ: $latitude,$longitude" else ""
        val deepLink = buildDeepLink(roadName, latitude, longitude)
        val text = "Đường: $roadName$location\nMở bằng Đường ở đâu: $deepLink"
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            val chooser = Intent.createChooser(share, "Chia sẻ từ Đường ở đâu")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            InteropResult.Opened("Chia sẻ")
        } catch (e: Exception) {
            InteropResult.Failed("Không mở được chức năng chia sẻ: ${e.message ?: "không xác định"}")
        }
    }

    fun buildDeepLink(roadName: String, latitude: Double?, longitude: Double?): String {
        val builder = Uri.Builder()
            .scheme("duongodau")
            .authority("road")
            .appendQueryParameter("name", roadName)
        latitude?.let { builder.appendQueryParameter("lat", it.toString()) }
        longitude?.let { builder.appendQueryParameter("lon", it.toString()) }
        return builder.build().toString()
    }

    fun parseIncoming(intent: Intent?): IncomingPayload? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "duongodau") {
            val data = intent.data ?: return null
            return IncomingPayload(
                roadName = data.getQueryParameter("name").orEmpty(),
                latitude = data.getQueryParameter("lat")?.toDoubleOrNull(),
                longitude = data.getQueryParameter("lon")?.toDoubleOrNull(),
                source = "deep_link"
            )
        }
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (text.isBlank()) return null
            return IncomingPayload(roadName = text.take(500), latitude = null, longitude = null, source = "share")
        }
        return null
    }

    private fun openWithFallback(context: Context, target: String, vararg intents: Intent): InteropResult {
        intents.forEach { intent ->
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return InteropResult.Opened(target)
            } catch (_: ActivityNotFoundException) {
                // Continue to the next compatible target.
            } catch (_: SecurityException) {
                // Continue to a less specific target.
            }
        }
        return InteropResult.Failed("Không có ứng dụng phù hợp để mở $target.")
    }
}

data class IncomingPayload(
    val roadName: String,
    val latitude: Double?,
    val longitude: Double?,
    val source: String
)
