# TvRemote — Samsung Smart TV

Aplicación Android en Kotlin + Jetpack Compose para controlar televisores Samsung compatibles desde la red local.

## Compilación automática
Cada push a `main` o a ramas `claude/**` compila la app en GitHub Actions:
- **Artifacts → `TvRemote-apk`**: el APK listo para instalar.
- **Artifacts → `TvRemote-screenshots`**: capturas de todas las pantallas (pruebas con Robolectric).
- Los push a `main` publican además un release `v0.3.<n>` con el APK.

### APK firmado (opcional)
Si existen estos secretos en *Settings → Secrets and variables → Actions*, CI genera un APK de release firmado y optimizado (R8) en lugar del de depuración:

| Secreto | Valor |
| --- | --- |
| `TVREMOTE_KEYSTORE_BASE64` | El keystore en base64 (`base64 -w0 tvremote.jks`) |
| `TVREMOTE_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `TVREMOTE_KEY_ALIAS` | Alias de la clave |
| `TVREMOTE_KEY_PASSWORD` | Contraseña de la clave |

Para crear el keystore:

```bash
keytool -genkeypair -v -keystore tvremote.jks -alias tvremote -keyalg RSA -keysize 4096 -validity 10000
```

Guarda el keystore en un lugar seguro: sin él no podrás publicar actualizaciones que se instalen encima. Al pasar del APK de depuración al firmado hay que desinstalar la app una vez.

## Funciones
- Mando con cruceta, OK, volumen, canales, multimedia, atrás e inicio; tres accesos directos configurables (mantén pulsada una app en *Aplicaciones* para fijarla).
- Páginas deslizables: mando, teclado numérico con teclas de color, y touchpad con envío y pegado de texto.
- *Aplicaciones*: lista de apps instaladas que comparte la TV (según modelo) o las más comunes.
- *Transmitir*: duplicar pantalla del móvil y cambio de entrada (TV, HDMI 1–4).
- *Ajustes*: buscar/conectar/olvidar televisores, pruebas de encendido con registro, SmartThings, tema oscuro/claro/sistema, vibración, botones de volumen del móvil y probador de teclas.
- Botones de volumen del móvil controlan la TV con la app abierta.
- Widget de pantalla de inicio (encender, volumen, silencio) y botón en Ajustes rápidos para encender o apagar.
- Si la TV cambia de IP, la app la vuelve a encontrar por su MAC.

## Encendido
1. Si la TV responde en la red (encendida o en reposo con red), se envía `KEY_POWER`.
2. Si no responde, se envía Wake-on-LAN repetidamente y, si está configurado, la orden *encender* de SmartThings; al despertar se conecta sola.
3. Para Wake-on-LAN activa en la TV *Ajustes › General › Red › Configuración experta › Encender con móvil*.
4. SmartThings necesita un token de account.smartthings.com/tokens; Samsung hace que caduquen en 24 horas.

## Detalles técnicos
- Comunicación WSS por el puerto 8002, emparejamiento con token y fijación TOFU del certificado por IP.
- El móvil y el televisor deben estar en la misma red local.
- La compatibilidad depende del modelo y firmware; algunas teclas varían (usa el probador de teclas en Ajustes).
- Logos oficiales de las apps (Prime Video, Disney+, Netflix, YouTube, Spotify, Apple TV+ y Max) obtenidos de sus recursos de marca y de Wikimedia Commons; son marcas de sus propietarios. El resto de apps usa el icono que envía la propia TV.
