## Novedades de la 1.4.1

- **Descargas FTP y FTPS (nuevo):** los enlaces que empiezan por ftp://, ftps:// o ftpes:// se descargan ahora como cualquier enlace directo, en varias partes y con reanudación cuando el servidor lo permite. Pon el inicio de sesión en el enlace, guárdalo en Ajustes > Red > Inicios de sesión de sitios, o déjalo vacío en los servidores anónimos. A tener en cuenta: los enlaces FTP no pasan por el proxy; en las aplicaciones de escritorio, FTP sobre TLS usa una sola conexión; FTP sobre TLS aún no está disponible en iPhone ni iPad.
- **Un archivo que cambia en el servidor:** si el archivo se sustituye en el servidor mientras se descarga, FetchJet lo detecta. La descarga se detiene con un mensaje claro en lugar de guardar un archivo mezclado y dañado, y "Reanudar" descarga la versión nueva.
- **Sin esperas al final:** el final de una descarga ya no espera a una sola conexión lenta. Una conexión que se arrastra al final se sustituye.
- **Audio en OGG:** elegir OGG para un audio de YouTube (Opus) ya no lo vuelve a codificar. Se guarda al instante, con la calidad y el tamaño originales.
- **Comprimir audio:** ahora ofrece los mismos formatos que Convertir (M4A, MP3, Opus, OGG, FLAC, WAV), mantiene OGG cuando el archivo es OGG, y el tamaño estimado se acerca más al real.
- **Extensión del navegador:** cuando el navegador aún no está conectado a la aplicación, la extensión lo dice enseguida ("Abre FetchJet y toca Permitir") en lugar de cargar sin fin. Después de tocar "Permitir", las calidades aparecen solas, sin recargar. Recargar una página de vídeo vuelve a intentarlo tras un error.
- **Ajustes > Extensión del navegador:** "Desvincular todos" desconecta todos los navegadores vinculados; cada uno volverá a pedir "Permitir" la próxima vez.
- **Ventana de actualización:** las notas de la versión siguen el idioma de la aplicación cuando lo cambias.
