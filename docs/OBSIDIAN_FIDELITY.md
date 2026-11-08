# Obsidian Fidelity — reconstrucción y entrega de prueba

## Base y procedencia

- Fuente integrada: `6a0c7c4046423f877bd15ee68af42754291ba4dc` (`arena/ce925682-total-music`, 1.8.6/code24) más la historia de la rama de sesión.
- Referencia funcional: APK original `Total-Music-1.10.0-universal.apk`, SHA-256 `283154348d8c289d5d8305279c35a3cca23f9600b61019c0cbdbbad3ad33dff7`.
- La fuente original de Obsidian Fidelity no estaba en las refs examinadas. Los componentes se reconstruyeron contrastando el binario 1.10.0 y las notas/specs disponibles.
- Versión preparada con `scripts/release/prepare_version.py`: `1.10.1`, code `30`.

## Componentes

- Quinto tema `OBSIDIAN`, persistido en `player_visual_style=OBSIDIAN`.
- Preferencia exacta 1.10.0 `obsidian_dynamic_background`, separada del fondo dinámico legacy.
- Biblioteca Obsidian: inicio, selección rápida, artistas circulares, álbumes, playlists, favoritas, búsqueda y selección múltiple sobre los stores existentes.
- Barra inferior integrada `obsidian-dock`: mini reproductor, disco animado, navegación y búsqueda transformable.
- Reproductor inmersivo `obsidian-player`: carátula/disco, fondo de carátula opcional, transporte, cola y letras.
- Letras sincronizadas: una sola línea activa, desplazamiento al renglón activo, seek por línea, offset y traducción/romanización existentes.
- Traducción local inglés/español con ML Kit, descarga de modelos solo por Wi-Fi, caché persistente y fallback online; los timestamps originales se conservan.
- Reducción de movimiento de la app y del sistema (`animator_duration_scale=0`).

## Entrega sin clave original

La clave privada original no está disponible en este entorno. Por eso el APK instalable de CI es:

```text
Paquete: org.nexlibre.totalmusic.obsidian.test
Versión: 1.10.1-obsidian-test (30)
Tipo: compilación de prueba no depurable, sin minificar para instrumentación
Firma: Android debug efímera de CI
```

**No es una actualización de Total Music 1.10.0.** Se instala al lado, con datos separados. No reemplaza la instalación original ni puede conservar sus datos por el cambio de package. No se generó una clave permanente de reemplazo.

La entrega verificable se publica como **prerelease TEST** desde la rama de sesión, no desde `main`, con descarga directa del APK y `SHA256SUMS`. El artefacto de Actions contiene:

- `Total-Music-Obsidian-Fidelity-1.10.1-code30-TEST-NOT-AN-UPDATE.apk`
- `SHA256SUMS`
- `LEEME-PRUEBA.txt`
- `android-test.apk` para instrumentación CI

La evidencia de compilación/pruebas se publica en el check `Obsidian Fidelity build` y en la incidencia #14. La evidencia Android 13 se publica en `Obsidian Fidelity Android 13`.

## Actualización estable futura

Para entregar una actualización real sobre 1.10.0 hace falta recuperar el keystore original y su archivo de contraseña. Entonces:

1. Compilar `:app:assembleProdRelease` sin sufijo de prueba.
2. Verificar `applicationId=org.nexlibre.totalmusic`, code `30`, certificado `9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa`.
3. Firmar localmente con `scripts/release/sign_apk.py` y `--previous-apk Total-Music-1.10.0-universal.apk`.
4. Probar instalación encima de 1.10.0 con datos representativos antes de entregar.

No usar el APK de prueba como si fuera esa actualización.
