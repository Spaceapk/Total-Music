# Why this branch has no build

Recorded so that the next person does not spend the same hour finding out.

The sandbox that applied the 1.8.0 recovery patch and finished the rebirth
layer has no JVM toolchain and cannot reach the hosts that would provide one.
Every command below was run in it; the output is quoted, not paraphrased.

## What is installed

```
$ java -version
bash: java: command not found
$ kotlinc -version
bash: kotlinc: command not found
$ ls /usr/lib/jvm
ls: cannot access '/usr/lib/jvm': No such file or directory
$ command -v adb sdkmanager
(no output)
$ echo $ANDROID_HOME $ANDROID_SDK_ROOT
(empty)
$ ls ~/.gradle ~/.android
ls: cannot access '~/.gradle': No such file or directory
```

Python 3.11.2 and Node are present; Debian 12 is the base image.

## What is reachable

| Host | Result |
| --- | --- |
| `github.com`, `api.github.com`, `codeload.github.com` | 200 — clone, fetch, push and `gh` all work |
| `release-assets.githubusercontent.com` | `curl: (35) SSL_ERROR_SYSCALL` — no release binary, so no Gradle mirror, no Kotlin compiler, no Android tools package |
| `registry.npmjs.org` | 200 |
| `pypi.org`, `files.pythonhosted.org` | 200 — `pip download` works |
| `services.gradle.org` | `SSL_ERROR_SYSCALL` — the wrapper cannot fetch its distribution |
| `dl.google.com` | `SSL_ERROR_SYSCALL` — no AGP, no Android platform, no Compose |
| `repo1.maven.org` | `SSL_ERROR_SYSCALL` — no dependencies |
| `api.adoptium.net` | `SSL_ERROR_SYSCALL` — no JDK |

## What was tried

* `pip download --no-deps jdk4py` — **works**, produces a 25.0.2.1 manylinux
  wheel with a full JDK. A JDK alone is not enough; it was not extracted
  because the Gradle distribution and the dependencies are unreachable.
* `npm search kotlin-compiler` — the package of that name is Kotlin/JS
  2.4.21, not the JVM compiler.
* `npm view android-sdk versions` — an unrelated 1.0.0 package, no SDK.
* `apt-get download` — "Unable to locate package" for every Android/JDK name
  tried; the image has no package lists.
* GitHub release download of Gradle, Kotlin and the Android command-line
  tools — the redirect to `release-assets.githubusercontent.com` fails TLS.

## Consequence

`./gradlew` has never run on this tree. The build files were not touched:
Kotlin 2.3.20, AGP 8.10.1, Compose 1.10.3, Gradle wrapper 8.11.1, and the
single build change the recovered patch carries (`incremental = false` on the
Compose compiler task, in `sharedUi/build.gradle.kts`).

Verification that *was* possible: `scripts/verify/rebirth_check.py`, described
in `VALIDATION.md`. It parses every Kotlin file, resolves every resource
reference, compares translation format arguments, checks the manifest and
refuses changes to signing and workflows. It is not a compiler and does not
claim to be.

## To build this branch elsewhere

```
git fetch origin arena/3b36059c-total-music
git switch arena/3b36059c-total-music
./gradlew :app:assembleDebug          # needs JDK 17+, Android SDK 37, Maven access
./gradlew :app:testDebugUnitTest      # app/src/test/.../rebirth/RebirthLibraryTest.kt
```

The unit tests in `app/src/test/java/com/music/bitchord/ui/rebirth/` are JVM
tests over the pure model and need no device.
