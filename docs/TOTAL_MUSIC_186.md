# Total Music 1.8.6 — playlists

Base: PR #12, commit 14312beac004e799bd5f8f9967201e299677508d.
Rama local: work/playlist-chronology-selection-186. No se ha modificado main.

## Cambios preparados

- Orden por álbum basado en el año de publicación de las etiquetas de audio. Álbumes sin año al final; empates conservan el orden de incorporación de los grupos. Discos y pistas siempre en orden ascendente dentro de cada álbum. Se conserva también la dirección de años más recientes primero.
- Lectura del año desde MediaStore y etiquetas; persistencia y actualización de las playlists existentes desde la biblioteca, sin confundir año con fecha de archivo.
- Barra de selección común para los cuatro temas: reproducir selección, reproducir después, añadir a la cola, copiar a otra playlist o crear una con el selector existente, añadir/quitar favoritas, mover al inicio/final, invertir y compartir nombres de canciones como texto.
- Quitar toda la selección en una operación atómica, con confirmación. No elimina los archivos de música. Las operaciones usan las identidades de las canciones, incluso con búsqueda y orden activo.
- Pulsación larga inicia la selección; durante la selección, tocar otra fila cambia su selección sin reproducirla.
- Eliminar playlist accesible en la cabecera de los temas Plus. Los temas originales conservan la acción en el menú superior.
- VersionName 1.8.6, versionCode 24; identidad y huella del certificado previstas intactas.

## Validación realizada

- scripts/release/check_release.py: identidad pública, huella de certificado, aislamiento de firma y notas de versión correctos.
- git diff --check: sin errores.
- Recursos XML de las versiones 1.8.x: 29 archivos analizados correctamente; todas las cadenas del nuevo menú resueltas.

## Pendiente antes de entregar un APK

No se ha compilado ni probado en emulador. El wrapper no puede descargar Gradle en este entorno (Network is unreachable).

Se añadieron pruebas JVM para cronología, años desconocidos y empates; pruebas Android de persistencia de operaciones en bloque, conservación de archivos y otras playlists; pruebas de interfaz para selección sin reproducción accidental, confirmación y vaciado en los temas originales y los dos Plus. Estas pruebas todavía no se han ejecutado.

La rama incluye el disparador de verificación estándar de GitHub Actions, con pruebas JVM, lint y compilación. La subida fue rechazada por la revisión automática, que requiere autorización explícita para publicar los cambios en GitHub. No se ha utilizado otra vía para eludir el bloqueo. Falta subir la rama, ejecutar las pruebas y, si se solicita APK, compilar y firmar con la clave original.
