# Total Music 1.8.0 — implementation handoff

Status: INCOMPLETE; not approved as a complete UI rebirth. No release, signing operation or main merge performed.

## Branch and recovery

Local branch: `feature/1.8.0-ui-rebirth`.
Base: `3dee549dbcda996ac94865d7b999fdacb399ee4a`.
Recovery commit: `05bd70f0` (restores the existing Library checkpoint, including recovered 1.7.0 sources).
Run `git rev-parse HEAD` after applying/importing the work to record the checkout actually tested. A plain patch does not preserve commit identities.

Remote branch creation via the Spaceapk integration was rejected with HTTP 403, "Resource not accessible by integration". No remote branch or PR was created. Do not treat the local commit as remotely available. No alternative write path was used to bypass this denial.

## New work after the recovered checkpoint

- RebirthEqualizer retains the new theme while exposing dynamic/manual modes, manual presets, ten/thirty-one graphic bands, named profile save/delete, preamp and limiter.
- RebirthEqAdvanced writes to the existing TotalMusicSettings DSP configuration: response plot, auto headroom, bass/treble, balance, width, crossfeed and up to eight parametric filters. Frequency sliders use logarithmic mapping. No audio engine changes.
- RebirthPlayer includes a lyrics page with line seeking using the stored offset, native system-volume binding, output picker and Listen Together entry points. Queue, lyrics and playback surfaces preserve transport callbacks. Back exits compatibility tools.
- Expressive mini-player now uses its own composition; Glass retains a compact cover capsule.
- Glass home song menus now invoke the real menu callback. Its session card starts the displayed song. Filtered collection rows are memoized. Playlist reorder controls are unavailable during search; the store also rejects incomplete reorder lists.

## Known unfinished work

- Player "Herramientas de reproducción" still opens the legacy player to preserve advanced features (provider selection, detailed synchronized lyrics controls and other compatibility features). It must be replaced with themed tools without losing callbacks before calling the redesign complete.
- Detailed information dialogs, output/cast drawers and some secondary settings still reuse legacy presentations. Settings and EQ need further independent layout work, especially narrow screens and large fonts.
- Text localization for newly authored Spanish strings remains pending.
- Glass sampling, theme toggles, artwork overlays, restored navigation, low-memory behavior, device rendering and frame timing have not been validated here.
- Remote commit publication and PR creation remain blocked.

## Validation in this workspace

`git diff --check`: passed.
All 30 changed XML files parsed successfully with Python ElementTree.
No changes under `.signing/` or `.github/workflows/` relative to the base.
`bash gradlew :app:compileDebugKotlin --console=plain`: blocked before Kotlin compilation; the Gradle distribution download fails with `java.net.SocketException: Network is unreachable`. This workspace has Java 17, no provisioned Android SDK or dependency cache. No unit tests or device tests were run here.
Historical compilation/test claims in RECOVERY.md and VALIDATION.md describe the earlier checkpoint only, not these changes.

## Arena continuation

Recover this local branch/checkpoint, provision the pinned toolchain, compile first and fix any compiler findings. Then finish the compatibility surfaces listed above, exercise both themes and Original with full function regression coverage, and validate the UI on-device. Packaging remains Arena's responsibility. Keep original signing material unchanged; do not publish or merge without user authorization.

## Files changed from main

- `CHANGELOG.md`
- `README.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/assets/release-notes.json`
- `app/src/main/java/com/music/bitchord/MainActivity.kt`
- `app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt`
- `app/src/main/java/com/music/bitchord/ui/components/SongActionsSheet.kt`
- `app/src/main/java/com/music/bitchord/ui/player/AndroidPlayerHost.kt`
- `app/src/main/java/com/music/bitchord/ui/rebirth/RebirthEqualizer.kt`
- `app/src/main/java/com/music/bitchord/ui/rebirth/RebirthLibrary.kt`
- `app/src/main/java/com/music/bitchord/ui/rebirth/RebirthMiniPlayer.kt`
- `app/src/main/java/com/music/bitchord/ui/rebirth/RebirthSettings.kt`
- `app/src/main/java/com/music/bitchord/ui/rebirth/RebirthSongActions.kt`
- `app/src/main/java/com/music/bitchord/ui/screens/EqualizerScreen.kt`
- `app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt`
- `app/src/main/java/com/music/bitchord/ui/theme/ArtworkPlayerTheme.kt`
- `app/src/main/java/com/music/bitchord/widget/MediaWidget.kt`
- `app/src/main/java/com/music/bitchord/widget/MediaWidgetArt.kt`
- `app/src/main/java/com/music/bitchord/widget/MediaWidgetPill.kt`
- `app/src/main/java/com/music/bitchord/widget/MediaWidgetPreferences.kt`
- `app/src/main/java/com/music/bitchord/widget/WidgetConfigActivity.kt`
- `app/src/main/res/drawable/widget_band_dark.xml`
- `app/src/main/res/drawable/widget_band_glass.xml`
- `app/src/main/res/drawable/widget_band_glass_dark.xml`
- `app/src/main/res/drawable/widget_band_light.xml`
- `app/src/main/res/values-b+zh+Hant/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/main/res/values-es/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-he/strings.xml`
- `app/src/main/res/values-hi/strings.xml`
- `app/src/main/res/values-id/strings.xml`
- `app/src/main/res/values-it/strings.xml`
- `app/src/main/res/values-ja/strings.xml`
- `app/src/main/res/values-pl/strings.xml`
- `app/src/main/res/values-pt/strings.xml`
- `app/src/main/res/values-ru/strings.xml`
- `app/src/main/res/values-th/strings.xml`
- `app/src/main/res/values-tr/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/main/res/values-zh-rHK/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/xml/widget_media_dynamic_art.xml`
- `app/src/main/res/xml/widget_media_glass_panel.xml`
- `app/src/main/res/xml/widget_media_immersive.xml`
- `app/src/main/res/xml/widget_media_pill.xml`
- `app/src/main/res/xml/widget_media_square.xml`
- `app/src/main/res/xml/widget_media_wide.xml`
- `app/src/test/java/com/music/bitchord/ui/rebirth/RebirthLibraryTest.kt`
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPlayerHost.kt`
- `docs/design/1.8.0/RECOVERY.md`
- `docs/design/1.8.0/VALIDATION.md`
- `docs/verification-1.7.0.md`
- `sharedUi/build.gradle.kts`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/NowPlayingScreen.kt`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerControls.kt`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerHost.kt`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerVisualStyle.kt`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/RebirthColors.kt`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/RebirthPlayer.kt`
- `sharedUi/src/jvmTest/kotlin/com/music/bitchord/ui/player/PlayerVisualStyleTest.kt`
- `total-music-release.properties`
- `app/src/main/java/com/music/bitchord/ui/rebirth/RebirthEqAdvanced.kt`
- `docs/design/1.8.0/IMPLEMENTATION_HANDOFF.md`
