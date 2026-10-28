# Total Music — auditoría del procedimiento de firma permanente

Fecha: 28 de octubre de 2026.
Rama auditada: `arena/c83a7b6e-total-music` (`ed5926b`), PR #5.
Alcance: cómo se firmaron 1.7.0 y 1.8.0, y si existe hoy un mecanismo autorizado
para firmar 1.8.1 con el mismo certificado.

## Resumen

**No existe ningún mecanismo de firma en GitHub Actions, y nunca lo hubo.** La
firma permanente de 1.7.0 y 1.8.0 se hizo **fuera de CI**, a mano, con
`scripts/release/sign_apk.py` y el keystore guardado fuera del repositorio.

Ese procedimiento está **íntegro y disponible** en el repositorio, con todas sus
comprobaciones. Lo que falta no es código: son los dos archivos de credenciales,
que no están en este entorno, ni en los secretos del repositorio, ni en el
historial de git.

Por eso **no se ha podido ejecutar** y **no se ha creado ninguna firma nueva**.

## 1. Cómo se firmaron 1.7.0 y 1.8.0

Cuatro pruebas independientes, que coinciden:

| Evidencia | Resultado |
| --- | --- |
| Autor de la release `v1.7.0` (API) | `Spaceapk`, tipo `User` — **no** `github-actions[bot]` |
| Autor de la release `v1.8.0-preview.1` (API) | `Spaceapk`, tipo `User` |
| `docs/verification-1.7.0.md:25` | «La unión se alineó con zipalign y **se firmó mediante `scripts/release/sign_apk.py`**» |
| `docs/verification-1.7.0.md:31` | «Las credenciales de firma permanecieron **fuera del repositorio** y no se enviaron a CI» |
| `docs/migration/README.md` (Release gate) | «**No new Android release key is created** […] The original encrypted keystore, separate password file and previous signed APK are required by `sign_apk.py` […] **CI outputs a TEST-ONLY debug APK and an unsigned release preparation, never a stable release**» |
| `docs/design/1.8.0/VALIDATION.md` | La firma figura como bloqueada; el único secreto que referenciaba cualquier workflow era `TOTAL_MUSIC_CANDIDATE_URL` |

El patrón histórico lo confirma desde otro ángulo. El workflow
`docs/upstream-workflows/publish-signed-total-music.yml` (paquete
`org.nexlibre.spacemusic.spatial`, certificado `dbb5b7c0…`, **otra** aplicación)
no firma nada: **descarga un APK ya firmado** desde la release de otro
repositorio y lo verifica. Es decir, incluso donde había publicación automática,
la firma ocurría antes y en otro sitio.

## 2. Auditoría de los 19 workflows

Se revisaron todos los archivos que alguna vez existieron bajo
`.github/workflows/` en cualquier rama:

- **19** archivos en total.
- **0** invocan `apksigner sign`.
- **0** referencian un keystore de Total Music, `keystore.properties`,
  `signingConfig` o firma inyectada por Gradle.
- **3** mencionan algo relacionado con firma, y ninguno firma:

| Workflow | Qué hace con la firma |
| --- | --- |
| `total-music-150-install.yml` | `apksigner **verify**` sobre un APK candidato descargado de `TOTAL_MUSIC_CANDIDATE_URL`, y exige `9ae145a7…`. Verifica; no firma. |
| `total-music-181-validate.yml` | Nombra `sign_apk.py` y `TOTAL_MUSIC_KEYSTORE` **solo dentro del texto** de `SIGNING-STATUS.txt`, como instrucciones para el lector. No los invoca. |
| `total-music-181-signing-check.yml` | Comprueba **presencia** de secretos e imprime la huella pública. No firma (y el gate le prohíbe firmar). |

Los otros 16 no tocan la firma en absoluto.

## 3. Estado de las credenciales

Comprobado con la sonda de [run 40605768510](https://github.com/Spaceapk/Total-Music/actions/runs/40605768510),
que informa presencia y huella pública sin leer valores:

| Secreto / archivo | Estado |
| --- | --- |
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | ausentes |
| `TOTAL_MUSIC_KEYSTORE_B64`, `TOTAL_MUSIC_KEYSTORE_PASSWORD` | ausentes |
| `SPACE_SPATIAL_KEYSTORE_B64`, `SPACE_SPATIAL_KEYSTORE_PASSWORD` | ausentes |
| `SPACE_KEYSTORE_B64`, `SPACE_KEYSTORE_PASSWORD` | ausentes |
| `TOTAL_MUSIC_CANDIDATE_URL` | presente (una URL, no una clave) |
| `keystore.properties`, `*.jks`, `*.p12` en el árbol | ninguno |
| `.signing/space-release-key.cms` | presente, pero es `EnvelopedData` RSA-OAEP para `CN=Space Music signing backup` — **otra identidad** y cifrada |

`scripts/release/check_release.py` confirma además que ningún archivo privado de
firma está versionado (`Public identity and repository signing isolation verified`).

## 4. Qué falta exactamente, y dónde

El procedimiento no pide secretos en CI: pide **dos archivos en disco, fuera del
checkout**. `sign_apk.py` lo impone explícitamente —

```python
key = Path(os.environ['TOTAL_MUSIC_KEYSTORE']).resolve()
password = Path(os.environ['TOTAL_MUSIC_PASSWORD_FILE']).resolve()
for private in (key, password):
    if private.is_relative_to(ROOT) or not private.is_file():
        raise ValueError('Signing files must exist outside the repository')
```

Hace falta, en la máquina donde se vaya a firmar:

| Requisito | Dónde | Notas |
| --- | --- | --- |
| Keystore permanente (certificado `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`) | Archivo `.jks` o `.p12` fuera del repo | No está aquí, ni en CI, ni en git. `.signing/space-release-key.cms` no sirve: otra identidad y cifrada |
| Su contraseña | Archivo de texto separado, fuera del repo; **solo** la contraseña | El alias está en `total-music-release.properties`: `totalmusic-release` |
| `ANDROID_BUILD_TOOLS` | Ruta a `build-tools/36.0.0` | Herramientas públicas; el workflow `total-music-local-tools.yml` exporta `aapt` y `apksigner` para esto |
| APK de la versión anterior | `Total-Music-1.8.0-universal.apk` de la release | Obligatorio: el script verifica continuidad de paquete y `versionCode` |

Variables de entorno exactas: `TOTAL_MUSIC_KEYSTORE`, `TOTAL_MUSIC_PASSWORD_FILE`,
`ANDROID_BUILD_TOOLS`.

Si en lugar de firmar localmente se quisiera que firmara CI, habría que añadir
esos dos valores como secretos del repositorio **y** autorizar un workflow de
publicación, que hoy no existe y que el gate de esta rama prohíbe a propósito
(«CI outputs […] an unsigned release preparation, never a stable release»). Esa
es una decisión tuya, no algo que haya que dar por hecho.

## 5. El comando exacto, verificado contra el código

```bash
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
export TOTAL_MUSIC_KEYSTORE=/ruta/fuera/del/repo/totalmusic.jks
export TOTAL_MUSIC_PASSWORD_FILE=/ruta/fuera/del/repo/password.txt

python3 scripts/release/sign_apk.py \
  app-prod-universal-release-unsigned.apk \
  dist \
  --previous-apk Total-Music-1.8.0-universal.apk
```

Lo que el script comprueba antes de escribir nada (leído en el código, no supuesto):

1. Que el APK sea `org.nexlibre.totalmusic`, `versionCode=15`, `versionName=1.8.1`
   y **no** debuggable.
2. Que el APK anterior esté firmado con `certificateSha256` de
   `total-music-release.properties` — si no, aborta.
3. Que mantenga el paquete y **aumente** el `versionCode` respecto al anterior.
4. Que las dos credenciales existan y estén fuera del repositorio.
5. Que el APK firmado resultante tenga exactamente el certificado esperado y la
   misma identidad; si no, borra el temporal y falla.
6. Solo entonces renombra a `Total-Music-1.8.1-universal.apk` y escribe
   `SHA256SUMS.txt` y `release-verification.json`.

Es decir: la herramienta no puede producir por accidente un APK con otra firma.

## 6. Lo que sí está entregado

El APK sin firmar, que es la entrada exacta de ese comando
([run 40606188366](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366)):

- Artefacto [`total-music-181-release-unsigned`](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366/artifacts/12781671728)
- `app-prod-universal-release-unsigned.apk`, SHA-256
  `059932b9c2ddab5e3b293738ac2d54bfc9f296412afd8789aab645fbc33ecd5d`
- Identidad leída del archivo: `org.nexlibre.totalmusic`, `versionCode=15`,
  `versionName=1.8.1`; certificado: ninguno.

Y la verificación posterior, cuando exista el APK firmado, con el mecanismo que
el repositorio ya usa:

```bash
"$ANDROID_BUILD_TOOLS/apksigner" verify --verbose --print-certs \
  dist/Total-Music-1.8.1-universal.apk
# debe imprimir: 9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa
```

## 7. Límites de esta auditoría

- No hay teléfono real; las comprobaciones de instalación sobre una instalación
  existente son de emulador.
- Los artefactos y assets de release no son descargables desde este entorno
  (`EOF` en el host de resultados de Azure), así que la auditoría se apoya en la
  API, en el historial de git y en los documentos del propio repositorio, no en
  volver a descargar los binarios.
- No se ha generado, sustituido ni rotado ninguna clave. No se ha publicado
  ninguna release. El PR #5 sigue abierto y sin fusionar.
