# Total Music 1.8.2 — recuperación segura de firma

Fecha de investigación: 2026-10-29. Continuación del PR #9, sin fusionar.

## Lo comprobado en esta sesión

1. `AGENTS.md` identifica dos archivos privados del propietario: `Total-Music-release-signing.p12` y `Total-Music-release-signing-password.txt`. Deben recuperarse por un canal privado, fuera de Git y fuera de CI. El conector/herramienta `library` consultado en esta sesión devolvió `unsupported`; no hay un canal Library utilizable por este agente.
2. La búsqueda por nombres de archivos en el espacio accesible no encontró `.p12`, archivo de contraseña ni APK anterior firmado. No se han leído contraseñas ni buscado secretos en logs. Las variables de rutas `TOTAL_MUSIC_KEYSTORE` y `TOTAL_MUSIC_PASSWORD_FILE` no están configuradas. Tampoco hay `ANDROID_BUILD_TOOLS`, JDK ni SDK local.
3. La API `GET /repos/Spaceapk/Total-Music/actions/secrets` devolvió **403 Resource not accessible by integration**. Esto significa que no se puede confirmar la presencia de secretos con este permiso, **no** que se haya probado que no existen. No se intenta eludir esa restricción ni extraer secretos de Actions.
4. La lista actual de releases de GitHub no contiene 1.8.1. La última prerelease visible es `v1.8.0-preview.1`; los informes de 1.8.1 en el repositorio describen APK sin firmar. Por tanto tampoco se dispone del APK 1.8.1 instalado que el usuario quiere utilizar como referencia.
5. Se verificó el certificado **público** `docs/total-music-signing-certificate.pem` con `openssl x509 -noout -fingerprint -sha256`. Coincide con la huella fijada en `total-music-release.properties`: `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`. Esto no demuestra que esté disponible su clave privada.
6. Se inspeccionó únicamente la cabecera pública del sobre CMS `.signing/space-release-key.cms`: su destinatario es `CN=Space Music signing backup, O=nexlibre`. La procedencia documentada en `docs/signing-recovery-1.8.1.md` y `docs/migration/README.md` demuestra que ese respaldo es de **Space Music**, no del firmante permanente de Total Music. No se ha intentado descifrarlo ni usarlo para firmar.

## Qué falta exactamente

- **Acceso privado autorizado a los archivos originales** del almacén PKCS12/JKS y su contraseña separada. Preferentemente un montaje privado fuera del checkout con permisos 0600, utilizable mediante las variables de rutas anteriores. No pegar contraseñas en el chat, issues, PR, comandos, logs ni commits.
- **APK 1.8.1 realmente firmado e instalado**, junto con su procedencia, para verificar certificado, paquete y versionCode de referencia. Un APK 1.8.1 sin firmar no sirve para comprobar continuidad de firma. Si solo se conserva la instalación, el propietario puede localizar sus APK mediante `adb shell pm path org.nexlibre.totalmusic` y copiarlos por ADB desde un dispositivo autorizado, sin desinstalar ni borrar datos. El certificado del APK es público; esta extracción no recupera la clave privada de firma.
- Herramientas locales Android (`aapt`, `apksigner`, `zipalign` y JDK) y acceso al APK release 1.8.2 construido. Los artefactos de CI permiten descargarlo desde el navegador del propietario; la red del sandbox redirige a almacenamiento externo no permitido. No se ha subido la clave a CI como alternativa.
- Tras firmar, una instalación de actualización **sin desinstalar** sobre 1.8.1 con datos de prueba preservados (favoritos, apariencia y biblioteca), y comprobación de esos datos después de `adb install -r`.

## Verificar el certificado ANTES de firmar

Estos pasos están preparados, **no ejecutados**, porque falta el almacén privado. En el entorno privado autorizado:

1. Materializar keystore y archivo de contraseña fuera del repositorio, modo 0600. No imprimir contenidos ni activar trazas de shell.
2. Exportar **solo el certificado público** del alias `totalmusic-release` con `keytool -exportcert -keystore "$TOTAL_MUSIC_KEYSTORE" -storepass:file "$TOTAL_MUSIC_PASSWORD_FILE" -alias totalmusic-release -file "$PRIVATE_TEMP/candidate-cert.der"`. La contraseña se lee de archivo; no aparece en argv.
3. Calcular SHA-256 con `openssl x509 -inform DER -in "$PRIVATE_TEMP/candidate-cert.der" -noout -fingerprint -sha256`. Comparar de forma exacta, normalizando separadores, con `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`. **Si difiere, detenerse antes de firmar.**
4. Ejecutar `apksigner verify --verbose --print-certs` sobre el APK 1.8.1 anterior y comprobar la misma huella; `aapt dump badging` debe confirmar `org.nexlibre.totalmusic`, versión 1.8.1 y un código menor que 20.
5. Usar el firmador existente, sin relajar sus comprobaciones:

   ```bash
   python3 scripts/release/sign_apk.py /ruta/1.8.2-release-unsigned.apk /ruta/salida-privada \
     --previous-apk /ruta/Total-Music-1.8.1-firmado.apk
   ```

6. El script comprueba la identidad, la continuidad del certificado, el incremento de versión y la firma resultante. Verificar asimismo la alineación con las build-tools. Guardar APK, SHA256SUMS y release-verification.json fuera de Git; probar la instalación de actualización y conservar su registro sin secretos.

## Estado

**Bloqueado para firma y para confirmar compatibilidad con 1.8.1 instalada.** No se ha generado ninguna clave permanente, no se ha utilizado la de Space Music, no se ha publicado un release y el PR #9 permanece abierto. El certificado público esperado se ha verificado, pero no un certificado de keystore candidato ni un APK release firmado 1.8.2.
