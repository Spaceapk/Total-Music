## Total Music 1.7.0 — Visual Experience

- Reproductores Original, Google Expressive+ y Liquid Glass+; Original conserva el aspecto de las instalaciones existentes.
- Efectos de fondo dinámico, cristal, controles alternativos y transiciones animadas configurables por separado.
- Seis widgets: Compacto 2×2, Clásico 4×2, Inmersivo 4×4, Minimal 4×1, Carátula dinámica y Panel de cristal.
- Apariencia automática, clara u oscura configurable para cada widget.
- Actualizaciones de controles y metadatos conectadas a la sesión multimedia Media3 existente.
- Pendiente de validación completa en Android; los límites de compilación se documentan en el informe de verificación.

## 1.4.1 — Corrección de numeración y prueba de instalación

- Aumenta versionCode a 9. Los APK 1.3.0 y 1.4.0 usaban el código 8.
- Conserva la firma permanente y verifica la continuidad frente al APK 1.3.0 real.
- Añade una prueba reproducible del APK firmado: instalación y actualización en Android 13.
- Conserva las mejoras de apariencia de 1.4.0. No se atribuye el error del teléfono a la numeración sin un registro del instalador.

## Total Music 1.4.0

- Navegación Liquid Glass adaptable opcional con minirreproductor y dos accesos laterales.
- Colores de carátula opcionales para el reproductor Google, con caché e interpolación de paletas.
- Fondo AMOLED negro puro y alternativa de cristal ligera; ambas opciones permanecen desactivadas por defecto.
- Nuevos textos traducidos a todos los idiomas compatibles y variantes de chino tradicional.
- 1061 pruebas, 1 omitida, sin fallos; lint, compilación y firma permanente verificados.
- Pruebas en dispositivo pendientes. Detalles en docs/verification-1.4.0.md.

# Total Music 1.1.0 — candidata, pendiente de validación Android

- Nueva identidad: nota musical y ondas; iconos adaptativo y monocromático.
- Google Expressive seleccionable en Apariencia, con paleta dinámica compatible.
- Total EQ: 31 bandas, ocho filtros paramétricos, preamplificación y curva digital.
- Graves, agudos, balance, amplitud estéreo, crossfeed y limitador de picos.
- Perfiles de fábrica y perfiles propios persistentes.
- Conserva tema Original, controles de ecualización anteriores y audio espacial.

Estado y pruebas: `docs/TOTAL_MUSIC_1_1_0.md`. Todavía no es una release verificada.

---

# Space Music — cambios

## 2.3.0 · interfaz compacta y Liquid Glass

- Minirreproductor y navegación inferior translúcidos, sin recuadro opaco detrás.
- Margen de contenido ajustado a la altura de los controles.
- Ajustes identifica a Space Music; atribuciones y GPL-3.0 accesibles sin conexión.
- Aviso de novedades actualizado. Mismo paquete y firma para actualizar sobre 2.2.0.

## 2.2.0 — estable

Cabecera compacta de listas, búsqueda propia y orden persistente por lista (manual, título, artista y añadidas recientemente), sin modificar Canciones. Reproducción y eliminación respetan el orden visible. Migración de listas existentes sin pérdida de canciones.

## 2.1.0 estable — listas compactas y avisos de actualización

- Primera entrega estable sin sufijo beta, con versionCode 5 y firma original.
- Reproducir, añadir y opciones comparten una fila; se eliminan las flechas por canción.
- Menús para reordenar por arrastre, renombrar, eliminar listas y quitar canciones.
- Avisos de nuevas versiones estables de Space Music, descarga e instalación mediante Android.
- Verificación del paquete, versión superior y certificado antes de ofrecer la instalación.
- Novedades una vez por versión, con notas de actualizaciones descargadas disponibles sin conexión.
- [Verificación y límites](docs/verification-2.1.0.md): pruebas JVM, lint y actualización firmada desde la beta 2.0.1 en emuladores Android 12, 13 y 15.

## 1.0.2 beta — letras locales y modo ambiente

- Letras FLAC con campos alternativos, MP3 USLT UTF-8/UTF-16/Latin-1 y texto sin sincronización.
- Lectura de etiquetas después de carátulas FLAC grandes y del audio M4A.
- Importación LRC/TXT por canción, persistencia atómica y archivo musical original intacto.
- Modo ambiente opcional con colores de carátula; respeta pausa, segundo plano y reducción de animaciones.
- Traducciones de los textos nuevos y matriz activa Android 12/13/15, manteniendo compatibilidad de instalación anterior.
- [Resultados y límites de validación](docs/verification-1.0.2.md).

## 1.0.1 beta — estabilidad y listas locales

- Restauración del reproductor en su hilo principal y errores locales recuperables.
- Listas locales desde menús y selección múltiple, arrastre, deduplicación y recuperación atómica.
- WAV visible en biblioteca; contraste adaptativo y navegación inferior simplificada.
- Actualización con firma original y datos conservados.
- 1.017 pruebas aprobadas y matriz firmada Android 8/13/15. [Informe](docs/verification-1.0.1.md).

## 1.0.0 — release optimizada con firma propia

- Derivación de Star Music y BitChord; historial y licencia GPL-3.0 conservados.
- Nombre independiente y paquete Android org.nexlibre.spacemusic.
- Biblioteca inicial del dispositivo, búsqueda local, canciones, artistas, álbumes y listas persistentes.
- Temas, Liquid Glass, reproductor, ecualizador, reproducción en segundo plano, widgets y ajustes locales heredados.
- Eliminados del inicio y ajustes: cuentas, streaming, servidores, integraciones, descargas y actualizaciones en línea.
- Android Auto muestra música local; búsquedas y solicitudes de reproducción usan archivos locales.
- Sin permiso de Internet ni de lectura del estado de red en el APK final.
- Carátulas y letras incrustadas se leen del archivo. No se consultan proveedores remotos.
- GitHub Actions comprueba regresiones, compilación, permisos, persistencia de listas, decodificación sin conexión y arranque en Android.

Verificación completada: regresiones, lint, compilación, instalación, arranque, permisos sin red, persistencia y reproducción local en Android 13 (API 33). Publicada la release optimizada con firma propia, respaldo privado y verificación adicional del APK firmado en Android 13. Pendientes pruebas manuales en teléfonos físicos. El APK de publicación es el release optimizado firmado con la clave independiente de Space Music. Los APK debug son exclusivamente para pruebas.

La lógica remota heredada se conserva parcialmente como código sin acceso desde la interfaz local. La eliminación del permiso de Internet impide conexiones de red de toda la aplicación, incluidas sus dependencias.
