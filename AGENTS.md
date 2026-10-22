# Total Music maintenance

The maintained GitHub integration is `main` in
`Spaceapk/Total-Music`. GitLab `nexlibre-group/total-music` remains a backup.
Preserve the source history, including ancestor
`369a1f68e80dae6780d317e82d226bff06d37b01`. Do not rebuild the app from scratch,
force-push, or overwrite the Space Music branches.

Read `docs/TOTAL_MUSIC_RELEASES.md` before preparing an APK. Keep
`org.nexlibre.totalmusic` and the pinned signing certificate unchanged.
`total-music-release.properties` is the single release version source.
Use `scripts/release/prepare_version.py` to increase both version fields.

Run GitHub Actions `total-music-android.yml` for this integration. Codemagic
remains available as a fallback. CI produces a clearly labeled debug test APK and an unsigned
release ZIP; that ZIP is not a user-installable delivery. Sign locally in Work
using `scripts/release/sign_apk.py` and verify the pinned certificate and hash.
For all releases after 1.0.2, supply the previous permanent-key-signed APK.
Never publish or deliver an unsigned/debug APK as a stable update.

The permanent encrypted keystore and its separate password file are private
user-owned Library files named `Total-Music-release-signing.p12` and
`Total-Music-release-signing-password.txt`. Retrieve them through the Library
skill, materialize outside the checkout with mode 0600, and use file-based
password input. Never print their contents, commit them, place them in CI,
attach them to a release, or send them to a third party without explicit
authorization. If either is unavailable, stop signing; do not generate a new key.

Deliver the signed APK and store a ZIP backup in the existing Google Drive
`02 - APK compiladas` folder. Keep source and checksums traceable to the build.
Preserve previous releases for update compatibility checks. Do not promise
device testing unless a device/emulator was actually used.

The first permanent-key release cannot replace the old debug-signed 1.0.1.
Do not uninstall anything on the user's device. Explain the one-time backup
and replacement requirement; later stable releases must update in place.
