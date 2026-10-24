# Total Music 1.5.0 — implementación pendiente de validación

Base: `ad8b1775639a4561bd79037217b4c4bd394b841c` de main, 1.4.1 (9).
Objetivo preparado con `prepare_version.py`: 1.5.0 (10).
No se ha publicado una Release. Los APK locales ya compilan; la entrega firmada sigue pendiente.

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

## Verificaciones al 23 de octubre de 2026

- Java 21 y SDK/NDK oficiales recuperados; bloqueo inicial de descargas resuelto.
- 50 pruebas shared + 1017 app: 0 fallos, 0 errores, 1 omitida.
- lintProdDebug y lintVitalProdRelease correctos, baseline existente intacto.
- assembleProdRelease, assembleProdDebug y assembleProdDebugAndroidTest correctos.
- Identidad, certificado público, notas de versión, XML e historia comprobados.
- Fuente conservada en work/total-music-1.5.0-recovery y PR #1 como borrador.
- CI de rama: https://github.com/Spaceapk/Total-Music/actions/runs/40026245060
- 7 pruebas Android 13 (álbum/selección/playlists): correctas.
- UI Android 13: https://github.com/Spaceapk/Total-Music/actions/runs/40026218805
- PR: https://github.com/Spaceapk/Total-Music/pull/1

## Regresiones recuperadas

Se restauraron los menús de información y eliminación local: etiquetas nativas
 disponibles, tamaño, formato y ubicación; confirmación explícita y consentimiento
MediaStore en Android 11+, permiso recuperable en Android 10. Su validación
funcional aún está pendiente. El lector nativo no expone etiquetas personalizadas
de productor: no se inventan créditos.

## Pendientes obligatorios antes de publicar

- Terminar CI de release y ampliar las regresiones Android; las siete pruebas
  de álbum, selección y playlists ya pasaron en Android 13.
- LocalTrackMetadataTest añade cuatro archivos sintéticos de 32 segundos con
  metadatos disco 2/3 y pista 01/12 en MP3, FLAC, Ogg y M4A. Verifica el escáner
  real y el lector de metadatos; todavía requiere ejecución Android.
- Ejecutar las demás pruebas instrumentadas existentes de reproducción local,
  playlists, preferencias, letras y audio espacial.
- Revisar eliminación con consentimiento, colores de carátula, AMOLED, EQ y
  navegación/minirreproductor en Google Expressive y Liquid Glass.
- Verificar novedades una vez tras actualizar y consulta offline desde Ajustes.
- Descargar el APK exacto de CI, comprobar commit y SHA256, firmar localmente con
  la clave permanente y validar instalación/actualización desde 1.4.1 en Android.
- Publicar solo después de las verificaciones obligatorias y respaldar en Drive.

El emulador local carece de KVM y no respondió a ADB; ese intento no cuenta como
instalación ni prueba de interfaz superada. Se usa KVM en GitHub para continuar.
La API de escritura de GitHub devuelve 403; la vía web del propietario permitió
conservar los cambios en rama sin alterar main. No se han publicado APK estables
ni sustituido claves. La recuperación solo puede escribir en su rama dedicada.

## Resultado de la suite ampliada

CI de c5e41d09: JVM, lint y APK release correctos (run 40027502397).
La suite completa inició 26 pruebas: 18 resultados recibidos, con un fallo de
expectativa heredada en Android Auto (el servicio local ya exponía local_music
en la base 1.4.1) y una interrupción de reproducción coincidente con pérdida
del emulador. No se consideran las pruebas restantes aprobadas. Se corrige
la expectativa al contrato local y se limita memoria para repetir la suite.
Metadatos reales se ejecutan primero para obtener evidencia independiente.
