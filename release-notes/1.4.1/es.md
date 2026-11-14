## Novedades de la 1.4.1

**Descargas**
- **Descargas FTP y FTPS (nuevo):** los enlaces que empiezan por ftp://, ftps:// o ftpes:// se descargan ahora como cualquier enlace directo, en varias partes y con reanudación cuando el servidor lo permite. Pon el inicio de sesión en el enlace, guárdalo en Ajustes > Red > Inicios de sesión de sitios, o déjalo vacío en los servidores anónimos. A tener en cuenta: los enlaces FTP no pasan por el proxy; en las aplicaciones de escritorio, FTP sobre TLS se obtiene con el curl del sistema; FTP sobre TLS aún no está disponible en iPhone ni iPad.
- **Un archivo que cambia en el servidor:** si el archivo se reemplaza en el servidor mientras se descarga, FetchJet lo detecta. La descarga se detiene con un mensaje claro en lugar de guardar un archivo mezclado y roto, y Reanudar descarga la versión nueva.
- **Sin esperas al final:** el final de una descarga ya no espera a una sola conexión lenta. Una conexión que se arrastra al final se sustituye.
- **Comprobaciones antes de guardar:** pega una suma de verificación (SHA-256 o MD5) en el diálogo Añadir, o deja que decida la cabecera de suma de verificación del propio servidor. Un archivo que no coincide no se guarda. Los archivos ZIP se comprueban entrada por entrada, y una parte dañada se descarga de nuevo una vez.
- **Se recuerdan los límites del servidor:** un sitio que permite pocas conexiones se recuerda durante un día, para que la siguiente descarga no empiece con conexiones que rechaza. Tras un 403, FetchJet puede reintentar por IPv4 (Windows, Android y Linux).
- **Probar el enlace:** un botón en el diálogo Añadir te dice si el enlace funciona, su tamaño y si se puede reanudar, sin descargarlo.
- **Una carpeta por tipo de archivo:** la carpeta que eliges para vídeos, música o archivos comprimidos se recuerda solo para ese tipo.
- **Reproducir desde la lista:** un archivo de vídeo o audio terminado muestra ahora un botón ▶ que lo abre en su reproductor.
- **Sin nombres de archivo inventados:** un enlace sin nombre de archivo muestra el campo «Nombre del archivo» vacío, y el inicio espera a que escriba uno.
- **Se ajusta a su pantalla:** la ventana de escritorio se abre ahora dentro de la pantalla visible y no en parte bajo la barra de tareas.

**Torrents**
- **Detalles:** ratio de compartición, cantidad subida, partes y tamaño de parte, trackers con su estado, pares, "Comprobar de nuevo", copiar el enlace magnet y "primeras y últimas partes primero" para previsualizar vídeos.
- **Añadir un tracker** a un torrent en marcha, y **dejar de compartir al llegar a un ratio** que elijas (Ajustes > BitTorrent).
- **Más seguro:** se rechaza un torrent cuyos archivos se escribirían fuera de su propia carpeta.
- **Detrás de un proxy HTTP:** los torrents que solo tienen trackers UDP no encuentran pares ahí; Ajustes > Red y el mensaje de error ahora lo dicen.

**Escritorio**
- **Teclado:** flechas, Shift, Home / End, Page Up / Down, Enter (Alt+Enter abre o cierra los detalles), Ctrl+R / P / O / E / N / F / A, Delete, Esc y F1 para la lista de atajos. También funcionan con teclados árabe, persa y ruso, y copiar / pegar en cada campo igualmente.
- **Línea de comandos:** `/d` enlace, `/p` carpeta, `/f` nombre de archivo, `/a` añadir en pausa sin el diálogo Añadir, `/n` añadir y empezar al instante sin ventana, `/s` iniciar todo lo que espera.
- **Límite de velocidad solo mientras usas el ordenador,** y **despertar el ordenador para las descargas programadas** (Windows).
- **Detalles menores:** la versión en el título de la ventana, Esc cierra el diálogo Añadir, las insignias de tipo de archivo se leen bien en el tema claro, y el título Enviar a un dispositivo está traducido en todas partes.

**Audio**
- **OGG:** elegir OGG para un audio de YouTube (Opus) ya no lo recodifica. Se guarda al instante, con la calidad y el tamaño originales.
- **Comprimir audio:** ahora ofrece los mismos formatos que Convertir (M4A, MP3, Opus, OGG, FLAC, WAV), mantiene OGG cuando el archivo es OGG, y el tamaño estimado se acerca más al real.

**Extensión del navegador (3.4)**
- **Conexión:** cuando el navegador aún no está conectado a la aplicación, la extensión lo dice al instante ("Abre FetchJet y toca Permitir") en lugar de cargar sin fin. Después de tocar Permitir, las calidades aparecen solas. Si el navegador pregunta "¿Abrir FetchJet?", la pestaña ahora se mantiene hasta que respondas. En Ajustes > Extensión del navegador hay "Desvincular todos".
- **El navegador conserva una descarga hasta que la aplicación la tiene,** los formularios que enviaste se quedan en el navegador, y las ventanas privadas usan sus propias cookies.
- **Enlaces:** un enlace de archivo se toma después de que se ejecute el código de clic de la propia página, así que los sitios que antes respondían 403 ahora funcionan. Un enlace a un PDF se abre en el navegador salvo que la página lo marque como descarga. "Descargar todos los enlaces" también encuentra los enlaces marcados como descargas y los guardados en atributos data-*, y Google Drive tiene una fila "Archivo original".
- **El botón de vídeo:** funciona con el teclado (Tab, Enter), se pega a un borde cuando lo arrastras hasta allí, hay un botón por cada grupo de vídeos apilados y ninguno en un vídeo oculto bajo la página. Puedes ocultarlo en un sitio, y Telegram Web explica por qué no se puede descargar.
- **La extensión sigue el tema de la app:** su ventana y su página de ajustes son claras u oscuras como la app (Sistema sigue al dispositivo), y como el navegador cuando la app no está abierta.

**Actualizaciones**
- **La ventana de actualización** sigue el idioma de la aplicación, y las notas de la versión están traducidas.
