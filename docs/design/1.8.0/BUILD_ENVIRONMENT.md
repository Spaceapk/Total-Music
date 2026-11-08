# Why this sandbox cannot build — and where the branch is built instead

The branch **is** compiled: in GitHub Actions, by
`.github/workflows/total-music-180-compile.yml`, which runs
`:app:compileProdDebugKotlin`, the rebirth unit tests and
`:app:assembleProdDebug` and uploads the APK. See
[VALIDATION.md](VALIDATION.md) for the run and its results.

This page is the record of why that has to happen elsewhere, so nobody repeats
the search.

## The blocker in one line

The sandbox has no Java, no Android SDK, and no route to any artifact host. A
JDK *can* be fetched from PyPI; the Gradle distribution and every Maven
artifact *cannot*, because those hosts refuse the TLS connection rather than
answering 403/404.

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
$ ls -d /opt/android* /root/.gradle /root/.m2
(no output)
```

Python 3.11.2 and Node/Yarn are present; Debian 12 is the base image. Home is
`/home/user`.

## Host names, one by one

| Host | Result |
| --- | --- |
| `github.com`, `api.github.com`, `codeload.github.com` | 200 — clone, fetch, push and `gh` work |
| `release-assets.githubusercontent.com` | `curl: (35) SSL_ERROR_SYSCALL` |
| `registry.npmjs.org` | 200 |
| `pypi.org`, `files.pythonhosted.org` | 200 — `pip download` works |
| `services.gradle.org` | `000` from curl; the wrapper itself fails as below |
| `downloads.gradle.org` | `000` |
| `plugins.gradle.org` | `000` |
| `dl.google.com` | `000` — no AGP, no Android platform, no Compose |
| `maven.google.com` | `000` |
| `repo1.maven.org` | `000` |
| `repo.maven.apache.org` | `000` |
| `api.adoptium.net` | `000` |
| `cache-redirector.jetbrains.com` | `000` — no Kotlin distribution |

`000` is curl's "connection never happened"; the wrapper's own view of the
same block is quoted below.

## The compile attempt, verbatim

With no JDK, the wrapper cannot even start:

```
$ ./gradlew --version
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

A JDK is obtainable — this is the one tool the sandbox *can* supply:

```
$ pip download --no-deps jdk4py -d /tmp/jdkdl
jdk4py-25.0.2.1-py3-none-manylinux_2_17_x86_64.whl        # 214 MB, Temurin 25
$ python3 -c "import zipfile; zipfile.ZipFile('/tmp/jdkdl/*.whl').extractall('/tmp/jdk')"
$ /tmp/jdk/jdk4py/java-runtime/bin/java -version
openjdk version "25.0.2" 2026-01-20 LTS
```

With that JDK the wrapper gets one step further and stops at the distribution:

```
$ JAVA_HOME=/tmp/jdk/jdk4py/java-runtime ./gradlew --version
Exception in thread "main" java.net.UnknownHostException ... (Download.java:87)
	at org.gradle.wrapper.Install.createDist(Install.java:48)
Caused by: java.io.EOFException: SSL peer shut down incorrectly
	at java.base/sun.security.ssl.SSLSocketInputRecord.read(Unknown Source)
```

`gradle-wrapper.properties` points at
`https://services.gradle.org/distributions/gradle-8.11.1-bin.zip`. That
connection is closed mid-handshake by the sandbox's egress proxy, for every
`000` host in the table.

## Alternatives that were tried and did not help

* `npm search kotlin-compiler` — the package of that name is Kotlin/JS
  2.4.21, not the JVM compiler.
* `npm view android-sdk` — an unrelated 1.0.0 package; no SDK.
* `apt-get download` for a JDK, Gradle, or the Android tools — "Unable to
  locate package" (no package lists in the image).
* GitHub releases as a mirror for Gradle, Kotlin or the command-line tools —
  the redirect to `release-assets.githubusercontent.com` fails TLS.
* PyPI for Gradle distributions or Maven artifacts — no such packages exist.

The missing pieces are not one tool, they are three: a Gradle distribution, the
Android SDK (`platforms;android-37`, `build-tools`), and ~1 GB of Maven
artifacts (AGP, Kotlin 2.3.20, Compose 1.10.3, Haze, Media3, Coil). No
combination of PyPI and npm supplies them.

## Consequence

`./gradlew` has never run on this tree. The build files were not touched:
Kotlin 2.3.20, AGP 8.10.1, Compose 1.10.3, Gradle wrapper 8.11.1, and the one
build change the recovered patch carries (`incremental = false` on the Compose
compiler task, in `sharedUi/build.gradle.kts`).

The closest substitute that *is* available is
`scripts/verify/rebirth_check.py`: it parses every Kotlin file, resolves every
resource and compose-resource reference, compares translation format arguments
in all 18 locales, checks named arguments against the declarations this branch
wrote, resolves capitalised names to imports or same-package declarations,
checks the manifest against declared classes, and refuses changes to signing
and workflows. It is a compile *proxy*, not a compiler, and
[VALIDATION.md](VALIDATION.md) is explicit about which errors each part can and
cannot catch.

## To build this branch yourself

Either push to the branch (the validation workflow runs on the pull request) or
run the same three tasks locally with a JDK, the Android SDK and Maven access:

```
./gradlew :app:compileProdDebugKotlin
./gradlew :app:testProdDebugUnitTest --tests 'com.music.bitchord.ui.rebirth.*'
./gradlew :app:assembleProdDebug
```

## Or, historically, what the sandbox offered

```
git fetch origin arena/3b36059c-total-music
git switch arena/3b36059c-total-music
./gradlew :app:compileDebugKotlin    # JDK 17+, Android SDK 37, Maven access
./gradlew :app:testDebugUnitTest     # app/src/test/.../rebirth/RebirthLibraryTest.kt
```

The unit tests over the library model are plain JVM tests and need no device.
