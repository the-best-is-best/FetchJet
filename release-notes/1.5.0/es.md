## Novedades de la 1.5.0

**Archivos comprimidos**
- **Extraer tras la descarga:** los archivos ZIP, TAR, GZ y XZ se pueden extraer solos al terminar, o con el nuevo botón Extraer de la fila. En el escritorio también funcionan 7Z, RAR, los archivos divididos (.001, .part1.rar, .z01) y los archivos con contraseña si 7-Zip está instalado; FetchJet pide la contraseña.
- **Dónde van los archivos:** a una carpeta con el nombre del archivo comprimido, siempre a una carpeta, o junto al archivo comprimido. También puedes mover el archivo comprimido a la Papelera de reciclaje después de extraerlo.

**Descargas**
- **Menú del clic derecho en una descarga** (pulsación larga en los teléfonos): abrir, abrir la carpeta, copiar el enlace, pausar, reanudar, eliminar y más.
- **Carpetas por sitio:** envía todo lo que llega de un sitio, o de enlaces que empiezan igual (un curso, un proyecto), a la carpeta que elijas. Un curso enviado desde el navegador también la sigue.
- **Los archivos eliminados van a la Papelera de reciclaje** (la Papelera en macOS y Linux) en lugar de desaparecer. Puedes desactivarlo en Ajustes.
- **Análisis tras la descarga:** Windows revisa el archivo terminado con tu antivirus, y una descarga que el antivirus bloquea se detiene al instante con un mensaje claro. En macOS, los archivos descargados y extraídos llevan la marca habitual de "descargado de internet".

**Red (escritorio)**
- **Tu propio DNS:** elige un servidor DNS para las descargas de FetchJet (DNS sobre HTTPS o un servidor DNS normal) y el adaptador de red que usan. Solo afecta a las descargas de FetchJet, no al resto del ordenador.

**Windows**
- **Nuevos instaladores:** un Setup.exe y un nuevo MSI en los que eliges si crear un acceso directo en el escritorio y en el menú Inicio. Te avisan si esta versión, una más nueva o una más antigua ya está instalada, y pueden abrir FetchJet al final.
- **Versión portátil:** un .zip que funciona sin instalar, con sus datos en una carpeta a su lado.
- **ffmpeg más reciente** en Windows y Linux (la herramienta que une el vídeo y el audio). Se actualiza solo la próxima vez que se inicie FetchJet.

**Android**
- **Aplicación más pequeña:** unos 53 MB en lugar de 83 MB en la mayoría de los teléfonos, y ocupa menos espacio una vez instalada.

**iPhone y iPad**
- **Páginas que requieren iniciar sesión, como las grabaciones de Zoom:** abre la página dentro de FetchJet, escribe el código de acceso una vez y descarga con la sesión de la página. El botón de la página en Safari también tiene "Abrir esta página en FetchJet".

**Para usuarios avanzados**
- **Línea de comandos:** `FetchJet status`, `pause`, `start`, `remove` y `delete` controlan un FetchJet en marcha. Están desactivados hasta que ejecutes `FetchJet automation on`.

**Extensión del navegador (3.5)**
- Toma todas las descargas del navegador, salvo que la limites a los tipos de archivo de tu lista.
- Un botón de descarga en las grabaciones de Zoom de solo audio.
- Los reproductores dentro de marcos encuentran su vídeo con más fiabilidad.

**Privacidad**
- FetchJet ahora envía un mensaje anónimo al día para que sepamos cuántas personas lo usan: un número aleatorio para esta instalación, la versión de la aplicación y el sistema; el servidor obtiene el país de la conexión. Sin enlaces, archivos ni datos personales. Los totales están en una página pública enlazada desde el README.
