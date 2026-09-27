package vn.fieldintel.app

object UpdateConfig {
    const val MANIFEST_URL =
        "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/download/data-latest/manifest.json"
    const val PACKAGE_URL =
        "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/download/data-latest/offline-map.pack"

    const val LIBRARY_MANIFEST_URL =
        "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/download/scientific-library-latest/library-manifest.json"
    const val LIBRARY_PACKAGE_URL =
        "https://github.com/giaphatgpt123-wq/FieldIntelligence/releases/download/scientific-library-latest/scientific-library.zip"

    val configured: Boolean
        get() = false // Releases of this private repository require authentication; never embed credentials in the APK.
}
