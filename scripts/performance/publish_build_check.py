#!/usr/bin/env python3
"""Publish a bounded, credential-redacted build log through the Checks API.

The Actions artifact/log CDNs have not been reliable in this environment. This
check is only a diagnostic channel; it does not claim that a physical benchmark
ran or that an unsigned APK is installable.
"""
from __future__ import annotations

import json
import os
import re
import urllib.request
from pathlib import Path

ROOT = Path(os.environ.get("GITHUB_WORKSPACE", "."))
ARTIFACTS = ROOT / "physical-benchmark-artifacts"
LOG = ARTIFACTS / "gradle.log"
EXIT = ARTIFACTS / "exit-code.txt"
run_id = os.environ.get("GITHUB_RUN_ID", "")
repo = os.environ["GITHUB_REPOSITORY"]
sha = os.environ["GITHUB_SHA"]
token = os.environ["GITHUB_TOKEN"]

try:
    code = int(EXIT.read_text().strip())
except (OSError, ValueError):
    code = 1
try:
    raw = LOG.read_text(errors="replace")
except OSError:
    raw = "No Gradle log was produced; setup may have failed before the build step."
# Keep the check below GitHub's output limits and avoid printing credentials if
# a local build ever accidentally includes them in an error message.
redacted = re.sub(r"(?i)(password|secret|token)(\s*[=:]\s*)\S+", r"\1\2[redacted]", raw)
tail = redacted[-24_000:]
conclusion = "success" if code == 0 else "failure"
status = "compilación correcta" if code == 0 else f"compilación fallida (exit {code})"
text = (
    "# Compilación del benchmark físico\n\n"
    "Este check cubre solo compilación. No ejecuta un Infinix, no mide frames y "
    "no convierte un APK unsigned en una actualización instalable.\n\n"
    f"Run: https://github.com/{repo}/actions/runs/{run_id}\n\n"
    f"Resultado: **{status}**.\n\n"
    "## Tail del log de Gradle (redacted)\n\n"
    "```text\n" + tail.replace("```", "` ` `") + "\n```"
)
payload = {
    "name": "total-music-physical-benchmark-build",
    "head_sha": sha,
    "status": "completed",
    "conclusion": conclusion,
    "details_url": f"https://github.com/{repo}/actions/runs/{run_id}",
    "output": {
        "title": f"Benchmark físico: {status}",
        "summary": "Diagnóstico de compilación; no es una medición física ni una afirmación de mejora.",
        "text": text,
    },
}
request = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/check-runs",
    data=json.dumps(payload).encode(),
    headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "X-GitHub-Api-Version": "2022-11-28",
    },
    method="POST",
)
with urllib.request.urlopen(request, timeout=30) as response:
    print("Published build diagnostic check:", response.status)
