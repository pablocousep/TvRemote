# Publicar TvRemote en Google Play

Guía con las respuestas para los formularios de Play Console. La ficha (título, descripciones,
capturas, gráfico destacado e icono) está en `fastlane/metadata/android/es-ES/`.

## 1. Crear la app
- Nombre: **TvRemote: mando para Smart TV** · Idioma: Español (España) · App · Gratis.
- Paquete: `com.reelog.tvremote` (no se puede cambiar después).

## 2. Archivo a subir
- Variante **play** en formato `.aab`, firmada con la **clave de subida** (no con `app/signing/tvremote.jks`, que es pública).
- CI la genera sola si existen estos secretos de GitHub: `TVREMOTE_UPLOAD_KEYSTORE_BASE64`,
  `TVREMOTE_UPLOAD_KEYSTORE_PASSWORD`, `TVREMOTE_UPLOAD_KEY_ALIAS`, `TVREMOTE_UPLOAD_KEY_PASSWORD`.
  Aparece como artefacto `TvRemote-play-aab` y adjunta en cada release de `main`.
- Activa **Firma de apps de Google Play** (Google guarda la clave final; la de subida se puede restablecer si se pierde).

## 3. Contenido de la app
- **Política de privacidad:** `https://github.com/pablocousep/TvRemote/blob/main/PRIVACY.md`
- **Acceso a la app:** toda la funcionalidad está disponible sin restricciones (no hay inicio de sesión).
- **Anuncios:** no contiene anuncios.
- **Clasificación de contenido:** categoría «Utilidad, productividad, comunicación u otros»; responder **No** a todo
  (sin violencia, sexo, lenguaje, drogas, apuestas, compras, interacción entre usuarios ni compartir ubicación).
- **Público objetivo:** 18 años o más (o 13+). No dirigida a niños.
- **Apps de noticias / salud / préstamos / VPN:** No.
- **Seguridad de los datos:**
  - ¿Recoge o comparte datos de usuario? **No.** Todo se guarda solo en el dispositivo y no se envía fuera.
  - (La ubicación solo se usa en el dispositivo para leer el nombre de la Wi‑Fi y no se transmite, así que no cuenta como «recogida» según Google.)
  - Cifrado en tránsito: sí (conexión TLS con la TV). Borrado de datos: desinstalando o borrando datos de la app.
- **Permisos sensibles:** ubicación (`ACCESS_FINE_LOCATION`) en primer plano. Justificación: leer el SSID de la Wi‑Fi
  para recordar en qué red está cada televisor; Android exige este permiso para ello. No se usa en segundo plano.

## 4. Ficha de Play Store
- Categoría: **Herramientas** (o Vídeo y reproductores). Etiquetas: mando a distancia, Smart TV.
- Icono 512×512: `images/icon.png` · Gráfico destacado 1024×500: `images/featureGraphic.png`
- Capturas de teléfono: `images/phoneScreenshots/` (1080×1920).
- Contacto: tu correo de desarrollador (obligatorio).

## 5. Pruebas y publicación
- **Cuenta personal creada después de nov. 2023:** primero una **prueba cerrada con al menos 12 testers durante 14 días**;
  después se puede pedir acceso a producción.
- **Cuenta de organización:** se puede ir directamente a producción.
- Recomendado: empezar por **Prueba interna** para instalarla tú desde Play en minutos.

## 6. Subidas automáticas (opcional)
Crea una cuenta de servicio con acceso a la API de Google Play Developer, guarda su JSON como secreto
`PLAY_SERVICE_ACCOUNT_JSON` y se puede añadir un paso a CI que suba el `.aab` a la pista de pruebas internas.
