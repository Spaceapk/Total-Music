#!/usr/bin/env python3
"""Drive Total Music on a booted emulator and record what happens.

This exists because the 1.8.0 rebirth shell is a Compose surface that cannot be
exercised on a JVM: the crashes reported against it (a close when coming back
from Settings or from the player, and a close on startup after an update) are
runtime failures in composition, layout or drawing. A parser cannot see them and
a unit test never composes them.

It is deliberately dependency-free: `adb` plus the standard library, so the CI
runner needs nothing installed beyond the Android platform tools.

Every step prints a marker, keeps the process id before and after, and reports
the first frame of `logcat -b crash` that appeared since the previous step. Exit
status is non-zero when the process died or the crash buffer gained an entry, so
a workflow can fail on it, and the transcript is what the workflow posts back.

Usage (the workflow does this; see .github/workflows/total-music-181-validate.yml):

    python3 scripts/verify/device_probe.py --label 181-glass --flows settings,player,categories
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE_DEFAULT = "org.nexlibre.totalmusic"
ACTIVITY_DEFAULT = "com.music.bitchord.MainActivity"


class Device:
    def __init__(self, serial: str | None, package: str, activity: str) -> None:
        self.serial = serial
        self.package = package
        self.activity = activity
        self.failures: list[str] = []
        self.notes: list[str] = []
        self.crash_seen = 0

    # ---- plumbing ---------------------------------------------------------

    def adb(self, *args: str, timeout: int = 60, check: bool = False) -> subprocess.CompletedProcess:
        command = ["adb"]
        if self.serial:
            command += ["-s", self.serial]
        command += list(args)
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
        if check and result.returncode != 0:
            raise RuntimeError(f"{' '.join(command)} failed: {result.stderr.strip()}")
        return result

    def shell(self, command: str, timeout: int = 60) -> str:
        return self.adb("shell", command, timeout=timeout).stdout

    def wait_boot(self, timeout: int = 900) -> None:
        self.adb("wait-for-device", timeout=timeout)
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.shell("getprop sys.boot_completed").strip() == "1":
                self.shell("input keyevent 82")
                time.sleep(3)
                return
            time.sleep(3)
        raise RuntimeError("the emulator never finished booting")

    # ---- crash bookkeeping -------------------------------------------------

    def crash_buffer(self) -> str:
        return self.adb("logcat", "-d", "-b", "crash", "-v", "brief", timeout=60).stdout

    def pid(self) -> str:
        return self.shell(f"pidof {self.package}").strip()

    def mark(self, label: str) -> int:
        """Remember how much crash output exists before a step runs."""
        self.crash_seen = len(self.crash_buffer())
        print(f"::group::{label}")
        return self.crash_seen

    def check(self, label: str, pid_before: str | None = None) -> bool:
        """Report a death or a new crash entry. True when the app is still up."""
        time.sleep(1)
        crash = self.crash_buffer()
        pid = self.pid()
        fresh = crash[self.crash_seen:] if len(crash) > self.crash_seen else ""
        alive = bool(pid)
        if pid_before is not None and not alive:
            self.failures.append(f"{label}: the process is gone (was {pid_before})")
        if fresh.strip():
            self.failures.append(f"{label}: crash log written")
            exits = self.shell(
                f"dumpsys activity exit-info {self.package} 2>/dev/null | head -30", timeout=60
            ).strip()
            if exits:
                print("---- exit-info ----")
                print(exits[:4000])
                print("-------------------")
            print("---- new crash log ----")
            print(fresh.strip()[:8000])
            print("-----------------------")
        print(f"{label}: pid={'alive' if alive else 'DEAD'} crash={'yes' if fresh.strip() else 'no'}")
        print("::endgroup::")
        return alive and not fresh.strip()

    # ---- ui ----------------------------------------------------------------

    def dump(self) -> str:
        for _ in range(3):
            self.adb("shell", "uiautomator dump /sdcard/window_dump.xml", timeout=90)
            xml = self.adb("shell", "cat /sdcard/window_dump.xml", timeout=60).stdout
            if xml.strip().startswith("<?xml"):
                return xml
            time.sleep(1)
        return ""

    @staticmethod
    def nodes(xml: str) -> list[tuple[str, str, tuple[int, int, int, int]]]:
        found = []
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return found
        for node in root.iter("node"):
            label = (node.get("text") or "").strip()
            description = (node.get("content-desc") or "").strip()
            bounds = re.findall(r"\d+", node.get("bounds") or "")
            if len(bounds) != 4:
                continue
            x1, y1, x2, y2 = (int(value) for value in bounds)
            if label:
                found.append((label, "text", (x1, y1, x2, y2)))
            if description:
                found.append((description, "desc", (x1, y1, x2, y2)))
        return found

    def find(self, pattern: str, last: bool = False):
        matcher = re.compile(pattern, re.IGNORECASE)
        hits = [node for node in self.nodes(self.dump()) if matcher.search(node[0])]
        if not hits:
            return None
        return hits[-1] if last else hits[0]

    def tap(self, pattern: str, last: bool = False, timeout: int = 25) -> bool:
        deadline = time.time() + timeout
        while time.time() < deadline:
            hit = self.find(pattern, last=last)
            if hit:
                x1, y1, x2, y2 = hit[2]
                self.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
                return True
            time.sleep(1)
        return False

    def back(self) -> None:
        self.shell("input keyevent 4")

    def visible(self, pattern: str, timeout: int = 25) -> bool:
        deadline = time.time() + timeout
        matcher = re.compile(pattern, re.IGNORECASE)
        while time.time() < deadline:
            if any(matcher.search(node[0]) for node in self.nodes(self.dump())):
                return True
            time.sleep(1)
        return False

    # ---- app ---------------------------------------------------------------

    def launch(self, label: str) -> str:
        self.shell(f"am force-stop {self.package}")
        time.sleep(2)
        self.adb(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            f"{self.package}/{self.activity}",
            timeout=90,
        )
        time.sleep(6)
        return self.pid()

    def grant_media(self) -> None:
        for permission in ("android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS"):
            self.adb("shell", "pm", "grant", self.package, permission, timeout=30)

    def push_tone(self, name: str = "Probe Tone.wav", seconds: int = 3) -> None:
        """A real, decodable track so the library, albums and the player fill."""
        import math
        import struct
        import wave

        path = f"/tmp/{name}"
        with wave.open(path, "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(44100)
            frames = bytearray()
            for index in range(44100 * seconds):
                sample = int(6000 * math.sin(2 * math.pi * 440 * index / 44100))
                frames += struct.pack("<h", sample)
            handle.writeframes(bytes(frames))
        self.adb("push", path, f"/sdcard/Music/{name}", timeout=60)
        self.shell(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE "
            f"-d file:///sdcard/Music/{name.replace(' ', '%20')}",
            timeout=60,
        )
        time.sleep(3)
        indexed = self.shell(
            "content query --uri content://media/external/audio/media "
            "--projection _display_name --where \"_display_name LIKE 'Probe%'\"",
            timeout=60,
        ).strip()
        self.notes.append(
            "media store row for the tone: "
            + (indexed.splitlines()[-1] if indexed.strip() else "NONE")
        )

    def set_style(self, style: str) -> bool:
        """Write the player presentation straight into the app's preferences.

        The alternative is tapping through Settings for every run, which makes a
        failure ambiguous: a run that never reached the switch looks exactly
        like a run that crashed before it. Writing the file is a precondition, so
        a probe always starts in the style it claims to test.

        Needs root: the emulator image used by the workflow is google_apis, where
        `adb root` is allowed. For a debuggable build it falls back to `run-as`.
        """
        xml = f"""<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="player_visual_style">{style}</string>
    <boolean name="player_glass_effects" value="true" />
    <boolean name="liquid_glass" value="true" />
    <boolean name="player_animated_transitions" value="true" />
    <boolean name="total_music_expressive" value="true" />
</map>
"""
        local = "/tmp/bitchord_settings.xml"
        with open(local, "w", encoding="utf-8") as handle:
            handle.write(xml)
        self.adb("push", local, "/data/local/tmp/bitchord_settings.xml", timeout=60)
        target = f"/data/data/{self.package}/shared_prefs/bitchord_settings.xml"
        # Root first; the google_apis image allows it. A build without root
        # falls through to the debuggable-build route below.
        self.adb("root", timeout=60)
        time.sleep(3)
        self.adb("wait-for-device", timeout=120)
        who = self.shell("id").strip()
        self.notes.append(f"shell identity: {who}")
        self.shell(f"mkdir -p /data/data/{self.package}/shared_prefs")
        written = self.adb(
            "shell",
            f"cp /data/local/tmp/bitchord_settings.xml {target} && echo written",
            timeout=60,
        ).stdout
        if "written" not in written:
            written = self.adb(
                "shell",
                f"run-as {self.package} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/bitchord_settings.xml' "
                "< /data/local/tmp/bitchord_settings.xml && echo written",
                timeout=60,
            ).stdout
        uid = self.shell(f"stat -c %u /data/data/{self.package}").strip()
        gid = self.shell(f"stat -c %g /data/data/{self.package}").strip()
        if uid.isdigit():
            self.shell(f"chown {uid}:{gid or uid} {target}")
            self.shell(f"chmod 660 {target}")
        stored = self.shell(f"grep -o 'player_visual_style\">[A-Z_]*' {target}")
        print(f"style file: {stored.strip() or 'NOT WRITTEN'}")
        return style in stored


def run_flows(device: Device, flows: list[str], style: str) -> None:
    print(f"== flows {flows} in {style} ==")
    device.mark(f"launch {style}")
    pid = device.launch(f"launch {style}")
    if not pid:
        device.failures.append(f"launch {style}: the app did not stay up")
        device.check(f"launch {style}")
        return
    settled = device.visible(r"^(Library|Biblioteca|Songs|Canciones)$", timeout=45)
    device.notes.append(f"{style}: library visible after launch = {settled}")
    device.check(f"launch {style}", pid)

    if "categories" in flows:
        device.mark(f"categories {style}")
        for category in ("Albums", "Artists", "Playlists", "Songs"):
            if not device.tap(rf"^{category}$", timeout=12):
                device.notes.append(f"{style}: category '{category}' not on screen")
                continue
            time.sleep(2)
            device.check(f"{style} category {category}", pid)
        device.notes.append(f"{style}: categories exercised")

    if "settings" in flows:
        device.mark(f"settings {style}")
        if device.tap(r"^(Settings|Ajustes)$", timeout=20):
            time.sleep(3)
            device.check(f"{style} settings open", pid)
            for _ in range(3):
                device.back()
                time.sleep(2)
                if not device.pid():
                    break
            device.check(f"{style} returned from settings", pid)
        else:
            device.notes.append(f"{style}: no settings button found")

    if "player" in flows:
        device.mark(f"player {style}")
        # Start the track from the library, then reopen the player from the
        # mini player at the foot of the page — the route the report describes.
        started = device.tap(r"Probe Tone", timeout=20)
        time.sleep(4)
        if started:
            device.check(f"{style} track started", pid)
        opened = device.tap(r"Probe Tone", last=True, timeout=20)
        time.sleep(4)
        device.check(f"{style} player opened", pid)
        for _ in range(2):
            device.back()
            time.sleep(3)
            if not device.pid():
                break
        device.check(f"{style} returned from player", pid)
        if not started:
            device.notes.append(f"{style}: the probe track never appeared in the library")

    if "reopen" in flows:
        # Leaving and re-entering the shell through the tabs, which is the other
        # half of "coming back to Library".
        device.mark(f"reopen {style}")
        device.tap(r"^(Library|Biblioteca)$", timeout=15)
        time.sleep(2)
        device.back()
        time.sleep(3)
        device.check(f"{style} after tab round trip", pid)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--package", default=PACKAGE_DEFAULT)
    parser.add_argument("--activity", default=ACTIVITY_DEFAULT)
    parser.add_argument("--label", default="probe")
    parser.add_argument("--apk", help="install this APK before probing")
    parser.add_argument("--styles", default="GOOGLE_EXPRESSIVE_PLUS,LIQUID_GLASS_PLUS")
    parser.add_argument("--flows", default="categories,settings,player,reopen")
    parser.add_argument("--tone", action="store_true", help="push a decodable test track first")
    parser.add_argument("--launch-only", action="store_true")
    args = parser.parse_args()

    device = Device(args.serial, args.package, args.activity)
    device.wait_boot()
    print(device.shell("getprop ro.build.version.release").strip(), "Android")
    device.shell("settings put global window_animation_scale 0")
    device.shell("settings put global transition_animation_scale 0")
    device.shell("settings put global animator_duration_scale 0")

    if args.apk:
        installed = device.adb("install", "-r", "-g", args.apk, timeout=600)
        print((installed.stdout or installed.stderr).strip()[:2000])
        if installed.returncode != 0:
            print("INSTALL FAILED")
            return 1
    device.shell(f"pm clear {args.package} || true")
    device.grant_media()
    if args.tone:
        device.push_tone()

    styles = [style.strip() for style in args.styles.split(",") if style.strip()]
    flows = [flow.strip() for flow in args.flows.split(",") if flow.strip()]
    for style in styles:
        device.set_style(style)
        device.shell(f"am force-stop {args.package}")
        if args.launch_only:
            device.mark(f"startup {style}")
            pid = device.launch(f"startup {style}")
            device.check(f"startup {style}", None)
            if not pid:
                device.failures.append(f"startup {style}: the app did not stay up")
            continue
        run_flows(device, flows, style)

    print("\n== notes ==")
    for note in device.notes:
        print(f"- {note}")
    print("\n== failures ==")
    if device.failures:
        for failure in device.failures:
            print(f"- {failure}")
        return 1
    print("- none")
    return 0


if __name__ == "__main__":
    sys.exit(main())
