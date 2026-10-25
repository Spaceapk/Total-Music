#!/usr/bin/env python3
"""Verify and package the independent Total Music Debug diagnostic APK."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

from sign_apk import certificate, configuration, identity, run

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "diagnostic-test-artifacts"
DEBUG_SUFFIX = ".debug"
DEBUG_LABEL = "Total Music Debug"


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def one_apk(directory, description):
    candidates = sorted(directory.glob("**/*.apk"))
    if len(candidates) != 1:
        raise ValueError(f"Expected one {description}, found: {[str(p) for p in candidates]}")
    return candidates[0]


def main():
    config = configuration()
    tools = Path(os.environ.get("ANDROID_BUILD_TOOLS", ""))
    if not tools.is_dir():
        raise ValueError("ANDROID_BUILD_TOOLS must point to the installed Android build-tools")
    aapt = tools / "aapt"
    apksigner = tools / "apksigner"
    # The ABI split produces several APKs; only the universal one is a simple
    # phone download. Select it explicitly.
    app_output = ROOT / "app/build/outputs/apk/prod/diagnostic"
    universal = sorted(
        path for path in app_output.glob("*.apk")
        if "universal" in path.name and "unsigned" not in path.name
    )
    if len(universal) != 1:
        raise ValueError(f"Expected one signed universal diagnostic APK, found: {universal}")
    source = universal[0]
    info = identity(aapt, source)
    expected_id = config["applicationId"] + DEBUG_SUFFIX
    expected_identity = {
        "application_id": expected_id,
        "version_code": int(config["versionCode"]),
        "version_name": config["versionName"],
        "debuggable": False,
    }
    if info != expected_identity:
        raise ValueError(f"Wrong diagnostic APK identity: {info!r}; expected {expected_identity!r}")
    badging = run([aapt, "dump", "badging", source])
    if f"application-label:'{DEBUG_LABEL}'" not in badging:
        raise ValueError("The diagnostic APK does not expose the Total Music Debug label")
    fingerprint = certificate(apksigner, source)
    permanent = config["certificateSha256"].lower()
    if fingerprint == permanent:
        raise ValueError("The diagnostic APK unexpectedly uses the permanent release certificate")
    manifest = run([aapt, "dump", "xmltree", source, "AndroidManifest.xml"])
    provider_authority = expected_id + ".fileprovider"
    if "profileable" not in manifest:
        raise ValueError("Diagnostic manifest is missing profileable")
    if provider_authority not in manifest:
        raise ValueError(f"Diagnostic FileProvider authority is missing: {provider_authority}")

    benchmark_source = one_apk(
        ROOT / "macrobenchmark/build/outputs/apk/diagnostic",
        "diagnostic Macrobenchmark APK",
    )
    OUTPUT.mkdir(parents=True, exist_ok=True)
    target = OUTPUT / f"Total-Music-{info['version_name']}-debug-diagnostic.apk"
    benchmark_target = OUTPUT / f"Total-Music-{info['version_name']}-debug-macrobenchmark.apk"
    shutil.copy2(source, target)
    shutil.copy2(benchmark_source, benchmark_target)
    apk_digest = sha256(target)
    benchmark_digest = sha256(benchmark_target)
    source_commit = os.environ.get("GITHUB_SHA") or subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True
    ).stdout.strip()
    metadata = {
        "artifact_type": "independent installable release-like diagnostic APK",
        "build_variant": "prodDiagnostic",
        "source_commit": source_commit,
        **info,
        "main_application_id": config["applicationId"],
        "application_id_suffix": DEBUG_SUFFIX,
        "visible_label": DEBUG_LABEL,
        "profileable": True,
        "provider_authority": provider_authority,
        "signature_verified": True,
        "certificate_sha256": fingerprint,
        "expected_permanent_release_certificate_sha256": permanent,
        "uses_permanent_release_certificate": False,
        "release_private_key_accessed": False,
        "apk_sha256": apk_digest,
        "macrobenchmark_apk_sha256": benchmark_digest,
        "can_coexist_with_main_application": True,
        "requires_uninstalling_main_application": False,
        "audible_output_latency_measured_by_this_metadata": False,
    }
    (OUTPUT / "diagnostic-test-verification.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    (OUTPUT / "SHA256SUMS.txt").write_text(
        f"{apk_digest}  {target.name}\n{benchmark_digest}  {benchmark_target.name}\n",
        encoding="utf-8",
    )
    (OUTPUT / "README.txt").write_text(
        "Total Music Debug — diagnóstico independiente\n"
        "=============================================\n\n"
        f"Paquete: {info['application_id']}\n"
        f"Versión: {info['version_name']} ({info['version_code']})\n"
        f"Nombre visible: {DEBUG_LABEL}\n"
        f"SHA-256 APK: {apk_digest}\n"
        f"SHA-256 certificado debug: {fingerprint}\n\n"
        "Se instala junto a Total Music principal: usa otro applicationId y otra\n"
        "autoridad FileProvider. No desinstales ni reemplaces la aplicación\n"
        "principal. Android pedirá permiso de audio para esta copia por separado.\n\n"
        "PRUEBA SIN COMPUTADORA\n"
        "1. Abre Total Music Debug y concede el permiso de audio.\n"
        "2. Selecciona manualmente una canción de la biblioteca y espera a que\n"
        "   suene. Copia el registro desde el menú de la canción actual.\n"
        "3. Mientras suena, pulsa Siguiente y espera la nueva canción. Copia\n"
        "   también su registro. Repite ambos recorridos cinco veces.\n"
        "4. Comparte los textos etiquetados manual-1...5 y siguiente-1...5,\n"
        "   junto con el tiempo audible observado si lo mediste.\n\n"
        "Las líneas TIMING distinguen manual_library y next_button, recepción\n"
        "de la orden, construcción de cola, prepare, estados de audio y callback.\n"
        "sinceServiceMs es la comparación común; sinceRequestMs incluye el tap\n"
        "cuando el origen es UI. isPlaying no prueba el primer sonido audible.\n",
        encoding="utf-8",
    )
    print(json.dumps({"apk": str(target), "metadata": metadata}, indent=2))


if __name__ == "__main__":
    main()
