package vn.fieldintel.app

object UpdateConfig {
    // Centralized endpoint configuration. Replace only these values when the
    // distribution host is ready; runtime code must not hard-code endpoints.
    const val MANIFEST_URL = ""
    const val PACKAGE_URL = ""

    val configured: Boolean
        get() = MANIFEST_URL.startsWith("https://") && PACKAGE_URL.startsWith("https://")
}
