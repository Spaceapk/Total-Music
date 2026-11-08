# Total Music 1.7.0 — verificación del APK

Fecha: 26 de octubre de 2026.
Base: main / v1.6.0, e0a02646c6a53cefab76fb2b0d8510f9b31188be.
Commit de código compilado: 1da04f5 (las revisiones posteriores de este informe solo documentan la entrega).

## Resultado

APK release optimizado, universal y firmado con la clave permanente. Paquete org.nexlibre.totalmusic, versión 1.7.0, versionCode 13, Android mínimo API 26. Certificado SHA-256: 9ae145a703a85902fe6d6ab3d886f6801873d717929deaa3e3d0b0a81de829fa.

SHA-256 del APK: 2f06be1aca0452d9a19a45195863d7b68a44d4c42be71dc09cb6353236e1bc2c.

## Validación

- 50 pruebas compartidas, 1.017 pruebas Android locales y 35 pruebas del reproductor: cero fallos; una prueba Android omitida. Total: 1.102 casos, 1.101 aprobados y uno omitido.
- Se corrigieron las 12 cadenas de widgets que faltaban en 15 idiomas, además de dos variantes de chino tradicional.
- Lint prodDebug terminó: 648 advertencias y 6 sugerencias, sin errores nuevos; 166 errores heredados filtrados por el baseline existente. Lint vital release terminó sin errores ni advertencias nuevos. El analizador y R8 emiten advertencias de metadatos Kotlin recientes; no se suprimieron esas comprobaciones.
- Identidad pública y aislamiento de la firma: check_release.py aprobado.
- APK oficial 1.6.0 descargado y verificado contra su SHA256SUMS publicado; mismo certificado permanente y versionCode inferior (12).
- Firmador oficial del repositorio: paquete, versión, no-debuggable, continuidad con 1.6.0 y firma verificados.
- apksigner verify y zipalign -c -P 16 4 aprobados. CRC ZIP y límites de cabeceras ELF de 24 bibliotecas nativas comprobados.

## Empaquetado universal

Gradle compiló el código, ejecutó R8 y generó correctamente los tres APK release por ABI. Su tarea de empaquetado universal falló dos veces con newPosition < 0. Se usó como alternativa la unión verificada de los tres APK completos: todos los archivos ajenos a lib/ coincidían byte por byte, y las entradas compartidas coincidían también. Se conservaron las bibliotecas de armeabi-v7a, arm64-v8a y x86_64. La unión se alineó con zipalign y se firmó mediante scripts/release/sign_apk.py. Los hashes de los tres APK de origen se conservan en packaging-sources.json.

## Límites

No se ejecutó el APK en un teléfono ni emulador. No se midieron FPS, consumo o tiempos de interacción, ni se validó visualmente el launcher. La continuidad criptográfica está comprobada; la actualización instalada y las seis variantes de widgets necesitan prueba en dispositivo.

El SDK, Java 21 y Gradle se prepararon localmente usando el proxy del entorno. Las credenciales de firma permanecieron fuera del repositorio y no se enviaron a CI. La rama remota sigue en la base 1.6.0 porque la integración rechaza escritura; main y las releases de GitHub no fueron modificadas.
