# Panel Gamer

Burbuja flotante con forma de control de consola para jugar sin salir del juego.
Al tocarla abre un panel con:

- Controles de música (anterior, play/pausa, siguiente) que funcionan con Spotify y otros reproductores.
- Accesos rápidos a WhatsApp, Spotify, YouTube, TikTok, Instagram, Messenger, Facebook, Discord, Telegram y Chrome (solo aparecen las que tengas instaladas).

Hecha para el ZTE Blade V Smart: sin librerías, sin animaciones pesadas, el panel solo existe en memoria mientras está abierto.

## Instalar en el celular

1. En GitHub entra a **Releases** y baja `PanelGamer.apk`.
2. Ábrelo y acepta "instalar apps de origen desconocido".
3. Abre **Panel Gamer**, toca **Dar permiso** (mostrar sobre otras apps) y luego **Iniciar panel**.
4. Para que no se cierre jugando: Ajustes > Batería > deja Panel Gamer sin restricciones.

## Nota sobre las ventanas

Android común no permite mostrar WhatsApp o Spotify como ventana pequeña encima del juego. La app lo intenta (modo ventana flotante) y, si tu sistema no lo soporta, abre la app a pantalla completa y vuelves al juego con el botón de recientes. Los controles de música sí se manejan desde la burbuja sin salir del juego.

## Compilar

Cada push a `main` compila el APK con GitHub Actions y lo publica en Releases.
