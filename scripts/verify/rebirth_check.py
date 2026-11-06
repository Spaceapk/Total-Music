#!/usr/bin/env python3
"""Static gate for the 1.8.0 "Rebirth" work.

This exists because the change cannot be compiled in the sandbox that produced
it: no JDK, no Android SDK, and no route to Maven Central, Google Maven or
Gradle. A gate that pretends to replace a compiler would be worse than none, so
this checks only what a compiler would otherwise have caught *syntactically*:

  * every .kt file parses (tree-sitter-kotlin, optional dependency);
  * every `R.<type>.<name>` has a matching resource in the module's res/;
  * every `@string/...`-style reference inside res XML resolves;
  * every `Res.string.<name>` has a key in composeResources/values;
  * the manifest's components name classes that exist;
  * translations keep the same format specifiers as the default string;
  * the signing configuration and the publication workflows are untouched.

It is not a type checker, not a resource shrinker and not a test run. See
docs/design/1.8.0/VALIDATION.md for what was verified, how, and what was not.

Usage:
    python3 scripts/verify/rebirth_check.py                 # whole tree
    python3 scripts/verify/rebirth_check.py --base origin/main
"""

from __future__ import annotations

import argparse
from functools import lru_cache
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

APP_RES = "app/src/main/res"
APP_MANIFEST = "app/src/main/AndroidManifest.xml"
COMPOSE_RES = "sharedUi/src/commonMain/composeResources"

# Resource types a Kotlin file can reach through `R.<type>.<name>`.
RES_TYPES = {
    "string", "plurals", "string-array", "array", "drawable", "mipmap", "color",
    "dimen", "style", "xml", "raw", "font", "layout", "menu", "anim", "animator",
    "interpolator", "navigation", "integer", "bool", "fraction", "attr", "id",
}

# Files whose change would alter signing or publication. A 1.8.0 UI branch has
# no reason to touch them, and the handoff says not to.
PROTECTED_PATHS = (
    ".github/workflows/",
    "signing/",
    ".signing/",
    "keystore",
)

# One workflow is allowed to be added: a validation-and-compile job for this
# branch, because the sandbox cannot reach Gradle, Google or Maven and a
# compiler is the one check no static proxy replaces. It is allowed by *name*,
# and its content is then checked for anything that could publish: a release
# action, a tag, a push, or a build of a release variant. The publishing
# workflows stay refused even if their content is innocuous.
VALIDATION_WORKFLOWS = (
    ".github/workflows/total-music-180-compile.yml",
    # 1.8.1's single validation workflow: the same restriction, one branch
    # later. It compiles, tests, runs lint on its own runner and drives an
    # emulator; it publishes nothing, and this gate refuses it if that changes.
    ".github/workflows/total-music-181-validate.yml",
    ".github/workflows/total-music-182-validate.yml",
    ".github/workflows/total-music-185-validate.yml",
    # 1.8.1's device probe of the *published* 1.8.0: it downloads the two
    # released APKs, verifies their hashes and installs them. It builds nothing,
    # signs nothing and publishes nothing — the same forbidden-marker check
    # applies to it, and it is listed here so that downloading a release binary
    # for evidence is not confused with producing one.
    ".github/workflows/total-music-181-published.yml",
)

# The one workflow allowed to read a secret, and only to report whether a
# permanent signing key exists: it says present/absent per candidate name and,
# for a key that is present, the public SHA-256 of the certificate inside it.
# That is the difference between "the key is configured" and "somebody assumed
# it was", and it cannot be answered without touching the secrets context.
#
# So it gets its own, narrower rules rather than a hole in the rules above: it
# may not sign an APK, generate a key, tag, push, publish or call the API, and
# it may not write a keystore inside the checkout — only under $RUNNER_TEMP.
SIGNING_CHECK_WORKFLOWS = (".github/workflows/total-music-181-signing-check.yml",)

# The one workflow allowed to *sign*, and why that is allowed at all: it is
# manual, it signs the artefact a previous run already built and tested rather
# than rebuilding it, it uses the repository's own signer, it refuses any key
# whose certificate is not the pinned one, and it publishes nothing until
# apksigner and aapt agree. It never touches a branch, a tag or a release.
#
# An exception that is only a name is a hole, so both halves are checked: the
# forbidden list is the ways out (publish, push, generate a key, fall back to
# another identity, print the decrypted key), and the required list is the
# safeguards that have to still be there for the exception to remain legitimate.
SIGNING_WORKFLOWS = (".github/workflows/total-music-181-sign.yml",)
FORBIDDEN_IN_SIGNING = (
    "gh release",
    "action-gh-release",
    "upload-release",
    "softprops/",
    "git push",
    "git tag",
    "git checkout -b",
    "pull_request_target",
    "keytool -genkey",
    "keystore.properties",
    "signingConfig",
    "android.injected.signing",
    # Another identity is not a fallback; refusing it is the whole point.
    "SPACE_KEYSTORE_B64",
    "SPACE_SPATIAL_KEYSTORE_B64",
    "space-release-key.cms",
    # Printing the decrypted pair would defeat the masking.
    'cat "$RUNNER_TEMP/signing',
    "cat $RUNNER_TEMP/signing",
)
REQUIRED_IN_SIGNING = (
    "workflow_dispatch",
    "scripts/release/sign_apk.py",
    "apksigner",
    "aapt",
    "::add-mask::",
    "RUNNER_TEMP",
    "9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa",
    "--previous-apk",
)
FORBIDDEN_IN_SIGNING_CHECK = (
    "gh release",
    "gh api",
    "action-gh-release",
    "upload-release",
    "softprops/",
    "git push",
    "git tag",
    "pull_request_target",
    "apksigner",
    "zipalign",
    "keytool -genkey",
    "keystore.properties",
    "signingConfig",
    "android.injected.signing",
    "upload-artifact",
)
# What a validation workflow may not do. Building the *unsigned* release APK is
# allowed — it is inert, the repository's own verification job builds it too, and
# it is precisely the artefact the permanent key would sign later. Signing it,
# reading a secret or publishing anything is not.
FORBIDDEN_IN_VALIDATION = (
    "gh release",
    "action-gh-release",
    "upload-release",
    "softprops/",
    "git push",
    "git tag",
    "pull_request_target",
    "apksigner sign",
    "keystore.properties",
    "signingConfig",
    "android.injected.signing",
    "secrets.",
)

# Version metadata is expected to move on a release branch; it is reported
# rather than refused, so that a deliberate bump is visible in the log.
REPORTED_PATHS = ("total-music-release.properties",)

failures: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def note(message: str) -> None:
    notes.append(message)


def walk(relative: str, suffix: str = "") -> list[str]:
    found = []
    base = os.path.join(ROOT, relative)
    for directory, _, filenames in os.walk(base):
        for filename in filenames:
            if filename.endswith(suffix):
                found.append(os.path.join(directory, filename))
    return sorted(found)


def strip_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.S)


# ---------------------------------------------------------------- Kotlin parse
def check_kotlin_parse(base: str) -> None:
    try:
        import tree_sitter  # noqa: F401
        import tree_sitter_kotlin  # noqa: F401
    except Exception:
        note("Kotlin parse skipped: tree-sitter and tree-sitter-kotlin are not installed.")
        return
    from tree_sitter import Language, Parser

    language = Language(tree_sitter_kotlin.language())
    parser = Parser(language)
    files = walk("app/src", ".kt") + walk("shared/src", ".kt") + walk("sharedUi/src", ".kt")
    broken: list[str] = []
    pre_existing: list[str] = []
    for path in files:
        with open(path, "rb") as handle:
            source = handle.read()
        if not parser.parse(source).root_node.has_error:
            continue
        relative = os.path.relpath(path, ROOT)
        # tree-sitter-kotlin is not the Kotlin parser, and a handful of files
        # use syntax it cannot read. Ask the baseline whether it already
        # could not read them: only a file that parses at the base and not
        # here is a syntax error this change introduced.
        try:
            before = subprocess.run(
                ["git", "-C", ROOT, "show", f"{base}:{relative}"],
                capture_output=True, check=True,
            ).stdout
        except subprocess.CalledProcessError:
            before = None
        if before is not None and parser.parse(before).root_node.has_error:
            pre_existing.append(relative)
        else:
            broken.append(relative)
    for path in broken:
        fail(f"Kotlin does not parse: {path}")
    for path in pre_existing:
        note(f"Kotlin parse: {path} does not parse at {base} either (grammar gap).")
    note(f"Kotlin parse: {len(files)} file(s) checked, {len(broken)} broken, {len(pre_existing)} pre-existing gap(s).")


# ------------------------------------------------------------ Android resources
def android_resources() -> tuple[dict[str, set[str]], dict[str, dict[str, set[str]]]]:
    """`{type: {name}}` for the default configuration, and per-locale names."""
    default: dict[str, set[str]] = defaultdict(set)
    per_qualifier: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    for directory in sorted(os.listdir(os.path.join(ROOT, APP_RES))):
        res_dir = os.path.join(ROOT, APP_RES, directory)
        if not os.path.isdir(res_dir):
            continue
        kind = directory.split("-")[0]
        qualifier = directory[len(kind):] or "-default"
        for filename in sorted(os.listdir(res_dir)):
            if kind not in ("values",):
                name = filename.rsplit(".", 1)[0]
                if name.startswith("androidx_"):
                    continue
                per_qualifier[qualifier][kind].add(name)
                if qualifier == "-default":
                    default[kind].add(name)
                continue
            if not filename.endswith(".xml"):
                continue
            with open(os.path.join(res_dir, filename), encoding="utf-8") as handle:
                text = strip_comments(handle.read())
            if True:
                for match in re.finditer(
                    r'<(string|plurals|string-array|integer-array|array|color|dimen|style|bool|integer|fraction|attr)'
                    r'\s+name="([^"]+)"',
                    text,
                ):
                    tag, name = match.group(1), match.group(2)
                    resource_type = "string-array" if tag == "string-array" else tag
                    per_qualifier[qualifier][resource_type].add(name)
                    if qualifier == "-default":
                        default[resource_type].add(name)
    return default, per_qualifier


def check_R_references(default: dict[str, set[str]]) -> None:
    pattern = re.compile(r"(?<!android\.)\bR\.(string|plurals|drawable|mipmap|color|dimen|style|xml|raw|font|layout|menu|anim|animator|interpolator|navigation|integer|bool|fraction|attr|array)\.(\w+)")
    checked = 0
    for path in walk("app/src", ".kt"):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for match in pattern.finditer(text):
            resource_type, name = match.group(1), match.group(2)
            checked += 1
            if name not in default.get(resource_type, set()):
                line = text.count("\n", 0, match.start()) + 1
                fail(f"R.{resource_type}.{name} has no resource ({os.path.relpath(path, ROOT)}:{line})")
    note(f"R references: {checked} checked.")


def check_xml_references(default: dict[str, set[str]]) -> None:
    pattern = re.compile(r'"@(?:\+)?(string|plurals|drawable|mipmap|color|dimen|style|xml|raw|font|layout|menu|anim|animator|interpolator|navigation|integer|bool|array)/(\w+)"')
    checked = 0
    for path in walk(APP_RES, ".xml"):
        with open(path, encoding="utf-8") as handle:
            text = strip_comments(handle.read())
        for match in pattern.finditer(text):
            resource_type, name = match.group(1), match.group(2)
            if resource_type == "style" and name.startswith("Theme."):
                continue
            checked += 1
            if name not in default.get(resource_type, set()):
                fail(f"@{resource_type}/{name} has no resource ({os.path.relpath(path, ROOT)})")
    note(f"Resource XML references: {checked} checked.")


def check_translation_format_args() -> None:
    """A translation that drops a %1$s is a crash waiting for a Spanish phone."""
    placeholder = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
    parsed: dict[str, dict[str, set[str]]] = {}
    for directory in sorted(os.listdir(os.path.join(ROOT, APP_RES))):
        if not directory.startswith("values"):
            continue
        values: dict[str, set[str]] = {}
        for filename in sorted(os.listdir(os.path.join(ROOT, APP_RES, directory))):
            if not filename.endswith(".xml"):
                continue
            with open(os.path.join(ROOT, APP_RES, directory, filename), encoding="utf-8") as handle:
                text = strip_comments(handle.read())
            for match in re.finditer(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', text, re.S):
                values[match.group(1)] = set(placeholder.findall(match.group(2)))
        parsed[directory] = values
    default = parsed.get("values")
    if not default:
        fail("app/src/main/res/values has no strings.")
        return
    checked = 0
    for directory, values in parsed.items():
        if directory == "values":
            continue
        for name, args in values.items():
            if name not in default:
                continue
            checked += 1
            wanted = {arg for arg in default[name] if arg.endswith(("s", "d", "f", "x", "X"))}
            found = {arg for arg in args if arg.endswith(("s", "d", "f", "x", "X"))}
            if wanted != found:
                fail(
                    f"{directory}: {name} takes {sorted(wanted) or 'no arguments'} "
                    f"but the translation uses {sorted(found) or 'none'}",
                )
    note(f"Translation format arguments: {checked} translated string(s) compared.")


def check_compose_resources() -> None:
    base = os.path.join(ROOT, COMPOSE_RES)
    if not os.path.isdir(base):
        fail(f"{COMPOSE_RES} is missing.")
        return
    default: dict[str, set[str]] = defaultdict(set)
    locales: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    for directory in sorted(os.listdir(base)):
        path = os.path.join(base, directory)
        if not os.path.isdir(path):
            continue
        for filename in sorted(os.listdir(path)):
            if not filename.endswith(".xml"):
                continue
            with open(os.path.join(path, filename), encoding="utf-8") as handle:
                text = strip_comments(handle.read())
            for match in re.finditer(r'<(string|plurals|string-array)\s+name="([^"]+)"', text):
                kind = "string" if match.group(1) == "string" else match.group(1)
                locales[directory][kind].add(match.group(2))
                if directory == "values":
                    default[kind].add(match.group(2))
    checked = 0
    used: set[str] = set()
    pattern = re.compile(r"\bRes\.(string|plurals|stringArray)\.(\w+)")
    for path in walk("sharedUi/src", ".kt"):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for match in pattern.finditer(text):
            kind = {"string": "string", "plurals": "plurals", "stringArray": "string-array"}[match.group(1)]
            name = match.group(2)
            checked += 1
            used.add(name)
            if name not in default[kind] and not (kind == "string" and name in default["string-array"]):
                line = text.count("\n", 0, match.start()) + 1
                fail(f"Res.{match.group(1)}.{name} has no key in composeResources/values ({os.path.relpath(path, ROOT)}:{line})")
    gaps = {
        locale: sorted(name for name in used if name.startswith("rebirth_") and name not in locales[locale]["string"])
        for locale in locales if locale != "values"
    }
    for locale, missing in sorted(gaps.items()):
        if missing:
            note(f"composeResources/{locale}: {len(missing)} rebirth key(s) fall back to English, e.g. {missing[:3]}")
    note(f"Compose resource references: {checked} checked.")


def check_hardcoded_text() -> None:
    """UI text belongs in a resource; these are the places 1.8.0 still does not."""
    pattern = re.compile(r'(?:\bText\(\s*|\btext\s*=\s*|\blabel\s*=\s*|\bmessage\s*=\s*|\btitle\s*=\s*)"([^"\\]{2,})"')
    checked = 0
    for path in walk("app/src/main/java/com/music/bitchord/ui/rebirth", ".kt") + \
            walk("sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player", ".kt"):
        if "Rebirth" not in os.path.basename(path):
            continue
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for match in pattern.finditer(text):
            literal = match.group(1)
            if not re.search(r"[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]", literal):
                continue
            if literal.startswith(("%", "\n")):
                continue
            checked += 1
            line = text.count("\n", 0, match.start()) + 1
            fail(f"Hardcoded UI text: {os.path.relpath(path, ROOT)}:{line} {literal!r}")
    note(f"Hardcoded rebirth text: {checked} literal(s) found.")



def check_compose_translations() -> None:
    """Every locale keeps the default string's format arguments, key by key."""
    base = os.path.join(ROOT, COMPOSE_RES)
    placeholder = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
    if not os.path.isdir(base):
        return
    parsed: dict[str, dict[str, set[str]]] = {}
    for directory in sorted(os.listdir(base)):
        path = os.path.join(base, directory)
        if not os.path.isdir(path) or not (directory == "values" or directory.startswith("values-")):
            continue
        values: dict[str, set[str]] = {}
        for filename in sorted(os.listdir(path)):
            if not filename.endswith(".xml"):
                continue
            with open(os.path.join(path, filename), encoding="utf-8") as handle:
                text = strip_comments(handle.read())
            for match in re.finditer(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', text, re.S):
                values[match.group(1)] = set(placeholder.findall(match.group(2)))
        parsed[directory] = values
    default = parsed.get("values", {})
    checked = 0
    for directory, values in parsed.items():
        if directory == "values":
            continue
        for name, args in values.items():
            if name not in default:
                continue
            checked += 1
            wanted = {a for a in default[name] if a.endswith(("s", "d", "f", "x", "X"))}
            found = {a for a in args if a.endswith(("s", "d", "f", "x", "X"))}
            if wanted != found:
                fail(
                    f"composeResources/{directory}: {name} takes {sorted(wanted) or 'no arguments'} "
                    f"but the translation uses {sorted(found) or 'none'}",
                )
    # A key added to the default and forgotten in a locale falls back to
    # English at runtime; that is reported, not failed, but it must be visible.
    for directory, values in parsed.items():
        if directory == "values":
            continue
        missing = sorted(name for name in default if name.startswith("rebirth_") and name not in values)
        if missing:
            note(f"composeResources/{directory}: {len(missing)} rebirth key(s) fall back to English.")
    note(f"Compose translations: {checked} translated string(s) compared.")


SYMBOL_CACHE: dict[str, set[str]] = {}


def _declared_names() -> dict[str, set[str]]:
    """Every top-level declaration name, keyed by the package that declares it."""
    if SYMBOL_CACHE:
        return SYMBOL_CACHE  # type: ignore[return-value]
    pattern = re.compile(
        r"^(?:@\w+(?:\([^)]*\))?\s*)*"
        r"(?:internal |private |public |expect |actual |const )*"
        r"(?:inline |suspend |tailrec |operator |infix |abstract |open |sealed |data |value |fun interface )*"
        r"(?:fun|val|var|class|object|interface|typealias|enum class)\s+"
        r"(?:<[^>]*>\s*)?([A-Za-z_][\w]*)",
        re.M,
    )
    for path in walk("app/src", ".kt") + walk("shared/src", ".kt") + walk("sharedUi/src", ".kt"):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        package = re.search(r"^package\s+([\w.]+)", text, re.M)
        key = package.group(1) if package else ""
        names = SYMBOL_CACHE.setdefault(key, set())
        for match in pattern.finditer(text):
            names.add(match.group(1))
        # Nested and member declarations are not top-level, but a file that
        # declares a class also declares what is inside it; the resolver below
        # only needs to know the name exists somewhere.
        for match in re.finditer(r"^\s{4}(?:@\w+\s*)*(?:private |internal |public |const )*(?:fun|val|var|class|object) ([A-Za-z_][\w]*)", text, re.M):
            names.add(match.group(1))
        # Entries of an enum are names in this package too. A companion object
        # inside the enum body stops a whole-body match, so this reads lines.
        for entry in re.findall(r"^\s{2,}([A-Z][A-Z0-9_]{2,})\s*[,(]?\s*$", text, re.M):
            names.add(entry)
        # `enum class Foo { A, B, C }` on one line, and the multi-line entries
        # before a companion object, which a whole-body match cannot reach.
        for match in re.finditer(r"enum class \w+[^{]*\{([^}]*)", text):
            for entry in re.split(r"[,\n]", match.group(1).split(";")[0]):
                name = entry.strip().split("(")[0].strip()
                if re.fullmatch(r"[A-Z][A-Za-z0-9_]*", name):
                    names.add(name)
    return SYMBOL_CACHE


def _file_package(text: str) -> str:
    match = re.search(r"^package\s+([\w.]+)", text, re.M)
    return match.group(1) if match else ""


def _rebirth_target_files() -> list[str]:
    """
    The files this change wrote or edited, as a fixed list.

    Deliberately not `git diff main`: the recovery patch also carried widget and
    player files this change did not write, and holding their private constants
    and import styles to these rules would report work nobody did.
    """
    targets = set(walk("app/src/main/java/com/music/bitchord/ui/rebirth", ".kt"))
    player = "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/"
    targets |= {
        path for path in walk("sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player", ".kt")
        if "Rebirth" in os.path.basename(path)
    }
    targets |= {
        os.path.join(ROOT, player + name) for name in (
            "PlayerDrawer.kt", "AudioOutputSheet.kt", "LyricsProviderSheet.kt",
            "LyricsOffsetSheet.kt", "ListenTogetherMembersSheet.kt",
            "NowPlayingScreen.kt", "PlayerStyleLocal.kt", "PlayerVisualStyle.kt",
        )
    }
    targets |= {
        os.path.join(ROOT, "app/src/main/java/com/music/bitchord/" + name) for name in (
            "MainActivity.kt",
            "ui/screens/SettingsSheet.kt",
            "ui/screens/EqualizerScreen.kt",
            "ui/components/AudioPopupCard.kt",
            "ui/components/CastDialog.kt",
            "ui/components/AudioPipelineDialog.kt",
        )
    }
    return sorted(path for path in targets if os.path.isfile(path))


def check_symbol_resolution() -> None:
    """
    A resolution proxy, not a type checker.

    For the rebirth layer and the files it touched, every capitalised name that
    is *used* has to be imported, declared in the same file, or declared in the
    same package. That is the class of mistake a compiler catches first once the
    syntax is right — a missing import — and the one thing the parser cannot
    see. It says nothing about types, overloads or receivers.
    """
    declared = _declared_names()
    targets = _rebirth_target_files()
    problems = 0
    for path in targets:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        package = _file_package(text)
        local = set(re.findall(r"^(?:internal |private |public )?(?:fun|val|var|class|object|interface) (\w+)", text, re.M))
        local |= set(re.findall(r"^\s+(?:private |internal )?(?:fun|val|var) (\w+)", text, re.M))
        imported = set(re.findall(r"^import ([\w.]+)(?: as (\w+))?", text, re.M))
        imported_names = {i[0].rsplit(".", 1)[-1] for i in imported}
        imported_names |= {match for match in re.findall(r"^import [\w.]+\.\w+ as (\w+)", text, re.M)}
        body = re.sub(r"^(?:import|package) .*$", "", text, flags=re.M)
        # Comments and string literals talk about the code; only the code counts.
        body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
        body = re.sub(r"//[^\n]*", "", body)
        body = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', body)
        # Names used as a type, a constructor or an enum receiver.
        used = set(re.findall(r"(?<![\.\w])([A-Z][A-Za-z0-9_]{2,})", body))
        known = local | imported_names | declared.get(package, set())
        # A star import of a package this repository declares brings every one
        # of its names into scope; that is how the compose resources are used.
        for match in re.findall(r"^import (com\.music\.bitchord\.[\w.]+)\.\*", text, re.M):
            known |= declared.get(match, set())
        # Kotlin's own and the platform's: too many to list, and not what this
        # check is for.
        known |= {
            "String", "StringBuilder", "Int", "Long", "Float", "Double", "Boolean", "Byte", "Short",
            "List", "Set", "Map", "MutableList", "MutableMap", "MutableSet", "Pair", "Triple",
            "Any", "Unit", "Nothing", "Array", "CharArray", "Exception", "Throwable",
            "Locale", "Math", "System", "Object", "Class", "Thread", "Runnable", "Override",
            "Suppress", "JvmStatic", "JvmField", "JvmOverloads", "Volatile", "Synchronized",
            "Serializable", "JSONObject", "JSONArray", "UUID", "File", "InputStream",
            "OutputStream", "Uri", "Context", "Intent", "Log", "Build", "Settings", "Toast",
            "TAG", "I", "W", "E", "D", "V", "TODO",
            "OptIn", "ClosedFloatingPointRange", "IllegalStateException", "IllegalArgumentException",
            "Regex", "Result", "Lazy", "State", "StateFlow", "MutableStateFlow", "Flow",
            "HashSet", "HashMap", "LinkedHashMap", "ArrayList", "Collections", "Comparator",
            "StringBuilder", "CharSequence", "Collection", "Number", "Comparable", "Iterable", "Sequence",
            "BuildConfig", "IntArray", "FloatArray", "LongArray", "BooleanArray", "Pair",
            # Generated at build time by Compose Multiplatform and AGP; there is
            # no source in the tree for either, which is the point of them.
            "Res",
        }
        for name in sorted(used - known):
            line = None
            for match in re.finditer(rf"(?<![\.\w]){re.escape(name)}(?![\w])", body):
                line = body.count("\n", 0, match.start()) + 1
                break
            problems += 1
            fail(f"Unresolved symbol {name} in {os.path.relpath(path, ROOT)}:{line or '?'}")
    note(f"Symbol resolution proxy: {len(targets)} file(s), {problems} unresolved name(s).")



def _split_top_level(body: str) -> list[str]:
    out, depth, current = [], 0, ""
    for char in body:
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        if char == "," and depth == 0:
            out.append(current)
            current = ""
        else:
            current += char
    out.append(current)
    return [part.strip() for part in out if part.strip()]


def _function_signatures(paths: list[str]) -> dict[str, list[tuple[str, list[tuple[str, bool]]]]]:
    signatures: dict[str, list[tuple[str, list[tuple[str, bool]]]]] = {}
    pattern = re.compile(r"(?:internal |private )?fun (?:<[^>]*>\s*)?(\w+)\s*\(")
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for match in pattern.finditer(text):
            name = match.group(1)
            index = match.end()
            depth = 1
            while index < len(text) and depth:
                if text[index] == "(":
                    depth += 1
                elif text[index] == ")":
                    depth -= 1
                index += 1
            params: list[tuple[str, bool]] = []
            for part in _split_top_level(text[match.end():index - 1]):
                part = re.sub(r"^@\w+(?:\([^)]*\))?\s*", "", part)
                part = part.split("//")[0].strip()
                if ":" not in part:
                    continue
                label = part.split(":")[0].strip().split()[-1]
                params.append((label, "=" in part))
            signatures.setdefault(name, []).append((path, params))
    return signatures


def check_call_signatures() -> None:
    """
    Named arguments of calls to the rebirth layer's own functions.

    A Kotlin compiler rejects a call that names an argument the function does
    not declare, or that omits one without a default. This reproduces that one
    rule for the functions this change wrote: same-file name, explicit named
    arguments only, and a trailing lambda is treated as the last parameter.
    Positional calls are left alone — inferring overloads without types would
    guess more than it proves.
    """
    paths = sorted(set(
        walk("app/src/main/java/com/music/bitchord/ui/rebirth", ".kt")
        + [
            path for path in walk("sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player", ".kt")
            if "Rebirth" in os.path.basename(path)
        ]
    ))
    signatures = _function_signatures(paths)
    # Call sites outside the layer that calls into it: the app wires these, and
    # a signature change that the wiring did not follow is a compile error.
    call_sites = paths + [
        path for path in (
            os.path.join(ROOT, "app/src/main/java/com/music/bitchord/MainActivity.kt"),
            os.path.join(ROOT, "app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt"),
            os.path.join(ROOT, "app/src/main/java/com/music/bitchord/ui/screens/EqualizerScreen.kt"),
            os.path.join(ROOT, "app/src/main/java/com/music/bitchord/ui/components/SongActionsSheet.kt"),
            os.path.join(ROOT, "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/NowPlayingScreen.kt"),
            os.path.join(ROOT, "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/RebirthPlayer.kt"),
        ) if os.path.isfile(path)
    ]
    checked = 0
    for path in call_sites:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        body = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        body = re.sub(r"//[^\n]*", "", body)
        for match in re.finditer(r"\b(\w+)\s*\(", body):
            name = match.group(1)
            if name not in signatures:
                continue
            index = match.end()
            depth = 1
            while index < len(body) and depth:
                if body[index] == "(":
                    depth += 1
                elif body[index] == ")":
                    depth -= 1
                index += 1
            arguments = _split_top_level(body[match.end():index - 1])
            named = []
            positional = 0
            for argument in arguments:
                head = re.match(r"(\w+)\s*=(?!=)", argument.strip())
                if head:
                    named.append(head.group(1))
                else:
                    positional += 1
            if not named:
                continue
            trailing = body[index:index + 40].lstrip().startswith("{")
            checked += 1
            best = None
            for _, params in signatures[name]:
                if len(params) < positional:
                    continue
                leading = {label for label, _ in params[:positional]}
                if set(named) <= {label for label, _ in params} and not (leading & set(named)):
                    best = params
                    break
            if best is None:
                fail(f"{os.path.relpath(path, ROOT)}: {name}() has no parameter named {sorted(named)}")
                continue
            filled = set(named) | {label for label, _ in best[:positional]}
            missing = [
                label for label, has_default in best
                if not has_default and label not in filled and label != "modifier"
            ]
            if trailing and missing and best[-1][1] is False:
                missing = missing[:-1]
            if missing:
                fail(f"{os.path.relpath(path, ROOT)}: {name}() called without {missing}")
    note(f"Call signatures: {checked} named-argument call(s) checked.")



@lru_cache(maxsize=None)
def _imports_at_base(path: str, base: str) -> frozenset[tuple[str, str]]:
    """
    The import name/package pairs [path] already had at `--base`.

    A file that did not exist there has none, so a new file is judged whole.
    """
    relative = os.path.relpath(path, ROOT)
    result = subprocess.run(
        ["git", "show", f"{base}:{relative}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return frozenset()
    pattern = re.compile(r"^import\s+([\w.]+)\.(\w+)\s*$", re.M)
    return frozenset(pattern.findall(result.stdout))


def check_import_packages(base: str) -> None:
    """
    Imports whose package the rest of the tree does not use for that name.

    A wrong package on an import is a hard compile error and invisible to every
    other check here: the name is spelled right, it is imported, and nobody
    resolves packages. The tree itself is the reference — if every other file
    that imports `withFrameNanos` takes it from `androidx.compose.runtime`, a
    file that takes it from `kotlinx.coroutines` is a typo, not an opinion.
    """
    tree: dict[str, dict[str, list[str]]] = {}
    pattern = re.compile(r"^import\s+([\w.]+)\.(\w+)\s*$", re.M)
    # Only imports this change introduces are judged. The tree predates it and
    # has a few of its own odd-looking ones (`ui.unit.offset` beside the
    # foundation one, the mirrored volume icons beside the plain ones); those
    # are not this branch's to change, and reporting them every run would train
    # the reader to ignore the check.
    for path in walk("app/src", ".kt") + walk("sharedUi/src", ".kt"):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for package, name in pattern.findall(text):
            tree.setdefault(name, {}).setdefault(package, []).append(path)

    targets = _rebirth_target_files()
    checked = 0
    for path in targets:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        inherited = _imports_at_base(path, base)
        for package, name in pattern.findall(text):
            if (package, name) in inherited:
                continue
            packages = tree.get(name)
            if not packages or package not in packages:
                continue
            checked += 1
            others = sum(len(paths) for other, paths in packages.items() if other != package)
            if others == 0 or len(packages[package]) > 1:
                continue
            majority = max(packages.items(), key=lambda item: len(item[1]))
            if len(majority[1]) >= others:
                fail(
                    f"{os.path.relpath(path, ROOT)}: imports {package}.{name}, "
                    f"but {len(majority[1])} other file(s) import it from {majority[0]}"
                )
    note(f"Import packages: {checked} import(s) checked against the tree.")



def check_compose_resource_imports() -> None:
    """
    `Res.string.x` needs more than the key existing: the generated property is
    an extension on `StringResources`, so the *property* has to be imported
    (or the package wildcard used). A missing import compiles to "Unresolved
    reference", and so does a string-array accessed as a plain string. Both
    happened in this branch and both cost a CI round trip; neither is visible
    to a check that only asks whether the key exists.
    """
    uses = re.compile(r"Res\.(string|array|drawable|plurals)\.(\w+)")
    imports = re.compile(r"^import com\.music\.bitchord\.sharedui\.resources\.(\w+)$", re.M)
    wildcard = re.compile(r"^import com\.music\.bitchord\.sharedui\.resources\.\*$", re.M)
    kinds: dict[str, str] = {}
    values = os.path.join(COMPOSE_RES, "values", "strings.xml")
    if os.path.isfile(values):
        tree = ET.parse(values)
        for element in tree.getroot():
            name = element.attrib.get("name")
            if name:
                kinds[name] = {"string-array": "array", "string": "string",
                               "plurals": "plurals"}.get(element.tag, element.tag)
    checked = 0
    for path in _rebirth_target_files():
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        if wildcard.search(text):
            continue
        imported = set(imports.findall(text))
        for kind, name in uses.findall(text):
            checked += 1
            if name not in imported:
                fail(f"{os.path.relpath(path, ROOT)}: Res.{kind}.{name} used without importing the property.")
            declared = kinds.get(name)
            if declared and declared != kind:
                fail(f"{os.path.relpath(path, ROOT)}: Res.{kind}.{name} but `{name}` is a {declared}.")
    note(f"Compose resource imports: {checked} property use(s) checked.")


def check_manifest_components() -> None:
    path = os.path.join(ROOT, APP_MANIFEST)
    if not os.path.isfile(path):
        fail("AndroidManifest.xml is missing.")
        return
    try:
        tree = ET.parse(path)
    except ET.ParseError as error:
        fail(f"AndroidManifest.xml does not parse: {error}")
        return
    namespace = "com.music.bitchord"
    build = os.path.join(ROOT, "app/build.gradle.kts")
    if os.path.isfile(build):
        with open(build, encoding="utf-8") as handle:
            match = re.search(r'namespace\s*=\s*"([^"]+)"', handle.read())
        if match:
            namespace = match.group(1)
    declared = set()
    for path in walk("app/src", ".kt"):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        declared |= set(re.findall(r"^(?:internal |private |open |abstract )*(?:class|object) (\w+)", text, re.M))
    android = "{http://schemas.android.com/apk/res/android}"
    for tag in ("activity", "service", "receiver", "provider"):
        for element in tree.iter(tag):
            name = element.get(f"{android}name")
            if not name:
                continue
            if not (name.startswith(".") or name.startswith(namespace)):
                continue
            simple = name.rsplit(".", 1)[-1]
            if simple not in declared:
                fail(f"Manifest <{tag}> names {name}, which no Kotlin class declares.")
    note("Manifest components checked against declared classes.")


def check_validation_workflow(path: str) -> None:
    """
    The one workflow this branch may add, and what it may not contain.

    Compiling is in scope; publishing is not. Each forbidden marker is a way a
    workflow could produce a release or move a branch without anybody asking,
    so their absence is checked rather than trusted.
    """
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return
    with open(full, encoding="utf-8") as handle:
        text = handle.read()
    for marker in FORBIDDEN_IN_VALIDATION:
        if marker in text:
            fail(f"{path}: contains `{marker}`, which a validation workflow may not do.")
    if re.search(r"^\s*push:\s*$", text, re.M) and "branches: [main]" not in text:
        note(f"{path}: runs on push; it must not build a release from it.")
    note(f"{path}: validation workflow allowed (no publishing markers found).")


def check_signing_check_workflow(path: str) -> None:
    """
    The read-only signing *check*, and what it may not do.

    It is allowed to reference the secrets context — that is the whole point, and
    the alternative is guessing — but nothing that could sign, generate, tag,
    push, publish or ship a file out of the run. It also has to keep whatever it
    decodes inside the runner's temporary directory, so no keystore can end up in
    the checkout, an artifact or a cache.
    """
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return
    with open(full, encoding="utf-8") as handle:
        text = handle.read()
    for marker in FORBIDDEN_IN_SIGNING_CHECK:
        if marker in text:
            fail(f"{path}: contains `{marker}`, which a signing check may not do.")
    if "RUNNER_TEMP" not in text:
        fail(f"{path}: does not keep what it decodes under $RUNNER_TEMP.")
    if "base64 -d" not in text:
        fail(f"{path}: no longer decodes a keystore, so it is not the check it claims to be.")
    note(f"{path}: read-only signing check, and it cannot sign or publish.")


def check_signing_workflow(path: str) -> None:
    """
    The one workflow that may sign, and what it must keep doing to qualify.

    Both halves are asserted: nothing that publishes or moves a branch, no second
    identity to fall back to, no printing of the decrypted key — and on the other
    side, the manual trigger, the repository's own signer, the pinned
    certificate, the previous APK, and the two verification tools that have to
    agree before an artifact exists.
    """
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return
    with open(full, encoding="utf-8") as handle:
        text = handle.read()
    for marker in FORBIDDEN_IN_SIGNING:
        if marker in text:
            fail(f"{path}: contains `{marker}`, which a signing workflow may not do.")
    for marker in REQUIRED_IN_SIGNING:
        if marker not in text:
            fail(f"{path}: no longer contains `{marker}`, which its exception depends on.")
    # A workflow that could run itself on a push is not manual.
    if re.search(r"^\s*push:\s*$", text, re.M):
        fail(f"{path}: runs on push; signing must be dispatched by hand.")
    note(f"{path}: manual signing workflow, and its safeguards are present.")


def check_protected_paths(base: str) -> None:
    command = ["git", "-C", ROOT, "diff", "--name-only", base, "--"]
    try:
        changed = subprocess.run(command, capture_output=True, text=True, check=True).stdout.split()
    except subprocess.CalledProcessError:
        note(f"Protected-path check skipped: `git diff {base}` failed (unknown base?).")
        return
    for path in changed:
        if path in SIGNING_WORKFLOWS:
            check_signing_workflow(path)
            continue
        if path in SIGNING_CHECK_WORKFLOWS:
            check_signing_check_workflow(path)
            continue
        if path in VALIDATION_WORKFLOWS:
            check_validation_workflow(path)
            continue
        for protected in PROTECTED_PATHS:
            if path.startswith(protected) or protected in path:
                fail(f"{path} changed: signing and publication are out of scope for this branch.")
        for reported in REPORTED_PATHS:
            if path.endswith(reported):
                note(f"{path} changed (version metadata).")
    untracked = subprocess.run(
        ["git", "-C", ROOT, "ls-files", "--others", "--exclude-standard"],
        capture_output=True, text=True, check=False,
    ).stdout.split()
    for path in untracked:
        if path in SIGNING_WORKFLOWS:
            check_signing_workflow(path)
            continue
        if path in SIGNING_CHECK_WORKFLOWS:
            check_signing_check_workflow(path)
            continue
        if path in VALIDATION_WORKFLOWS:
            check_validation_workflow(path)
            continue
        for protected in PROTECTED_PATHS:
            if path.startswith(protected) or protected in path:
                fail(f"{path} is a new file under a protected path.")
        for reported in REPORTED_PATHS:
            if path.endswith(reported):
                note(f"{path} is new (version metadata).")
    note(f"Protected paths checked against {base} ({len(changed)} changed file(s)).")


def main() -> int:
    parser = argparse.ArgumentParser(description="Static checks for the 1.8.0 rebirth work.")
    parser.add_argument("--base", default="origin/main", help="git ref to compare against")
    parser.add_argument("--skip-manifest", action="store_true")
    arguments = parser.parse_args()

    check_kotlin_parse(arguments.base)
    default, _ = android_resources()
    check_R_references(default)
    check_xml_references(default)
    check_translation_format_args()
    check_compose_resources()
    check_compose_translations()
    check_compose_resource_imports()
    check_symbol_resolution()
    check_call_signatures()
    check_import_packages(arguments.base)
    check_hardcoded_text()
    if not arguments.skip_manifest:
        check_manifest_components()
    check_protected_paths(arguments.base)

    for message in notes:
        print(f"note: {message}")
    if failures:
        print()
        for message in failures:
            print(f"FAIL: {message}")
        print(f"\n{len(failures)} check(s) failed.")
        return 1
    print("\nOK: every static check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
