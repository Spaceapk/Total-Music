# Total Music 1.5.0 — implementación pendiente de validación

Base: `ad8b1775639a4561bd79037217b4c4bd394b841c` de main, 1.4.1 (9).
Objetivo preparado con `prepare_version.py`: 1.5.0 (10).
No se ha publicado una Release ni generado un APK en esta sesión.

## Cambios de código

- Se restaura «Número de pista» en el menú real de ordenamiento. El escáner
  incorpora el campo TRACK de MediaStore (disco × 1000 + pista); el lector
  de archivos utiliza las claves nativas CD_TRACK_NUMBER y DISC_NUMBER.
  Android se encarga de interpretar las etiquetas del contenedor. El parser
  admite valores numéricos, con ceros y con total (`01/12`). Pistas sin número
  van al final; empates conservan su orden de entrada. Ningún archivo se escribe.
- El orden dentro de un álbum se recalcula al cambiar la preferencia existente,
  que ya se guarda en SharedPreferences. Las playlists descargadas conservan
  su orden de colección y no pasan por el nuevo ordenamiento de álbumes.
- Seleccionar todas usa canciones visibles: búsqueda, pestaña y colección
  abierta. Los cambios de búsqueda eliminan de la selección los IDs ocultos.
- Los artistas y álbumes disponen de ⋮ > Añadir a playlist en vista lista y
  cuadrícula. Utiliza el selector existente con creación de listas. Un álbum
  se añade por disco/pista; se conserva la deduplicación por URI. Se informa
  el número efectivo de canciones nuevas. Los artistas se agrupan por igualdad
  del campo artist, sin búsqueda por coincidencia parcial para añadir canciones.
  Si un archivo solo tiene una cadena de colaboración, se conserva esa cadena;
  no se inventan créditos individuales ni se separan nombres por delimitadores.
- El componente de navegación adaptable tiene una superficie Material para
  Google Expressive y mantiene el renderizador de Liquid Glass para ese tema.
  Ambos reciben los eventos de desplazamiento y usan la preferencia existente.
- Las novedades instaladas salen de assets/release-notes.json y se buscan por
  el par versionCode/versionName, sin depender de una descarga o de Internet.
  Se mantiene el registro seen_version y se añade el historial en Ajustes.
  check_release.py falla si la versión preparada no tiene notas correspondientes.
  El historial conserva 1.3.0 y 1.4.0 con código 8: la colisión histórica está
  documentada en el repositorio; las entradas se distinguen por ambos campos.

## Verificaciones ejecutadas

- check_release.py: identidad, certificado público fijado, aislamiento de la
  firma privada y correspondencia de notas de versión: correcto.
- git diff --check: correcto.
- Parseo XML de recursos modificados y nuevos: correcto.
- Ancestro histórico 369a1f68 preservado: comprobado.

## Pruebas preparadas, todavía NO ejecutadas

- AlbumOrderingTest (shared): etiquetas 1/01/1/12, inválidas y desbordamientos,
  álbum desordenado, multidisco, pistas sin etiquetas y estabilidad de empates.
- AlbumTrackOrderUiTest (Android Compose): abre el álbum real de prueba,
  utiliza el menú Número de pista, verifica la cola reproducida y recarga las
  preferencias. Prueba adicional de Seleccionar todas con búsqueda y cambio
  de búsqueda para descartar selección oculta.
- DevicePlaylistStoreTest ampliado: persiste disco y pista al guardar una lista.
- Se conservan los tests anteriores de playlists, temas y Total EQ.

## Bloqueos observados y condiciones para publicar

La invocación Gradle se detuvo antes de compilar porque la descarga de
services.gradle.org no tiene acceso de red. El entorno solo tiene Java 17;
el flujo CI instala Java 21 y el SDK/NDK requeridos. No hay un resultado de
compilación, lint, JVM ni emulador atribuible a estos cambios.

La lectura Git del repositorio funciona. La comprobación de subida
`git push --dry-run` falla porque no hay credenciales de escritura. La conexión
GitHub anunciada devuelve Unknown tool al intentar leer mediante ella. Por eso
no se ha podido subir la rama ni ejecutar Actions desde aquí.

Antes de publicar:

1. Subir la rama y abrir PR hacia main para ejecutar total-music-android.yml.
2. Superar JVM, pruebas Android locales, lint y ensamblado release del flujo.
3. Ejecutar connectedProdDebugAndroidTest en un dispositivo/emulador; estas
   pruebas de interfaz no se ejecutan en el flujo actual por defecto.
4. Probar archivos reales MP3/TRCK, FLAC/Ogg TRACKNUMBER y M4A, álbumes
   multidisco y archivos sin etiquetas. Confirmar persistencia al reiniciar.
5. Revisar eliminación de archivos y consentimiento Android, metadatos,
   colores de portada, AMOLED, EQ de 10 bandas, perfiles y navegación con
   ambos temas. Que su código no se haya modificado no demuestra su ejecución.
6. Recuperar la clave permanente y el APK anterior por el procedimiento seguro
   del repositorio. Firmar con sign_apk.py y comprobar actualización desde 1.4.1.
7. Solo después publicar APK firmado y verificaciones, y respaldar el ZIP en
   Google Drive. No se cambió ni se generó ninguna clave privada.

## Avance de continuación

Se recuperó el paquete guardado sin perder cambios. La compilación Android y
las pruebas JVM pasaron localmente con Java 21, SDK oficial y el proxy del entorno:
50 pruebas shared + 1017 app; 0 fallos, 0 errores, 1 omitida.

Se restauraron también los menús de información de canción y eliminación
local: lectura de etiquetas nativas disponibles, tamaño, formato y ubicación;
confirmación explícita, consentimiento MediaStore en Android 11+ y recuperación
de permiso en Android 10. Falta validación funcional de esas rutas en un emulador.
Las etiquetas personalizadas de productor no se inventan ni están cubiertas
por el lector nativo.

Lint detectó 26 recursos nuevos sin traducción. Se incorporaron traducciones
en los 14 idiomas adicionales soportados; el baseline se mantiene intacto.
Se está repitiendo la verificación tras esta corrección.

La API de GitHub lee el repositorio con Spaceapk, pero deniega crear ramas y
árboles con 403 Resource not accessible by integration. Se está probando la vía
web autorizada del propietario para conservar el código y disparar Actions.
La nueva workflow de regresión usa KVM en GitHub después de que el emulador
local sin aceleración no lograra responder mediante ADB. No contar ese intento
local como una prueba de instalación o de interfaz superada.
