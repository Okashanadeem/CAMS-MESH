# CAMS-MESH Android App

Offline-first peer-to-peer classroom material sharing Android application using **Google Nearby Connections**, **Jetpack Compose**, **Room Database**, and **Kotlin Coroutines**.

## Features Built

- **Zero-Internet P2P Networking:** Google Nearby Connections (`P2P_CLUSTER` strategy) for Wi-Fi direct & Bluetooth mesh sharing.
- **Material Management:** Upload files (PDF, images, docs, archives up to 50MB) and paste rich text notes.
- **Content Integrity:** SHA-256 verification on all transfers, preventing corruption or tampered payloads.
- **Multi-Hop Relay:** Material availability is advertised across peers via manifest exchange (`HELLO`, `MANIFEST_REQUEST`, `MANIFEST_RESPONSE`, `SYNC_RECORD`, `REQUEST_ITEM`, `TOMBSTONE`).
- **Complete Privacy & Offline Persistence:** Room Database for local history, internal app-private storage, no external server/portal login needed.
- **Nuclear Clear All Data:** Wipes database, tombstones, and local cached/materials files instantly.

## Automated APK Build (GitHub Actions)

This repository includes a preconfigured GitHub Actions workflow:
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

### How to get the APK:
1. Commit and push this codebase to your GitHub repository:
   ```bash
   git add .
   git commit -m "Complete CAMS-MESH offline Android app implementation"
   git push origin main
   ```
2. Open the **Actions** tab on your GitHub repository.
3. The **Build Android APK** workflow will execute automatically on Ubuntu with JDK 17 and Gradle.
4. Once completed, download the release artifact **`cams-mesh-debug-apk`** containing `app-debug.apk`.
5. Install the APK on any Android phone (Android 7.0+ / API 24+).
