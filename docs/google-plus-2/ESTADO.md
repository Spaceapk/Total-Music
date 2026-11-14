# Google Plus 2.0 — estado del avance

Fecha: 14 de noviembre de 2026. Estado: BORRADOR PARCIAL, NO APTO PARA PUBLICACIÓN.

## Cambios preparados en código

- Inicio con título Tu Mix, collage de carátulas de la biblioteca y reproducción al tocarlas; conserva la exploración de álbumes y acciones de canciones.
- Navegación inferior Inicio / Buscar / Biblioteca.
- Mini reproductor en cápsula, portada circular, anterior / pausa / siguiente, con los mismos gestos y registro de carátula para la transición compartida.
- Reproductor con carátula más grande, título compacto, letras junto al título y acceso a salida, cola, herramientas y menú de canción.
- Transporte de tres botones y grupo inferior de aleatorio / repetición / favoritos. Mantiene el control de volumen cuando la preferencia actual lo muestra.
- Conservación del fondo tonal que ya genera ArtworkPlayerTheme y etiqueta de calidad obtenida del sistema existente.
- Pantalla de letras exclusiva Plus: texto centrado, una sola línea activa, desfase existente, seguimiento, desplazamiento manual y regreso al seguimiento. Incluye controles de reproducción, progreso y modo estático.
- Traducción reutilizada, con original, traducida y ambas; proveedores, romanización y ajuste de desfase siguen accesibles.
- Cola enlazada a los mismos callbacks y motor de arrastre. Nueva transición vertical exclusiva Plus.
- Pruebas JVM de índice activo y conservación de paleta, y prueba Compose de accesibilidad de reproductor / cola / letras, preparadas.
- Workflow de comprobación preparado: tests, lint, ensamblado interno, instalación y regresiones en emulador API33. No se ha ejecutado en GitHub.

## Conservación: alcance de la afirmación

No se editaron motores, servicio de audio, QueueCoordinator, DevicePlaylistStore,
LikeState, repositorio/caché de letras, identificador de publicación, versión,
certificado ni claves. Esto limita el cambio a presentación y pruebas; NO demuestra
por sí solo que no haya regresiones. Obsidian y Liquid Glass+ no se han rediseñado.
La rama original de Arena y main permanecen sin cambios remotos.

## Resultados y límites

| Comprobación | Resultado |
| --- | --- |
| Identidad y aislamiento de firma (`check_release.py`) | Correcto |
| Notas de versión existentes | Coherentes con la versión de la rama |
| `git diff --check` | Correcto |
| Sintaxis de 9 archivos Kotlin mediante parser | Sin errores sintácticos; no equivale a compilación |
| XML de recursos y YAML de workflow | Analizados correctamente |
| Gradle inicial | Descarga directa Java bloqueada por red |
| Gradle mediante distribución descargada y proxy | Se inició; primer intento falló porque el Java instalado carecía de compilador |
| JDK completo descargado, segundo intento | Interrumpido tras permanecer en resolución de dependencias; no llegó a compilar |
| Intento sin red con JDK completo | Falló en 9 s: faltan dependencias en caché (Ktor, coroutines, jsoup, NewPipe y otras). No ejecutó la suite |
| Escritura GitHub por integración | Rechazada: HTTP403, Resource not accessible by integration |
| Envío Git HTTPS | Sin credenciales disponibles; rama no enviada |
| Pruebas Android / instalación / actualización con datos | No ejecutadas |
| Firma de lanzamiento | No intentada; no se generó una clave permanente |
| APK de prueba o lanzamiento entregada | Ninguna |
| Comparación visual de implementación contra video | Pendiente: no hay capturas de la app compilada |

## Diferencias pendientes que impiden darlo por terminado

1. Ajustar la composición y los datos reales del mix diario, recientes y estadísticas. El collage actual usa canciones de biblioteca; no se presenta como recomendador nuevo.
2. Reproducir las categorías de géneros de búsqueda con datos reales. Se conserva la búsqueda existente, pero su presentación todavía difiere.
3. Resolver las pestañas/carpetas y reordenación de pestañas observables en la referencia, manteniendo compatibilidad de listas.
4. Medir y ajustar tamaños, fuente, curvas y duraciones en dispositivo. Los valores actuales son iniciales, no mediciones certificadas.
5. Completar animación de desplazamiento de portada al cambiar canción y verificar la transición continua hacia el mini reproductor.
6. Ajustar el panel y las tarjetas de cola al fotograma equivalente, verificar arrastre con duplicados, sesión bloqueada y persistencia.
7. Verificar contraste y modo oscuro con carátulas claras/oscuras; escala de fuente, orientación, teclado, insets y movimiento reducido del sistema.
8. Probar reproducción real, segundo plano, favoritos, listas, ecualizador, velocidad/pitch, letras con traducciones reales, caché, cierre/reapertura y cambios entre todos los temas.
9. Ejecutar instalación y actualización desde la APK permanente anterior, con la firma original y sin pérdida de datos.

## Cómo continuar desde otra IA

El ZIP contiene un parche de Git y copias de los archivos cambiados. La base exacta
es `98dfb39fe08621667479675e3d8a2dc93a3c3307`, en
`arena/1ae8428b-total-music`. No aplicar a main sin resolver su desfase de versión.
No aceptar una APK como final por el solo hecho de compilar o instalar.
Conservar las instrucciones AGENTS.md y docs/TOTAL_MUSIC_RELEASES.md del repositorio.

## Resultados reales — commit `c62210de` (run 43609408185, 2026-11-14)

Evidencia en `refs/ci-evidence/gp2/c62210de4497-{build,assemble,device}`.

- Compilación `assembleProdPlusTest`: correcta.
- Pruebas JVM: 1170 ejecutadas, 0 fallos, 1 omitida (incluye `GooglePlusDataTest` y `GooglePlusLyricsTest`).
- Emulador Android 13: APK de prueba aislado (`org.nexlibre.totalmusic.plus.test`, firma debug efímera, NO actualiza Total Music) instalado con `Success`; 0 `FATAL EXCEPTION`.
- Instrumentación: 34 ejecutadas, 1 fallo. Las de Google Plus pasan todas. Falla
  `ObsidianFidelityUiTest.integratedDockSearchesAndTransforms` (`obsidian-disc` solo existe en el árbol sin fusionar).
  Este trabajo no toca Obsidian y el flujo `obsidian-fidelity` ya fallaba en la base `98dfb39f`, pero no se ha
  comprobado que sea exactamente el mismo fallo.
- Capturas Compose reales (12) en `compose-captures/` de la referencia `-device`.

### Pendiente (no declarado como terminado)

- Comparación con el vídeo de referencia: sin vídeo ni fotogramas en esta sesión; proporciones y animaciones sin validar.
- `scripts/plus/device_tour.py` solo comprueba el arranque: reproducción real, arrastre de cola, persistencia tras
  reinicio y cambio entre temas en la app completa NO están probados en dispositivo.
- Carátulas reales y colores dinámicos no verificados visualmente (las pruebas usan canciones sin carátula).
- Fila de modos de letra: se recorta a la derecha en 360dp (desplazable).
- Publicación: requiere la clave original; no se ha generado ninguna.
