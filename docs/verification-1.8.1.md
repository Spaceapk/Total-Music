# Verificación de Total Music 1.8.1

## Estado

Rama: `arena/bac32391-total-magic` (commit `0e1786c5`), sobre `main` (`3dee549`).
Integra la recuperación del trabajo pendiente de Work (Google Expressive+, Liquid Glass+, navegación del renacimiento, sin Explorar):
- reproductor Expressive+ equilibrado: columna del reproductor centrada (transporte en la mitad baja en pantallas altas, sin huecos desproporcionados entre elementos) y fundilla ajustada a la mitad de la altura disponible;
- cristal más realista: panel de volumen envuelto en superficie esmerilada y superficie de respaldo más translúcida (alpha 0.82);
- barra inferior y mini-player con áreas seguras (`navigationBarsPadding`) y cápsulas de navegación más compactas (mínimo 60 → 52 dp);
- corrección del cierre inesperado al volver de Ajustes / del reproductor a Biblioteca (ruta de cristal).
Pruebas de regresión nuevas: `RebirthVisualTokensTest` (contrato de `rebirthColors` para los tres estilos).

## Resultado de la validación 1.8.1 (GitHub Actions, run 40753733098)

Cuatro de cuatro trabajos **verde**:

| Trabajo | Resultado | Qué cubrió |
|---|---|---|
| `lint` | éxito | 0 errores, 657 advertencias |
| `build` | éxito | compilación limpia, `assemble` de release-like APKs, **1083 pruebas unitarias aprobadas** en 107 clases |
| `ui` | éxito | 16/16 pruebas instrumentadas en emulador Android 13: shell de cristal (idos y vueltas de Ajustes), navegación (restauración de destino), corazón de favoritos en los tres estilos, ajuste del arte en paisaje, transporte en la mitad baja, tiendas de favoritos y playlists |
| `device` | éxito | sondeo real en Android 13: ambos estilos (`LIQUID_GLASS_PLUS`, `GOOGLE_EXPRESSIVE_PLUS`), flujos biblioteca / Ajustes / reproductor, proceso vivo sin cierres, sin entradas nuevas en `logcat -b crash` |

Capturas de pantalla reales del emulador: artefacto CI `total-music-181-screenshots` (6 PNG: biblioteca, Ajustes y reproductor por cada estilo). No se pudo descargar desde este entorno (los artefactos redirigen a Azure Blob, fuera del alcance de la red de la máquina); están en el run 40753733098 de GitHub Actions.

## Identidad del APK (compilado por CI, no firmado)

- Artefacto: `total-music-181-release-unsigned` → `app-prod-universal-release-unsigned.apk`
- SHA-256: `6185686f4f51058a060c884679c70df6c4b54b2fcab76646cfc8040f03981e30`
- ABI adicionales firmados con certificado de depuración (solo pruebas): arm64 `5e1bafc8...`, armeabi `dd6b2ad7...`, x86_64 `0d28e031...`
- Paquete: `org.nexlibre.totalmusic`
- versionName: `1.8.1` / versionCode: `19` (aumenta sobre el 14 de 1.8.0: permite actualizar instalaciones existentes)
- Certificado esperado (de `total-music-release.properties`, la fuente única de versión, y de `docs/total-music-signing-certificate.pem`): `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`, alias `totalmusic-release`
- `release-notes.json` contiene la entrada 19 / 1.8.1 (comprobado con `scripts/release/check_release.py`, pasa)

**El APK se entrega SIN FIRMAR.** La clave permanente (PKCS12, `.signing/space-release-key.cms`, cifrada) y su contraseña son archivos privados del propietario, fuera del repositorio y fuera del alcance de este entorno; la contraseña no se conoce, por tanto no se ha podido firmar. Conforme a las reglas del proyecto: no se genera una clave nueva, no se expone contraseña alguna, y no se promete compatibilidad de actualización sin verificarla.

Para terminar la entrega (propietario, con los archivos de clave fuera del checkout, modo 0600):

```bash
python3 scripts/release/sign_apk.py app-prod-universal-release-unsigned.apk dist \
  --previous-apk app-prod-universal-release-unsigned-1.8.0.apk
```

requerirá `ANDROID_BUILD_TOOLS`, `TOTAL_MUSIC_KEYSTORE` y `TOTAL_MUSIC_PASSWORD_FILE`; el firmante exige la clave permanente, el paquete, el código de versión creciente y el no-debuggable.

## Sobre la compatibilidad de actualización

Identidad verificada: paquete `org.nexlibre.totalmusic`, versionCode 19 > 14, y el certificado nombrado en `total-music-release.properties` coincide con el huella `9ae145a7...829fa` — las condiciones previas para que una actualización firmada con la clave permanente sea aceptada están en su lugar.

**No verificado aún:** la prueba de instalación real sobre una Total Music publicada (certificado + versión creciente + instalador) requiere el APK firmado con la clave permanente, que no está disponible en este entorno. Reglas del proyecto: no se da como verificada hasta que el propietario firme e instale sobre una versión publicada.

## Lo que no se pudo verificar desde este entorno

- Gradle local (no hay JDK/Android SDK en la sandbox); todas las compilaciones y pruebas corrieron en GitHub Actions.
- Teléfono físico: las pruebas de instrumentación corrieron en el emulador Android 13 de CI.
- Firma con la clave permanente y prueba de instalación de actualización.
