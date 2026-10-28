# Verificación de Total Music 1.8.1

## Qué se verificó

Rama: `arena/bac32391-total-magic` (commit `bd57a0c9`), sobre `main` (`3dee549`).
Integra la recuperación del trabajo pendiente (PRs #5, #6, #7, revisión 19 de `work/player-glass-balance`):
- Google Expressive+ y Liquid Glass+ con navegación del renacimiento (sin Explorar).
- Favoritos persistentes del dispositivo y restauración de navegación.
- Corrección del cierre inesperado de Liquid Glass+ al volver de Ajustes o del Reproductor a Biblioteca.
- Ajuste visual de esta entrega: reproductor Expressive+ equilibrado, cristal más convincente y barra inferior con áreas seguras (ver CHANGELOG 1.8.1).

## Identidad del APK

- Paquete: `org.nexlibre.totalmusic`
- versionName: `1.8.1`
- versionCode: `19` (sobre el 14 de 1.8.0; aumenta para actualizar instalaciones existentes)
- Certificado permanente esperado: `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`
  (coincide con `docs/total-music-signing-certificate.pem` y con el de las publicaciones 1.7.0 y 1.8.0; comprobado con `scripts/release/check_release.py`)
- release-notes.json contiene la entrada 19 / 1.8.1 (comprobado con `check_release.py`).

## Resultado de compilación (GitHub Actions, runners ubuntu-latest)

- `Total Music Android verification` (pull request): **success**
  - `:shared:jvmTest` y `:app:testProdDebugUnitTest`: pasan
  - Lint de producción: sin errores
  - `assembleProdRelease` produce el APK release sin firmar (universal)
    - SHA-256: `3b91f74ee8291bf83d71982eff8ef5719938da59218d2eee100eb9997e168cf8`
    - No firmado: espera la clave permanente para poder instalarse como actualización.
- `1.8.0 compile, tests and test APK` (pull request): **success**
  - 1.083 pruebas unitarias aprobadas, 0 fallos
  - APKs de depuración y release-unsigned por ABI con identidad 1.8.1 / código 19.

## Pruebas en Android 13 (emulador, `total-music-181-validate.yml`, trabajo `device`)

- `device`: **success** — ambos estilos de reproductor (`LIQUID_GLASS_PLUS` y `GOOGLE_EXPRESSIVE_PLUS`), flujos de biblioteca, Ajustes (ida y vuelta) y reproductor (ida y vuelta) sin cierre del proceso ni entradas nuevas en `logcat -b crash`.
- Trabajo `ui`: pruebas de shell de cristal, navegación, favoritos del reproductor y tiendas de dispositivos/playlists en el emulador. El informe de este trabajo no se pudo leer desde este entorno (los registros de artefactos están en un host fuera del alcance de la conexión de esta máquina); repetir antes de entregar.

## Firma

La clave permanente (PKCS12) y su contraseña son archivos privados del propietario, fuera del repositorio y fuera de los hosts alcanzables desde este entorno, por lo que este APK se entrega **sin firmar**. Para convertirlo en actualización:

```bash
python3 scripts/release/sign_apk.py Total-Music-1.8.1-universal.apk dist \
  --previous-apk Total-Music-1.8.0-universal.apk
```

con `ANDROID_BUILD_TOOLS`, `TOTAL_MUSIC_KEYSTORE` y `TOTAL_MUSIC_PASSWORD_FILE` apuntando a los archivos del propietario (fuera del checkout, modo 0600). El firmante rechaza cualquier cosa que no sea la clave permanente, el paquete, el código de versión creciente y el no-debuggable.

## Alcance no verificado desde este entorno

- Pruebas en teléfono físico (ningún dispositivo/emulador local disponible).
- Firma con la clave permanente y prueba de actualización sobre una instalación publicada.
