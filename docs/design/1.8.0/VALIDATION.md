# 1.8.0 validation

Written 2026-10-27 in the recovery sandbox that applied
`recuperacion-1.8.0.patch`. It replaces the previous VALIDATION.md, whose
figures (1018 tests, four APKs, SDK 37) belong to a checkout that was never
pushed and cannot be reproduced here. Nothing in this file is quoted from that
one.

## What the sandbox could and could not do

| Tool | State | Evidence |
| --- | --- | --- |
| JDK | absent | `java`, `javac`, `kotlinc`: `command not found`; no `/usr/lib/jvm` |
| Android SDK | absent | no `sdkmanager`, `adb`; no `ANDROID_HOME`; `apt-get download android-sdk` finds no package |
| Gradle | unusable | wrapper is 8.11.1 and needs a distribution; `services.gradle.org` and the GitHub mirror are unreachable |
| Maven Central | unreachable | `repo1.maven.org` fails TLS (`curl (35) SSL_ERROR_SYSCALL`) |
| Google Maven | unreachable | `dl.google.com` fails the same way; without it there is no AGP and no Compose artifact |
| PyPI | reachable | `pip download jdk4py` succeeded (a JDK is obtainable, an Android SDK is not) |
| npm | reachable | `kotlin-compiler` on npm is Kotlin/JS, not the JVM compiler |
| GitHub | reachable | clone/fetch/`gh` work; release *assets* do not (`release-assets.githubusercontent.com`) |

So: **`./gradlew` has never been run on this tree**, no APK exists, and no
emulator or device has booted. That is a fact about the sandbox, not a choice,
and no result below is offered as a substitute for it.

## What was actually run

```
$ /tmp/venv_ts/bin/python scripts/verify/rebirth_check.py --base main
note: Kotlin parse: 565 file(s) checked, 0 broken, 15 pre-existing gap(s).
note: R references: 1604 checked.
note: Resource XML references: 164 checked.
note: Translation format arguments: 18577 translated string(s) compared.
note: Compose resource references: 234 checked.
note: Hardcoded rebirth text: 0 literal(s) found.
note: Manifest components checked against declared classes.
note: Protected paths checked against main (68 changed file(s)).
OK: every static check passed.
```

What each line does:

* **Kotlin parse** — every `.kt` under `app/src`, `shared/src` and
  `sharedUi/src` is parsed with tree-sitter-kotlin. The 15 files that fail are
  the ones that already fail at `main`: the grammar does not read some of the
  advanced syntax this codebase uses (multi-interface `by` delegations,
  context receivers, some annotation targets). The check compares against the
  base, so only a file that *newly* fails is reported.
* **R references** — every `R.string.*`, `R.drawable.*`, `R.font.*`, … in the
  app module resolves to a resource in `app/src/main/res` (including binary
  directories such as `font/`, which is why the check is not XML-only).
  `android.R.string.*` is excluded.
* **Resource XML references** — `@string/…`, `@drawable/…`, `@font/…` inside
  `res/` resolve, including the four widget layouts.
* **Translation format arguments** — for all 18 577 translated strings, the
  set of `%1$s`-style specifiers matches the default. A translation that drops
  an argument crashes at runtime, and this is the only way to see that without
  a phone.
* **Compose resources** — every `Res.string.*` in `sharedUi` has a key in
  `composeResources/values`. The twelve `rebirth_*` keys added by this branch
  are reported as untranslated outside the default locale; that is deliberate
  (see "Known gaps").
* **Hardcoded text** — no string literal in `ui/rebirth/*.kt` or
  `ui/player/Rebirth*.kt` is passed to `Text(`, `text =`, `label =`, `title =`
  or `message =`.
* **Manifest components** — every activity/service/receiver/provider declared
  with an app-relative `android:name` maps to a class some Kotlin file
  declares. Vendored `androidx.*` components are skipped.
* **Protected paths** — nothing under `.github/workflows/`, `signing/`,
  `.signing/` or any `keystore` path changed. `total-music-release.properties`
  is reported rather than refused: it carries the 1.8.0 version number, which
  the patch legitimately bumps, and it is printed as a note.

## Acceptance criteria, one by one

"Static" means the code path was read and the static gate covers its
resources; it does **not** mean the behaviour was observed.

| Requirement | Where it lives now | Status |
| --- | --- | --- |
| Playback from the library | `RebirthLocalExperience` → `onPlay` → `MainActivity.playFrom` | wired, uncompiled |
| Search inside the library | `rebirthMatches`, `RebirthSearchField`, both scenes | unit-tested (`RebirthLibraryTest`) |
| Filtered multi-select | `rebirthFilteredSelection`, selection caps in `RebirthLibraryModel` | unit-tested |
| Multi-select → add to playlist | `DevicePlaylistPickerDialog` from the selection bar | wired, uncompiled |
| Create / rename / delete playlist | `RebirthDialog` + `DevicePlaylistStore.create/rename/delete` | wired, uncompiled |
| Track ordering (library) | `rebirthSortedLibrary` + `LocalMusicSort` | unit-tested |
| Favourites | `RebirthCategory.FAVORITES` + `rebirthVisibleSongs` | unit-tested |
| Playlist reorder | `onMoveInPlaylist` → `DevicePlaylistStore.move`; `rebirthCanReorder` blocks it under a query and says why | unit-tested (guard), device pending |
| Lyrics | `RebirthLyricsPage` (tap-to-seek, translation/romanisation via `rememberLyricsTranslation`) | static |
| Queue | `RebirthQueuePage` (jump, move, remove, clear) | static |
| Equalizer | `RebirthEqualizer` (`dynamic`, `manual`, Total EQ with 10/31 bands, parametric page) | static, resource-checked |
| Settings | `RebirthSettings` home → the app's own groups, filtered by section | static |
| Song actions from Home | `RebirthSongActions` header + the app's action list | static |
| Google Expressive+ and Liquid Glass+ | separate compositions in library, player, mini player, navigation, settings, equalizer, song menu | static |
| Original theme | never routed through the rebirth layer (`PlayerVisualStyle.ORIGINAL` early-returns) | unchanged from `main` |

## Known gaps, with what would close them

1. **No compilation.** Highest risk in this branch: 5 900 lines of Kotlin that
   no compiler has seen. The parse gate proves syntax, not types.
   *Next step:* run `./gradlew :app:compileDebugKotlin` on a machine with a
   JDK, the Android SDK and Maven access. Expect “unresolved reference”
   errors, not syntax errors; the parser has already been through everything.
2. **No device run.** Narrow screens, large font scales and the two Plus
   themes in motion are unverified. *Next step:* the device checklist in
   IMPLEMENTATION_HANDOFF.md.
3. **Twelve `rebirth_*` strings only exist in the default Compose locale.**
   English is the fallback, so nothing is broken; it is untranslated, not
   wrong.
4. **Player panes are re-implementations.** The rebirth queue uses up/down
   arrows rather than the original's drag reorder, and the rebirth lyric page
   does not yet offer the original's line picker or the offset sheet. Both are
   listed in the handoff as the next porting step rather than hidden behind
   the "classic player" row.
5. **Output, cast and pipeline sheets are still the legacy `PlayerDrawer`.**
   They are frosted and shaped like the glass style and look out of place in
   the expressive one.

## Reproducing the static gate

```
python3 -m venv /tmp/venv_ts
/tmp/venv_ts/bin/pip install tree-sitter tree-sitter-kotlin
/tmp/venv_ts/bin/python scripts/verify/rebirth_check.py --base main
```

Without the two packages the script still runs and reports the resource,
translation, manifest and protected-path checks; only the parse step is
skipped, with a note saying so.
