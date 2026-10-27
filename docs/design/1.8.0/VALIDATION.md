# 1.8.0 validation

Rewritten 2026-10-27. It replaces the checkpoint file that shipped in the
recovery patch (whose figures — 1018 tests, four APKs, SDK 37 — describe a
checkout that was never pushed).

## What is proven here, and what is not

| Question | Answer |
| --- | --- |
| Does the whole app compile? | **Yes** — `:app:compileProdDebugKotlin` and `:app:assembleProdRelease` are green on Actions (see below). |
| Do the tests pass? | **Yes** — every unit test in the tree, not just the rebirth subset. |
| Is there an APK? | **Yes** — a debug-signed test APK and the unsigned release APK, in the run's artifact. |
| Can it update an installed Total Music? | **No.** It is not signed with the permanent key, which this environment does not have. See *Signing*. |
| Is the redesign complete? | **No.** The player's sheets still use the previous internal layout (see *Known gaps*). |

## The build

**Workflow:** `.github/workflows/total-music-180-compile.yml` — validation only:
no release, no tag, no push, no signing key, and the static gate fails it if it
ever grows one.

**Run:** https://github.com/Spaceapk/Total-Music/actions/runs/40491761559
**Commit:** `be1778b`

| Step | What it covers |
| --- | --- |
| `:app:compileProdDebugKotlin` | every module's Kotlin, the resource merger and the Compose compiler plugin — the whole app |
| `:app:assembleProdDebug` | the installable test APK |
| `:app:assembleProdRelease` | the minified release build (R8), left unsigned |
| `:shared:jvmTest` + `:app:testProdDebugUnitTest` | every unit test in the repository |
| `scripts/release/package_ci.py` | identity of both APKs against `total-music-release.properties`, certificate check, test counts, `SHA256SUMS`, `build-verification.json` |

The repository's own `Total Music Android verification` workflow runs on the
same pull request as well: shared JVM tests, the full app suite, **Android
lint** (`:app:lintProdDebug`, `:app:lintVitalProdRelease`) and the unsigned
release build. Both have to be green for the branch to count as building.

## Version and signature

From `total-music-release.properties`:

```
applicationId      org.nexlibre.totalmusic
versionName        1.8.0
versionCode        14
certificateSha256  9ae145a7a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa
```

| Artifact | Signed with | Installable? |
| --- | --- | --- |
| `Total-Music-1.8.0-test-only-debug-signed.apk` | the runner's throwaway Android debug key (`CN=Android Debug`), different every run | yes, but **not over the release-signed app** |
| `Total-Music-1.8.0-unsigned-release-NOT-INSTALLABLE.apk` | nothing | no |

`package_ci.py` also refuses the build if the debug APK ever carries the
permanent certificate — the test APK cannot be mistaken for a release.

### Signing: the blocker, stated exactly

The permanent key is **not available** to this branch or to CI:

* `.signing/space-release-key.cms` is committed, but it is a PKCS#7
  **EnvelopedData**: the content is AES-256-CBC and its key is wrapped with
  **RSA-OAEP (SHA-256)** for a recipient certificate
  `CN=Space Music signing backup, O=nexlibre`, serial
  `3DDEE665A704C29D53813C9E001814D334A8A2D4`. Decrypting it needs that
  recipient's **private key**, which is not in this checkout, not in this
  environment, and not in the workflow's secrets — the only secret any workflow
  in this repository references is `TOTAL_MUSIC_CANDIDATE_URL`.
* The keystore and its passwords (`keystore.properties`, `*.jks`) are
  gitignored by design and are absent.

So no APK here can carry the permanent signature, and **no other key was
generated**. Installing this build as an update would require an APK signed
with certificate `9ae145a7…`, produced by whoever holds the private key of the
envelope above. That check is the only one possible without the device: the
installed app's signature cannot be read from here.

What that means in practice: the test APK can be tried where Total Music is not
already installed — another device, a work profile, a second user — and it is
labelled `test-only` everywhere it appears. The existing installation is
untouched and needs nothing done to it.

## What the compiler found

`be1778b` is the fourteenth commit of this branch; the first real compile
happened on `2882ae2b` and found ten defects, none of which static analysis
could see, all fixed:

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
| 9 | `EqualizerPreset` imported from `data.settings`; it lives in `playback` | the import check (added here) |
| 10 | `Notes` taken from the non-mirrored icon package | the import check |

Eight of the ten are why compiling is not optional: a parser, a symbol grep and
a signature check between them still cannot see overloads, composable context
or resource *kinds*.

## Tests

* `app/src/test/.../rebirth/RebirthLibraryTest.kt` — 12 tests over the pure
  library model. One was red on the first run and **the test was right**: the
  model sorted a track with no disc tag after every disc, so disc 2 of an
  untagged set came first. `rebirthAlbumOrder` now delegates to the shared
  `List<Song>.inAlbumOrder()` (`AlbumOrdering`), the rule the album screen
  already used.
* The rest of the repository's unit tests run in the same job. The counts, per
  class, are in `build-verification.json` and `ci-logs/tests-summary.md` of the
  run's artifact.
* `AlbumOrderingTest` in `shared/src/commonTest` is run by `:shared:jvmTest`.

Nothing has run on a device.

## Static gate (a supplement, never a substitute)

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

## Known gaps — the redesign is not finished

1. **Panels with the previous distribution.** `AudioOutputSheet`,
   `LyricsProviderSheet`, `LyricsOffsetSheet`, `CastDialog` and
   `AudioPipelineDialog` draw in the *material* of the player that opened them,
   but their internal layout is still the original's. The pipeline readout is
   the clearest example: its signal graph was drawn for a dark pane and now
   takes the theme's ink, but it is still composed the old way. These five are
   the remaining "old distribution" panels, and until they are rebuilt the
   redesign is not done.
2. **Second-level settings.** Accent picker, artwork colours and the adaptive
   navigation row are legacy widgets inside a rebirth frame.
3. **No device pass.** Narrow screens, 200 % font scale, both Plus themes,
   Original, RTL (`values-he`), glass sampling, frame timing and the two drag
   gestures have not been seen running.
4. **Translation scope.** The 18 player strings exist in all 18 locales. The
   library, settings and equalizer strings in
   `res/values{,-es}/total_music_180.xml` are English and Spanish only; the
   other locales fall back to English, as that file's header says.
5. **The two drags are compiled, not exercised.** No test moves a finger.
