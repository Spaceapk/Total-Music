# 1.8.0 validation

Rewritten 2026-10-27, after the branch was compiled for the first time. It
replaces the checkpoint file that shipped in the recovery patch (whose figures
— 1018 tests, four APKs, SDK 37 — describe a checkout that was never pushed).

## Compilation: real, on Actions, green

The sandbox this work was written in has no JDK, no Android SDK and no route to
Gradle, Google or Maven ([BUILD_ENVIRONMENT.md](BUILD_ENVIRONMENT.md)), so the
first compile happens in CI:

**Run:** https://github.com/Spaceapk/Total-Music/actions/runs/40448078644
**Commit:** `2882ae2b`
**Workflow:** `.github/workflows/total-music-180-compile.yml` (validation only;
publishes nothing)

| Step | Result |
| --- | --- |
| `:app:compileProdDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `:app:testProdDebugUnitTest --tests 'com.music.bitchord.ui.rebirth.*'` | ✅ 12/12 passed |
| `:app:assembleProdDebug` | ✅ APK produced |

The repository's own `Total Music Android verification` workflow also runs on
this pull request (shared JVM tests, the full app unit-test suite, lint and the
unsigned release build); it exercises the same tree from the other direction.

### What the compiler found, and what that says about the static gate

Ten defects, none of which static analysis had caught, all fixed:

| # | Defect | Caught by |
| --- | --- | --- |
| 1 | `padding(horizontal = …, bottom = …)` — no such overload | compiler |
| 2 | `Res.string.listen_together` used without importing the generated property | compiler |
| 3 | same for `Res.string.more` | compiler |
| 4 | `roundToInt` without `kotlin.math.roundToInt` | compiler |
| 5 | `lyrics_loading_lines` is a string-array, used as a string | compiler |
| 6 | the drag state's private edge fields assigned from a composable | compiler |
| 7 | `LocalAudioPopupInk.current` read inside a `DrawScope` | compiler |
| 8 | `Icons.AutoMirrored.Rounded.ArrowBack` without its import | compiler |
| 9 | `EqualizerPreset` imported from `data.settings`; it lives in `playback` | the import check (added in this branch) |
| 10 | `Notes` taken from the non-mirrored icon package | the import check |

Two of the ten were found by a static check written after the fact. The other
eight are why **compiling is not optional**: a parser, a symbol grep and a
signature check between them still cannot see overloads, composable
context or resource *kinds*.

## Unit tests

`app/src/test/java/com/music/bitchord/ui/rebirth/RebirthLibraryTest.kt` — 12
tests over the pure model. **All 12 pass on CI** (commit `2882ae2b`).

One of them was red on the first run, and the test was right: the model
sorted a track with no disc tag after every disc, so disc 2 of a set whose
first disc is untagged came out first. The rule already exists in
`com.music.bitchord.data.model.AlbumOrdering` (`List<Song>.inAlbumOrder()`:
missing disc means disc one, a track with no number goes last), and the album
screen uses it. `rebirthAlbumOrder` now delegates to it instead of keeping a
near-copy, and the tests pin the shared answer.

## The APK

From the same run:

```
app-prod-universal-debug.apk
sha256  909bfe59c9e6cdeb96adc63bbb0d0f8f61c8f86b6d440ff67c5ba708af8451ba
```

**Signature: the Android debug certificate** — `CN=Android Debug`,
cert SHA-256 `ce5dbc1f99cca2ea2add43846e72bbda6cd307d080b19b8c0c5e593003c7d368`.

It is a `prodDebug` build: debuggable, not minified, not a release, and **not
installable over a build signed with the release key** — the signatures differ,
so Android will refuse to update and the app has to be uninstalled first. It is
not a compatible update to an installed Total Music, and it is not presented as
one. The release keystore is untouched by all of this; this workflow has no
access to it and asks for none.

Artifact: `total-music-180-test-apk-<sha>` on the run page, which also carries
`ci-logs/` (the Gradle logs, the signature block above) and the JUnit reports.

## Static gate (a supplement, not a substitute)

```
$ python3 scripts/verify/rebirth_check.py --base main
Kotlin parse: 568 file(s) checked, 0 broken, 15 pre-existing gap(s).
R references: 1614 checked.
Resource XML references: 164 checked.
Translation format arguments: 18578 translated string(s) compared.
Compose resource references: 242 checked.
Compose resource imports: 37 property use(s) checked.
Compose translations: 3230 translated string(s) compared.
Symbol resolution proxy: 32 file(s), 0 unresolved name(s).
Call signatures: 126 named-argument call(s) checked.
Import packages: 784 import(s) checked against the tree.
Hardcoded rebirth text: 0 literal(s) found.
Manifest components checked against declared classes.
Protected paths checked against main (109 changed file(s)).
OK: every static check passed.
```

It is still worth running before every push — it takes two seconds and the
import check has already earned its keep — but everything in the previous
paragraphs is what proves the branch builds.

## Acceptance criteria

"Built" means the check above compiled the code; none of it has been *run*.

| Requirement | Where it lives | Status |
| --- | --- | --- |
| Playback from the library | `RebirthLocalExperience` → `onPlay` | built, unit-tested model |
| Search inside the library | `rebirthMatches`, `RebirthSearchField` | built, unit-tested |
| Filtered multi-select | `rebirthFilteredSelection` | built, unit-tested |
| Selection → playlist | `DevicePlaylistPickerDialog` | built |
| Create / rename / delete playlist | `RebirthDialog` + `DevicePlaylistStore` | built |
| Library ordering | `rebirthSortedLibrary` + shared `inAlbumOrder()` | built, unit-tested |
| Favourites | `RebirthCategory.FAVORITES` + `rebirthVisibleSongs` | built, unit-tested |
| Playlist reorder by drag | `RebirthPlaylistDrag` (one move on drop) + row menu | built, not exercised |
| Queue reorder by drag | `RebirthQueueReorder` + `RebirthQueuePage` | built, not exercised |
| Queue locked in a party | drag and menu items disabled, reason on screen | built |
| Lyrics: tap to seek, translate, romanize | `RebirthLyricsPage` | built |
| Lyrics: offset, provider, sync state | offset sheet, provider sheet, read-only sync note | built |
| Equalizer | `RebirthEqualizer` (dynamic, manual, 10/31 bands, presets, preamp, limiter, parametric) | built |
| Settings | `RebirthSettings` → the app's own groups, filtered | built |
| Song actions from Home | `RebirthSongActions` + the app's action list | built |
| Output drawer, cast, pipeline | themed per player via `LocalPlayerStyle` | built |
| Google Expressive+ / Liquid Glass+ | separate compositions in library, player, mini player, navigation, settings, equalizer, song menu, sheets | built |
| Original theme | never enters the rebirth layer | built |

## Known gaps

1. **No device pass.** Nothing has been seen running: narrow screens, 200 %
   font scale, both Plus themes, Original, RTL (`values-he`), glass sampling,
   frame timing, install-and-update behaviour.
2. **The two drags are compiled, not exercised.** Neither has a test that moves
   a finger. The playlist drag writes nothing until the drop, so a bug there
   cannot corrupt a playlist; the queue drag sends each swap as it happens,
   which is what the player's queue needs.
3. **Player sheets share material, not layout.** `AudioOutputSheet`,
   `LyricsProviderSheet`, `LyricsOffsetSheet`, `CastDialog` and
   `AudioPipelineDialog` draw in the material of the player that opened them;
   their internal layout is still the original's. The pipeline graph now takes
   its ink from the theme, which it needed to be visible on the expressive
   page at all.
4. **App-side rebirth strings are English and Spanish only.** The 18 strings
   the *player* uses live in Compose resources and are translated into all 17
   locales. The library/settings/equalizer strings live in
   `res/values/total_music_180.xml` with `values-es` only; the other locales
   fall back to English, as the file's header says.
5. **Second-level settings.** Accent picker, artwork colours and adaptive
   navigation rows are still legacy widgets inside a rebirth frame.
