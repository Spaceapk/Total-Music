#!/usr/bin/env python3
"""Publish public APK identity/install limits through the GitHub Checks API."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import urllib.error
import urllib.request


def main():
    root = Path(__file__).resolve().parents[2]
    metadata = json.loads(
        (root / "measurement-test-artifacts/measurement-test-verification.json").read_text()
    )
    text = "\n".join([
        "## Installable measurement test APK",
        "Release-like `prodMeasurement`; not a published release.",
        f"Package: `{metadata['application_id']}`",
        f"Version: `{metadata['version_name']} ({metadata['version_code']})`",
        f"APK SHA-256: `{metadata['apk_sha256']}`",
        f"Debug certificate SHA-256: `{metadata['certificate_sha256']}`",
        f"Expected permanent release certificate SHA-256: `{metadata['expected_permanent_release_certificate_sha256']}`",
        "Signature verified with apksigner; exactly one signer.",
        f"Debuggable: `{metadata['debuggable']}`; profileable: `{metadata['profileable']}`.",
        "The release private key was not accessed and no release was published.",
        "",
        "### Installation limits",
        "The applicationId is unchanged. This APK cannot coexist with another",
        "installation of the same package in the same Android user/profile.",
        "It cannot update an installation signed with the permanent release",
        "certificate (or any different certificate) in place. Export a backup",
        "before any uninstall; do not delete music files.",
        "",
        "### No-ADB latency log",
        "Start one song, then change to another. Use the current song menu >",
        "Copy log and preserve whether each sample was first start or change.",
        "TIMING lines cover queue request/build/submit and isPlaying callback.",
        "isPlaying is not proof of audible speaker output.",
        f"Source: `{metadata['source_commit']}`",
        f"Artifact run: {os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/"
        f"{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    ])
    payload = {
        "name": "total-music-measurement-test-apk",
        "head_sha": os.environ["GITHUB_SHA"],
        "status": "completed",
        "conclusion": "success",
        "completed_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "output": {
            "title": "Total Music 1.6.0 installable measurement test",
            "summary": "Signed test APK verified; package, certificate and installation limits recorded.",
            "text": text,
        },
    }
    request = urllib.request.Request(
        f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/check-runs",
        data=json.dumps(payload).encode(),
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        print(f"GitHub Checks API returned HTTP {error.code}: {error.read().decode()[:2000]}")
        raise
    print(f"Published measurement check {result['id']}: {result.get('html_url', '')}")


if __name__ == "__main__":
    main()
