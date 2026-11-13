# Security

## Reporting a problem

Please use GitHub's private vulnerability reporting for this repository (Security → Report a vulnerability). If that is not available, open an issue without details and ask for a private channel.

## What the app stores and where

Tryfolio has no account and no server of its own. Everything stays on the device.

| Data | Storage | Protection |
| --- | --- | --- |
| API keys and other credentials you enter | Vault | AES-256-GCM, entry name bound as additional data |
| Practice depot, orders, watchlist, settings, notes | App data folder | Not encrypted by the app; protected by the operating system's app or user separation |
| Downloaded market data and caches | App data folder | Not encrypted |

Vault key by platform:

| Platform | Where the key lives |
| --- | --- |
| Android | Android Keystore, the key does not leave the device. App data is excluded from backups. |
| iOS | Keychain |
| Windows | Key file protected with DPAPI for the current user |
| Linux, macOS | Key file readable only by the current user |

Limits you should know:

- On Windows, Linux and macOS a program running under your own user account can read the vault. The vault protects against reading the settings files alone and against backups that do not include the key file.
- The Android vault key is not tied to the screen lock, because price updates with your API keys also run in the background.
- Depot data is not secret to someone with access to your unlocked user account or device.

## Network

- The app talks directly to the public data sources listed in `NOTICE` and in the app under Sources. They see your IP address and what is requested, for example the securities you look at. Keys you enter are sent only to the provider they belong to.
- No analytics, no advertising, no crash reporting to third parties.
- Requests use HTTPS and certificate errors abort the request. The Android app has no cleartext exception.
- Embedded web pages run without access to local files. The two script bridges are limited to the learning pages bundled with the app and to saving a file you chose to download.

## Releases

- Android packages are signed with the project's key.
- Windows, Linux and macOS packages and the iOS file are not code-signed. Compare the SHA-256 checksums published with each release.
- Dependencies are pinned and verified by checksum (`gradle/verification-metadata.xml`, lock files).
