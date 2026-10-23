# Total Music 1.4.0 — verification

Source baseline: main a54380862d9fe0549dee24422ac91f3dc522de2d (1.2.0/code 7).
No 1.3.0 branch was present in the fetched repository. This update preserves that baseline;
it does not claim to deliver unreleased album/deletion/metadata changes.

Implemented:
- Opt-in adaptive Liquid Glass navigation using the existing shared-transition tab bar.
  The compact state keeps the selected library destination, the mini-player and Playlists.
- Scroll threshold, reversible direction, expansion at list top, saved compact state,
  stable list padding during morph, existing safe navigation insets and transport callbacks.
- Lightweight tinted surface on Android <31 or with reduced dynamic blur; backdrop
  capture only on supported devices when blur is enabled.
- Opt-in Google player cover colors, scoped to the full player. Shared 128px quantizer,
  128-entry LRU, background processing, keyed cancellation, cached transitions and
  retention of the previous palette while an unknown cover loads. No cover/error
  returns to normal theme. AMOLED player background remains pure black.
- Existing reduce-animation/reduce-blur settings retained; no new appearance mode.

Verification:
- git diff --check: passed.
- New localized XML resources parsed: passed.
- scripts/release/check_release.py: passed.
- Local compile attempted: blocked before compilation, Gradle distribution download
  failed with Network is unreachable. No Android SDK/emulator is available locally.
- Added regression tests for visual preference defaults/persistence and navigation
  threshold, direction reversal, non-consumption and list-top expansion.
- JVM tests, Android lint and APK assembly NOT RUN. Git push lacked credentials;
  connected GitHub create_tree rejected writing with HTTP 403 Resource not accessible
  by integration. No remote commit or new Actions run was created.
- APK signing and stable Release NOT performed because there is no verified build.
- Physical-device checks pending: rapid skip, missing/monochrome covers, clear/dark/
  system switch, narrow/tablet navigation, gesture insets, long lists, scrolling while
  playing, artwork docking animation, background audio and equalizer regression.
- No FPS, memory or physical-device performance measurement has been made.

Do not publish a stable APK until the build passes and permanent signing/update
compatibility checks have completed. Use the signing workflow in TOTAL_MUSIC_RELEASES.md.
