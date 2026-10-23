# Verificación de Total Music 1.4.0

Código fuente compilado: `b4fd19b1b1bc3690f881aa00ee1fcda2cffe2601`.
Ejecución de CI: https://github.com/Spaceapk/Total-Music/actions/runs/39982856887
Identificador: `org.nexlibre.totalmusic`; versionName `1.4.0`; versionCode `8`.

## Cambios

- Navegación adaptable de Liquid Glass mediante transiciones compartidas existentes. En compacto conserva el minirreproductor, el acceso de biblioteca y Listas.
- Umbral de desplazamiento reversible, expansión al llegar al inicio, estado compacto guardado y geometría estable de la lista.
- Superficies translúcidas ligeras en Android sin soporte o con desenfoque reducido; captura del fondo solo con soporte y desenfoque activado.
- Colores de carátula opcionales en Google, limitados al reproductor completo: imágenes de 128 px, procesamiento fuera del hilo principal, caché LRU de 128 entradas, cancelación de tareas y transición entre paletas.
- Retorno al tema normal sin carátula y fondo principal negro puro en oscuro. Opciones desactivadas por defecto y preferencias persistentes.
- Traducciones completas de los nuevos textos.

## Comprobaciones realizadas

- 1061 pruebas JVM/Android locales: 0 fallos, 0 errores, 1 omitida.
- Lint de debug y comprobación vital de release: aprobados con el baseline existente.
- Ensamblado debug y release optimizado: aprobados.
- Identificador, versiones, sumas de los artefactos y procedencia del commit: verificados.
- Firma permanente: verificada. Certificado SHA-256 `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`.
- Compatibilidad de identidad/firma y aumento de código de versión frente a la APK permanente 1.2.0 (código 7): verificados.
- SHA-256 del APK firmado: `63c55c8c7944ecff0c3fab54337abc40d56862b2e6cd65c541b0640018a08af0`.

La primera ejecución pasó las pruebas y se detuvo por cinco textos sin traducciones completas; se añadieron las traducciones y se repitieron los controles con éxito.

## Pendiente de dispositivo

No se utilizó teléfono ni emulador. Pendientes: comprobar instalación real sobre la versión anterior, gestos e insets, pantallas estrechas y tabletas, cambios rápidos y portadas monocromas o ausentes, listas grandes, animación del reproductor, reproducción en segundo plano y regresión del ecualizador. No se midieron FPS ni memoria.

La base publicada era 1.2.0. No existía una rama 1.3.0 al recuperar el repositorio y esta entrega no afirma incorporar funciones de esa versión ausentes de main.
