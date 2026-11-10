# 17. Kotlin/Native on Windows: system APIs (crypto, key vault, registry, clipboard, shell, power, time)

Finished 2026-11-09. The working code is not copied into this report: it is the helper layer
itself, in the repository, with its tests.

```
sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/win/
    WinCore.kt      errors, environment, temp / profile / executable path, memory use, hex
    WinFiles.kt     wide-path file helpers (report 16)
    WinCrypto.kt    SHA-256, AES-256-GCM, ECDSA P-256 verify, ECDH P-256, secure random (BCrypt)
    WinSystem.kt    DPAPI, registry, shell open / reveal, known folders, clipboard, time, suspend, named mutex
    WinProcess.kt   processes (report 18)
    WinZip.kt       zip reader (report 18)
    WinHttpEngine.kt  the HTTP engine (report 18)
desktopNative/tests/winhelpers/      a console test program for all of it
    run.sh [crypto secrets system files zip process net]
    Vectors.java    makes and checks the Java cross-check vectors
```

`bash desktopNative/tests/winhelpers/run.sh` builds nothing; build first with
`gradlew -p desktopNative/tests/winhelpers linkDebugExecutableMingwX64 --priority low`
(`FJ_TEST_RELEASE=1` runs the release program). Result on 2026-11-09: **155 checks pass**, debug
and release.

## 1. Bindings

The built-in `platform.windows` library already has everything except two functions, so
`sharedLogic` needs **no cinterop `.def` file**:

| Need | In `platform.windows`? |
|---|---|
| BCrypt (`BCryptOpenAlgorithmProvider`, hash, AES-GCM info struct, key pairs, secret agreement, `BCryptGenRandom`) | yes |
| DPAPI (`CryptProtectData`, `CryptUnprotectData`, `DATA_BLOB`) | yes |
| registry, `HKEY_CURRENT_USER` / `HKEY_LOCAL_MACHINE` | yes |
| `ShellExecuteW`, `SHOpenFolderAndSelectItems`, `ILCreateFromPathW`, `SHGetKnownFolderPath`, COM (`CoCreateInstance`, `IFileOpenDialog`) | yes (link with `-lole32` for `CoTaskMemFree`) |
| clipboard, `AddClipboardFormatListener`, `GetClipboardSequenceNumber` | yes |
| `CreateProcessW`, pipes, job objects, `InitializeProcThreadAttributeList` | yes |
| winsock basics (`WSAStartup`, `getaddrinfo`, `select`, `recvfrom`...) | yes |
| `CreateMutexW`, tokens and privileges, `ExitWindowsEx`, `InitiateSystemShutdownExW` | yes |
| `SetSuspendState` (powrprof) | **no**: loaded with `LoadLibraryW` + `GetProcAddress` |
| `GetProcessMemoryInfo` (psapi) | **no**: `K32GetProcessMemoryInfo` through `GetProcAddress` |
| `GetAdaptersAddresses` (iphlpapi), `WSAPoll`, `CreateToolhelp32Snapshot` | no (not needed so far) |
| `platform.winhttp` (all of WinHTTP), `platform.zlib` (inflate, crc32) | separate built-in libraries, present |

Wide-string macros such as `BCRYPT_SHA256_ALGORITHM` are not bound: pass the literal (`"SHA256"`,
`"AES"`, `"ECDSA_P256"`, `"ECDH_P256"`, `"ChainingModeGCM"`, `"ECCPUBLICBLOB"`). A Kotlin `String`
can be passed directly to any `LPCWSTR` parameter. Constants are `Int` or `UInt` depending on
their spelling in the header: call `.toUInt()` on them. `const val X = a or b` does not compile
for unsigned values: write the number.

## 2. Crypto, byte-compatible with the JVM code

All checked against Java 21 in both directions (`Vectors.java`).

| JVM | Native (`WinCrypto.kt`) | Checked |
|---|---|---|
| `MessageDigest("SHA-256")` | `winSha256`, `WinSha256` (incremental), `winSha256OfFile` | small input, empty input, a 20 MB file read in pieces (33 ms), odd piece sizes |
| `Cipher("AES/GCM/NoPadding")`, 12-byte IV, 128-bit tag, layout `IV ++ ciphertext ++ tag` (`SecretBox`, `CryptoBox`) | `winAesGcmSeal(key, plain)`, `winAesGcmOpen(key, sealed)` | native opens Java's output (text, empty, 300 KB); Java opens native output; a changed byte or a wrong key throws `WinCryptoException` |
| `Signature("SHA256withECDSA")` verify with an X.509 `SubjectPublicKeyInfo` P-256 key and a DER signature (`verifyUpdateSignature`) | `winEcdsaP256Verify(spki, sha256, derSignature)` | a Java signature of a 20 MB file; 40 signatures with DER lengths 70 / 71 / 72; wrong key, changed file, changed signature and garbage all return false; the app's real `UPDATE_PUBLIC_KEYS[0]` parses |
| `KeyPairGenerator("EC", 256)`, `KeyAgreement("ECDH")`, `SHA-256(secret)` with public keys sent as SPKI DER (device sync) | `WinEcdhP256().publicSpki`, `.deriveSha256(peerSpki)` | Java and native derive the same 32 bytes; a point off the curve is refused |
| `SecureRandom` | `winRandomBytes(n)` | |

Details that matter:

- BCrypt wants raw `r ++ s` (64 bytes), Java writes DER: `winEcdsaDerToRaw` converts.
- A P-256 SPKI is always the 26-byte prefix `3059301306072a8648ce3d020106082a8648ce3d030107034200`,
  then `04`, X, Y (91 bytes). BCrypt blobs are `magic, 32, X, Y` (`ECS1` for ECDSA, `ECK1` for ECDH).
- `BCryptDeriveKey(secret, "HASH", SHA256)` equals Java's `SHA-256(generateSecret())`.
- AES-GCM open of a 77-byte message: 3 us (release).

## 3. The key vault and `fjenc1:` secrets

The JVM app keeps a 32-byte AES key in `<home>/secrets.key`: the Base64 text of a DPAPI blob made
by `[Security.Cryptography.ProtectedData]::Protect(bytes, $null, CurrentUser)` (through
PowerShell). Stored secrets are `fjenc1:` + Base64 of `IV ++ ciphertext ++ tag`.

Checked: a key protected by that PowerShell call opens with `winDpapiUnprotect`; a `fjenc1:` value
written by Java then opens with `winAesGcmOpen`; a key protected by `winDpapiProtect` opens with
the JVM app's PowerShell `Unprotect` call; a `fjenc1:` value written natively opens in Java; a
damaged blob throws. So the native app and the JVM app can share one app home, in both
directions. The native `SecretBox` reads and writes `secrets.key` without starting PowerShell.

## 4. Registry, folders, environment (`WinSystem.kt`, `WinCore.kt`)

- `winRegReadString / WriteString / DeleteValue / KeyExists / SubKeys / DeleteTree` on
  `WinRegRoot.CURRENT_USER` and `LOCAL_MACHINE`, with `WIN_REG_VIEW_64 / 32`. Checked: write, read
  back (with Arabic text), delete under HKCU; the user's `Run` key opens; installed browsers are
  listed from `HKLM\SOFTWARE\Clients\StartMenuInternet`; `App Paths\msedge.exe` reads.
- The "start at login" value format the JVM app wrote and the format the native app writes are in
  report 01 (section 3.21): the helpers above are all it needs.
- `winKnownFolder(DOWNLOADS | DOCUMENTS | DESKTOP | VIDEOS | MUSIC | PICTURES | LOCAL_APP_DATA |
  ROAMING_APP_DATA | PROGRAM_FILES | PROGRAM_FILES_X86)`, `winTempDir()`, `winUserProfile()`,
  `winExecutablePath()`, `winGetEnv` / `winSetEnv` (Unicode), `winMemoryUse()`.

## 5. Clipboard

`winClipboardText()` (CF_UNICODETEXT, retries while another program holds the clipboard),
`winSetClipboardText(text)`, `winClipboardSequence()`. Watching for changes: the cheap way is to
compare `GetClipboardSequenceNumber()` on a timer (the JVM app polls too); no window is needed and
the text is read only when the number moved. `AddClipboardFormatListener` needs a window, so if
it is ever wanted it belongs to the host, which then calls the core. The tests only read the
clipboard, they never change the owner's.

## 6. Shell, power, time, single instance

- `winShellOpen(target, parameters, directory)` (`ShellExecuteW`), `winRevealInExplorer(path)`
  (`SHOpenFolderAndSelectItems`, falling back to `explorer.exe /select,`). Compiled, **not run** by
  the tests: they open windows on the owner's screen.
- `winSuspend(hibernate)` = `SetSuspendState(hibernate, false, false)`, what the JVM app's
  PowerShell call (`Application.SetSuspendState('Suspend', false, false)`) does. Only the lookup is
  tested (`winCanSuspend()`), the call is never made. Shut down stays `shutdown.exe /s /t 0`
  through `WinProcess` (the JVM app runs the same command), so no privilege code is needed.
- `winLocalUtcOffsetMs()` (`GetTimeZoneInformation`, daylight bias included) and
  `winLocalMinutesOfDay()` (`GetLocalTime`): equal to .NET's `TimeZoneInfo.Local` and clock.
- `winCreateNamedMutex(name)`: the second opener sees `alreadyExisted`; the name is free again
  after both close. The host uses it for single instance and then talks to the running instance
  through the bridge port, as the JVM app does (report 10).
