# 1.8.0 draft validation

This remains an incomplete redesign checkpoint, not an official release or an accepted complete UI rebirth.

## Current evidence

- Restored source compiles against the pinned Android/Kotlin dependencies.
- Current Android unit results: 1019 total, 1018 passed, 1 skipped, 0 failures, 0 errors.
- A subsequent compilation exposed duplicate generated common Compose resource declarations. Disabling incremental compilation only within sharedUi allowed recompilation and tests to pass. App/data modules remain incremental; dependency versions and runtime behavior are unchanged.
- Packaging with android.injected.build.abi=x86_64 failed in AGP PackageAndroidArtifact.kt:317 while mapping stale split outputs. Building all configured ABIs without that injection succeeded. Keep split output selection consistent; do not distribute mixed intermediate artifacts.
- Four internal debug APKs were generated. AAPT confirmed org.nexlibre.totalmusic, versionName 1.8.0 and versionCode 14 in all four, including universal. ZIP integrity passed.
- These use the Android debug signature and are ONLY for an isolated emulator. They are not stable updates and are not suitable for replacing the user's original-key-signed installation.
- The original signing key has not been retrieved or changed. Nothing was merged into main or officially published.

## Still pending

- Complete advanced player/equalizer presentation, detail-dialog parity and all remaining secondary screens.
- Actual device screenshots, concept-board fidelity, narrow widths, large fonts, navigation, overlay regression checks and all effects toggles.
- Widget/lock-screen visual and functional review. Existing six widget implementations and the Media3 session were preserved from 1.7.0.
- Device frame/memory measurements and full runtime regression review.
- Release-mode compilation and original-key signing only when the draft is ready for a genuine test delivery.

The API 35 software emulator did not complete startup within ten minutes. A minimal API 33 AOSP test image was installed as a fallback; there has not yet been a successful app run or screenshot. Do not claim device testing from compilation or unit results.

The GitHub integration previously denied creating the remote design branch with HTTP 403. This branch is local; the unsynced patch is backed up separately. Preserve that identity when updating the checkpoint.
