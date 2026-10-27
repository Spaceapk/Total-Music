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

## Remaining work, in the order it should be done

1. **Compile.** `./gradlew :app:compileDebugKotlin` on a machine with a JDK,
   the Android SDK and Maven access. The parse gate has already been through
   every file, so expect unresolved references rather than syntax errors. See
   `VALIDATION.md` for why the sandbox could not do this.
2. **Port the two player panes that are still re-implementations.** The
   rebirth queue moves entries with arrows, not by dragging, and the rebirth
   lyric page has no line picker and no offset sheet. Both exist in the legacy
   player (`InlineQueue`, `LyricsPanel`, `LyricsOffsetSheet`); they are
   `internal` in the same module, so either lift them into the rebirth scenes
   or move the shared machinery behind an interface both use.
3. **Restyle the shared drawers.** `AudioOutputSheet`, `CastDialog` and
   `AudioPipelineDialog` are the legacy `PlayerDrawer` — right for the glass
   style, foreign in the expressive one.
4. **Translate the twelve player strings** into the fifteen other Compose
   locales. The static gate prints the list on every run.
5. **Finish the second-level settings screens.** The appearance group's
   accent picker, artwork colours and adaptive-navigation rows are still
   legacy widgets inside a rebirth frame.
6. **Device pass.** Narrow screens, 200 % font scale, both Plus themes and
   Original, playlist reorder under a search, and a song menu opened from the
   Home list.

## Device checklist (nothing here has been run)

- [ ] Library tab: songs, albums, artists, playlists, favourites; sort menu
      changes the order and survives a rotation.
- [ ] Search inside the library; select three rows; clear the search — the
      count drops with the hidden rows.
- [ ] Add the selection to a playlist; create, rename and delete one.
- [ ] Open a playlist, reorder two tracks, then type in search: the arrows
      disappear and the reason is shown.
- [ ] Play from Home; the mini player follows the queue; expanding opens the
      sound system you selected.
- [ ] Playback tools → output, pipeline, cast, lyrics provider, equalizer,
      classic player.
- [ ] Lyrics page: tap a line to seek; translation and romanisation toggles.
- [ ] Queue page: jump, move, remove, clear added.
- [ ] Equalizer: dynamic, manual, Total EQ 10/31 bands, a saved profile, the
      advanced page.
- [ ] Switch to Original: everything above behaves exactly as `main`.

## Files changed from `main`

Regenerate rather than trust this list:

```
git diff --name-status main...HEAD
git status --short
```
