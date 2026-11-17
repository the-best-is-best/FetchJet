## What's new in 1.5.0

**Archives**
- **Extract after the download:** ZIP, TAR, GZ and XZ files can be extracted by themselves when they finish, or with the new Extract button on the row. On the desktop, 7Z, RAR, split archives (.001, .part1.rar, .z01) and archives with a password work too when 7-Zip is installed; FetchJet asks for the password.
- **Where the files go:** a folder named after the archive, always a folder, or next to the archive. You can also move the archive to the Recycle Bin after it is extracted.

**Downloads**
- **Right-click menu on a download** (long press on phones): open, open the folder, copy the link, pause, resume, delete, and more.
- **Folders by site:** send everything from a site you choose to its own folder.
- **Deleted files go to the Recycle Bin** (Trash on macOS and Linux) instead of disappearing. You can switch it off in Settings.
- **Scan after the download:** Windows checks a finished file with your antivirus, and a download the antivirus blocks stops at once with a clear message. On macOS, downloaded and extracted files carry the usual "downloaded from the internet" mark.
- **Monthly report:** Settings > Downloads shows, for each month, how many files you downloaded, their total size and the sites they came from. It is counted on your device only.

**Network (desktop)**
- **Your own DNS:** pick a DNS server for FetchJet's downloads (DNS over HTTPS or a plain DNS server), and choose which network adapter they use. Only FetchJet's downloads are affected, not the rest of the computer.

**Windows**
- **New installers:** a Setup.exe and a new MSI with your choice of a desktop and Start menu shortcut. They tell you if this version, a newer or an older one is already installed, and can start FetchJet at the end.
- **Portable version:** a .zip that runs without installing, with its data in a folder next to it.
- **Newer ffmpeg** on Windows and Linux (the tool that merges video and audio). It updates by itself the next time FetchJet starts.

**Android**
- **Smaller app:** about 53 MB instead of 83 MB on most phones, and it takes less space once installed.

**iPhone and iPad**
- **Pages that need a login, like Zoom recordings:** open the page inside FetchJet, type the passcode once, and download with the page's session. The page button in Safari also has "Open this page in FetchJet".

**For advanced users**
- **Command line:** `FetchJet status`, `pause`, `start`, `remove` and `delete` control a running FetchJet. They are off until you run `FetchJet automation on`.

**Browser extension (3.5)**
- Takes every browser download, unless you limit it to the file types you list.
- A download button on Zoom's audio-only recordings.
- Players inside frames find their video more reliably.

**Privacy**
- FetchJet now sends one anonymous message a day so we know how many people use it: a random number for this install, the app version and the system; the server reads the country from the connection. No links, files or personal data. The totals are on a public page linked from the README.
