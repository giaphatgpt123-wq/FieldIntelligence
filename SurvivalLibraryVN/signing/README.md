# Test signing key

`survivallibrary-test.jks` is a dedicated **non-production** signing key for test APKs of `vn.survivallibrary.app`.

Purpose: keep the APK signing certificate stable across GitHub Actions runs so Android can update one test build over the next without the recurring `package conflicts with an existing package` / signature mismatch caused by a newly generated debug keystore on each CI runner.

Certificate SHA-256 fingerprint:
`F9:C4:A5:7A:C7:41:77:47:3D:AA:81:7E:8D:21:3A:27:B4:62:40:46:F7:95:FA:57:9E:C8:87:BC:C9:55:08:36`

This key is intentionally limited to internal/test APK distribution. It must never be reused for a Play Store or production release.

Migration note: APKs v0.1.0 and v0.2.0 were signed by ephemeral CI debug keys. A device that already has one of those builds installed must uninstall it once before installing v0.2.1. From v0.2.1 onward, test APK updates keep the same package and signing certificate and should install over the previous test build normally.
