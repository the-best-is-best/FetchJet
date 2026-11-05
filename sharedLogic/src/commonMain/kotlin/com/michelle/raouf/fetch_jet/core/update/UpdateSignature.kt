package com.michelle.raouf.fetch_jet.core.update

/**
 * Public keys of the update signing key (ECDSA P-256, X.509 SubjectPublicKeyInfo, base64).
 * CI signs every release file with the private key (`scripts/sign-release-file.sh`, secret
 * `UPDATE_SIGNING_KEY`) and uploads `<file>.sig`; the app installs only a file whose signature
 * matches one of these keys. A new key = add it here, ship that version signed with the old
 * key, then sign with the new one. Never remove the key the installed versions trust.
 */
internal val UPDATE_PUBLIC_KEYS = listOf(
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE55ElrDkiL7DyOHMrcoJj9A7RWTSiJx2/64nxGKTr+1TBHM48ZgCOdDttzZd3C39qRsAXg6WpAwMe1K4+ki53Lg=="
)

/** True when [signatureBase64] (the `.sig` file) signs the file at [path] with a key above. */
internal expect fun verifyUpdateSignature(path: String, signatureBase64: String): Boolean
