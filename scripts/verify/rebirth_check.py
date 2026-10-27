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
    missing = sorted(name for name in used if name.startswith("rebirth_") and any(
        name not in locales[locale]["string"] for locale in locales if locale != "values"))
    if missing:
        note(
            "Compose strings used by the rebirth player but translated in no locale besides "
            f"the default: {', '.join(missing)}",
        )
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


def check_protected_paths(base: str) -> None:
    command = ["git", "-C", ROOT, "diff", "--name-only", base, "--"]
    try:
        changed = subprocess.run(command, capture_output=True, text=True, check=True).stdout.split()
    except subprocess.CalledProcessError:
        note(f"Protected-path check skipped: `git diff {base}` failed (unknown base?).")
        return
    for path in changed:
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
