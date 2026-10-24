#!/usr/bin/env python3
"""Publish small, verifiable performance evidence through the Checks API.

Actions artifacts remain the source for APKs, traces and full logs.  This check
run deliberately carries only small text evidence so it can still be read when
an artifact CDN is unreachable.  It never claims a physical-device result or an
improvement.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import statistics
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "device-results"
MAX_TEXT = 60_000


def read(path: Path) -> str:
    try:
        return path.read_text(errors="replace").strip()
    except OSError:
        return ""


def redacted(text: str) -> str:
    return re.sub(r"(?i)(password|secret|token)(\s*[=:]\s*)\S+", r"\1\2[redacted]", text)


def percentile(values: list[float], fraction: float) -> float:
    """Linear-interpolated percentile, with the method recorded in the output."""
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def csv_evidence(path: Path) -> tuple[str, str]:
    text = read(path)
    if not text:
        return "(CSV no disponible; la ejecución no terminó correctamente)", ""
    rows = list(csv.DictReader(text.splitlines()))
    grouped: dict[tuple[str, str], list[float]] = {}
    for row in rows:
        for metric in ("submit_ms", "is_playing_ms"):
            try:
                value = float(row[metric])
            except (KeyError, TypeError, ValueError):
                continue
            if math.isfinite(value):
                grouped.setdefault((row.get("queue_size", "?"), metric), []).append(value)
    summary = [
        "queue_size,metric,n,p50,p90,p95,p99,min,max",
    ]
    for (queue_size, metric), values in sorted(
        grouped.items(), key=lambda item: (int(item[0][0]) if item[0][0].isdigit() else item[0][0], item[0][1])
    ):
        summary.append(
            ",".join(
                [
                    queue_size,
                    metric,
                    str(len(values)),
                    *(f"{percentile(values, p):.3f}" for p in (0.50, 0.90, 0.95, 0.99)),
                    f"{min(values):.3f}",
                    f"{max(values):.3f}",
                ]
            )
        )
    method = "Percentiles: interpolación lineal; n=8 por cola es exploratorio, no validación del Infinix."
    return method + "\n" + "\n".join(summary), text


def main() -> int:
    run_url = (
        f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/"
        f"{os.environ.get('GITHUB_REPOSITORY', '')}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    )
    status = read(RESULTS / "overall-status.txt")
    complete = status == "0"
    parts = [
        "# Total Music: evidencia automatizada de rendimiento",
        "",
        "**Alcance:** emulador Android 13 en un runner de GitHub; no es una medición del Infinix X6528 ni de audio audible.",
        "La comparación usa el mismo harness y el mismo emulador para base y candidato. No se infiere una mejora a partir de estas filas aisladas.",
        f"Run: {run_url}",
        f"Estado de la recolección: {'completa' if complete else 'incompleta o fallida; revisar los archivos y el estado del job'}",
        "",
    ]
    for variant in ("baseline", "candidate"):
        source = read(RESULTS / f"{variant}-source.txt") or "desconocido"
        csv_path = RESULTS / f"{variant}-local-playback-performance.csv"
        summary, raw = csv_evidence(csv_path)
        parts.extend([f"## {variant}", f"Source: `{source}`", "", "### Resumen", "```csv", summary, "```", ""])
        if raw:
            parts.extend(["### CSV crudo", "```csv", raw, "```", ""])
        else:
            parts.extend([summary, ""])

    if not complete:
        for variant in ("baseline", "candidate"):
            build_log = read(RESULTS / f"{variant}-build.log")
            if build_log:
                parts.extend([
                    f"## {variant}: tail del build de Gradle (redacted)",
                    "```text",
                    redacted(build_log[-12_000:]).replace("```", "` ` `"),
                    "```",
                    "",
                ])

    for name in ("apk-sha256.txt", "environment.txt"):
        content = read(RESULTS / name)
        if content:
            parts.extend([f"## {name}", "```text", content, "```", ""])
    sums = read(ROOT / "release-artifacts" / "SHA256SUMS.txt")
    if sums:
        parts.extend(["## SHA256 de artefactos empaquetados", "```text", sums, "```", ""])

    text = "\n".join(parts)
    if len(text) > MAX_TEXT:
        text = text[:MAX_TEXT] + "\n\n[Texto recortado; el CSV y los logs completos están en el artefacto del run.]"
    repository = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("GITHUB_SHA")
    token = os.environ.get("GITHUB_TOKEN")
    if not repository or not sha or not token:
        print("Faltan GITHUB_REPOSITORY, GITHUB_SHA o GITHUB_TOKEN; no se publica el check", file=sys.stderr)
        return 1
    payload = {
        "name": "total-music-performance-evidence",
        "head_sha": sha,
        "status": "completed",
        "conclusion": "success" if complete else "neutral",
        "details_url": run_url,
        "output": {
            "title": "Evidencia automatizada (emulador; no Infinix)",
            "summary": "CSV y hashes publicados como alternativa al CDN de artefactos. No es una validación física ni una afirmación de mejora.",
            "text": text,
        },
    }
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repository}/check-runs",
        data=json.dumps(payload).encode(),
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
            "User-Agent": "total-music-performance-evidence",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            body = json.load(response)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        print(f"No se pudo publicar el check de evidencia: {error}", file=sys.stderr)
        return 1
    print(f"Check de evidencia creado: {body.get('id')} {body.get('html_url')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
