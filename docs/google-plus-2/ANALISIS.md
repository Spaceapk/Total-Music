# Google Plus 2.0: análisis previo y matriz

Estado: implementación en curso. No es una actualización validada ni una APK entregada.

## Procedencia

Referencia: `1000248433.mp4`, 720 × 1612, 98,2406 segundos, 1746 fotogramas.
Se extrajeron vistas de todo el intervalo a un segundo, además de fotogramas
individuales del reproductor y las letras. Las acciones entre muestras y las curvas
requieren reproducción/captura adicional antes de certificar fidelidad de movimiento.

Repositorio: Spaceapk/Total-Music. `main` y `v1.10.0` resuelven a
`3dee549dbcda996ac94865d7b999fdacb399ee4a`, cuyo archivo de versión dice 1.6.0/code12.
Esa base no contiene los temas Plus ni Obsidian. La rama
`arena/1ae8428b-total-music`, commit `98dfb39fe08621667479675e3d8a2dc93a3c3307`,
contiene esos temas y se identifica como 1.10.1/code30. Se trabaja en una rama
independiente sobre esa base para conservarlos. No se ha resuelto la inconsistencia
entre la etiqueta publicada y el contenido del código de main.

## Matriz de correspondencia (antes de editar)

| Tiempo aproximado | Evidencia observable | Componente existente | Diferencia / acción |
| --- | --- | --- | --- |
| 00–06, 37–38 s | Tu Mix, collage con carátulas de formas distintas, aleatorio, mix diario, recientes, estadísticas | RebirthLibraryExpressive.kt / ExpressiveHome | El inicio actual usa métricas y un álbum rectangular; requiere nueva composición y datos reales de recientes/estadísticas. No inventar un motor de recomendaciones. |
| 06–09, 20, 26–37 s | Reproductor tonal; carátula cuadrada; título pequeño; salida/cola arriba; letras junto al título; transporte en tres bloques; aleatorio/repetir/favorito en grupo inferior | RebirthExpressivePlayer.kt, RebirthPlayerAtoms.kt, ArtworkPlayerTheme.kt | Cambiar únicamente la escena Expressive. Conservar callbacks y menú/herramientas. |
| 09–19 s | Letras centradas; una línea activa; cápsula de canción; pausa flotante y progreso; pestañas sincronizadas/estáticas | RebirthLyricsPage, rememberLyricsTranslation, LyricsOffsetSheet | La implementación existente no sigue automáticamente y marca todas las líneas anteriores como activas. Crear presentación exclusiva Plus con el mismo proveedor/traducción. |
| 13–14 s | Menú de letras: guardar, traducir, alineación, sincronización y opciones | Hoja de proveedores, traducción y ajuste de desfase existentes | Reusar opciones disponibles; no declarar guardado o alineación nuevos como implementados. |
| 21–25 s | Cola sobre panel; carátulas; canción actual; asas; menú y grupo de acciones al pie | RebirthQueuePage, RebirthQueueDrag, QueueCoordinator | Conservar orden real, claves de filas duplicadas, bloqueo de sesión y callbacks; ajustar transición exclusiva Google. El video no demuestra persistencia tras reinicio. |
| 39–44 s | Búsqueda, tarjetas de géneros a dos columnas, colores e iconos | Búsqueda global existente / RebirthLibraryExpressive | No sustituir por tarjetas ficticias sin datos de géneros. Pendiente enlazar catálogo real. |
| 45–54 s | Biblioteca; canciones/álbumes/artistas/listas/carpetas/me gusta; búsqueda de pestañas y reordenación | RebirthLibraryData, DevicePlaylistStore, CategoryPills | Los cinco grupos actuales deben conservarse. Carpetas y orden de pestañas son alcance adicional observable, sin inventar su estado. |
| 55–98 s | Ajustes, gestión, apariencia, reproducción, comportamiento, IA, desarrollador, capacidades | RebirthSettings, SettingsSheet, TotalEqStudio | Mantener rutas existentes. No reconstruir servicios de IA o diagnóstico solo por verlos en el video. |
| Todo el video | Mini reproductor fino, carátula circular, anterior/pausa/siguiente; Inicio/Buscar/Biblioteca | RebirthChrome.kt, PlayerDock, miniPlayerGestures | Cambiar forma y orden de navegación manteniendo registro de carátula y gestos. |

## Arquitectura y conservación

Android/Compose Multiplatform, Java 21, Gradle 8.11.1, SDK/NDK fijados por los
workflows. Paquete de publicación: org.nexlibre.totalmusic. Mínimo Android 8/API26.
El servicio/PlayerConnection aporta reproducción, posición y cola; las escenas
reciben RebirthTransport. No crear otro reproductor. DevicePlaylistStore mantiene
listas; LikeState mantiene favoritos. Las letras usan LyricsRepository, proveedores,
rememberLyricsTranslation y desfase compartidos. No cambiar su almacenamiento.
Los nombres persistidos de PlayerVisualStyle siguen iguales.

## Decisiones que no demuestra el video

- Las medidas dp se derivan de proporciones, no de la densidad física desconocida.
- La tipografía disponible del proyecto sustituye a la fuente no identificada.
- Duraciones iniciales de transición son provisionales; no son mediciones del video.
- Modo oscuro, accesibilidad, teclado, rotación y actualización de datos no se prueban en el video.
- La calidad debe provenir de metadatos reales. Nunca insertar “FLAC” o tasas de ejemplo.
- Los controles adicionales de Total Music quedan accesibles mediante menú/herramientas aunque no aparezcan en la referencia.

## Límites verificados del entorno

Java 21 disponible; no se encontró adb, emulador, SDK ni Gradle instalado.
`./gradlew --version` intentó descargar Gradle y falló con `Network is unreachable`.
Eso bloquea compilación, lint, pruebas JVM/Android y pruebas de instalación locales.
Ninguna comparación de firma equivale a una prueba de actualización en dispositivo.
