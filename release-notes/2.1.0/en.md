## What's new in 2.1.0

**Hidden downloads**
- **New:** hide a download, a playlist or a folder group behind a PIN of 4 to 6 digits (More > Hidden). Hidden items leave the list, the gallery and the file manager, and their files move into a private folder of the app. Hiding is instant and shows one progress line while the files move. Unhiding brings the files back to their folder.
- **Audio and video are encrypted:** the audio and video you hide are encrypted with a key tied to the device, their file names are scrambled, and they are decrypted only while you play them. Other file types are only moved out of sight. A notice explains this the first time.
- **New downloads start hidden** when you start them from the Hidden screen (the + button). Hidden downloads show no notification.
- **Forgot the PIN:** confirm with the lock of your phone or computer (fingerprint, screen lock, Windows Hello, Face ID) and set a new PIN. Your hidden files stay. Not available on Linux.
- **Recovery code:** one code for the whole vault, shown once. With it you can reopen hidden audio and video if the device key is lost, for example after restoring to a new phone. Deleting the app or clearing its data deletes the hidden files, so unhide them before you uninstall.

**Backup and restore**
- **Back up downloads** saves the list of your downloads, folders and settings to one file, with no passwords or cookies. **Restore from backup** brings it back and merges it with what is there. The downloaded files are not inside the backup: finished downloads reconnect to their files, and unfinished ones come back paused.

**Browser extension 3.8.0**
- **Open in FetchJet:** a new arrow next to the page download button and an "Open in FetchJet" row send the page link to the app at once, and you pick the quality there. This also works on pages where the qualities cannot be listed, for example a Udemy lecture.
- **Open FetchJet directly:** a new option hands links to the app without the local bridge.

**Smaller things**
- A long press on a download in the Hidden screen opens a menu. All lists now share one row menu.
- Android: if a file was downloaded by another install of FetchJet and cannot be read, FetchJet now opens the "Allow access to all files" page instead of failing silently.
- The Add dialog no longer has the "download as hidden" switch.
