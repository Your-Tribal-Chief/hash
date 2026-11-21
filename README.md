# # Hash Browser

A privacy-first Android browser in the spirit of Brave — built with Kotlin, Jetpack Compose and Android System WebView.

## Features

- **Hash Shields** — EasyList + EasyPrivacy ad and tracker blocking (network + element hiding), per-site toggle, blocked-request log, pop-up and third-party-cookie blocking, privacy stats on the new tab page
- **Hash Tunnel** — route only this browser through your own HTTP or SOCKS5 proxy, with exit-IP check, kill switch and bypass list. Not a system VPN, so it works alongside Tailscale or any VPN app
- **Hash Mods** — Greasemonkey/Tampermonkey userscripts: install from any `.user.js` link (Greasy Fork, OpenUserJS), paste or edit code. Ships with dark-mode, force-text-selection and video-speed mods
- **Hash Inspect** — full Eruda devtools (Elements, Console, Network, Resources, Sources), tap-to-inspect with "hide this element", page source viewer, and optional remote debugging via `chrome://inspect`
- Tabs with thumbnails, private tabs (isolated profile where WebView supports it), bottom address bar, bookmarks, history, downloads, find in page, desktop mode, reader mode, HTTPS upgrade, default-browser support

## Get the APK

Every push to `main` builds a signed APK with GitHub Actions:

- **Releases** page → latest `build-N` → `HashBrowser.apk`
- or the `apk` branch → `HashBrowser.apk`

All builds use the same signing key (`app/hash.keystore`), so a new build installs over the old one.

## Build locally

Open the folder in Android Studio (JDK 17), or run `gradle assembleRelease` with Gradle 8.11+.
Filter lists are downloaded into `app/src/main/assets/filters/` by the CI workflow; locally the app falls back to the small bundled base list and can update lists from Shields › Update lists.

## Limits

- Chrome Web Store extensions can't run in Android WebView; Hash Mods supports userscripts instead.
- WebView cannot authenticate to SOCKS5 proxies; use an HTTP proxy for username/password servers.
