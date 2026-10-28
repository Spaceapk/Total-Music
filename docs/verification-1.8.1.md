# Total Music 1.8.1 — verificación

Fecha: 27 de octubre de 2026.
Rama: `arena/c83a7b6e-total-music`, base `main` en `3dee549d`.
Commits de código: `c8bc0c4f` (1.8.1: Explore fuera de la navegación), `9c200c69` (el cierre de
Liquid Glass+), `eb20a6a9` y `28ff69ff` (prueba de UI y su compilación).
Última ejecución de CI completa y verde: [run 40591890427](https://github.com/Spaceapk/Total-Music/actions/runs/40591890427).

## Resumen

Versión 1.8.1, versionCode 15, paquete `org.nexlibre.totalmusic`, mínimo API 26. Compila, pasan
1.079 pruebas unitarias, lint no encuentra errores, y el cierre de Liquid Glass+ está reproducido,
explicado y corregido. El APK release se entrega **sin firmar**: la clave permanente no está en
este entorno ni en CI, y no se ha generado ninguna en su lugar.

## El cierre de Liquid Glass+, causa y corrección

Reproducción, con el APK publicado
([run 40580679112](https://github.com/Spaceapk/Total-Music/actions/runs/40580679112):
las dos APK se descargan por SHA-256, sin recompilar nada):

- `v1.8.0-preview.1` instalado sobre 1.7.0 en Android 13 (API 33, `google_apis`, x86_64), estilo
  **Original**: `am start -W` responde `Status: ok`, `LaunchState: COLD`, el proceso sigue vivo
  (pid 5729) y `logcat -b crash` está vacío.
- El mismo APK en **Liquid Glass+**: muere a los ~2 s del arranque.

```
F/libc  ( 7416): Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x79f457107f00
                 in tid 7455 (RenderThread), pid 7416 (ibre.totalmusic)
F/DEBUG ( 7480): Cause: stack pointer is not in a rw map; likely due to stack overflow.
F/DEBUG ( 7480): #00 pc 00000000003b007a /system/lib64/libhwui.so
                 (android::uirenderer::RenderNode::prepareTreeImpl(...)+26)
F/DEBUG ( 7480): Process uptime: 2s
```

- En **Google Expressive+** el mismo APK arranca y sigue vivo.

La misma prueba sobre una compilación release-like de esta rama (R8, `versionName=1.8.1`, paquete
publicado) da el mismo resultado antes y después del cambio, con la misma imagen de emulador, la
misma sonda y la misma configuración:

| Build | Sonda Liquid Glass+ | Run |
| --- | --- | --- |
| `c8bc0c4f`, sin el arreglo | `launch LIQUID_GLASS_PLUS: pid=DEAD crash=yes` | [40578895170](https://github.com/Spaceapk/Total-Music/actions/runs/40578895170) |
| `9c200c69`, con el arreglo | `launch LIQUID_GLASS_PLUS: pid=alive crash=no` | [40582557419](https://github.com/Spaceapk/Total-Music/actions/runs/40582557419) |

No es, por tanto, algo que solo tenga el binario publicado.

Causa: en el código publicado (`3fa7c1f0`) la página del renacimiento se grababa en la capa de
fondo y la cápsula flotante —que toma esa capa como fondo— quedaba **dentro** de la grabación,
también en el APK 1.8.0 publicado:

- `ui/rebirth/RebirthLibrary.kt:210-216`: `CompositionLocalProvider(LocalAppBackdrop provides
  backdrop) { Box(... .layerBackdrop(backdrop)) { RebirthGlassLibrary(...) } }`
- `ui/rebirth/RebirthLibraryGlass.kt:181-186`: `RebirthGlassNav(glassEffects = data.glassEffects
  && data.backdropRecording)` → `Modifier.liquidGlass(shape)` → `drawBackdrop(backdrop)` →
  `drawLayer(backdrop.graphicsLayer)`.

La capa terminaba conteniendo un dibujo de sí misma. `RenderThread` recorre ese árbol hasta agotar
la pila: es la señal 11 de arriba, y por eso no hay excepción de Kotlin ni entrada de ANR. Como la
cápsula se dibuja en el primer fotograma, el cierre ocurre al abrir la aplicación y también cada
vez que se vuelve a ella desde Ajustes o el reproductor.

Corrección (`9c200c69`):

- La página se graba sola y el minirreproductor y la cápsula se dibujan **encima**, fuera de la
  grabación, que es la disposición que ya usan las pantallas clásicas para su barra de cristal.
  La página ocupa ahora toda la ventana (es lo que le da a la cápsula algo real que refractar) y
  cada lista reserva el alto medido del chrome para que su última fila no quede debajo.
- La capa de fondo incluida en la grabación pinta el fondo del estilo, como `MainActivity` hace con
  el fondo de ventana en la capa de la aplicación: una página que no pinta nada graba píxeles
  transparentes y el cristal no tiene qué desenfocar.
- El `LayerBackdrop` vendorizado (Kyant0/backdrop) se niega a muestrear la capa que está grabando.
  Eso no arregla una composición equivocada, pero convierte un fallo así en «sin desenfoque» en vez
  de en una muerte nativa que nadie puede capturar.

## Explorar fuera de la navegación

- `RebirthDestination` y las tres composiciones del shell solo ofrecen Inicio, Biblioteca y Buscar;
  la barra clásica solo ofrece Biblioteca. No hay ningún acceso a Explorar en la interfaz.
- Una sesión guardada con Explorar seleccionado se restaura en Inicio.
- El acceso «abrir origen» usa `PlaybackSourceType.EXPLORE -> TAB_HOME`.

## Entrega: el run 40606188366

Compilado desde el commit `70a15ae`, todo el pipeline en verde
([run 40606188366](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366)):

| Trabajo | Resultado |
| --- | --- |
| `build` | **success** — compilación, 3 sabores de APK, APK de pruebas de instrumentación |
| `lint` | **success** — 0 errores, 655 avisos |
| `ui` | **success** — 2 de 2 pruebas del shell |
| `device` | **success**, con una salvedad: el emulador se atascó al final (ver «Límites») |

Descargas (requieren sesión en GitHub; la página del artefacto trae sus propios `SHA256SUMS`):

- **Sin firmar**, es el release: [`total-music-181-release-unsigned`](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366/artifacts/12781671728)
  — `app-prod-universal-release-unsigned.apk`, SHA-256
  `059932b9c2ddab5e3b293738ac2d54bfc9f296412afd8789aab645fbc33ecd5d`.
- **APK de prueba** (certificado de depuración del runner): [`total-music-181-test-apk`](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366/artifacts/12781492062)
  — `app-prod-universal-debug.apk`, SHA-256
  `f64ba1ee3637d890b0430ca91957cbcb6cbc983b135491b9de607169b583c136`, y
  `app-prod-universal-measurement.apk` (R8, paquete publicado), SHA-256
  `5bfd0bbea7d7f64f65ad58e53a87fda6098662eabaae7c92bf03fcd90aa69d09`.
- Conjunto completo por ABI: [`total-music-181-apks`](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366/artifacts/12781492121).
- Registros: [`total-music-181-logs`](https://github.com/Spaceapk/Total-Music/actions/runs/40606188366/artifacts/12781966697).

Identidad del APK release, leída con `aapt` sobre el propio archivo:

```
package: name='org.nexlibre.totalmusic' versionCode='15' versionName='1.8.1'
certificate: ninguno (NOT SIGNED)
```

## Compilación, pruebas y lint (run 40591890427, idéntico en 40606188366)

- `:app:compileProdDebugKotlin`: correcto.
- `:app:assembleProdDebug`, `assembleProdMeasurement` (R8, firmado con la clave de depuración del
  runner), `assembleProdRelease` (sin firmar) y `assembleProdDebugAndroidTest`: correctos.
- Pruebas unitarias: **1.079 aprobadas, 0 fallos, 106 clases** (`shared:jvmTest` y
  `app:testProdDebugUnitTest`).
- lint `prodDebug`: **0 errores**, 655 avisos. Es un trabajo aparte con su propio runner, un
  trabajador de Gradle y `--no-daemon`, que fue justo el problema de 1.8.0: el lint se quedaba sin
  veredicto cuando compartía memoria con las pruebas.
- `scripts/release/check_release.py`: correcto. `scripts/verify/rebirth_check.py --base main`:
  correcto.

## Dispositivo (Android 13, API 33)

En el run de entrega 40606188366, sonda sobre el APK *release-like* (R8, paquete publicado):

```
== flows ['settings', 'player', 'categories'] in LIQUID_GLASS_PLUS ==
launch LIQUID_GLASS_PLUS: pid=alive crash=no
LIQUID_GLASS_PLUS player opened: pid=alive crash=no
LIQUID_GLASS_PLUS returned from player: pid=alive crash=no
== flows ['settings', 'player', 'categories'] in GOOGLE_EXPRESSIVE_PLUS ==
launch GOOGLE_EXPRESSIVE_PLUS: pid=alive crash=no
```

Después, el emulador se atascó: `adb logcat -d -b crash` agotó su tiempo y el segundo
lanzamiento de la sonda no llegó a arrancar. El paso de sonda es `continue-on-error`, así que el
trabajo figura en verde aunque esa mitad no exista; es un defecto del informe, no de la aplicación,
y está anotado en «Límites». La pasada completa y limpia, sobre código de aplicación idéntico, es el
run 40591890427 de más abajo.

Prueba de interfaz (job `ui`), Compose UI sobre emulador, dos casos **aprobados**:

- `RebirthGlassShellUiTest > liquidGlassShellDrawsAndSurvivesTheSettingsRoundTrip`
- `RebirthGlassShellUiTest > expressiveShellDrawsAndSurvivesTheSettingsRoundTrip`

El primero compone el shell Liquid Glass+ con efectos de cristal activos, dibuja fotogramas con el
reloj bajo control, abre Ajustes encima, vuelve y exige que el shell siga ahí. Es la secuencia del
informe («se cierra al volver de Ajustes»), y en 1.8.0 no habría llegado a la primera aserción:
el proceso moría al dibujar.

Sonda de dispositivo sobre el APK release-like universal (R8, no debuggable):

```
style file: player_visual_style">LIQUID_GLASS_PLUS
launch LIQUID_GLASS_PLUS: pid=alive crash=no
LIQUID_GLASS_PLUS player opened: pid=alive crash=no
LIQUID_GLASS_PLUS returned from player: pid=alive crash=no
style file: player_visual_style">GOOGLE_EXPRESSIVE_PLUS
launch GOOGLE_EXPRESSIVE_PLUS: pid=alive crash=no
```

## Disponibilidad de firma: comprobada, y no está

Listar los secretos del repositorio exige permisos de administración que la integración no tiene
(403), y eso es lo correcto: una clave que se puede leer desde CI es una clave que se filtra desde
CI. Por eso la comprobación se hace dentro del propio runner
([`total-music-181-signing-check.yml`](../.github/workflows/total-music-181-signing-check.yml),
[run 40605768510](https://github.com/Spaceapk/Total-Music/actions/runs/40605768510)), que informa
solo presencia y huella pública — nunca una contraseña, ni el keystore, ni material de clave:

| Secreto candidato | Configurado |
| --- | --- |
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | no |
| `TOTAL_MUSIC_KEYSTORE_B64`, `TOTAL_MUSIC_KEYSTORE_PASSWORD` | no |
| `SPACE_SPATIAL_KEYSTORE_B64`, `SPACE_SPATIAL_KEYSTORE_PASSWORD` | no |
| `SPACE_KEYSTORE_B64`, `SPACE_KEYSTORE_PASSWORD` | no |
| `TOTAL_MUSIC_CANDIDATE_URL` | sí (una URL de descarga, no una clave) |

**Veredicto: la firma permanente no está disponible en CI.** Ningún keystore está configurado en los
secretos de Actions de este repositorio, así que ningún APK puede salir firmado con la clave
permanente desde aquí.

## Artefactos: dos entregas separadas

El APK release y el APK de prueba van en artefactos distintos, cada uno con sus sumas y un archivo
de texto que dice qué es:

- **`total-music-181-release-unsigned`** — `app-prod-universal-release-unsigned.apk`, **sin firmar**,
  más `SHA256SUMS` y `SIGNING-STATUS.txt`. Es el único archivo que una firma permanente puede
  convertir en actualización.
- **`total-music-181-test-apk`** — `app-prod-universal-debug.apk` y
  `app-prod-universal-measurement.apk`, firmados con el certificado de depuración del runner, más
  `SHA256SUMS` y `TEST-ONLY.txt`. Instalan en un dispositivo limpio; **no** se instalan encima de un
  Total Music publicado.
- `total-music-181-apks` — el conjunto completo por ABI, el que consume el job de dispositivo.
- `total-music-181-logs` — compilación, pruebas, lint y registro de firmas.

Los SHA-256 exactos de cada entrega los imprime el comentario del run
(«### Deliverables»), porque cambian con cada compilación.

## Qué falta exactamente para una actualización compatible

1. El keystore permanente (el del certificado `9ae145a7…`) y sus dos contraseñas, fuera de este
   entorno. `.signing/space-release-key.cms` no sirve: es un `EnvelopedData` RSA-OAEP dirigido a
   `CN=Space Music signing backup`, otra identidad y cifrada, y su clave privada tampoco está aquí.
2. Con eso, firmar el APK sin firmar con `scripts/release/sign_apk.py` (que además comprueba
   paquete, `versionCode` creciente y continuidad del certificado contra el APK anterior), o
   exponer el keystore como los secretos `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
   `KEY_PASSWORD`, con lo que la compilación de CI lo firmaría directamente.
3. Instalar el resultado sobre 1.8.0 en un dispositivo o emulador y comprobar `dumpsys package`
   (`versionCode=15`, `versionName=1.8.1`) y que el arranque no cambia de identidad.

## Firma y actualización

- El paquete y la identidad no cambian: `org.nexlibre.totalmusic`, `versionCode` 15 frente al 14 de
  1.8.0, mismo `certificateSha256=9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`
  declarado en `total-music-release.properties` (el del certificado de 1.7.0 y 1.8.0 publicados).
- Con eso, un APK 1.8.1 firmado con la clave permanente es una actualización válida sobre 1.8.0 y
  sobre 1.7.0. Sin esa firma, no lo es: los APK de CI están firmados con la clave de depuración del
  runner y no se pueden instalar encima de una instalación existente.
- No se ha generado ni sustituido ninguna clave, no se ha publicado ninguna release y no se ha
  integrado nada en `main`: el trabajo vive en el PR #5, sin fusionar.

## Límites

- No hay teléfono real en este entorno: todo lo anterior es emulador (API 33, x86_64) y CI.
- El cierre se ha reproducido en Liquid Glass+ y no en Original ni en Google Expressive+; si en el
  teléfono del informe aparece también con otros estilos, hace falta el `logcat -b crash` de ese
  arranque, porque sería otra causa.
- Lint deja 655 avisos heredados; no se han silenciado ni se ha tocado el baseline.
- Los hashes de APK son de esta ejecución: una recompilación produce otros distintos.
- Una repetición posterior de la sonda del APK publicado
  ([run 40600190808](https://github.com/Spaceapk/Total-Music/actions/runs/40600190808)) no llegó a
  ejecutar los dos estilos y no aporta nada: el control publicado válido es el run 40580679112.
- La sonda por `adb` no consigue leer siempre la jerarquía de accesibilidad del shell, así que sus
  notas de flujo («qué botón encontró») no son prueba de que Ajustes se abriera; la prueba de
  Compose sí lo hace de forma explícita.
- Los pasos de sonda del job de dispositivo son `continue-on-error`, de modo que un emulador que se
  atasca deja el trabajo en verde con un traceback en el comentario. Deberían fallar cuando el
  proceso de la sonda termina por excepción (no cuando encuentra un fallo de la aplicación) y
  publicar una línea de veredicto por estilo; queda pendiente.
