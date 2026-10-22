# Entregas de Total Music

Rama de mantenimiento en GitHub: `design/total-music-1.2.0` en `Spaceapk/Total-Music`.
GitLab se conserva como respaldo.
Identidad Android: `org.nexlibre.totalmusic`. Android mínimo: 8 (API 26).
La primera entrega con firma permanente es 1.0.2, versionCode 3.

## Una sola firma para las siguientes versiones

El certificado público está en `total-music-signing-certificate.pem` y su huella
SHA-256 se fija en `total-music-release.properties`. La clave privada está
cifrada en un archivo PKCS12, fuera del repositorio y de Codemagic.
El archivo de clave y su contraseña se conservan por separado en los archivos
privados del propietario. No regenerar la clave si falla una descarga o si una
compilación no encuentra las credenciales: recuperar las copias originales.

Codemagic solo necesita el código fuente. Produce un ZIP con el APK release
sin firmar, el commit de origen y su suma SHA-256. La firma final se aplica
localmente después de descargar el ZIP. Esto permite automatizar la entrega
sin entregar la autoridad de firma al servicio de compilación.

## Preparar una actualización

1. Partir de la última versión estable y conservar sus cambios.
2. Ejecutar `python3 scripts/release/prepare_version.py 1.0.3`, sustituyendo
   la versión por la siguiente. Esto aumenta también versionCode.
3. Confirmar los cambios en GitHub y ejecutar `total-music-android.yml` en GitHub Actions.
   Codemagic `total-music-release` permanece disponible como alternativa.
   El flujo comprueba procedencia, pruebas JVM, pruebas Android locales, lint
   y ensamblado release. Mantiene el baseline de lint preexistente.
4. Descargar el artefacto ZIP de verificación de Actions. Extraer de
   `release-artifacts/` únicamente el APK `unsigned-release-NOT-INSTALLABLE` para
   firmarlo. Comprobar SHA256SUMS y que source-commit corresponda al commit
   construido. El APK debug de ese artefacto es solo de pruebas.
5. Recuperar la clave privada y el archivo de contraseña mediante el flujo de
   archivos privados. Materializarlos fuera del checkout, con permisos 0600.
6. Definir ANDROID_BUILD_TOOLS, TOTAL_MUSIC_KEYSTORE y
   TOTAL_MUSIC_PASSWORD_FILE. Sus valores son rutas, nunca contraseñas.
7. Ejecutar el firmador con el APK anterior firmado con la clave permanente:

   ```bash
   python3 scripts/release/sign_apk.py /ruta/nuevo-unsigned.apk /ruta/salida \
     --previous-apk /ruta/Total-Music-version-anterior-universal.apk
   ```

   Para 1.0.2, y solo para esa primera entrega, usar `--first-stable`.

8. El firmador rechaza versiones incorrectas, APK debuggable, cambios de firma,
   cambios de identificador y códigos de versión que no aumentan. Publicar
   exclusivamente el APK resultante, SHA256SUMS y release-verification.json.
9. Guardar el APK entregado y respaldar un ZIP en la carpeta existente de APK
   de Google Drive. Conservar el APK anterior, el commit y el resultado del build.

No se necesita solicitar al propietario contraseñas antiguas de Space Music.
Los secretos no deben aparecer en el chat, código, registros o artefactos CI.

## Cambio único desde la versión de prueba

La versión de prueba 1.0.1 fue firmada con un certificado Android Debug distinto.
Android no permite reemplazarla por 1.0.2 con otra firma conservando sus datos.
Si aún no se instaló la prueba, instalar directamente la versión permanente.

Si ya está instalada, antes de quitarla usar Ajustes > Exportar datos y guardar
el JSON fuera de la app. La exportación incluye preferencias e historial de
escucha; no incluye sesiones de acceso, caché, audio descargado ni carátulas.
No asumir que es una copia completa de toda la biblioteca. Conservar aparte
los archivos de música y los datos que no cubre esta exportación. Después del
reemplazo, importar el JSON e iniciar sesión otra vez si hace falta.

Las versiones posteriores firmadas con la misma clave y con versionCode mayor
se instalan como actualizaciones. Validar la compatibilidad con el APK anterior
antes de entregar. Una comprobación de firma no sustituye una prueba en teléfono.

## Canal de avisos dentro de la aplicación

El comprobador heredado todavía consulta GitHub. La entrega actual se realiza
mediante APK verificado y respaldo privado; no prometer avisos automáticos
dentro de la app hasta configurar un canal de distribución compatible. Nunca
insertar un token de GitLab o de Drive en el APK para leer repositorios privados.
