#!/usr/bin/env python3
"""Drives the installed isolated test APK through adb and stores captures.

Every step records what was really found on screen; a missing control is a
recorded failure, never a silent pass.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = os.environ.get("TEST_PACKAGE", "org.nexlibre.totalmusic.plus.test")
OUT = Path(sys.argv[1]); SHOTS = OUT / "tour"; SHOTS.mkdir(parents=True, exist_ok=True)
results = []


def adb(*args, timeout=60, binary=False):
    proc = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    return proc.stdout if binary else proc.stdout.decode(errors="replace")


def shot(name):
    data = adb("exec-out", "screencap", "-p", binary=True)
    (SHOTS / f"{name}.png").write_bytes(data)


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/plus-window.xml")
        xml = adb("exec-out", "cat", "/sdcard/plus-window.xml")
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            time.sleep(1); continue
        found = []
        for node in root.iter("node"):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if not m: continue
            x1, y1, x2, y2 = map(int, m.groups())
            found.append({"text": node.get("text", ""), "desc": node.get("content-desc", ""),
                          "box": (x1, y1, x2, y2), "clickable": node.get("clickable") == "true"})
        return found
    return []


def labels(found=None):
    found = nodes() if found is None else found
    return [n["text"] or n["desc"] for n in found if n["text"] or n["desc"]]


def find(pattern, found=None, last=False):
    found = nodes() if found is None else found
    rx = re.compile(pattern, re.I)
    hits = [n for n in found if rx.search(n["text"]) or rx.search(n["desc"])]
    if not hits: return None
    return hits[-1] if last else hits[0]


def tap_node(node):
    x1, y1, x2, y2 = node["box"]
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap(pattern, wait=1.5, last=False, required=True):
    node = find(pattern, last=last)
    if node is None:
        if required: record(f"tap {pattern}", False, "not on screen: " + " | ".join(labels())[:700])
        return False
    tap_node(node); time.sleep(wait); return True


def record(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (" — " + detail if detail else ""), flush=True)


def expect(name, pattern, capture=None):
    found = nodes()
    ok = find(pattern, found) is not None
    record(name, ok, "" if ok else "screen: " + " | ".join(labels(found))[:900])
    if capture: shot(capture)
    return ok


def launch():
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.music.bitchord.MainActivity")
    time.sleep(8)


def alive():
    return bool(adb("shell", "pidof", PKG).strip())


def media_state():
    text = adb("shell", "dumpsys", "media_session")
    block = text[text.find(PKG):] if PKG in text else ""
    m = re.search(r"state=PlaybackState \{state=(\w+)\((\d+)\)|state=PlaybackState \{state=(\d+)", block)
    pos = re.search(r"position=(\d+)", block)
    return (m.group(0) if m else "none"), (int(pos.group(1)) if pos else -1)


def main():
    launch()
    shot("00-first-launch")
    print("FIRST SCREEN:", " | ".join(labels())[:1500], flush=True)
    record("process alive after launch", alive())
    summary = "\n".join(f"{'PASS' if ok else 'FAIL'}\t{name}\t{detail}" for name, ok, detail in results)
    (OUT / "tour-results.tsv").write_text(summary + "\n")
    failed = [r for r in results if not r[1]]
    print(f"TOUR: {len(results) - len(failed)} passed, {len(failed)} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
