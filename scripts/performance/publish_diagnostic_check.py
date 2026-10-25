#!/usr/bin/env python3
"""Publish independent diagnostic APK identity and test instructions."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import urllib.error
import urllib.request


def main():
    root = Path(__file__).resolve().parents[2]
    metadata = json.loads(
        (root / "diagnostic-test-artifacts/diagnostic-test-verification.json").read_text()
    )
    text = "\n".join([
        "## Total Music Debug — independent diagnostic APK",
        "Release-like `prodDiagnostic`; not a published release.",
        f"Package: `{metadata['application_id']}`",
        f"Main package preserved: `{metadata['main_application_id']}`",
        f"Label: `{metadata['visible_label']}`",
        f"Version: `{metadata['version_name']} ({metadata['version_code']})`",
        f"APK SHA-256: `{metadata['apk_sha256']}`",
        f"Debug certificate SHA-256: `{metadata['certificate_sha256']}`",
        f"FileProvider authority: `{metadata['provider_authority']}`",
        f"Expected permanent release certificate SHA-256: `{metadata['expected_permanent_release_certificate_sha256']}`",
        "Signature verified with apksigner; exactly one signer.",
        f"Debuggable: `{metadata['debuggable']}`; profileable: `{metadata['profileable']}`.",
        "The main applicationId and release signing configuration were not changed.",
        "The release private key was not accessed and no release was published.",
        "",
        "### Install beside the main app",
        "The diagnostic package and FileProvider authority are distinct. Do not",
        "uninstall the main app. Grant audio permission separately to Total Music",
        "Debug after installation.",
        "",
        "### No-computer comparison",
        "Copy logs after five manual library selections and five Next-button",
        "changes. Routes are manual_library and next_button. TIMING includes",
        "request receipt, queue build, session/player queue receipt, prepare,",
        "audio states and isPlaying. sinceServiceMs is the common comparison;",
        "sinceRequestMs additionally includes the UI tap for manual selection.",
        "isPlaying is not proof of audible speaker output.",
        f"Source: `{metadata['source_commit']}`",
        f"Artifact run: {os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/"
        f"{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    ])
    payload = {
        "name": "total-music-diagnostic-apk",
        "head_sha": os.environ["GITHUB_SHA"],
        "status": "completed",
        "conclusion": "success",
        "completed_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "output": {
            "title": "Total Music Debug independent diagnostic APK",
            "summary": "Independent package, label, provider and signature verified; no release published.",
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
    print(f"Published diagnostic check {result['id']}: {result.get('html_url', '')}")


if __name__ == "__main__":
    main()
