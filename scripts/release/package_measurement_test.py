#!/usr/bin/env python3
"""Verify and package the installable, release-like measurement test APK.

This is deliberately not a release signer. The workflow supplies Gradle's
existing Android debug signing config for prodMeasurement; no private release
key is read, requested, generated, or included in the artifact.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

from sign_apk import certificate, configuration, identity, run

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "measurement-test-artifacts"


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def one_apk(directory, predicate, description):
    candidates = sorted(path for path in directory.glob("**/*.apk") if predicate(path))
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
    source = one_apk(
        ROOT / "app/build/outputs/apk/prod/measurement",
        lambda path: "universal" in path.name and "unsigned" not in path.name,
        "signed universal prodMeasurement APK",
    )
    info = identity(aapt, source)
    expected_identity = {
        "application_id": config["applicationId"],
        "version_code": int(config["versionCode"]),
        "version_name": config["versionName"],
        "debuggable": False,
    }
    if info != expected_identity:
        raise ValueError(f"Wrong test APK identity: {info!r}; expected {expected_identity!r}")
    fingerprint = certificate(apksigner, source)
    permanent = config["certificateSha256"].lower()
    if fingerprint == permanent:
        raise ValueError("The test artifact unexpectedly uses the permanent release certificate")
    manifest = run([aapt, "dump", "xmltree", source, "AndroidManifest.xml"])
    if "profileable" not in manifest:
        raise ValueError("prodMeasurement manifest is missing profileable")

    benchmark_source = one_apk(
        ROOT / "macrobenchmark/build/outputs/apk/measurement",
        lambda path: True,
        "measurement Macrobenchmark APK",
    )
    OUTPUT.mkdir(parents=True, exist_ok=True)
    target = OUTPUT / f"Total-Music-{info['version_name']}-measurement-test-debug-signed.apk"
    benchmark_target = OUTPUT / f"Total-Music-{info['version_name']}-macrobenchmark-measurement.apk"
    shutil.copy2(source, target)
    shutil.copy2(benchmark_source, benchmark_target)
    apk_digest = sha256(target)
    benchmark_digest = sha256(benchmark_target)
    source_commit = os.environ.get("GITHUB_SHA") or subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True
    ).stdout.strip()
    metadata = {
        "artifact_type": "installable release-like measurement test; Android debug certificate",
        "build_variant": "prodMeasurement",
        "source_commit": source_commit,
        **info,
        "profileable": True,
        "signature_verified": True,
        "certificate_sha256": fingerprint,
        "expected_permanent_release_certificate_sha256": permanent,
        "uses_permanent_release_certificate": False,
        "release_private_key_accessed": False,
        "apk_sha256": apk_digest,
        "macrobenchmark_apk_sha256": benchmark_digest,
        "same_application_id_as_release": True,
        "can_coexist_with_same_package_in_same_android_user": False,
        "can_update_permanent_release_in_place": False,
        "audible_output_latency_measured_by_this_metadata": False,
    }
    (OUTPUT / "measurement-test-verification.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    (OUTPUT / "SHA256SUMS.txt").write_text(
        f"{apk_digest}  {target.name}\n{benchmark_digest}  {benchmark_target.name}\n",
        encoding="utf-8",
    )
    (OUTPUT / "README.txt").write_text(
        "Total Music 1.6.0 — APK de prueba de medición\n"
        "=============================================\n\n"
        f"Paquete: {info['application_id']}\n"
        f"Versión: {info['version_name']} ({info['version_code']})\n"
        f"SHA-256 APK: {apk_digest}\n"
        f"SHA-256 certificado de prueba: {fingerprint}\n\n"
        "Es prodMeasurement: release-like, R8, no depurable y profileable.\n"
        "Está firmada con el certificado debug de Android, no con el certificado\n"
        "release permanente. No se accedió a ninguna clave privada release.\n\n"
        "INSTALACIÓN IMPORTANTE\n"
        "Conserva org.nexlibre.totalmusic: no puede convivir con otra APK del\n"
        "mismo paquete en el mismo usuario Android. Si la instalación actual usa\n"
        "el certificado release permanente (u otro certificado), Android no la\n"
        "actualizará in-place. Exporta primero un backup en Settings > Your data;\n"
        "solo desinstala la app si aceptas perder sus datos/ajustes. No borres tus\n"
        "archivos musicales. No hace falta desinstalar si el certificado coincide.\n\n"
        "PRUEBA SIN ADB\n"
        "Inicia una canción y luego cambia a otra. En el reproductor abre el menú\n"
        "de la canción actual y elige Copy log. Pega ese texto y conserva si fue\n"
        "primer inicio o cambio. Las líneas TIMING muestran solicitud de cola,\n"
        "construcción, envío y callback isPlaying. isPlaying NO prueba el instante\n"
        "en que el altavoz produjo sonido; anota también esa observación con un\n"
        "cronómetro o grabación si quieres medir latencia audible.\n",
        encoding="utf-8",
    )
    print(json.dumps({"apk": str(target), "metadata": metadata}, indent=2))


if __name__ == "__main__":
    main()
