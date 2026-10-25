# Total Music Debug: diagnóstico independiente

`prodDiagnostic` es una copia release-like de Total Music 1.6.0 para instalar
junto a la aplicación principal. La variante principal conserva
`org.nexlibre.totalmusic` y su configuración de firma; la copia usa:

- `applicationId`: `org.nexlibre.totalmusic.debug` (`applicationIdSuffix = ".debug"`).
- Nombre visible: **Total Music Debug**.
- Firma debug de prueba, R8, `debuggable=false` y `profileable`.
- `FileProvider`: `org.nexlibre.totalmusic.debug.fileprovider`.
- ProfileInstaller y el módulo Macrobenchmark en su variante `diagnostic`.

No hay que desinstalar ni reemplazar la aplicación principal. Android solicita
el permiso de lectura de audio para la copia por separado. Los ajustes y datos
locales de cada paquete son independientes; no uses `pm clear` ni borres medios.

## Instalación desde el teléfono

1. Descarga el ZIP del artifact de GitHub Actions y extráelo con Archivos.
2. Abre `Total-Music-1.6.0-debug-diagnostic.apk` y permite la instalación desde
   esa fuente si Android la solicita.
3. Abre **Total Music Debug**, concede lectura de audio y espera a que aparezca
   la biblioteca. No instales el APK de Macrobenchmark para esta prueba manual.
4. Comprueba en el cajón de aplicaciones que coexisten **Total Music** y
   **Total Music Debug**. El artifact incluye paquete, versión, SHA-256 y
   certificado verificados en el workflow.

## Comparar selección manual y Siguiente, sin computadora

El hallazgo a investigar es: selección manual de 3–5 s, Siguiente aproximadamente
1 s, UI inmediata y la canción anterior sonando durante la espera. Es una
observación del dispositivo, no todavía una causa demostrada.

Para cada par de muestras, con la misma cola, red, carátulas y ajustes:

1. Pulsa manualmente una canción de la biblioteca. Si quieres medir audio
   audible, inicia un cronómetro en el tap y deténlo al escucharla. La interfaz
   puede responder antes de que cambie el audio.
2. Cuando suene, abre el menú de la canción actual en el reproductor y elige
   **Copiar registro / Copy log**. Guarda el texto como `manual-1`.
3. Con esa canción sonando, pulsa **Siguiente**. Espera la nueva canción y copia
   su registro como `siguiente-1`.
4. Repite cinco pares. No borres datos ni cambies la biblioteca entre pares.
   Comparte los textos completos y, si los tienes, los tiempos del cronómetro.

No hace falta ADB, root, logcat ni conectar el teléfono a una computadora.

## Qué mirar en los logs

- `request received`: ruta y origen del reloj. `manual_library` empieza en el
  callback de UI; `next_button` empieza al recibir el servicio la orden.
- `queue build started`, `queue built` y `queue submitted`: trabajo de crear y
  enviar una cola nueva. El recorrido Siguiente no debería necesitar estas fases.
- `session queue received` y `player queue received`: llegada de la cola al
  servicio y al reproductor, separadas de la construcción en la UI.
- `audio prepare received`, `audio state BUFFERING/READY`, `play command
  received` y `track selected`: preparación y callbacks del reproductor.
- `isPlaying callback`: incluye `route`, `sinceRequestMs` y `sinceServiceMs`.
  Compara `sinceServiceMs` entre recorridos; `sinceRequestMs` además incluye el
  tap y la preparación previa cuando el origen es UI.

`isPlaying` no prueba el primer sonido audible. El cronómetro o una grabación
es necesario para esa afirmación. Una diferencia en estos logs todavía no
atribuye por sí sola la causa a Compose, MediaStore, red, carátulas, DSP o
ExoPlayer; hay que revisar qué fase concentra el tiempo y repetir en condiciones
comparables. El ecualizador desactivado en estas muestras debe anotarse como
condición de la prueba.
