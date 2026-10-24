# Medición física: Infinix X6528 / Android 13

Esta guía es para comparar builds de Total Music en **el mismo teléfono**, con la
misma biblioteca local (aproximadamente 954 canciones en el caso reportado), el
mismo estado de carátulas, almacenamiento, Android y configuración de tema. No
usa un emulador como sustituto del teléfono.

## Qué se añadió

- `app` tiene la variante `prodMeasurement`: deriva de `release`, conserva
  `org.nexlibre.totalmusic`, no es depurable y no añade sufijo de paquete.
- Solo esa variante incluye `<profileable android:shell="true"/>` y
  ProfileInstaller, necesarios para las trazas de Macrobenchmark. El release
  normal no recibe esos añadidos.
- `macrobenchmark` es un módulo `com.android.test` independiente y
  self-instrumenting. Sus pruebas usan UI Automator contra la interfaz real.
- `local_songs_list` es un `testTag` del `LazyColumn`/`LazyVerticalGrid` de
  Songs; no cambia el contenido ni el comportamiento de las filas.

## Compilar

En un checkout con Android SDK, Java 21 y la configuración de firma local que
ya se use para Total Music:

```bash
./gradlew :app:assembleProdMeasurement :macrobenchmark:assembleMeasurement \
  -PspaceSpatialEdition=true --build-cache
```

No hay que crear ni cambiar claves para esta prueba. Si el checkout no tiene el
keystore release existente, Gradle produce un APK **unsigned**: sirve para
comprobar compilación y hashes, pero **no es una actualización instalable**.
El workflow `Total Music 1.6.0 physical benchmark build` (push limitado a sus
archivos o despacho manual) solo compila y publica esos artefactos; no ejecuta
el benchmark ni afirma que estén firmados. Su check `total-music-physical-benchmark-build`
contiene un tail redacted del log para diagnosticar compilación sin el CDN de logs.

Para una prueba instalable sin tocar la firma release, el workflow
`Total Music 1.6.0 installable measurement test` compila la misma variante con
`-PtotalMusicMeasurementDebugSigning=true`. Esto usa el `signingConfig` debug
existente de Android, mantiene R8, `debuggable=false`, `profileable` y el mismo
paquete, y no accede a la clave privada release. El artifact incluye el hash del
certificado de prueba y su verificación. **No es una actualización compatible
con una instalación firmada con otro certificado**: el paquete no puede convivir
con otra copia en el mismo usuario Android. Exporta `Settings > Your data` antes
de cualquier desinstalación; no borres archivos musicales.

## Prueba manual de la demora al reproducir (sin ADB)

La app registra una cadena acotada por canción. Las líneas `TIMING` usan un reloj
monotónico y se pueden copiar desde el teléfono:

1. Instala la APK de prueba solo después de comprobar el certificado y entender
   la advertencia anterior. Abre Total Music y concede el permiso de audio.
2. Para **primer inicio**, cierra la app desde Recientes, vuelve a abrirla y pulsa
   una canción. Espera a que suene y anota el tiempo audible con un cronómetro o
   una grabación si quieres medir esa parte; el callback no lo demuestra.
3. En el reproductor abierto, entra al menú de la canción actual y elige
   **Copy log**. Pega el texto en un archivo o mensaje etiquetado `primer-inicio-1`.
4. Para **cambio de canción**, deja la primera sonando, pulsa otra y espera el
   cambio. Abre el menú de la nueva canción y copia su log como `cambio-1`.
5. Repite cada caso cinco veces, sin cambiar biblioteca, red, carátulas ni
   ajustes entre repeticiones. Comparte los textos completos, no solo una
   conclusión. No hace falta logcat, root ni ADB.

En cada log, `queue requested` incluye la canción anterior y el tamaño de cola;
en una selección de la biblioteca su reloj comienza en `playFrom`, antes del
trabajo de preparación de la cola, no solamente al llamar a Media3. `queue built`
y `queue submitted` separan construcción y entrega al reproductor; `track selected`
y `isPlaying after track selected` pertenecen al servicio; `isPlaying callback ...
sinceQueueRequestMs` es el intervalo solicitud/tap→callback en ese camino.
`isPlaying` **no prueba el primer sonido audible**, por lo que no debe convertirse
en una afirmación de mejoría de altavoz. Si una línea no aparece, conserva el
log igualmente: indica que ese camino no pasó por una nueva solicitud de cola.

## Preparar el teléfono

1. Conecta el Infinix por ADB y confirma `adb devices` y Android 13.
2. Instala la build `prodMeasurement` firmada con la misma configuración que la
   build con la que se comparará. No uses la variante histórica `.benchmark`.
3. Abre Total Music una vez, concede lectura de audio y cierra el diálogo de
   novedades. Verifica que Songs muestra la biblioteca que se quiere medir.
4. No ejecutes `pm clear`, no borres datos y no copies, renombres o elimines
   canciones. La prueba no siembra archivos ni modifica la biblioteca.
5. Mantén el teléfono desbloqueado, con carga suficiente, sin ahorro de batería
   y en condiciones térmicas similares entre builds. Deja que las carátulas
   terminen de cargarse antes de la primera medición si ese será el estado
   común de la comparación.

## Ejecutar una prueba

Con el dispositivo físico como único dispositivo ADB:

```bash
./gradlew :macrobenchmark:connectedMeasurementAndroidTest \
  -PspaceSpatialEdition=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.music.bitchord.macrobenchmark.LibraryBenchmark#scrollLocalLibrary
```

Sustituye el nombre final por `libraryTabNavigation`, `localSongQueueSubmission`
o `coldStart` para las otras pruebas. Ejecuta cada prueba por separado y conserva
el JSON y todas las trazas de cada build. No actives la supresión de errores
`EMULATOR`: estas pruebas están destinadas a hardware físico.

- **scrollLocalLibrary**: ocho flings (cuatro hacia abajo y cuatro hacia arriba)
  sobre la lista real. Publica `FrameTimingMetric`, incluidos percentiles y
  `frameOverrunMs` cuando la plataforma los expone.
- **libraryTabNavigation**: recorre Songs → Albums → Artists → Songs usando los
  textos localizados del propio paquete y mide frames durante las transiciones.
- **localSongQueueSubmission**: pulsa la primera canción real, espera el estado
  `pause` del controller y lee `TotalMusic.buildQueue` y
  `TotalMusic.submitQueue` mediante `TraceSectionMetric`. La UI queda pausada
  para la siguiente iteración. Mide preparación/envío y el callback de la UI;
  no mide el primer sonido audible. Las dos revisiones comparadas deben contener
  esas mismas secciones de traza.
- **coldStart**: mide arranque frío con `StartupTimingMetric`; las notas de
  versión deben haberse cerrado antes para que las iteraciones sean equivalentes.

Los archivos se copian normalmente a:

```text
macrobenchmark/build/outputs/connected_android_test_additional_output/
```

Abre las trazas `.perfetto-trace` en Perfetto o Android Studio. Guarda también
el JSON, la variante, el hash del APK, el modelo, Android, el número de canciones
visibles y si las carátulas estaban ya almacenadas.

## Comparación aceptable

Para 1.5.0 frente a 1.6.0, compila ambas revisiones con la **misma infraestructura
de medición** y ejecuta las mismas pruebas en el mismo Infinix, sin mezclar
emulador, runners ni teléfonos. Compara distribuciones y trazas, no una sola
iteración. Si una build no tiene `prodMeasurement`, aplica únicamente el cambio
de infraestructura de medición a esa revisión antes de compilar; no uses un APK
debug contra uno release-like.

## Límites

Estas pruebas no demuestran por sí solas la latencia de audio audible. El harness
`LocalPlaybackPerformanceTest` de CI usa una WAV sintética, colas artificiales y
un emulador con `-no-audio`; `is_playing_ms` es el callback del controller, no la
salida audible. Un resultado de scroll, arranque o callback tampoco prueba una
mejora de reproducción en el Infinix. Hace falta una sonda física específica y
una comparación antes/después para atribuir ese efecto.
