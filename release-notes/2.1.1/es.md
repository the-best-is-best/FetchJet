## Novedades de la 2.1.1

**Reproductor multimedia**
- **Nuevo:** reproduzca el audio y el vídeo terminados dentro de FetchJet en todas las plataformas: un botón de reproducir en la fila, «Reproducir» para una carpeta, lista de reproducción o grupo entero, una cola con anterior / siguiente y la lista, velocidades, y mantener pulsado para 2x. Se recuerda la última posición.
- **Controles compactos:** los controles se muestran sobre el vídeo y se ocultan a los 3 segundos. Una barra de progreso fina, una sola fila pequeña con la velocidad, un interruptor de **Reproducción automática** y **CC**. Cuando un vídeo está en pausa, o el siguiente espera con la reproducción automática desactivada, aparece un ▶ grande en el centro.
- **Reproducción automática:** activada, el siguiente elemento empieza solo; desactivada, se carga en pausa.
- **Subtítulos:** el menú CC muestra los subtítulos incluidos en el vídeo, los archivos de subtítulos que están a su lado (su idioma primero) y «Abrir archivo de subtítulos…» (SRT, VTT, ASS). La elección se guarda para cada descarga. Los subtítulos van abajo y suben por encima de los controles cuando estos aparecen. CC se ve relleno mientras hay un subtítulo activo.
- **Teléfonos y tabletas:** deslice hacia arriba o abajo en la mitad derecha para el volumen y en la mitad izquierda para el brillo (solo el del reproductor).
- **Ordenadores:** un control de volumen hasta el 200 % con botón de silencio, y pantalla completa. En Windows, macOS y Linux la biblioteca del reproductor se descarga solo la primera vez que reproduce algo, mostrando su progreso, y el archivo se abre solo cuando está lista.

**Cortar, extraer y comprimir**
- **Cortar vídeo:** desde el menú de la fila o los detalles, por tiempo o en una línea de tiempo. La línea de tiempo funciona como en un editor de vídeo: una escala de tiempo fija, arrastrar o desplazar para moverse, zoom desde unos segundos hasta el vídeo entero, tiradores que desplazan la vista en los bordes, miniaturas y una barra de desplazamiento fina. El fragmento es un archivo nuevo que aparece bajo su vídeo.
- **Cortar audio:** los archivos de audio se cortan igual, sobre una línea de tiempo con la forma de onda. Las partes MP3 se recodifican con la tasa de bits de la original, así no se pierde sonido y el archivo no crece.
- **Extraer audio:** guarda la pista de sonido de un vídeo como un archivo aparte, sin recodificar.
- **Eliminar el original:** mientras no se haya creado nada más a partir de un archivo, Cortar y Comprimir ofrecen «Eliminar el original». El archivo nuevo pasa entonces a ser la descarga principal: puede añadirle subtítulos (su tiempo sigue al fragmento), cortarlo o comprimirlo otra vez. Quitar una descarga quita también los fragmentos y copias creados a partir de ella, y la confirmación dice cuántos son.

**Lector de PDF**
- **Nuevo:** abra archivos PDF dentro de FetchJet: páginas una debajo de otra, número de página e «Ir a la página», ajustar al ancho, zoom, y se recuerda la última página leída. Android e iOS usan el motor PDF del sistema; Windows, macOS y Linux descargan una pequeña biblioteca PDF la primera vez. Un ajuste elige el lector de la app, la app del sistema o preguntar.

**Descargas ocultas**
- **Nuevo:** oculte una descarga, una lista de reproducción o un grupo de carpeta tras un PIN de 4 a 6 dígitos o un patrón (Más > Ocultas). Los elementos ocultos salen de la lista, de la galería y del administrador de archivos, y sus archivos pasan a una carpeta privada de la app. Ocultar es instantáneo y muestra una línea de progreso mientras se mueven los archivos. Al dejar de ocultar, los archivos vuelven a su carpeta.
- **El audio y el vídeo se cifran:** el audio y el vídeo que oculta se cifran con una clave ligada al dispositivo, sus nombres de archivo se alteran y solo se descifran mientras los reproduce en el reproductor de la app. Los demás tipos de archivo solo se apartan de la vista. Un aviso lo explica la primera vez.
- **Al salir de la app, esta se cubre:** al volver pide su huella, Face ID, Windows Hello o PIN, y se cierra un vídeo oculto que se estuviera reproduciendo.
- **Las descargas nuevas empiezan ocultas** si las inicia desde la pantalla Ocultas (el botón +). Las descargas ocultas no muestran notificación.
- **Olvidé el PIN:** confirme con el bloqueo de su teléfono u ordenador y fije un PIN nuevo. Sus archivos ocultos se conservan. No disponible en Linux.
- **Código de recuperación:** un código para toda la bóveda, que se muestra una sola vez. Con él puede volver a abrir el audio y el vídeo ocultos si se pierde la clave del dispositivo, por ejemplo al restaurar en un teléfono nuevo. Si borra la app o sus datos, se borran también los archivos ocultos: deje de ocultarlos antes de desinstalar.

**Copia de seguridad y restauración**
- **«Copia de seguridad de descargas»** guarda en un solo archivo la lista de sus descargas, carpetas y ajustes, sin contraseñas ni cookies. **«Restaurar desde copia de seguridad»** la recupera y la une a lo que ya hay. Los archivos descargados no van dentro de la copia: las descargas terminadas se vuelven a enlazar con sus archivos y las inacabadas vuelven en pausa.

**Navegador e inicios de sesión**
- **Navegador integrado** (Más > Navegador, Android e iOS): una barra de direcciones, atrás / adelante / recargar; un archivo o vídeo que abra allí pasa al diálogo de añadir con la sesión de la página.
- **Iniciar sesión con el navegador** (Ajustes > Red > Inicios de sesión de sitios): inicie sesión una vez en un sitio y sus descargas usarán esa sesión. Quitar el inicio de sesión del sitio borra también las cookies del navegador para ese sitio.

**Descargas**
- **Conexiones elegidas solas:** FetchJet empieza con 4 conexiones, añade más mientras la velocidad siga subiendo y recuerda lo que funcionó en cada sitio.
- **Fragmentos con tiempos exactos:** un fragmento ahora empieza y termina exactamente en Desde y Hasta, y Desde / Hasta aceptan fracciones de segundo (12.500). Antes un fragmento podía empezar tarde (Desde 12 empezaba al final del segundo 12) o pronto (11.5 empezaba en 11), porque el corte se movía al fotograma clave más cercano. Pasaba en todas las plataformas desde que existen los fragmentos.
- **Reanudar muestra una flecha de descarga** (⬇) en lugar de ▶, para que ya no parezca reproducir un vídeo. ▶ es ahora solo para reproducir un archivo terminado.
- **El curso completo de Udemy** trae también los archivos del curso: clases de PDF, libro electrónico, diapositivas y código, y los recursos adjuntos a las clases, cada uno justo después de su clase.
- **Los detalles** vuelven a mostrar el enlace de origen; un enlace que lleva un inicio de sesión o un token muestra una nota en su lugar.
- **Nuevo icono** de la app en todas las plataformas.

**Seguridad**
- Quitar una descarga sin terminar o fallida con «borrar también los archivos» ya no borra un archivo anterior suyo con el mismo nombre. Quitar un archivo restaurado desde una copia comprueba antes que de verdad es el archivo de esa descarga.
- Una descarga que no se pudo mover a su sitio ya no deja un archivo cortado con el nombre final, y un tamaño incorrecto al final ya no tira las partes ya descargadas.
- Los archivos 7z y RAR se comprueban antes de extraerlos (sin enlaces, sin rutas que salgan de la carpeta, límites de tamaño), y la contraseña del archivo ya no es visible para otros programas.
- La conexión local con la extensión del navegador siempre necesita el emparejamiento; la API de automatización solo la puede activar usted, no a través de la API.
- Unos ajustes que no se pueden descifrar por un momento ya no sustituyen el emparejamiento de la extensión.

**Android**
- **«Solo el fragmento» vuelve a funcionar:** desde la 1.5.0, «Solo el fragmento» fallaba en Android con «ffmpeg exited with code 8» en todos los sitios, porque el ffmpeg reducido creado para la app no tenía acceso a la red. ffmpeg vuelve a leer la parte del vídeo desde el sitio, y si alguna vez falla, la app descarga el vídeo entero y lo corta con exactitud en lugar de fallar.
- **Unión de partes de vídeo:** desde la 1.5.0 algunos sitios que envían un vídeo en partes (HLS) fallaban con «dimensions not set». Corregido.
- Un fragmento cortado con exactitud en el teléfono ya no es mucho más grande que su parte del vídeo (un fragmento de 5 segundos de un vídeo pequeño ocupaba 1,6 MB).
- En el reproductor, deslizar en los bordes laterales cambia el volumen o el brillo en lugar de ir Atrás, y Atrás cierra primero un menú abierto antes que el reproductor.
- Los enlaces abiertos desde otras apps o páginas web muestran siempre primero el diálogo de añadir, un archivo torrent abierto desde otra app se comprueba primero, y la pregunta de la batería se hace solo una vez.

**Windows, macOS y Linux**
- Las bibliotecas del reproductor y del PDF se cargan solo desde las carpetas propias de FetchJet, nunca desde la carpeta actual ni desde el PATH.
- macOS y Linux: las actualizaciones y los archivos temporales usan una carpeta que solo usted puede abrir, y una actualización se vuelve a comprobar justo antes de instalarla.
- La elección de «Cortar audio» muestra las dos opciones a todo el ancho.

**iOS**
- **«Solo el fragmento»:** un fragmento podía guardarse como un archivo diminuto que no se reproducía. La app ahora lo detecta, descarga el vídeo entero y lo corta con exactitud.
- El navegador integrado detiene su sonido y libera su memoria al cerrarlo.

**Extensión del navegador 3.8.0**
- **Abrir en FetchJet:** una flecha junto al botón de descarga de la página y una fila «Abrir en FetchJet» envían el enlace de la página a la app al instante, y allí elige la calidad. Funciona también en páginas donde no se pueden listar las calidades, por ejemplo una clase de Udemy.
- **Abrir FetchJet directamente:** una opción nueva entrega los enlaces a la app sin el puente local.
- **Canva:** cada vídeo que se reproduce recibe el botón de descarga, con el nombre de su página; su imagen y su sonido se guardan en un solo archivo.
- **Página de inicio de YouTube:** el botón de un vídeo que se reproduce al pasar el ratón ya no parpadea cuando se mueve sobre él, y siempre pertenece al vídeo que está bajo el ratón. Antes desaparecía y volvía, y podía mostrar las calidades del primer vídeo en otro.
- **Udemy:** la emisión de una clase lleva el nombre de la clase en lugar de un código aleatorio.
- **Más segura:** solo las páginas propias de la extensión pueden cambiar sus ajustes, las cookies de una página se envían solo a ese mismo sitio, y la lectura de cursos de Udemy solo funciona en udemy.com.

**Cosas menores**
- Una pulsación larga sobre una descarga en la pantalla Ocultas abre un menú. Todas las listas comparten ahora un mismo menú de fila, por secciones, con Principal primero.
- Android: si otra instalación de FetchJet descargó un archivo y no se puede leer, FetchJet abre ahora la página «Permitir acceso a todos los archivos» en lugar de fallar en silencio.
- El diálogo de añadir ya no tiene el interruptor «Descargar como oculta».
