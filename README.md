# TvRemote — Samsung Smart TV

Aplicación Android en Kotlin + Jetpack Compose para controlar televisores Samsung compatibles desde la red local.

## Compilación automática
Cada push a `main` y cada ejecución manual de GitHub Actions compilan un APK de depuración. El archivo aparece en **Actions → Android APK → Artifacts** como `TvRemote-debug-apk`.

## Estado del prototipo
- Interfaz oscura con controles de navegación, volumen, canales y envío de texto.
- Comunicación WSS por el puerto 8002.
- Emparejamiento con token y fijación TOFU del certificado por IP.
- Requiere que el móvil y el televisor estén en la misma red local.
- No incluye todavía descubrimiento automático, touchpad, Wake-on-LAN ni lista de aplicaciones.

La compatibilidad depende del modelo y firmware del televisor. No se ha validado aún en un televisor físico; el APK de CI es una compilación de depuración, no una certificación de compatibilidad.
