# TvRemote — Samsung Smart TV

Aplicación Android en Kotlin + Jetpack Compose para controlar televisores Samsung compatibles desde la red local.

## Compilación automática
Cada push a `main` o a ramas `claude/**` compila la app en GitHub Actions:
- **Artifacts → `TvRemote-apk`**: el APK listo para instalar.
- **Artifacts → `TvRemote-screenshots`**: capturas de todas las pantallas (pruebas con Robolectric).
- Los push a `main` publican además un release `v0.3.<n>` con el APK.

### Firma y actualizaciones desde la app
- Todas las compilaciones se firman con la clave compartida `app/signing/tvremote.jks` (incluida en el repositorio a propósito). Así cada versión nueva se instala encima de la anterior y la app puede actualizarse sola.
- Al abrirse, la app consulta el último release de GitHub como mucho cada 6 horas. Si hay uno más nuevo, ofrece descargarlo e instalarlo. También se puede comprobar tocando la versión al pie de *Ajustes*.
- La primera vez Android pide permitir que TvRemote instale apps.
- Los releases usan la etiqueta `v0.3.<n>`, donde `<n>` es el `versionCode` del APK.
- Al ser pública, cualquiera podría firmar un APK con esa clave. La app solo descarga de los releases de este repositorio, así que no instales APKs de TvRemote de otras fuentes.
- Si en el futuro se configura una clave privada mediante los secretos `TVREMOTE_KEYSTORE_BASE64`, `TVREMOTE_KEYSTORE_PASSWORD`, `TVREMOTE_KEY_ALIAS` y `TVREMOTE_KEY_PASSWORD`, CI firmará el release con ella. Ese cambio de clave obliga a desinstalar la app una vez.

## Funciones
- Mando con cruceta, OK, volumen, canales, multimedia, atrás e inicio; tres accesos directos configurables (mantén pulsada una app en *Aplicaciones* para fijarla).
- Páginas deslizables: mando, teclado numérico con teclas de color, y touchpad con envío y pegado de texto.
- *Aplicaciones*: lista de apps instaladas que comparte la TV (según modelo) o las más comunes.
- *Transmitir*: duplicar pantalla del móvil y cambio de entrada (TV, HDMI 1–4).
- *Ajustes*: lista de televisores (guardados y encontrados en la red) y tema oscuro/claro/sistema.
- Botones de volumen del móvil controlan la TV con la app abierta.
- Widget de pantalla de inicio (encender, volumen, silencio) y botón en Ajustes rápidos para encender o apagar.
- Guarda los televisores a los que te has conectado (*Mis televisores*, con la fecha de último uso).
- Si la TV cambia de IP, la app la vuelve a encontrar por su MAC.
- Con Wake-on-LAN configurado (la app aprende la MAC al conectar), una TV apagada se muestra "en reposo" en lugar de dar error de conexión.
- Se actualiza sola desde los releases de GitHub.

## Encendido
1. Si la TV responde en la red (encendida o en reposo con red), se envía `KEY_POWER`.
2. Si no responde, se envía Wake-on-LAN repetidamente; al despertar se conecta sola.
3. Para Wake-on-LAN activa en la TV *Ajustes › General › Red › Configuración experta › Encender con móvil*.

## Detalles técnicos
- Comunicación WSS por el puerto 8002, emparejamiento con token y fijación TOFU del certificado por IP.
- El móvil y el televisor deben estar en la misma red local.
- La compatibilidad depende del modelo y firmware; algunas teclas varían según el modelo.
- Logos oficiales de las apps (Prime Video, Disney+, Netflix, YouTube, Spotify, Apple TV+ y Max) obtenidos de sus recursos de marca y de Wikimedia Commons; son marcas de sus propietarios. El resto de apps usa el icono que envía la propia TV.
