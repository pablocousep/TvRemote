# TvRemote — Samsung Smart TV

Aplicación Android en Kotlin + Jetpack Compose para controlar televisores Samsung compatibles desde la red local.

## Compilación automática
Cada push a `main` y cada ejecución manual de GitHub Actions compilan un APK de depuración. El archivo aparece en **Actions → Android APK → Artifacts** como `TvRemote-debug-apk`.

## Estado del prototipo
- Interfaz oscura tipo "cristal" con cruceta circular y botón OK, volumen, canales, multimedia, atrás e inicio.
- Accesos directos a Prime Video, Disney+ y Netflix (y más apps en la pestaña Aplicaciones).
- Tres páginas deslizables en el mando: principal, teclado numérico con teclas de color, y touchpad con envío de texto.
- Pestañas de Transmitir (duplicar pantalla y entradas HDMI) y Ajustes (buscar, conectar u olvidar televisores, vibración).
- Comunicación WSS por el puerto 8002, emparejamiento con token y fijación TOFU del certificado por IP.
- Descubrimiento automático en la red local y encendido por Wake-on-LAN.
- Requiere que el móvil y el televisor estén en la misma red local.

La compatibilidad depende del modelo y firmware del televisor. No se ha validado aún en un televisor físico; el APK de CI es una compilación de depuración, no una certificación de compatibilidad.
