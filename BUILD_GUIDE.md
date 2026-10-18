# CAMS-MESH Android Project Implementation Plan

## Overview
CAMS-MESH is an offline-first peer-to-peer classroom material sharing Android application using Jetpack Compose, Room, Kotlin Coroutines/Flow, and Google Nearby Connections API.
Since local Android build tools (Gradle/Android SDK) are not installed locally, the project structure is created fully compliant with standard Gradle Android conventions, accompanied by a GitHub Actions workflow (`.github/workflows/build-apk.yml`) to automatically compile, test, and release APK artifacts upon pushing to GitHub.

## Architecture
- **Language & Runtime:** Kotlin (Target JVM 17), Android SDK (compileSdk 34/35, minSdk 24, targetSdk 34)
- **UI Framework:** Jetpack Compose + Material3 + Navigation Compose
- **Local Database:** Room Database with full entity model (`Material`, `Session`, `Peer`, `Transfer`, `Tombstone`)
- **P2P Networking:** Google Nearby Connections API (`com.google.android.gms:play-services-nearby`) with custom frame protocol, manifest sync, chunked transfer, and multi-hop relay logic
- **File Management:** Safe app-private internal storage, SHA-256 validation, path-traversal protection, and streaming file I/O
- **Clear All Data:** Complete nuclear cleanup of room DB, app-private files, session states, and tombstone records
