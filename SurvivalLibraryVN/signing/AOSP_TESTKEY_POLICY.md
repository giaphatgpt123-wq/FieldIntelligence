# Stable beta signing policy

Beta APKs from v0.2.1 onward are signed with Android's public AOSP development `testkey`. This gives the test channel a stable signing certificate across GitHub Actions runs without storing a private production key in this public repository.

This signing method is **development/testing only**. AOSP test keys are public and must never be used for a production or Play Store release.

Migration: v0.1.0 and v0.2.0 were built with ephemeral CI debug signing. v0.2.1 also moves to application id `vn.survivallibrary.vn`, so it can be installed without colliding with the old beta package `vn.survivallibrary.app`. The old beta can then be removed manually. Future beta releases must keep both the new application id and the same AOSP test signing certificate.
