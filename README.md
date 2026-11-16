# Panel Gamer

Burbuja flotante con forma de control de consola para jugar sin salir del juego.
Al tocarla abre un panel con ventanas chicas conectadas a las apps de tu celular
(sin versiones web ni iniciar sesión):

- **Música**: Lark Player, Spotify, YT Music o la que esté sonando. Muestra portada, título y botones anterior / play-pausa / siguiente.
- **Mensajes**: WhatsApp, Telegram, Messenger, Instagram y Discord. Muestra los chats con mensajes nuevos y los puedes responder desde la ventana.

Cada ventana:
- Tiene una **X roja abajo** para cerrarla.
- Se **arrastra** desde la barra de arriba.
- Se **ajusta de tamaño** arrastrando la esquina de abajo a la derecha (el tamaño se guarda).
- Por defecto es chica: ni la mitad de la pantalla ni pantalla completa.

## Cómo se conecta a las apps

Usa el permiso **Acceso a notificaciones** de Android (el mismo que usan los relojes inteligentes). Con él lee la música que suena y los mensajes que llegan, y responde con el botón "Responder" de la propia notificación. La app **no tiene permiso de internet**, así que nada sale del celular.

## Instalar

1. En GitHub entra a **Releases** y baja `PanelGamer.apk`.
2. Ábrelo y acepta "instalar apps de origen desconocido".
3. Abre **Panel Gamer**: **Dar permiso** (mostrar sobre otras apps), **Dar acceso a notificaciones** y **Iniciar panel**.
4. Para que no se cierre jugando: Ajustes > Batería > deja Panel Gamer sin restricciones.

## Limitaciones

- Los mensajes aparecen cuando llega una notificación; si ya la quitaste o abriste el chat, ya no se ve aquí.
- La música aparece cuando el reproductor ya tiene una canción (dale play una vez en Lark Player).
- Videos (YouTube, TikTok) no se pueden mostrar en ventana chica; no hay forma sin una versión web o modo ventana del sistema.

## Compilar

Cada push a `panel-gamer` compila el APK con GitHub Actions y lo publica en Releases.
