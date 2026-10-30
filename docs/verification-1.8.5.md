# Total Music 1.8.5 — arrastre de listas, orden por pista y favoritas unificadas

## Base y alcance

- PR: https://github.com/Spaceapk/Total-Music/pull/12 (abierto, **sin fusionar**).
- Rama de la sesión: `arena/8c5cead2-total-music` (nombre fijo del entorno).
- Base 1.8.4: `ef58cf86`; integrada mediante fast-forward. La base no estaba en `main`
  (`main` sigue en `3dee549` y no se tocó).
- Código probado: `676a6851` (workflow de 1.8.5). Los commits posteriores solo añaden
  documentación y no cambian el APK.
- Identidad conservada: `org.nexlibre.totalmusic`; versión `1.8.5`, código `23`
  (1.8.4: `22`). Fuente única: `total-music-release.properties`.
- No se modificaron `main`, las ramas de los PR anteriores ni el certificado permanente.
- No se reinició el proyecto: se parte del árbol de 1.8.4 y se cambian las piezas
  necesarias.

## Cambios

### Mis favoritas en los cuatro temas

`LikeState` sigue siendo la única fuente de verdad (corazones del reproductor, la
notificación, los controles multimedia y las bibliotecas). Sobre ella:

- Las bibliotecas + (Google Expressive+ y Liquid Glass+) siguen mostrando la colección
  virtual **Mis favoritas** en la categoría Listas, con su corazón, su reproducción y su
  selector de orden.
- Las dos experiencias originales, que comparten la biblioteca clásica, tienen la misma
  colección en la pestaña **Listas**, como entrada `Mis favoritas` con el mismo
  reproductor, el mismo botón de aleatorio y el mismo selector de orden que las +.
- Quitar un corazón desde cualquiera de las dos rutas escribe el mismo `LikeState`, de
  modo que la fila desaparece a la vez en las cuatro apariencias y en el reproductor.
- La colección se calcula una sola vez con `favoriteSongsInOrder`: combina las canciones
  favoritas guardadas con las de la biblioteca local, descarta duplicados por `videoId`
  y aplica `FavoriteSort` (título, artista, álbum o fecha añadida). No se guarda como
  playlist real ni se puede renombrar o borrar.

### Arrastrar para reordenar

- Los botones de subir/bajar desaparecen de las listas: el único control es un asa de
  arrastre de 44 dp. Está en las dos bibliotecas + y en la pestaña de listas clásica.
- Un gesto = un movimiento. Al soltar se llama una sola vez a `DevicePlaylistStore.move`
  con la posición de origen y la de destino, y se guarda como orden **manual**.
- Si la lista estaba mostrando otro criterio (título, artista, álbum, pista o fecha), el
  arrastre toma exactamente las filas visibles, en ese orden, y las convierte en el orden
  manual. No se salta al orden manual antiguo ni se pierden canciones.
- Auto-scroll: al acercar la fila a 56 dp del borde superior o inferior, la lista se
  desplaza a 1100 dp/s mientras el dedo siga cerca del borde. El desplazamiento se
  detiene en un extremo que la lista no puede pasar y no se ejecuta en listas que caben
  en pantalla. La regla vive en `playlistAutoScrollVelocity`, probada sin Android.
- El arrastre no se activa en búsquedas ni en colecciones que no son listas; en esos
  casos el asa no aparece.

### Ordenación

`PlaylistSort` ofrece ahora: manual, título ↑/↓, artista ↑/↓, álbum ↑/↓, número de pista
↑/↓ y fecha de incorporación. Cada lista guarda su criterio en el mismo JSON y lo
recupera al reabrir la aplicación (`fromPersisted`).

Semántica exacta:

1. **Álbum**: agrupa por *(álbum, artista)*. Dos discos homónimos de artistas distintos
   son dos grupos y no se intercalan. Dentro del grupo, disco (ausente = 1) y pista como
   números —1, 2, 3… 10, no 1, 10, 2— y el título como desempate.
2. **Artista**: artista → álbum → disco → pista, con el mismo orden numérico.
3. **Pista**: disco y pista; las canciones sin número de pista van al final, también en
   orden descendente (no se promocionan al principio por tener un número ausente).
4. **Título y fecha**: sin cambios; la fecha usa el orden de incorporación guardado y el
   título desempata.
5. Descendente invierte la clave de grupo (álbum o artista), no el orden interno de cada
   disco.

`MANUAL` devuelve exactamente el orden guardado; cambiar de criterio nunca reescribe ese
orden hasta que el usuario arrastra.

### Aleatorio desactivado y orden de la cola

Con el aleatorio apagado, la cola se construye con la misma lista que se muestra en
pantalla (`sortedSongs()` filtrada por la búsqueda) y el índice inicial es el de la fila
pulsada. Reproducir una colección desde la cabecera usa las filas visibles, no el orden
manual oculto. `QueueShuffle` solo se activa cuando el usuario pide aleatorio; no se
toca su comportamiento ni el de la reproducción aleatoria existente.

### Compatibilidad

- El JSON de listas se lee sin migración destructiva: `sort` sigue siendo opcional, las
  canciones conservan URI, ruta, disco y pista, y `addedUris` mantiene la independencia
  entre el orden manual y la pertenencia.
- No se añadieron bases de datos ni se tocaron audio, listas guardadas, ajustes, temas o
  la firma.
- Las cadenas nuevas viven en `values/total_music_185.xml` y
  `values-es/total_music_185.xml`; no se modificó `strings.xml` (regla de
  `LocaleStringsTest`).

## Evidencia ejecutada

Run de validación completo: https://github.com/Spaceapk/Total-Music/actions/runs/41115728323

| Comprobación | Resultado |
| --- | --- |
| Compilación completa (`:app:compileProdDebugKotlin`, shared, sharedUi) | Éxito |
| Ensamblado debug, measurement R8 y release sin firmar | Éxito |
| Unitarias JVM (`:shared:jvmTest` + `:app:testProdDebugUnitTest`) | **1112 aprobadas, 109 clases** |
| Instrumentación Android 13 | **36 aprobadas de 36** |
| Lint | 0 errores (job `lint` en éxito) |
| Sondeo Android 13 (release-like R8 y debug, ambos estilos) | Jobs en éxito; alcance limitado, véase abajo |
| `rebirth_check.py --base origin/main` | Aprobado |
| `check_release.py` | Identidad y notas 1.8.5/23 aprobadas |
| `python3 -m unittest discover -s scripts/release -p 'test_*.py'` | 3 aprobadas |
| `git diff --check` | Aprobado |

Las 36 instrumentadas incluyen las cuatro nuevas de `PlaylistReorderUiTest`:

- arrastre real (toque, desplazamiento, suelte) en Google Expressive+ y en Liquid Glass+,
  comprobando que el orden visible queda como manual y que no se duplica ni se pierde
  ninguna canción;
- arrastre real en la pestaña de listas clásica;
- la colección **Mis favoritas** de esa pestaña clásica, con el corazón que quita la fila
  del mismo almacén compartido.

También incluyen las nuevas de `DevicePlaylistStoreTest` (orden de pista persistido por
lista, álbumes homónimos con discos, arrastre que conserva el orden visible y 300 pistas
sin pérdidas) y las de `FavoriteCollectionUiTest` (ambas direcciones de álbum y la
colección virtual en Liquid Glass+).

### Iteraciones con fallo, conservadas por honestidad

| Run | Fallo | Corrección |
| --- | --- | --- |
| `41111127602` | El reemplazo de `DevicePlaylistStore.kt` había borrado `package` e imports: 719 errores de compilación | Restaurados cabecera e imports en `e62e9e27` |
| `41112530846` | Faltaba `import PlaylistSort` en las dos bibliotecas + | Añadido en `714c5523` |
| `41113572903` | Una expectativa de `PlaylistDragTest` ponía la fila justo fuera de la zona de borde | Caso movido dentro de la zona en `6ef5df2b`; el comportamiento era correcto |
| `41115728323` (primera pasada de UI) | `PlaylistReorderUiTest` no estaba en la lista de clases del job `ui` | Añadido al workflow en `676a6851` |

El run final `41115728323` no tiene fallos en compilación, unitarias, instrumentadas ni
lint.

## Artefactos

Disponibles en GitHub Actions; requieren acceso al repositorio y están sujetos a
retención de CI. La descarga local con `gh run download` está bloqueada en este entorno
(redirección a Azure Blob), así que no se afirma una segunda verificación local del
binario.

Run `41115728323`, commit `676a6851`:

- Release **sin firmar, no instalable como actualización** — `total-music-185-release-unsigned` (id `13005923870`):
  `app-prod-universal-release-unsigned.apk`, SHA-256
  `9818d9357b1290a0a1aecd394b243cbec8cc25f2683efe6dc02f6aecf2532c9d`.
- APK **solo de pruebas**, firmado con la clave debug del runner — `total-music-185-test-apk` (id `13007355553`):
  `app-prod-universal-debug.apk`, SHA-256
  `41448d4e63880241289f0af0305edc07ebfe5ea8ba29fe2ba9e918e041140586`.
  Certificado debug del runner: `f13e1aaca7300475d3436a9b85f63165e78457b1346606989de0b9bc284fe1c9`
  (distinto en cada runner; no es la clave permanente).
- Informe instrumentado — `total-music-185-uitest` (id `13006651760`).
- Lint — `total-music-185-lint` (id `13007030364`).
- Sondeo del emulador — `total-music-185-device` (id `13008975777`).
- Capturas del emulador — `total-music-185-screenshots` (id `13007029756`).
- APKs por ABI y registros — `total-music-185-apks` (id `13007041002`) y
  `total-music-185-logs` (id `13007180792`).

Los APK release tienen `versionCode=23` y `versionName=1.8.5`, compilados con R8 y sin
firma. No son una actualización estable.

## Firma y compatibilidad: bloqueo de entrega estable

No se dispone de la clave privada permanente ni de su contraseña, ni de un APK 1.8.4
firmado con el que comparar. No se generó ninguna clave nueva, no se expusieron secretos
y no se publicó ninguna release.

La huella exigida sigue siendo
`9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`
(alias `totalmusic-release`). Esto es el contrato esperado, **no una verificación de firma
del APK sin firmar**.

Para terminar la entrega hace falta:

1. materializar el keystore y su contraseña fuera del checkout, con permisos `0600`;
2. obtener el APK 1.8.4 firmado que esté instalado como referencia;
3. firmar `app-prod-universal-release-unsigned.apk` con
   `scripts/release/sign_apk.py --previous-apk`, usando el certificado permanente;
4. verificar huella, `applicationId`, `versionCode=23` e instalación sobre 1.8.4
   conservando datos.

El workflow `total-music-181-sign.yml` puede producir el APK firmado si los secretos
`TOTAL_MUSIC_KEYSTORE_B64`, `TOTAL_MUSIC_PASSWORD` y `EXPECTED_CERT` están configurados.
En este repositorio no lo están, y `workflow_dispatch` no está permitido desde la rama.

## Límites

- **No verificado:** actualización real sobre 1.8.4 instalada, teléfono físico,
  reproducción audible y controles de notificación/widget/pantalla de bloqueo mediante una
  prueba manual exhaustiva. La instrumentación usa un emulador Android 13.
- El sondeo del job `device` confirma arranque y dibujo de las dos rutas; no sustituye una
  revisión manual de capturas ni la comprobación de audio.
- El entorno local no tiene JDK ni SDK Android; compilación, lint y pruebas se ejecutaron
  en GitHub Actions.
- El criterio de orden y el orden manual se prueban con listas de 300 y 500 canciones en
  JVM, y con 300 en instrumentación; no se afirma una prueba con la biblioteca completa de
  un usuario real.
