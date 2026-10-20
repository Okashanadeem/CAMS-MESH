# CAMS-MESH

> **An offline-first, peer-to-peer material and communication mesh app built by OKASHA NADEEM.**

CAMS-MESH connects devices locally without internet, cellular data, or external servers using **Google Nearby Connections (Bluetooth & Wi-Fi Direct)**, **Jetpack Compose**, **Room Database**, and **Kotlin Coroutines**.

---

## 🚀 Key Features

### 🌐 Zero-Internet Local Mesh
- **Google Nearby Connections (`P2P_CLUSTER`):** Form decentralized local mesh networks over Bluetooth Low Energy, Bluetooth Classic, and local Wi-Fi hotspots.
- **No Internet or Router Needed:** Devices communicate directly peer-to-peer with zero data costs.
- **SHA-256 Content Integrity:** Every payload transferred over the mesh is cryptographically checked to prevent corruption or tampering.

### 👥 Room & Network Management
- **Create & Join Rooms:** Create public or private rooms identified by a 6-character room code or dynamic QR code.
- **Public Room Discovery:** Toggle "Visible to all" when creating a room so nearby users can discover and join with a single tap.
- **Non-Duplicate Active Room Cards:** Clean home hierarchy featuring **Create Room** and **Join Room** buttons at the top, the **Current Active Room** card directly underneath, followed by joined rooms.
- **Seamless Switching:** Easily switch between rooms with automatic networking cleanup to avoid advertising and connection conflicts.

### 💬 In-Room Chat & Dedicated File Repository
- **Real-Time Mesh Chat:** Text messages broadcast instantly across all connected peers in the room.
- **In-Chat File Notice:** Uploading files generates a notice in the room's chat: `"[Name] has uploaded a file please view it in files"`.
- **Room Files Screen:** Browse room-specific files ordered newest-first (stack order) with file metadata and one-tap download.

### 🖼️ Instant File Download & Android Gallery Integration
- **Direct Gallery Integration:** Saved images (JPG, PNG, WebP, GIF) are saved directly into Android `MediaStore` (`Pictures/CAMS-MESH`) and scanned via `MediaScannerConnection` so they immediately show up in the phone's native Gallery.
- **Instant File Open:** Downloaded files immediately launch the system file viewer / opener upon completion (WhatsApp-style).

### 🌐 External User Web Upload (Zero-App Portal)
- **Local HTTP Web Server:** Integrated lightweight HTTP server running on the device.
- **Any Browser Connects:** An external user (e.g. guest or presenter) can connect to the host's Wi-Fi hotspot and navigate to the IP address to upload files or paste text notes directly into CAMS-MESH without installing the app.
- **Branded Confirmation:** Displays *"CAMS-MESH an app built by OKASHA NADEEM"*.

### 👤 Profile & Support
- **Display Name Prompt:** New users or default `"Student"` usernames are prompted upon app launch to update their name, automatically focusing the input field in Settings.
- **Persistent Preferences:** Name and device ID persist across app sessions.
- **One-Tap WhatsApp Issue Reporting:** If any networking, permission, or file error occurs, a **"Report Issue"** button opens WhatsApp with pre-filled error details sent directly to developer support (`+923412342629`).

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3 Design
- **P2P Networking:** Google Play Services Nearby Connections API
- **Local Database:** Room (SQLite) with Coroutines Flow
- **Local Web Server:** NanoHTTPD (Embedded Java HTTP server)
- **QR Code Engine:** ZXing (Zebra Crossing)

---

## 📦 Automated APK Build (GitHub Actions)

This repository includes a preconfigured GitHub Actions workflow:
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

### How to get the APK:
1. Push code to the `main` branch:
   ```bash
   git push origin main
   ```
2. Open the **Actions** tab on your GitHub repository.
3. The **Build Android APK** workflow will execute automatically on Ubuntu with JDK 17 and Gradle.
4. Once completed, download the release artifact **`cams-mesh-debug-apk`** containing `app-debug.apk`.
5. Install the APK on any Android phone (Android 7.0+ / API 24+).

---

## 👤 Author

Built with pride by **OKASHA NADEEM**.  
For support or inquiries: [WhatsApp Support](https://wa.me/923412342629)

