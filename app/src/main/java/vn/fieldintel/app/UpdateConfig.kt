package vn.fieldintel.app

object UpdateConfig {
    const val MANIFEST_URL =
        "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/download/data-latest/manifest.json"
    const val PACKAGE_URL =
        "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/download/data-latest/offline-map-2.pack"

    val configured: Boolean
        get() = MANIFEST_URL.startsWith("https://") && PACKAGE_URL.startsWith("https://")
}
