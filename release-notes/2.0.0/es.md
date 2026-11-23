## Novedades de la 2.0.0

**Un diálogo de añadir más inteligente**
- **FetchJet elige el motor según el enlace:** un archivo, un enlace magnet o un sitio de vídeo conocido va solo a Direct, Torrent o Media. Primero comprueba el enlace una vez («Comprobando el enlace…»), y la elección entre Direct y Media solo aparece para una página con vídeo o para un enlace que no se pudo comprobar.
- **Se acabó comprobar con cada letra que escribes:** la comprobación empieza cuando dejas de escribir (al instante si pegas el enlace). Cambiar el enlace cancela la lectura anterior, así que el diálogo ya no se queda atascado en «Buscando el nombre…», por ejemplo con un magnet que no encuentra pares.

**Buscar archivos en esta página**
- **Novedad:** pega el enlace de una página y usa «Buscar archivos en esta página». FetchJet enumera los archivos de la página: imágenes, enlaces y direcciones escritas como texto. A los enlaces sin nombre de archivo les pide al servidor el nombre y el tamaño reales. Si lo pides, también lee las páginas a las que enlaza, uno o dos niveles más abajo.
- **Resultados mientras lee:** los archivos aparecen a medida que se lee la página, y «Detener la lectura» conserva lo que se encontró.
- **Elige qué descargar:** los tipos de archivo son botones que ocultan o muestran esos archivos, y los archivos ocultos no se descargan. Cada fila muestra su tipo (ZIP, MP3, PNG…).
- **Se guardan como grupo:** los archivos van a una carpeta y a un grupo con el nombre de la página. Más adelante, «Buscar archivos nuevos» marca los que ya tienes y los bloquea («N ya descargados»).
- **Las páginas de versiones de GitHub** se leen en una sola petición.
- **Páginas que no se pueden leer:** se cuentan las páginas que el sitio rechazó (una comprobación o un bloqueo). Las páginas que construyen sus enlaces con JavaScript se nombran, con una pista para abrirlas en el navegador y usar «Descargar todos los enlaces» de la extensión.

**Listas de reproducción y subtítulos**
- **Listas de reproducción:** los vídeos que ya descargaste se marcan y se cuentan («N ya descargados»), y una lista guardada como grupo se puede comprobar en busca de vídeos nuevos.
- **Subtítulos:** elige «Incrustar en el vídeo» o «Archivo aparte», y después SRT o VTT. Los idiomas se piden uno a uno, y uno que el sitio rechaza un momento se vuelve a pedir. También puedes añadir subtítulos a un vídeo terminado desde sus detalles o desde el menú de su fila.
- **«Enviar al dispositivo» conserva los subtítulos:** un vídeo terminado que se envía a otro dispositivo sigue ofreciendo allí «Añadir subtítulos», sabe qué idiomas ya están en el archivo, y los archivos de subtítulos que lo acompañan viajan con él.

**Vídeos**
- **Google Drive:** cualquier enlace de archivo de Drive se descarga directamente, y un archivo cuyo propietario desactivó la descarga lo indica.
- **Vídeos protegidos:** un vídeo protegido con DRM se indica como tal antes de la lista de calidades, en lugar de fallar más tarde.
- **Vídeos que se reproducen en todas partes:** el interruptor (ahora en Ajustes > Herramientas multimedia, y también en Android) elige archivos H.264 + AAC que abre cualquier televisor o reproductor, o archivos AV1 / VP9 más pequeños.

**Ajustes**
- **Reorganizados:** Descargas (ubicación, conexiones, cola), una página nueva de Límites de velocidad (ajustes predefinidos, límite en reposo, horario) y una página nueva de Archivos y carpetas (ordenar por tipo, agrupar por carpeta, extraer, papelera, análisis, carpetas por sitio).

**Velocidad**
- Los archivos grandes usan por defecto hasta 32 conexiones.
- Una petición que pasa 20 segundos sin recibir datos se descarta y se vuelve a pedir, así que una conexión atascada ya no detiene una descarga durante un minuto.
- En transferencias cortas y rápidas, una conexión lenta se sustituye antes.
- macOS permite 64 conexiones por sitio, así que una segunda descarga del mismo sitio ya no espera.

**Cosas menores**
- «Al terminar las descargas» también espera a un archivo comprimido que se está extrayendo o a un archivo que se está analizando.
- Un torrent con nombre largo ya no estira su tarjeta de archivos, y las filas de sus archivos muestran primero el nombre del archivo.
- Los enlaces y las rutas de carpetas conservan su dirección en árabe y persa.
- Android: una lista de reproducción leída en segundo plano ya no cierra la aplicación.
- La ventana de escritorio se redibuja por completo al mostrarse o cambiar de tamaño, sin zonas sin pintar.
- La aplicación de macOS está compilada para funcionar en macOS 12 y posteriores.
- **Android: sin lista rota tras reinstalar o cambiar de teléfono:** la lista de descargas ya no se restaura desde una copia de seguridad, porque sus enlaces están ligados al teléfono y no se podían leer en otro. Los archivos que descargaste se quedan en tu carpeta.
- **Mensaje más claro en los subtítulos:** cuando no se puede leer el enlace de una descarga antigua, «Añadir subtítulos» dice que no pudo leer los subtítulos en lugar de decir que el vídeo no tiene.
- **Cola más rápida:** la siguiente descarga en espera empieza en cuanto queda un hueco libre (50 archivos pequeños: de 16 s a menos de 1 s), y unir las partes de un archivo es más rápido.
- **Una página Más** sustituye a Ajustes en la navegación: versión y actualización, Ajustes, FetchJet en todos tus dispositivos, Compartir FetchJet, y Comunidad y ayuda.
- **Mismo nombre de archivo:** dos descargas que acaban con el mismo nombre en una carpeta ahora se guardan como «nombre (1).ext», y una descarga ya no puede desaparecer de una cola larga.
- **Una fuente por idioma** en todas las plataformas (la aplicación pesa unos 7 MB más).
- **Sitios de vídeo recordados:** un sitio donde una comprobación Media encontró calidades se trata como sitio de vídeo la próxima vez, y los tamaños de las calidades HLS se estiman y se muestran con ~.
- **La ventana de añadir empieza vacía** después de cerrarla.
- **Windows:** ya no parpadean ventanas de consola, cada versión incluye SHA256SUMS.txt y la versión portátil se actualiza sola.
- **También de la 1.5.1:** la comprobación de actualizaciones de Microsoft Store, los nombres de archivo en árabe en ZIP hechos en un Mac y los torrents en iOS a través de un proxy.
