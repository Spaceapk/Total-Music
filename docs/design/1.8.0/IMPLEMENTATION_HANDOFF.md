# Total Music 1.8.0 — implementation handoff

Status: the rebirth layer is written and statically verified, but **not
compiled and not run**. No release, no signing operation, no merge to `main`.
This file supersedes the earlier handoff written inside the lost ChatGPT
checkout; the parts it could not know (branch, commit, what the sandbox can
do) are recorded here instead.

## Where this work lives

* Base: `3dee549dbcda996ac94865d7b999fdacb399ee4a` (`main`).
* Branch: `arena/3b36059c-total-music` — the name is fixed by the Arena
  session that produced the work. The `feature/1.8.0-ui-rebirth` name the
  recovery instructions asked for is not available to that session, so the
  pull request is opened from this branch instead; renaming it later does not
  touch a single commit.
* Recovery commit: `0cdb83b0`, which is the base plus
  `recuperacion-1.8.0.patch` applied unmodified. A patch does not carry commit
  identity, so the original local commit (`98aec6bb…`) cannot be reproduced.
* The 403 that stopped the previous attempt was the integration token lacking
  `contents: write`; the token in this sandbox has it, and the recovery commit
  is on the remote.

## The layer, and why it is shaped this way

Two styles, one behaviour layer. What is shared is plumbing and labels; what
is not shared is layout.

### sharedUi — the player (`src/jvmSharedMain/.../ui/player/`)

| File | What it owns |
| --- | --- |
| `RebirthColors.kt` | `rebirthColors(style, base)` colour schemes and `RebirthSpec` — the corner radii, gutters and type scale of each style |
| `RebirthPlayer.kt` | the dispatcher: page state (`PLAYER`/`QUEUE`/`LYRICS`), the tools sheet, and the back order tools → page → screen |
| `RebirthExpressivePlayer.kt` | the expressive player: edge-to-edge sleeve, display-size title, wide play pill, action pills, up-next card |
| `RebirthGlassPlayer.kt` | the glass player: status row, segmented pages, centred sleeve ≤360 dp, capsule transport, three glass tiles, Listen Together pill |
| `RebirthPlayerAtoms.kt` | everything both need but neither should draw twice: `RebirthSeekBar`, `RebirthTransportRow`, `RebirthVolumeRow`, `RebirthSleeve`, `RebirthQueuePage`, `RebirthLyricsPage`, `RebirthUpNext` |
| `RebirthPlayerTools.kt` | "Playback tools" as a themed sheet: full-width rows in expressive, a two-column grid in glass. This is the surface that used to open the *legacy* player |
| `RebirthQueueReorder.kt` | drag-to-reorder for the queue: long-press a handle, carry the row, live swaps read off the layout, edge auto-scroll at the original's pace, and the arrows kept as the accessible path |
| `PlayerStyleLocal.kt` | `LocalPlayerStyle` — how the shared sheets (output, lyric providers, lyric offset, party members, cast and pipeline popups) learn which player opened them, and the per-style sheet ink, background, corner and title scale |

`NowPlayingScreen.kt` routes to it before the original player is composed and
keeps the ownership of the sheets that are still the app's: audio output,
audio pipeline, cast and lyrics-provider. The classic player is one row at the
foot of the tools sheet, labelled as such, not the contents of the button.

### app — the screens (`ui/rebirth/`)

| File | What it owns |
| --- | --- |
| `RebirthLibraryModel.kt` | pure model: `RebirthDestination`, `RebirthCategory`, `RebirthCollection`, `RebirthLibraryData`, `RebirthLibraryActions`, and the functions that decide things (order, match, filtered selection, reorder guard, gallery rows) |
| `RebirthLibrary.kt` | `RebirthLocalExperience`: one state holder, both compositions |
| `RebirthLibraryExpressive.kt` | editorial library: numbered rows, hero, metric tiles, collection gallery, playlist CRUD |
| `RebirthLibraryGlass.kt` | gallery library: adaptive rail (≥520 dp) or chip row, floating glass navigation, session cards |
| `RebirthDesign.kt` | the atoms both app-side scenes share: dialog, text field, search field, empty state, section header, filter pill, labels |
| `RebirthChrome.kt` | the two mini players and the expressive navigation bar |
| `RebirthSettings.kt` | the settings home in both styles: player style, the four effects, equalizer, and a door to every existing group |
| `RebirthEqualizer.kt` | the equalizer in both styles: dynamic/manual/Total EQ, presets, profiles, preamp, limiter, parametric page (`RebirthEqAdvanced.kt`) |
| `RebirthSongActions.kt` | the frame around the song menu's existing actions, in both styles |

### Wiring in `MainActivity.kt`

* `RebirthLocalExperience` replaces `LocalMusicScreen` on the Library tab and
  takes over the bottom of the screen while it is up (`rebirthRootVisible`
  suppresses the legacy bars).
  `onOpenGlobalSearch` and `onOpenExplore` leave for the app's own Search and
  Explore tabs, which the shell does not contain.
* The player's equalizer row now closes the player first
  (`onOpenEqualizer = { dismissPlayer(); showEqualizer = true }`), so the tool
  does not open a page behind the sheet it was tapped in.

## What changed relative to the 1.7.0 checkpoint

* The library is two compositions over one state, not one screen with a style
  flag.
* The player's tools sheet lists the app's real surfaces; the legacy player is
  reachable but is no longer what the button does.
* Settings and the equalizer have a rebirth home each, in both styles, with
  the second-level groups drawn by the legacy list *filtered to the group that
  was opened* — not by an escape hatch back to an unfiltered legacy page.
* The equalizer's advanced page (response plot, tone, stereo, parametric
  filters) is available in both styles and writes to the same DSP config.
* Every string the rebirth layer shows comes from a resource; the app-side
  additions are in `app/src/main/res/values{,-es}/total_music_180.xml`, the
  sixteen new player strings in `sharedUi/.../composeResources/values{,-es}`.
* `scripts/verify/rebirth_check.py` — the static gate this work was verified
  with, and the reason the next person does not have to guess what passed.

## Second pass (this branch, commits after `b4df136a`)

* **Queue drag.** `RebirthQueueReorder.kt` implements the gesture for the
  rebirth queue: the finger holds the row's centre, swaps are sent as it
  crosses half a neighbour, the held row is drawn over the list from the
  engine's own top so nothing jumps, and holding near an edge scrolls the list
  at the same ramp the original queue uses (`edgeScrollSpeed`). The up/down
  actions stay reachable without a drag through a per-row menu (move up, move
  down, remove), because a drag is not reachable by a screen reader. The party
  lock disables the menu's items and says why.
* **Lyric controls.** The lyric page now carries the rest of the original
  panel's controls: the offset sheet (signed value shown on the chip), the
  provider picker, and whether the player follows the words at all — read-only,
  because the player's settings interface exposes no setter for that one.
* **Themed sheets.** `AudioOutputSheet`, `LyricsProviderSheet`,
  `LyricsOffsetSheet` and `ListenTogetherMembersSheet` draw in the material of
  the player that opened them; `AudioPopupCard` (cast picker, pipeline readout)
  does the same, and its rows now take their ink from a local instead of
  `Color.White`. Original is untouched by all of it: `LocalPlayerStyle`
  defaults to `ORIGINAL` and every branch falls through to the old values.
* **Localization.** The 18 `rebirth_*` string keys exist in the default locale
  and in all 17 translations, with matching format arguments (checked).
* **Large fonts and narrow screens.** Reviewed by reading, and four things
  changed: the lyric control strip and the player-style chips scroll rather
  than clip; the equaliser's band labels widen with the font scale instead of
  wrapping into the fader; and the queue row's three 30 dp action targets
  became one 42 dp menu (the settings search keywords also learned the
  *localized* titles, since the search reads nothing else).
* **Static gate.** `scripts/verify/rebirth_check.py` grew compose-translation
  parity, a symbol-resolution proxy, a call-signature check and an
  import-package check. Between them they found and this branch fixes four real
  defects: a missing `Spacer` import, a lost `enabled` argument, an
  `EqualizerPreset` imported from a package that does not declare it, and a
  `Notes` icon taken from the non-mirrored Material package.

## Third pass: compiled on Actions, tests green

The branch is now built and tested in CI (`.github/workflows/total-music-180-compile.yml`),
because this sandbox cannot reach Gradle, Google or Maven. On commit `2882ae2b`:

* `:app:compileProdDebugKotlin` — success. Ten defects fixed to get there; the
  list is in [VALIDATION.md](VALIDATION.md), and eight of the ten were invisible
  to every static check.
* `:app:testProdDebugUnitTest` — 12/12 rebirth tests pass. One had been red and
  the test was right: the model sorted a track with no disc tag after every
  disc, so disc 2 of an untagged set came first. `rebirthAlbumOrder` now
  delegates to the shared `List<Song>.inAlbumOrder()`.
* `:app:assembleProdDebug` — APK produced, debug-signed, uploaded as an
  artifact. Not a release and not an installable update over the release build.

Also in this pass:

* **Playlist rows drag.** `RebirthPlaylistDrag` writes nothing until the drop:
  a playlist is persisted on every move, so live swaps would write orders
  nobody asked for and a cancelled drag would leave a half-way list. The two
  arrows and the close button became one row menu (up, down, remove), which is
  both narrower and reachable with a screen reader.
* **Playback tools** still open the real surfaces; the sheets now draw in the
  player's own material (see the third-pass note in the table above).

## Remaining work, in the order it should be done

1. **Device pass.** The checklist below. This is now the only way to learn what
   the compiler cannot: whether the two drags feel right, whether the glass
   samples, whether 200 % text still fits.
2. **Sheet internals.** The player's sheets match their player's material; their
   *layouts* are still the original's. The larger one is the pipeline readout,
   whose signal graph is still drawn for a dark pane even though its ink now
   follows the theme.
3. **Localisation scope.** The 18 player strings are in all 17 locales. The
   library, settings and equalizer strings in `res/values/total_music_180.xml`
   are English and Spanish only; the file says so at the top.
4. **Second-level settings.** Accent picker, artwork colours and adaptive
   navigation rows are still legacy widgets inside a rebirth frame.

## Device checklist (nothing here has been run)

- [ ] Library tab: songs, albums, artists, playlists, favourites; sort menu
      changes the order and survives a rotation.
- [ ] Search inside the library; select three rows; clear the search — the
      count drops with the hidden rows.
- [ ] Add the selection to a playlist; create, rename and delete one.
- [ ] Open a playlist, drag a row by its handle: the row follows the finger and
      lands once on release; the menu (up, down, remove) does the same three
      things without a drag.
- [ ] Open a playlist, reorder two tracks, then type in search: the handle
      disappears, the menu's moves go disabled, and the reason is shown.
- [ ] Play from Home; the mini player follows the queue; expanding opens the
      sound system you selected.
- [ ] Playback tools → output, pipeline, cast, lyrics provider, equalizer,
      classic player.
- [ ] Lyrics page: tap a line to seek; translation and romanisation toggles;
      the offset chip opens the sheet and shows the signed value after.
- [ ] Lyrics page → provider chip opens the picker; tools sheet → same picker.
- [ ] Queue page: drag a row by its handle to a new position; the row follows
      the finger, edges auto-scroll, and the party hears one reorder on drop.
- [ ] Queue page: the row menu moves the same row up and down; its items are
      disabled in a party, and the reason is on screen.
- [ ] Queue page: jump, remove, clear added.
- [ ] Equalizer: dynamic, manual, Total EQ 10/31 bands, a saved profile, the
      advanced page.
- [ ] At 200 % font scale: library header, settings home, equalizer rows and
      the song menu still scroll, and nothing is clipped out of reach.
- [ ] Switch to Original: everything above behaves exactly as `main`, including
      the sheets' colours.

## Files changed from `main`

Regenerate rather than trust this list:

```
git diff --name-status main...HEAD
git status --short
```
