# Total Music 1.8.2 — continuación de validación Android 13

## Alcance conservado

Continuación del PR #9 desde `ff62958`. No se ha cambiado ninguna función de producción, recurso, identificador, versión ni configuración de firma. Los cambios de esta continuación están limitados a pruebas instrumentadas, su ejecución en CI y documentación. No se ha fusionado el PR.

## Resultado ejecutado (Android 13)

Código y workflow validados: `8eec09d63cc5ee97ded4d2f955a3ff2d770e861d`.
Run: https://github.com/Spaceapk/Total-Music/actions/runs/40869107699
Job Android: https://github.com/Spaceapk/Total-Music/actions/runs/40869107699/job/123432384674

| Comprobación | Resultado verificable |
| --- | --- |
| Suite conectada (corazones, gestos, temas, navegación, favoritas y playlists) | **22 aprobadas** |
| Escritura antes del cierre de proceso | **1 aprobada**, `OK (1 test)` |
| Lectura tras `am force-stop` y nueva instrumentación | **1 aprobada**, `OK (1 test)` |
| Cola de 731 entradas y recreación de MainActivity en proceso aislado | **1 aprobada**, `OK (1 test)` |
| Job UI completo, incluida la puerta de fallo | **Éxito** |
| Lint | **0 errores, 661 advertencias** |
| Compilación debug / measurement R8 / release sin firmar | **Éxito** |
| Unitarias en el run final | **1085 aprobadas, 108 clases** |
| Análisis estático local Kotlin / recursos | Aprobado |
| Identidad y notas de versión (`check_release.py`) | Aprobado |
| Pruebas Python del preparador de versión | 3 aprobadas |

En total, **25 ejecuciones instrumentadas aprobadas** (22 en suite y 3 en instrumentaciones independientes). No se infiere la persistencia del contenido de una variable en memoria: las dos fases de reinicio se ejecutaron por separado y los valores de apariencia se cargaron desde Application en el nuevo proceso.

Evidencia archivada en GitHub:
- [Informe Android y logs de reinicio/regresión](https://github.com/Spaceapk/Total-Music/actions/runs/40869107699/artifacts/12894888913).
- [Lint](https://github.com/Spaceapk/Total-Music/actions/runs/40869107699/artifacts/12895048714).
- El comentario del job en PR #9 incluye tanto los 22 resultados como la salida íntegra de las dos fases de reinicio y de la regresión de cola grande.

## APK compilados y trazabilidad

El job build del mismo run terminó en éxito sobre `8eec09d`. Se conserva `org.nexlibre.totalmusic`, versión `1.8.2`, código `20`. Los commits posteriores de documentación no cambian los binarios.

- [Release universal **SIN FIRMAR**, no es una actualización instalable](https://github.com/Spaceapk/Total-Music/actions/runs/40869107699/artifacts/12896232217).
  - SHA-256 de CI: `6fbe9d22fd000d2ddf0b34ea6a97112f0d45148a092eff88003fb571b719e589`.
- [APK debug y measurement, **SOLO PRUEBAS**](https://github.com/Spaceapk/Total-Music/actions/runs/40869107699/artifacts/12895209561).
  - Debug universal: `78171de5d0802d6b81d7329b906ac91be1040aae6a98fda58853406a3b6412c4`.
  - Measurement universal: `7c8c49e07d797cc58e56082d4be50b2f10fab9c729259095c5117f5e169c52c9`.

Los hashes son los publicados por el job, no una segunda verificación local. Los artefactos requieren acceso al repositorio y están sujetos a retención. No se ha entregado un APK release firmado ni se ha probado una actualización sobre 1.8.1 instalada.

## Nuevas comprobaciones

- `FavoritePlaybackJourneyTest`: abre las bibliotecas reales Google Expressive+ y Liquid Glass+ con tres archivos/temas de prueba, selecciona Mis favoritas, comprueba la exclusión de un tema no favorito y utiliza sus filas y botones de reproducción/mezcla. Los callbacks llaman al `playSongs` y `QueueShuffle` de producción, conectados mediante `MediaController` al `PlaybackService` real. Exige el tema correcto, estado READY, isPlaying, ausencia de error y avance de posición mayor de 250 ms. Verifica membresía de la cola, siguiente según el orden visible, regreso desde la composición real de Ajustes con la colección conservada y retirada persistente sin borrar audio.
- `FavoriteProcessRestartTest`: dos instrumentaciones separadas por `am force-stop`. La primera guarda favorito, retirada explícita y apariencia; la segunda comprueba los datos cargados desde disco y la apariencia restaurada por Application en un proceso nuevo. Las preferencias de prueba y las selecciones anteriores se limpian/restauran.
- Se conservan las pruebas de corazones en los tres reproductores, gestos de ambos +, las cuatro tarjetas y navegación/restauración de destinos de la validación anterior.
- Se añade la ejecución de la regresión existente `LocalPlaybackServiceTest#largeLocalQueueSurvivesPlaybackAndActivityRecreation` (731 entradas) en instrumentación aislada, después de los recorridos funcionales. Su fallo sigue haciendo fallar el job; no se elimina ni se convierte en éxito.

## Incidencias investigadas

En `40861029636` y `40862476309`, ambos recorridos nuevos de reproducción de favoritas pasaron, pero la prueba adicional de cola grande coincidió con la pérdida del emulador (`device emulator-5554 not found`). La ejecución quedó interrumpida: no se contabiliza como una suite aprobada ni como prueba de que la aplicación causó la desconexión. La causa raíz de esas desconexiones no está demostrada; la ejecución aislada final sí pasó.

Se separó la compilación del arranque del emulador, se detuvo el daemon de compilación y se redujo memoria concurrente. Como la desconexión se repitió, se aisló la prueba de cola grande en un proceso nuevo, manteniendo una puerta de fallo explícita. Los recorridos de favoritas, las pruebas de persistencia tras muerte de proceso y las de playlists pueden así producir resultados independientes del estrés de 731 entradas.

En los runs `40864333375`, `40866281517` y `40867769031` la suite funcional ya registró **22 aprobadas**, pero las fases posteriores fallaron en la preparación porque no quedaban instalados los paquetes ni disponibles las rutas de APK esperadas. No se contaron esas fases como superadas. La corrección final conserva explícitamente los paquetes de instrumentación con `android.injected.androidTest.leaveApksInstalledAfterRun=true` y respalda APK de los directorios de compilación, con fallback de instalación `-r -t`. La ejecución final verificó ambos paquetes presentes, ambas fases y la regresión.

## Límites de interpretación

Los recorridos nuevos usan audio PCM no silencioso de prueba y observan decodificación y progreso del servicio. El emulador se ejecuta con `-no-audio`: no se ha escuchado el resultado por altavoz. Las canciones se suministran como fixtures a la composición de Biblioteca; no se declara probado el selector de carpetas SAF ni un escaneo MediaStore completo. No se han probado teléfono físico, todos los formatos de archivo ni actualización firmada desde 1.8.1. El sondeo genérico heredado sigue siendo diagnóstico y no sustituye estas aserciones estrictas.

Firma y requisitos pendientes: [signing-status-1.8.2.md](signing-status-1.8.2.md).
