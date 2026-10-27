# 1.8.0 validation

Rewritten 2026-10-27, after the second pass on this branch. It replaces both the
checkpoint file that shipped in the recovery patch (whose figures — 1018 tests,
four APKs, SDK 37 — describe a checkout that was never pushed) and the earlier
draft of this page.

**Nothing on this branch has been compiled or run.** What follows says exactly
what was executed, what it proves, and what it does not.

## Why there is no compile or device run

No JDK, no Android SDK, and no route to a Gradle distribution or a Maven
repository in this sandbox. A JDK *was* fetched from PyPI (Temurin 25) and given
to the wrapper, which then failed downloading Gradle itself. Exact commands,
hosts and stack traces: [BUILD_ENVIRONMENT.md](BUILD_ENVIRONMENT.md).

## What was executed

```
$ python3 scripts/verify/rebirth_check.py --base main
note: Kotlin parse: 567 file(s) checked, 0 broken, 15 pre-existing gap(s).
note: R references: 1604 checked.
note: Resource XML references: 164 checked.
note: Translation format arguments: 18577 translated string(s) compared.
note: Compose resource references: 242 checked.
note: Compose translations: 3230 translated string(s) compared.
note: Symbol resolution proxy: 31 file(s), 0 unresolved name(s).
note: Call signatures: 123 named-argument call(s) checked.
note: Hardcoded rebirth text: 0 literal(s) found.
note: Manifest components checked against declared classes.
note: Protected paths checked against main (105 changed file(s)).
OK: every static check passed.
```

What each check can and cannot catch:

| Check | Catches | Cannot catch |
| --- | --- | --- |
| Kotlin parse (tree-sitter) | unbalanced braces, malformed declarations, a file that stops parsing | everything semantic; 15 files already fail this grammar at `main` and are reported, not failed |
| R references | `R.string.x` with no resource | a wrong-but-existing resource |
| Resource XML references | `@font/x` in a layout with no font | attribute-level validity |
| Translation format arguments | a locale that drops `%1$s` (a runtime crash) | awkward translations |
| Compose resources | `Res.string.x` with no key | missing *usages* |
| Compose translations | argument mismatch per locale; missing keys | tone and grammar of 18 locales |
| Symbol resolution proxy | a capitalised name that is neither imported nor declared in its package — the missing-import class of error | types, overload resolution, receivers, generics |
| Call signatures | a named argument a function does not declare, or a required one omitted (including calls from `MainActivity`, `SettingsSheet`, `EqualizerScreen`, `SongActionsSheet`, `NowPlayingScreen`) | positional-argument type errors |
| Hardcoded text | a literal passed to `Text()`, `label =`, `title =` in the rebirth layer | literals inside resources |
| Manifest components | an `android:name` no class declares | runtime contract of the component |
| Protected paths | changes to `.github/workflows/`, `signing/`, `.signing/`, any `keystore` | — |

The symbol and signature proxies found two real defects on this pass: a missing
`Spacer` import in `RebirthPlayerAtoms.kt`, and a `DynamicCard`/`ManualCard`
call that had lost its `enabled` argument. Both are fixed; the checks that
found them now run on every commit.

## Acceptance criteria, one by one

"Static" = written, reviewed and covered by the gate above. It does **not**
mean observed running.

| Requirement | Where it lives | Status |
| --- | --- | --- |
| Playback from the library | `RebirthLocalExperience` → `onPlay` → `MainActivity.playFrom` | static |
| Search inside the library | `rebirthMatches`, `RebirthSearchField`, both scenes | unit-tested |
| Filtered multi-select | `rebirthFilteredSelection` | unit-tested |
| Selection → playlist | `DevicePlaylistPickerDialog` from the selection bar | static |
| Create / rename / delete playlist | `RebirthDialog` + `DevicePlaylistStore` | static |
| Library ordering | `rebirthSortedLibrary` + `LocalMusicSort` (all five, including track number) | unit-tested |
| Favourites | `RebirthCategory.FAVORITES` + `rebirthVisibleSongs` | unit-tested |
| Playlist reorder by drag | `RebirthQueueDrag` is for the queue; playlists move with the two arrows, which stay reachable under a screen reader | static |
| Queue reorder by drag | `RebirthQueueReorder.kt` + `RebirthQueuePage` — long-press a handle, carry, live swaps, edge auto-scroll; a per-row menu keeps the same moves reachable without a drag | static |
| Queue locked in a party | drag and menu items disabled, with the reason shown (`rebirth_queue_locked`) | static |
| Lyrics: tap to seek | `RebirthLyricsPage` | static |
| Lyrics: translate / romanize | `rememberLyricsTranslation` toggles | static |
| Lyrics: offset | `LyricsOffsetSheet`, opened from the lyric page; signed value shown when it is not zero | static |
| Lyrics: provider | `LyricsProviderSheet`, opened from the lyric page and from the tools sheet | static |
| Lyrics: sync state | reported on the lyric page (read-only: the player's settings interface has no setter) | static |
| Equalizer | `RebirthEqualizer`: dynamic, manual, Total EQ 10/31 bands, presets, saved profiles, preamp, limiter, parametric page | static |
| Settings | `RebirthSettings` home → the app's own groups, filtered to the opened one | static |
| Song actions from Home | `RebirthSongActions` + the app's action list | static |
| Output drawer, cast, pipeline | themed per player via `LocalPlayerStyle`: glass = dark pane of glass, expressive = opaque editorial page, original = unchanged | static |
| Google Expressive+ / Liquid Glass+ | separate compositions in library, player, mini player, navigation, settings, equalizer, song menu, and now the shared sheets | static |
| Original theme | `PlayerVisualStyle.ORIGINAL` never enters the rebirth layer; `LocalPlayerStyle` defaults to it | static |

## Unit tests

`app/src/test/java/com/music/bitchord/ui/rebirth/RebirthLibraryTest.kt` — 12
tests over the pure model: album order across discs, search matching, track
order vs scan order, date order, selection pruned by a query, the reorder guard,
the sort cycle returning to manual and covering every entry, favourites
filtering, an open collection overriding the category, saved playlists keeping
their own order, and gallery rows staying paired. **Not executed here** — there
is no JVM test runner in the sandbox. They are plain JUnit 4 tests with no
Android dependency, so `./gradlew :app:testDebugUnitTest` runs them anywhere.

## Known gaps

1. **No compiler.** 6 400 lines of Kotlin that no type checker has seen. This is
   the branch's largest risk and the first thing to fix elsewhere.
2. **No device pass.** All of these are unverified on hardware: narrow screens,
   200 % font scale, both Plus themes, Original, glass sampling, frame timing,
   low-memory behaviour, RTL (`values-he`).
3. **The player's own sheets are themed by one local, not redesigned.** The
   output drawer, the lyric provider sheet and the lyric offset sheet change
   material, corner and ink per style, and reuse the app's real controls — that
   was deliberate, since rewriting four sheets would have been four chances to
   lose a control. Their *internal layout* is still the original's.
4. **Narrow screens and large fonts were reviewed by reading, not by running.**
   What was fixed for them: the lyric control strip scrolls instead of clipping,
   the player-style chips scroll, the equaliser bands widen with the font scale
   (they were 44 dp of label space and wrapped), and the queue row's three
   30 dp action targets became one 42 dp menu whose items are full width.
5. **Queue drag is a second implementation**, not a refactor of the original's
   private one, which is welded to its tiered rows. Both use the same edge-scroll
   ramp (`edgeScrollSpeed`), so the pace matches; the gesture code is not shared.
6. **Playlists reorder with arrows only.** Drag on playlist rows is the next
   parity item; the arrows are a real path, not a placeholder.
7. **Second-level settings.** Accent picker, artwork colours and adaptive
   navigation rows are still legacy widgets inside a rebirth frame.

## Reproducing the gate

```
python3 -m venv /tmp/venv_ts
/tmp/venv_ts/bin/pip install tree-sitter tree-sitter-kotlin
/tmp/venv_ts/bin/python scripts/verify/rebirth_check.py --base main
```

Without tree-sitter the script still runs the resource, translation, signature,
manifest and protected-path checks; only the parse step is skipped, with a note
saying so.
