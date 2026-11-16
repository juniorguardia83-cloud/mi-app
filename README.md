# Panel Gamer

Burbuja flotante con forma de control de consola para jugar sin salir del juego.
Al tocarla abre un panel con:

- **Ventana chica** (con una X roja abajo para cerrarla): WhatsApp, YouTube, Spotify, Instagram, TikTok, Telegram, Discord, Facebook y buscador. Se arrastra desde la barra de arriba y no ocupa ni la mitad ni la pantalla entera.
- **Controles de música** (anterior, play/pausa, siguiente) que funcionan con Lark Player, Spotify y otros reproductores.
- **App completa**: Lark Player, WhatsApp, Spotify, etc. instaladas en el celular (solo aparecen las que tengas).

Hecha para el ZTE Blade V Smart: sin librerías, el panel y la ventana solo existen en memoria mientras están abiertos.

## Instalar en el celular

1. En GitHub entra a **Releases** y baja `PanelGamer.apk`.
2. Ábrelo y acepta "instalar apps de origen desconocido".
3. Abre **Panel Gamer**, toca **Dar permiso** (mostrar sobre otras apps) y luego **Iniciar panel**.
4. Para que no se cierre jugando: Ajustes > Batería > deja Panel Gamer sin restricciones.

## Cosas a saber

- La ventana chica usa las versiones web de las apps (Android común no deja encoger apps instaladas). Hay que iniciar sesión una vez; después queda guardada.
- WhatsApp Web: usa "Vincular con número de teléfono" (no se puede escanear el QR con el mismo celular).
- Spotify Web puede no reproducir en algunos celulares; para música usa Lark Player con los botones del panel.
- Lark Player no tiene versión web, así que se abre a pantalla completa y se controla desde el panel.
- Mantén actualizado "Android System WebView" en Play Store para que las páginas funcionen bien.
- Cada ventana abierta gasta RAM: ciérrala con la X cuando termines.

## Compilar

Cada push a `panel-gamer` compila el APK con GitHub Actions y lo publica en Releases.
