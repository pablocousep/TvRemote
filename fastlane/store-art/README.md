# Arte de la ficha de Google Play

1. Genera las pantallas en bruto (Robolectric, sin barra de estado ni logos de terceros):
   `gradle testGithubDebugUnitTest --tests '*StoreScreens*'` → `app/build/screenshots/store/`
2. Prepara las fuentes y el icono junto a `make.js`:
   `npm pack @fontsource/inter && tar xzf fontsource-inter-*.tgz -C fonts` y copia el `pathData` de
   `app/src/main/res/drawable/ic_launcher_foreground.xml` a `glyph.txt`.
3. `node make.js ../../app/build/screenshots/store ../metadata/android/es-ES/images`
   (necesita Playwright con Chromium).

Genera 6 capturas de 1080×1920, el gráfico destacado de 1024×500 y el icono de 512×512.
