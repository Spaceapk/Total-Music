# Total Music 1.8.1 — artefactos históricos, el almacén original y qué falta

Investigación sobre los artefactos de 1.7.0 y 1.8.0, sus releases, workflows y
documentación, para localizar referencias al almacén de claves original y
determinar si existe un procedimiento seguro de recuperación. No se ha intentado
extraer ninguna clave privada de un APK firmado ni acceder a ningún secreto.

Fecha: 2026-10-27. Rama `arena/c83a7b6e-total-music`. PR #5 abierto, sin fusionar;
`main` sin tocar.

## 1. Resumen en cinco líneas

1. **No existe ninguna copia del almacén permanente de Total Music** (alias
   `totalmusic-release`, certificado `9ae145a7…`) en ningún lugar accesible desde
   este entorno: ni en git, ni en las 175 artefactos de los 180 runs, ni en las
   releases, ni en ninguno de los siete repositorios relacionados.
2. **El único material cifrado del repositorio no es la clave de Total Music.**
   `.signing/space-release-key.cms` es el respaldo de *Space Music*
   (alias `space-music`), y usarlo produciría exactamente el certificado
   prohibido.
3. **Sí existe un procedimiento de recuperación seguro**, pero es del propietario
   y sólo se puede ejecutar donde estén sus archivos privados; aquí no hay nada
   que descifrar sin autorización.
4. **El APK 1.8.1 sin firmar sí está disponible** como artefacto descargable, vivo
   hasta el 27 de noviembre de 2026. No ha hecho falta publicarlo en ningún sitio.
5. **Falta un solo eslabón**: el `.p12`/`.jks` con alias `totalmusic-release` y su
   contraseña. Todo lo demás está listo y verificado.

## 2. El APK 1.8.1 sin firmar: disponible, con enlace

| Dato | Valor |
| --- | --- |
| Run | [40606188366 — «Total Music 1.8.1 validation»](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366) (`completed/success`, rama del PR #5) |
| Artefacto | `total-music-181-release-unsigned` |
| ID | 12781671728 |
| Tamaño | 78 859 896 bytes (75,2 MiB) |
| Creado | 2026-10-28T00:16:56Z |
| **Caduca** | **2026-11-27T00:16:52Z** |
| Estado | `expired: false` — descargable |
| SHA-256 registrado | `059932b9c2ddab5e3b293738ac2d54bfc9f296412afd8789aab645fbc33ecd5d` |

Descarga: abrir el run, sección **Artifacts**, y bajar el ZIP. Por API, el
archivo está en `https://api.github.com/repos/Spaceapk/Total-Music/actions/artifacts/12781671728/zip`
(requiere sesión con acceso al repositorio).

**No se ha publicado nada nuevo**, porque no hacía falta: el artefacto sigue vivo
y es el binario que pasó las 1.079 pruebas, el lint y el emulador. Publicar una
copia sólo tendría sentido si se quiere que sobreviva al 27 de noviembre; el
procedimiento autorizado para eso, si se decide, sería una release **en borrador**
(no visible al público, borrable, y sin tocar `main`), con el APK sin firmar, sus
`SHA256SUMS.txt` y una nota que diga «sin firmar, no instalable».

**Límite honesto:** este entorno no puede descargar el ZIP. `api.github.com`
responde 302 hacia `productionresultssa17.blob.core.windows.net`, que no está
entre los hosts permitidos, y la conexión TLS se corta. Tampoco se pueden bajar
los adjuntos de las releases: van a `release-assets.githubusercontent.com`, con el
mismo resultado (`EOF`). Por eso el SHA-256 de arriba es el **registrado** por el
run, no uno recalculado aquí.

## 3. Inventario histórico completo (lo que se ha mirado)

**Runs y artefactos.** 180 runs entre 2026-10-22T19:02:28Z y 2026-10-28T00:57:19Z;
175 artefactos enumerados uno por uno por su API. Nueve nombres contienen
`sign`/`key`/`cert`, y los nueve son evidencia de instalación y actualización
(`signed-180-startup-android13`, `total-music-150-signed-upgrade`,
`signed-141-install-upgrade-android13`, `total-music-181-release-unsigned`), no
material de firma. **Cero artefactos con un `.jks`, `.p12`, `.cms` o contraseña.**

**Git.** `git rev-list --objects --all` sobre todas las referencias: no hay ningún
`.jks`, `.keystore`, `.p12` ni `.pfx`. `git log --all --diff-filter=D` demuestra
que **tampoco se borró nunca** ningún fichero de firma: no hubo un keystore
comprometido y luego eliminado. `keystore.properties` sólo aparece como
`keystore.properties.example`, que documenta `bitchord-release.jks` / alias
`bitchord` — el linaje de Star Music, otra identidad.

**Releases.** Siete: `v1.8.0-preview.1` (pre-release), `v1.7.0` (latest), `v1.6.0`,
`v1.5.0` (borrador), `v1.4.1`, `v1.4.0`, `v1.2.0`. Adjuntos de 1.7.0 y 1.8.0:
APK universal, `SHA256SUMS.txt`, `release-verification.json` y, en 1.7.0, el ZIP
de código fuente, el parche y `verification-1.7.0.md`. **Ninguno contiene una
clave.** Las dos releases están firmadas por el usuario `Spaceapk`, no por el bot:
la firma se hizo fuera de CI, a mano.

**Otros repositorios.** `nexlibre/Space-Music`, `nexlibre/Total-Music-App`,
`Spaceapk/My-Music` y `Spaceapk/Total-Music` son el linaje Space Music/Total
Music; los tres primeros tienen exactamente los mismos 1.055 blobs de Space Music.
`Spaceapk/My-Music-import-log` sólo contiene un workflow de importación desde
GitLab. **En ninguno hay un almacén de Total Music.** La búsqueda de código de la
API no es utilizable con este token (devuelve 0 incluso para cadenas que existen),
así que se ha hecho por árboles de git, que sí responden.

**Workflows.** Los 19 ficheros que han existido en `.github/workflows/` nunca
llamaron a `apksigner sign` (auditoría en `docs/signing-audit-1.8.1.md`).
`codemagic.yaml` se llama literalmente «release for private signing» y sólo
empaqueta el APK sin firmar; su historia **nunca** tuvo un bloque de firma ni un
keystore. Los secretos que algún workflow ha nombrado son
`TOTAL_MUSIC_KEYSTORE_B64`, `TOTAL_MUSIC_KEYSTORE_PASSWORD`, `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `SPACE_SPATIAL_KEYSTORE_*`,
`SPACE_KEYSTORE_*` y `TOTAL_MUSIC_CANDIDATE_URL`. Ninguno se ha leído: listarlos
requiere permisos de administración que este token no tiene (403).

## 4. El único material cifrado del repositorio, y por qué no sirve

`.signing/space-release-key.cms` (5 255 bytes) es un PKCS#7 **EnvelopedData**:

- contenido AES-256-CBC, clave envuelta con **RSA-OAEP (SHA-256)**;
- destinatario `CN=Space Music signing backup, O=nexlibre`, serial
  `3DDEE665A704C29D53813C9E001814D334A8A2D4`, que es exactamente el certificado
  público `docs/space-signing-public.pem`;
- el blob es **idéntico byte a byte** (`506e45ca…`) en `Spaceapk/Total-Music`,
  `Spaceapk/My-Music`, `nexlibre/Space-Music` y `nexlibre/Total-Music-App`.

Su procedencia está en el código, no en una suposición:
`docs/upstream-workflows/space-release.yml` lo genera con
`tar -czf space-signing.tar.gz space-release.p12 password.txt` y
`openssl cms -encrypt … -recip docs/space-signing-public.pem`, donde
`space-release.p12` se crea con `-alias space-music` y
`-dname 'CN=Space Music, OU=Android, O=nexlibre'`. Es el almacén de **Space
Music**, no el de Total Music (`totalmusic-release`, `CN=Total Music`,
`9ae145a7…`).

**Consecuencia práctica, y es importante:** `docs/design/1.8.0/VALIDATION.md`
afirma que la firma permanente de Total Music la produce «quien tenga la clave
privada del sobre anterior». Con la evidencia de arriba, esa frase es incorrecta:
descifrar ese sobre da la clave de Space Music, cuyo certificado
(`dbb5b7c0…`, el que usó Total Music 1.0.1 con paquete
`org.nexlibre.spacemusic.spatial`) **no** es el permanente de Total Music.
`docs/migration/README.md` lo dice al revés y bien: «The original Space Music
signing bridge is preserved but is not used for Total Music: its certificate is
not the pinned permanent Total Music certificate.» Quien siga la frase de
`VALIDATION.md` acabará con un APK firmado con la identidad equivocada, que no
actualiza nada y que el workflow de firma rechaza en su paso de certificado.
Esa frase debería corregirse; no se ha tocado porque es un documento de diseño
cerrado de 1.8.0 y no es necesario para desbloquear 1.8.1.

No se ha intentado descifrar el sobre: haría falta la clave privada del
destinatario, que no está aquí, y obtenerla o forzarla queda fuera de lo
autorizado.

## 5. Dónde está la clave permanente, según la propia documentación

- `docs/TOTAL_MUSIC_RELEASES.md`, paso 5: «Recuperar la clave privada y el archivo
  de contraseña mediante el flujo de archivos privados. Materializarlos fuera del
  checkout, con permisos 0600.» Es un PKCS12 **fuera del repositorio y de
  Codemagic**, con la contraseña en un fichero separado, ambos «conservados por
  separado en los archivos privados del propietario».
- El paso 9 añade el respaldo que sí existe y sí es localizable: guardar el APK y
  un ZIP en «la carpeta existente de APK de Google Drive».
- `docs/TOTAL_MUSIC_1_1_0.md` lo confirma en pasado: «La clave permanente se
  recuperó fuera del checkout, no se creó una nueva ni se envió a CI», y enlaza un
  respaldo privado en Drive:
  <https://drive.google.com/file/d/1H7yjT98onXigp9Rkfwq3FFkPzSyUC24i/view>.
- `AGENTS.md` y `docs/TOTAL_MUSIC_RELEASES.md` señalan GitLab
  `nexlibre-group/total-music` como respaldo del código (sin webhooks, con
  pipelines). Es el único sitio plausible que este entorno **no puede consultar**:
  GitLab no está entre los hosts permitidos.

Orden razonable de búsqueda para el propietario, del más al menos probable:
carpeta de Drive citada arriba → gestor de contraseñas / almacenamiento cifrado
personal → variables de CI del proyecto GitLab → copias locales de firma en el
equipo donde se firmaron 1.7.0 y 1.8.0.

## 6. Procedimiento de recuperación seguro (sólo el propietario, sin exponer nada)

Nada de esto se ha ejecutado aquí. Es el camino que no crea ninguna firma nueva y
no toca `main`.

1. Localizar el almacén y su contraseña en los archivos privados. No pegarlos en
   el chat, en un issue, en un PR, en un commit ni en un log.
2. **Verificar antes de usar.** Con el keystore y su contraseña en local:

   ```bash
   keytool -list -v -keystore /ruta/privada/almacen.p12 -alias totalmusic-release
   ```

   Debe mostrar `CN=Total Music, O=NexLibre` y huella SHA-256
   `9A:E1:45:A7:03:A8:59:02:FE:6D:6A:B3:D8:86:F6:80:18:73:D7:17:92:9D:EA:A3:E3:D0:B0:A8:1D:E8:29:FA`.
   Si muestra `CN=Space Music` o `dbb5b7c0…`, **parar**: es la clave puente, no la
   permanente.
3. Firmar el APK del artefacto con el firmador sin modificar y el APK anterior:

   ```bash
   export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
   export TOTAL_MUSIC_KEYSTORE=/ruta/privada/almacen.p12
   export TOTAL_MUSIC_PASSWORD_FILE=/ruta/privada/password.txt
   python3 scripts/release/sign_apk.py /ruta/total-music-181-release-unsigned.apk \
     /ruta/salida --previous-apk /ruta/Total-Music-1.8.0-universal.apk
   ```

   El APK anterior sale de la release
   [v1.8.0-preview.1](https://github.com/Spaceapk/Total-Music/releases/tag/v1.8.0-preview.1);
   el firmador comprueba continuidad de certificado y que el `versionCode`
   aumente (15 > 14).
4. Alternativa en CI, si prefiere que firme Actions: configurar
   `TOTAL_MUSIC_KEYSTORE_B64` y `TOTAL_MUSIC_PASSWORD` (o
   `TOTAL_MUSIC_KEYSTORE_PASSWORD`) y fusionar el workflow en `main` para poder
   dispararlo. Sin eso, `workflow_dispatch` no existe en GitHub.

## 7. Los pasos exactos que faltan para el APK firmado con la identidad original

1. Descargar `total-music-181-release-unsigned` del run 40606188366 y comprobar
   que su SHA-256 es `059932b9c2ddab5e3b293738ac2d54bfc9f296412afd8789aab645fbc33ecd5d`.
2. Descargar `Total-Music-1.8.0-universal.apk` de `v1.8.0-preview.1`.
3. Tener el almacén `totalmusic-release` y su contraseña en los archivos privados.
4. Verificar la huella del almacén (paso 2 de la sección 6). Si no coincide, parar
   y no firmar nada.
5. Ejecutarlo con `sign_apk.py --previous-apk` (sección 6, paso 3), o por el
   workflow manual tras fusionarlo.
6. Comprobar el resultado: `apksigner verify --print-certs` debe dar
   `9ae145a7…`; `aapt dump badging` debe dar `org.nexlibre.totalmusic`,
   `versionCode 15`, `versionName 1.8.1`.
7. Instalar encima de 1.8.0 sin desinstalar y confirmar `dumpsys package`.

De esos siete, **el único que no se puede hacer desde aquí es el 3**, y no por un
problema técnico: por diseño, esa clave nunca ha estado en el repositorio, ni en
CI, ni en un artefacto. Los otros seis están resueltos o documentados arriba.

## 8. Cambio incluido en este trabajo

El workflow de firma aceptaba `TOTAL_MUSIC_PASSWORD` y `KEYSTORE_PASSWORD`, pero
la sonda de presencia del propio repositorio
(`total-music-181-signing-check.yml`) lee `TOTAL_MUSIC_KEYSTORE_PASSWORD`. Si el
propietario hubiera configurado ese nombre, el firmador habría dicho «falta» con
el secreto ya puesto. Ahora acepta los tres nombres de contraseña y los dos de
keystore, y el listado de «falta» nombra las tres variantes. Verificado con el
propio bloque del YAML: YAML válido, 15 pasos, `bash -n` sin fallos, y la puerta
sigue saliendo con error 1 cuando falta el keystore y `ok=true` cuando están los
dos.

## 9. Límites

- Los adjuntos de releases y los artefactos de Actions no se pueden descargar
  desde este entorno (hosts `release-assets.githubusercontent.com` y
  `blob.core.windows.net` bloqueados). Los hashes citados son los registrados por
  GitHub, no recalculados aquí.
- GitLab (`nexlibre-group/total-music`) no es alcanzable desde aquí.
- Listar los nombres de secretos configurados requiere administración (403); no se
  ha intentado saltar.
- No se ha descifrado el sobre `.cms` y no se ha extraído nada de ningún APK
  firmado.
- El artefacto sin firmar caduca el 2026-11-27; a partir de ahí habría que
  recompilar (y el SHA-256 cambiaría, porque una recompilación no es reproducible
  byte a byte).
