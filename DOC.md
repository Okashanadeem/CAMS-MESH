# CAMS-MESH — Full Product Requirements Document (PRD)
**Version:** 1.0  
**Product type:** Offline-first Android application  
**Implementation target:** Claude Code  
**Working principle:** Students and teachers share text and files locally without requiring internet access, user accounts, or a central server.

---

# 1. Product Overview

## 1.1 Product vision

CAMS-MESH is an offline classroom material-sharing application. It allows students and teachers in the same physical area to share notes, documents, images, PDFs, presentations, and other supported files through nearby devices.

A participant should be able to open the app, join a classroom, upload a file or paste text, and have that material automatically distributed to other participants in the same classroom.

Every participant maintains a local history of materials uploaded and received. Users can remove individual items or clear all locally stored application data.

The application must continue to function when:
- There is no internet connection.
- There is no school Wi-Fi.
- The school portal is unavailable.
- Teachers do not have student-portal accounts.
- Multiple students upload materials at the same time.

## 1.2 Core product goals

1. Create and join offline classroom sessions.
2. Share files from Android devices.
3. Paste text and share it as a material item.
4. Automatically distribute materials to connected classroom participants.
5. Relay materials between devices to extend distribution beyond a single direct connection.
6. Maintain a searchable history on each device.
7. Support individual deletion and a clearly defined **Clear All Data** action.
8. Require no central backend for core offline functionality.
9. Provide a simple interface usable by students and teachers without technical knowledge.

## 1.3 Non-goals for the first release

The first release does not need:
- Student portal authentication.
- Cloud storage.
- Internet-based synchronization.
- Teacher accounts.
- Chat, voice calls, or video calls.
- Online notifications.
- A website that performs the actual offline mesh networking.
- Guaranteed delivery to a phone that is disconnected and never reconnects.
- Automatic access to other devices without user permission.

---

# 2. Target Platform and Technology

## 2.1 Initial platform

Build a native Android application distributed as an APK.

Recommended stack:
- **Language:** Kotlin.
- **UI:** Jetpack Compose.
- **Architecture:** MVVM with repositories and unidirectional UI state.
- **Local database:** Room.
- **Local file storage:** App-private storage.
- **Background and deferred work:** WorkManager where appropriate, supplemented by foreground execution when an active transfer requires it.
- **Nearby networking:** Google Nearby Connections API, subject to device compatibility and Google Play Services availability.
- **Dependency injection:** Hilt or a similarly maintainable solution.
- **Asynchronous work:** Kotlin coroutines and Flow.
- **Testing:** JUnit, coroutine tests, Compose UI tests, and real-device integration tests.

Use current stable, mutually compatible Android dependencies at implementation time. Record the selected versions in the project configuration.

**Networking reference:** https://developers.google.com/nearby/connections/overview

The application must not depend on a web server or cloud database for its primary sharing workflow.

## 2.2 Offline-first requirements

All essential operations must work offline:
- Opening the application.
- Creating a classroom session.
- Joining a nearby session.
- Pasting and saving text.
- Selecting and saving files.
- Viewing history.
- Searching local history.
- Deleting individual items.
- Clearing local data.
- Synchronizing with reachable peers.

If a networking library or Google Play Services is unavailable, display a clear compatibility message and explain which functionality cannot be used. Do not silently pretend that sharing is working.

---

# 3. User Types and Permissions

## 3.1 Student

Students can:
- Create a classroom session.
- Join a session.
- Upload files.
- Paste and share text.
- View all materials received by their device.
- View their own uploaded materials.
- Search and filter their history.
- Download or export a received file.
- Delete individual items locally.
- Clear all local application data.
- Leave a classroom session.

## 3.2 Teacher or guest

Teachers do not need a student-portal account.

Teachers can:
- Open CAMS-MESH.
- Join a classroom by scanning a QR code or entering a classroom code.
- Set a display name.
- Upload files.
- Paste and share text.
- View and download available classroom materials.
- View their local history.
- Delete their own local copies and clear their own local application data.

If a teacher uses an Android device with the APK installed, their sharing experience should be substantially the same as a student's.

## 3.3 Classroom creator

The device that creates a classroom session is the initial session coordinator.

The creator can:
- Display the join QR code and room code.
- View connected participants.
- Approve or reject join requests if approval mode is enabled.
- End the local session.
- Request a classroom-wide clear operation, subject to the deletion rules in Section 11.

The creator must not become a mandatory permanent server. If the creator disconnects, connected peers should continue sharing where the networking topology and implementation allow it.

## 3.4 No mandatory accounts

Do not require email, phone number, password, or student ID.

On first launch, generate a random local device identifier and ask for a display name. The display name is not a verified identity.

Never use a display name as a security credential.

---

# 4. Main Screens and Navigation

Use a simple bottom navigation bar or equivalent navigation structure.

Required destinations:

1. **Classroom**
2. **Materials**
3. **History**
4. **Settings**

The default screen should be Classroom.

## 4.1 First launch

Display:
- CAMS-MESH logo and name.
- A short explanation: “Share classroom materials nearby, even without internet.”
- Display-name input.
- Continue button.
- A concise privacy explanation.

Ask for operating-system permissions only when the relevant feature is first used. Do not request every permission immediately on launch.

## 4.2 Classroom home screen

The screen should show:

- Current connection state.
- Current classroom name and room code, if joined.
- Number of connected peers.
- Number of materials available locally.
- **Create Classroom** button.
- **Join Classroom** button.
- **Upload File** button.
- **Paste Text** button.
- Recent materials list.
- **Leave Classroom** button when a session is active.

Connection states must be explicit:
- Offline.
- Searching for nearby devices.
- Waiting for participants.
- Connected.
- Synchronizing.
- Sending.
- Receiving.
- Transfer failed.
- Permission required.

Do not display “All students synchronized” unless the app has actual evidence that the relevant participants acknowledged the materials.

## 4.3 Create Classroom screen

Fields and controls:
- Classroom name, optional.
- Session visibility.
- Join approval setting.
- Create button.

After creation, show:
- Six-character room code.
- QR code.
- Shareable invitation information.
- Number of connected participants.
- Connection status.
- Incoming join requests, if approval is enabled.
- End Session button.

The six-character code should be randomly generated using an unambiguous character set that excludes confusing characters such as `O` and `0`.

A six-character code is for convenient discovery, not strong authentication. Use a separate random session secret in the QR/invitation where supported. Do not advertise that secret in public Bluetooth discovery metadata.

A manually entered room code should be verified against an actual nearby session. Do not imply that a code can connect devices that are out of range or have no communication path.

## 4.4 Join Classroom screen

Provide:
- Scan QR Code.
- Enter Room Code.
- Search Nearby Sessions.
- Display-name input if the user has not configured one.
- Join button.

After selecting a session:
1. Discover the intended session.
2. Establish a connection.
3. Validate session information.
4. Perform authentication or session-secret verification where supported.
5. Request creator approval if enabled.
6. Synchronize classroom metadata and material manifests.
7. Begin receiving available materials.

Show a clear error if the room cannot be found, the session has ended, the code is invalid, the host rejects the request, or permissions are missing.

---

# 5. Material Sharing

Two primary content creation methods are mandatory.

## 5.1 Upload File

When the user taps **Upload File**:

1. Open the Android system file picker.
2. Allow selection of a supported file.
3. Show file name, file type, and size.
4. Allow the user to cancel or confirm.
5. Read the file through the Android document-provider APIs.
6. Create a material record.
7. Store a safe local copy in app-private storage.
8. Calculate a content hash.
9. Add the item to local history.
10. Start distribution to reachable classroom peers.
11. Show upload and delivery progress.

Use the Android system file picker rather than broad storage permissions where possible.

### Supported file types

At minimum:
- PDF.
- TXT.
- DOC and DOCX.
- PPT and PPTX.
- XLS and XLSX.
- JPG and JPEG.
- PNG.
- WEBP.
- ZIP.

The app may accept other file types as generic attachments, subject to the configured size limit and available storage.

The app is not required to preview every format. Unknown formats can be downloaded or exported using Android's normal share/open mechanisms.

### File-size policy

Make the maximum file size configurable.

For the initial MVP:
- Default maximum: **50 MB per file**.
- Reject files larger than the configured maximum before beginning a transfer.
- Check available storage before receiving a file.
- Show the size limit in the upload interface.
- Never load an entire large file into memory merely to transfer it.
- Transfer files in bounded chunks and support retries.

A future settings screen may allow a classroom administrator to reduce the size limit.

### Upload status

Each file should display a meaningful status:
- Preparing.
- Ready to send.
- Sending.
- Received by some peers.
- Received by all currently connected peers.
- Waiting for other peers.
- Failed.

Do not label a file “delivered to everyone” merely because the local send operation completed.

## 5.2 Paste Text

When the user taps **Paste Text**, open a dedicated composition screen.

Required fields:
- Title.
- Multiline text editor.
- Optional category.
- Share button.
- Cancel button.

The title can be suggested automatically from the first line, but the user must be able to edit it.

Example:

Title: Biology — Cell Division

Text:
“Revision points for mitosis:
1. Prophase
2. Metaphase
3. Anaphase
4. Telophase”

On Share:
1. Validate that the text is not empty.
2. Validate the configured maximum text length.
3. Generate a unique material ID.
4. Save the text to the local database.
5. Add it to local history.
6. Synchronize the new material record with reachable peers.
7. Show the current delivery state.

Suggested initial limit: 100,000 characters per text item.

Support copying text to the clipboard. Do not silently copy content to the clipboard when the user merely opens an item.

Text materials must remain searchable and readable without internet access.

## 5.3 Material detail screen

Each material detail screen should display:
- Title.
- Type.
- Original filename, if applicable.
- Uploader display name.
- Date and time created.
- File size, if applicable.
- Text content or attachment details.
- Local availability.
- Distribution status.
- Copy Text button for text items.
- Open/Export button for files.
- Delete Local Copy button.

For files that are not stored locally, show Download or Request Copy if another reachable peer is known to have the file.

Do not show a functional Download button if no source is available.

---

# 6. Peer-to-Peer Networking and Synchronization

This is the most technically important part of the application.

## 6.1 Networking principle

Each device is both a consumer and a potential provider of materials.

When a device receives a new material, it stores the material and advertises its availability to reachable peers. Those peers can request and distribute it further.

However, a networking library that connects nearby devices does not automatically provide a multi-hop mesh. The application must implement synchronization, relaying, deduplication, and reconnection logic itself.

## 6.2 Peer discovery

Implement a networking service responsible for:
- Starting and stopping discovery.
- Advertising the active classroom session.
- Discovering nearby sessions.
- Tracking known peers.
- Establishing and maintaining connections.
- Handling disconnections.
- Retrying failed connections with backoff.
- Detecting duplicate peers.
- Cleaning up expired sessions.

Only synchronize material with peers that have joined the same classroom session and passed the required validation.

Use the networking API's supported discovery and connection strategies. Do not hardcode an assumption that Bluetooth alone can maintain dozens of simultaneous connections.

## 6.3 Room membership

Each session needs:
- A unique session ID.
- A room code.
- A session secret or equivalent invitation credential where applicable.
- A creator device ID.
- A creation timestamp.
- A session state.
- A membership policy.

Each joining device receives or establishes a unique session participant ID.

Room codes must not be used as the only security control for sensitive data.

## 6.4 Synchronization protocol

Implement a versioned, documented application-level protocol.

At a minimum, define message types such as:

- `HELLO`
- `JOIN_REQUEST`
- `JOIN_APPROVED`
- `JOIN_REJECTED`
- `SESSION_INFO`
- `MATERIAL_MANIFEST_REQUEST`
- `MATERIAL_MANIFEST_RESPONSE`
- `MATERIAL_METADATA`
- `MATERIAL_REQUEST`
- `TRANSFER_START`
- `TRANSFER_CHUNK`
- `TRANSFER_ACK`
- `TRANSFER_COMPLETE`
- `TRANSFER_FAILED`
- `DELETE_REQUEST`
- `DELETE_TOMBSTONE`
- `CLEAR_REQUEST`
- `HEARTBEAT`
- `ERROR`

Messages must include a protocol version and session identifier where relevant.

Validate incoming messages. Enforce size limits, supported message types, and state transitions. Do not deserialize arbitrary untrusted data into executable objects.

## 6.5 Material distribution algorithm

When a new material is created:

1. Generate a globally unique material ID, preferably UUID.
2. Save the material and metadata locally.
3. Calculate its SHA-256 content hash.
4. Add it to the local manifest.
5. Notify each eligible connected peer that a new item is available.
6. Peers compare the new item with their local manifests.
7. Peers that do not have the item request its metadata and content.
8. Transfer the content in chunks.
9. Verify the final content hash.
10. Store the verified content locally.
11. Record receipt in the local database.
12. Advertise the newly available material to eligible peers.
13. Retry failed or interrupted transfers when a viable connection returns.

Prevent infinite loops using unique material IDs, hashes, and a persistent received-item ledger.

A peer must not repeatedly retransmit the same content to another peer that already has it.

## 6.6 Relay behavior

Suppose device A creates a PDF, device B receives it, and device C is reachable only through B.

Expected behavior:
- A sends the PDF to B.
- B verifies and stores the PDF.
- B advertises the PDF to C.
- C requests and receives the PDF from B.
- C verifies the hash and stores the PDF.

This relay behavior must be implemented and tested.

Do not promise that all devices can always reach one another. Delivery depends on the network topology, supported device connections, radio availability, permissions, and whether intermediate devices remain available.

## 6.7 Disconnections and late joiners

If a device disconnects during transfer:
- Preserve verified partial-transfer metadata.
- Resume from the last valid chunk where practical.
- Verify the final hash before marking the transfer complete.
- Retry after reconnection using backoff.
- Avoid creating duplicate material records.

When a new participant joins:
- Exchange manifests.
- Compare known material IDs and hashes.
- Request missing items from reachable peers.
- Do not automatically resend items already present and verified.

A late joiner can retrieve only material that remains available on reachable peers. There is no guaranteed recovery of data that has been deleted from every device.

## 6.8 Delivery receipts

Track receipt state per material and per known peer when feasible.

Possible states:
- Created locally.
- Queued.
- Sent to peer.
- Confirmed received.
- Failed.
- Peer unavailable.
- Delivery unknown.

Distinguish:
- Sent by this device.
- Received by a particular peer.
- Available on all currently connected peers.
- Available on all known participants.

The last state can only be claimed when supported by reliable membership and acknowledgement data. In a changing, disconnected peer network, the app should prefer “delivery status unknown” over an inaccurate guarantee.

## 6.9 Resource management

The networking service must:
- Limit concurrent transfers.
- Prioritize metadata and small text items before large files.
- Avoid unbounded in-memory queues.
- Use streaming or chunked file transfer.
- Back off when connections fail.
- Expose progress and errors to the UI.
- Avoid excessive battery usage.
- Respect Android background execution limits.

Provide a foreground notification when necessary for long-running user-initiated transfers.

---

# 7. Materials and History

Every device has a persistent local history.

## 7.1 History requirements

The History screen must list both:
- Materials uploaded from this device.
- Materials received from other participants.

Each row should show:
- Title or filename.
- File/text type.
- Uploader.
- Date and time.
- File size, if applicable.
- Local availability.
- Transfer status, if applicable.

Sort by newest first by default.

## 7.2 History filters

Provide:
- All Materials.
- Uploaded by Me.
- Received.
- Text.
- Files.
- PDFs.
- Images.
- Other Files.

Include a search box that searches:
- Titles.
- Filenames.
- Text content.
- Uploader display names.

Use database-backed search where practical. For text content, implement a Room full-text search index or an equivalent solution if supported by the selected database configuration.

## 7.3 History persistence

History must survive:
- Closing and reopening the app.
- Losing internet access.
- Leaving a classroom session.
- Temporary Bluetooth or Wi-Fi disconnections.
- Device restarts.

History should not survive an explicit full local-data clear.

Do not automatically delete historical items merely because the user leaves a classroom.

## 7.4 Material availability

A history entry and its file availability are separate concepts.

If a file has been deleted locally but another reachable peer still has it, retain the metadata if appropriate and allow the user to request another copy.

If the file is not available anywhere reachable, show that the content is unavailable rather than presenting a broken attachment.

---

# 8. Local Data Model

Use Room entities and migrations.

## 8.1 Material entity

Suggested fields:

- `materialId`: UUID string, primary key.
- `sessionId`: session identifier, nullable where appropriate.
- `title`: user-facing title.
- `contentType`: TEXT or FILE.
- `mimeType`: MIME type.
- `originalFileName`: nullable.
- `localStoragePath`: nullable, internal path only.
- `textContent`: nullable.
- `fileSizeBytes`: nullable.
- `sha256Hash`: nullable until calculated.
- `uploaderDeviceId`: source device identifier.
- `uploaderDisplayName`: display name at creation.
- `createdAt`: UTC timestamp.
- `receivedAt`: nullable UTC timestamp.
- `isUploadedByLocalUser`: boolean.
- `localAvailability`: available, partial, unavailable, or deleted.
- `transferState`: queued, transferring, complete, failed, or unknown.
- `deletedAt`: nullable timestamp.
- `schemaVersion`: version metadata if required.

Do not store actual file bytes inside a single database field. Keep attachments in app-private files and store their metadata in Room.

## 8.2 Session entity

Suggested fields:
- `sessionId`.
- `roomCode`.
- `sessionName`.
- `creatorDeviceId`.
- `createdAt`.
- `sessionState`.
- `approvalMode`.
- `lastSeenAt`.

Do not persist reusable room secrets in plaintext logs or display them unnecessarily.

## 8.3 Peer entity

Suggested fields:
- `deviceId`.
- `displayName`.
- `sessionId`.
- `lastSeenAt`.
- `connectionState`.
- `lastSuccessfulSyncAt`.

Use pseudonymous device IDs rather than hardware identifiers.

## 8.4 Transfer entity

Suggested fields:
- `transferId`.
- `materialId`.
- `peerDeviceId`.
- `direction`.
- `bytesTransferred`.
- `totalBytes`.
- `transferState`.
- `retryCount`.
- `lastErrorCode`.
- `updatedAt`.

## 8.5 Tombstone entity

A tombstone records that a material was deleted or invalidated.

Suggested fields:
- `tombstoneId`.
- `materialId`.
- `sessionId`.
- `deletionScope`.
- `deletedByDeviceId`.
- `createdAt`.
- `acknowledgedByPeers`, or a separate acknowledgement table.

Tombstones are important for preventing deleted items from reappearing during later synchronization. They must be handled carefully so that a local deletion does not accidentally erase everyone else's data.

---

# 9. Clear All Data and Deletion Logic

This feature is mandatory and must be explicit about what it deletes.

## 9.1 Clear All Data button

Place **Clear All Data** in Settings.

Require a confirmation dialog with a warning:

“This will permanently delete locally stored materials, text notes, history, downloaded files, and local classroom data from this device. It will not automatically delete copies stored on other devices.”

Buttons:
- Cancel.
- Clear All Data.

Require a second confirmation or a deliberate confirmation input if the data volume or product risk warrants it.

Never execute a full clear from a single accidental tap.

## 9.2 Local clear operation

When the user confirms Clear All Data:

1. Stop active uploads and downloads.
2. Stop or leave the active session.
3. Cancel pending transfer jobs associated with the local data.
4. Close active file handles.
5. Delete local attachment files and temporary transfer files.
6. Clear material metadata and history.
7. Clear transfer records.
8. Clear peer and session state stored locally.
9. Clear local search indexes.
10. Remove local tombstones only if doing so cannot cause stale remote material to reappear in a way that violates the deletion policy.
11. Reset the UI to the initial setup screen.
12. Show confirmation that local data was cleared.

Use a transaction or an idempotent cleanup workflow where possible. If deletion fails for some files, report the failure and retry cleanup. Do not claim success while known sensitive files remain.

The implementation should account for the distinction between app-managed data and operating-system backups. Disable Android backup for sensitive local classroom content unless a deliberate backup policy is implemented.

## 9.3 Delete one material

Every material detail screen must provide **Delete Local Copy**.

For a received material:
- Delete the local attachment and local content where appropriate.
- Remove it from the active local history, or mark it deleted according to the retention design.
- Do not delete copies on other devices by default.

For a material originally uploaded by the local user:
- The default delete action still deletes the local copy only.
- If a classroom-wide deletion option is offered, label it separately and explain its scope.

## 9.4 Optional classroom-wide clear

Implement classroom-wide clear only with explicit scope and confirmation.

Provide a separate action such as **Clear Classroom Materials for Everyone**. It must not be confused with Clear All Data.

Recommended behavior:
1. The authorized creator requests a room-wide clear.
2. Connected peers validate the request and applicable permissions.
3. The request includes a unique operation ID and a session identifier.
4. Each participating device processes the deletion according to the room's policy.
5. Devices record deletion tombstones.
6. Devices acknowledge the operation.
7. When a device reconnects, it synchronizes deletion events before accepting older material manifests.

**Important limitation:** A device that is offline cannot be forced to delete its copy immediately. A disconnected or modified client may also fail to comply. The app must not claim that every copy has been erased unless it has a defensible verification mechanism.

For the MVP, it is acceptable to ship reliable local Clear All Data first and defer classroom-wide clearing behind a clearly marked future feature.

---

# 10. User Interface and Design Requirements

Use a clean, lightweight, classroom-oriented design.

## 10.1 Visual hierarchy

Prioritize:
- Upload File.
- Paste Text.
- Join/Create Classroom.
- Recent Materials.
- Connection status.

Avoid a crowded dashboard.

## 10.2 Material cards

Each material card should include:
- File-type icon.
- Title.
- Uploader.
- Time.
- File size, if applicable.
- Delivery or availability indicator.

Use neutral colors and clear status text. Do not rely on color alone to communicate state.

## 10.3 Loading and error states

Every asynchronous action needs a visible state.

Examples:
- “Searching for classrooms…”
- “Waiting for nearby devices…”
- “Preparing file…”
- “Sending to 3 connected peers…”
- “2 of 4 peers have confirmed receipt.”
- “Transfer paused because the connection was lost.”
- “No reachable peer currently has this file.”
- “Storage is insufficient to receive this file.”
- “Nearby-device permission is required.”

Do not leave the user staring at an indefinite spinner without an explanation or recovery option.

## 10.4 Accessibility

- Support Android font scaling.
- Provide content descriptions for icons.
- Maintain adequate contrast.
- Make primary controls easy to tap.
- Support screen readers.
- Do not communicate status through color alone.
- Handle long filenames and long pasted text gracefully.

---

# 11. Privacy and Security

CAMS-MESH operates in classrooms where files may contain private educational information. Security must be part of the design.

## 11.1 Data minimization

- No mandatory account.
- No cloud upload.
- No advertising SDKs in the MVP.
- No analytics requiring internet access.
- No unnecessary collection of device identifiers.
- No logging of full text content, room secrets, or file contents.
- No unnecessary storage of location data.

Some Android networking permissions may be required by the selected API. Explain these permissions accurately; do not claim that the app collects location information merely because an operating-system permission is needed for a networking feature.

## 11.2 Session security

- Validate that a peer belongs to the intended session.
- Use secure connection authentication where supported.
- Avoid trusting a room code as a complete authentication mechanism.
- Keep session secrets out of public discovery metadata.
- Reject malformed or oversized messages.
- Rate-limit repeated join attempts.
- Prevent unauthenticated peers from sending materials into a protected room.
- Use TLS-like authenticated transport or the security features provided by the selected networking API where applicable.
- Never invent a custom cryptographic protocol if a reviewed platform mechanism is available.

## 11.3 File security

- Treat incoming files as untrusted.
- Sanitize display filenames.
- Never execute incoming files.
- Prevent path traversal.
- Store attachments in app-private directories.
- Verify hashes after transfer.
- Enforce configured size limits.
- Avoid unsafe automatic opening.
- Use Android's safe document-sharing mechanisms when exporting files.

## 11.4 Data retention

All classroom content should remain local unless the user explicitly exports it.

Display a privacy note in Settings explaining:
- Where the app stores files.
- What Clear All Data deletes.
- What deleting a local copy does not delete.
- That other devices may retain previously received copies.
- That offline networking cannot guarantee deletion from disconnected devices.

---

# 12. Permissions and Android Lifecycle

Implement the permission flow for the actual Android versions targeted by the project.

Potential requirements depend on the chosen networking library and OS version and may include:
- Nearby Wi-Fi or device discovery permissions.
- Bluetooth scan/connect permissions.
- Legacy Bluetooth permissions on older Android versions where required.
- Camera permission for scanning QR codes.
- Notification permission when required for foreground transfer notifications.

Use the minimum permissions needed.

Requirements:
1. Explain the reason before requesting a permission where appropriate.
2. Handle denied and permanently denied permissions.
3. Provide a route to Android Settings if the user must enable a permission manually.
4. Stop discovery when the user leaves the session or disables sharing.
5. Handle Bluetooth/Wi-Fi being turned off.
6. Handle process death and app restart.
7. Restore local history even if networking is unavailable.
8. Never imply that transfers continue in the background if Android has stopped the service.

Background discovery and networking behavior must be validated on real devices. Android power management and vendor-specific restrictions can interrupt peer connections.

---

# 13. Technical Architecture

Use clear separation of concerns.

Recommended modules or packages:

`ui/`
- Compose screens.
- Reusable components.
- UI state and events.

`domain/`
- Material use cases.
- Session use cases.
- Synchronization policies.
- Deletion policies.
- Validation logic.

`data/local/`
- Room database.
- DAO interfaces.
- Entities.
- Migrations.
- File storage.

`data/network/`
- Nearby Connections integration.
- Discovery and advertising.
- Connection lifecycle.
- Message serialization.
- Transfer management.

`data/sync/`
- Manifest comparison.
- Deduplication.
- Relay scheduling.
- Retry management.
- Delivery receipts.
- Tombstone synchronization.

`service/`
- Foreground transfer service if needed.
- Notification handling.
- Lifecycle coordination.

`di/`
- Dependency injection.

`util/`
- Hashing.
- MIME type handling.
- Safe filename handling.
- Error mapping.

Use interfaces for networking and file storage so that the transport layer can be replaced or tested with fakes.

## 13.1 Core services

### SessionManager
Creates, joins, leaves, and tracks classroom sessions.

### PeerManager
Tracks discovered peers and active connections.

### MaterialRepository
Creates, retrieves, searches, and deletes material records.

### FileStorageManager
Stores, reads, verifies, and removes attachments.

### TransferManager
Queues, executes, retries, and reports file transfers.

### SyncEngine
Compares manifests, requests missing materials, and handles relay propagation.

### HistoryRepository
Queries and filters local history.

### DataClearManager
Performs safe local clearing and coordinates any supported deletion events.

### PermissionManager
Checks permission state and communicates missing requirements to the UI.

---

# 14. Core User Flows

## Flow A: Student creates a classroom

1. Open app.
2. Enter or confirm display name.
3. Tap Create Classroom.
4. Enter a classroom name if desired.
5. Configure join approval.
6. Tap Create.
7. Grant required nearby-device permissions.
8. Display room code and QR code.
9. Wait for participants.
10. Share files and text as needed.

## Flow B: Teacher joins without a portal account

1. Install and open the APK.
2. Enter a display name.
3. Tap Join Classroom.
4. Scan the QR code or enter the code.
5. Discover and validate the nearby session.
6. Complete any required connection confirmation.
7. Receive approval if the session requires it.
8. Synchronize the available material manifest.
9. Browse or share materials.

No student-portal login should be required.

## Flow C: Student uploads a file

1. Tap Upload File.
2. Select a file.
3. Review file details.
4. Confirm upload.
5. App stores the local copy and metadata.
6. App begins sending to eligible connected peers.
7. Peers verify and store the file.
8. Peers relay the file when appropriate.
9. Sender sees per-peer delivery status.
10. The item appears in the sender's history.

## Flow D: Student pastes text

1. Tap Paste Text.
2. Enter a title.
3. Paste or type the content.
4. Tap Share.
5. App stores the text locally.
6. App sends text metadata/content to peers.
7. Peers store it and propagate it.
8. The item appears in every receiving device's history.

## Flow E: A participant reconnects

1. The app discovers or reconnects to a peer in the same session.
2. The devices exchange manifests and synchronization metadata.
3. Each device determines which material IDs are missing.
4. Missing items are requested from reachable peers.
5. Transfer resumes or restarts safely.
6. Content hashes are verified.
7. History and delivery states update.

## Flow F: User clears all local data

1. Open Settings.
2. Tap Clear All Data.
3. Read the warning.
4. Confirm the action.
5. App stops transfers and leaves the active session.
6. App deletes locally managed content, attachments, history, and state.
7. App returns to the initial setup screen.
8. App confirms the local clear and reports any cleanup failure.

## Flow G: Delete one item

1. Open a material.
2. Tap Delete Local Copy.
3. Confirm if appropriate.
4. Delete the local content according to the retention policy.
5. Update history and availability state.
6. Do not silently delete copies on other devices.

---

# 15. Performance Requirements

The initial MVP should target:
- Smooth operation on supported mid-range Android phones.
- Responsive browsing of at least 1,000 local material records.
- Chunked transfers for large files.
- No unbounded memory usage during transfers.
- Persistent history across restarts.
- Safe retries after connection loss.
- Correct handling of duplicate materials.
- No crashes when a peer disconnects during a transfer.

Set the default file limit to 50 MB but make it configurable.

Do not promise a fixed transfer speed or a maximum supported classroom size until tested on real hardware.

For classroom tests, begin with 2 devices, then 5, then 10, and then 20 or more. Validate whether the selected networking strategy can support the intended number of simultaneous peers. If it cannot, implement controlled connection scheduling or recommend a local Wi-Fi access point as a more reliable transport.

---

# 16. MVP Acceptance Criteria

The MVP is complete only when the following are verified.

## Classroom and onboarding

- [ ] A user can configure a display name without creating an account.
- [ ] A device can create a classroom offline.
- [ ] A second device can discover and join the classroom.
- [ ] A user can join through a QR code or a valid room code.
- [ ] A teacher can join without a student-portal account.
- [ ] Invalid, expired, or unavailable sessions produce clear errors.

## Text sharing

- [ ] A user can paste or type text and assign a title.
- [ ] The text is saved locally.
- [ ] Another connected peer receives the text.
- [ ] The receiving peer can read and copy the text.
- [ ] The text remains in local history after app restart.
- [ ] Duplicate synchronization does not create duplicate history entries.

## File sharing

- [ ] A user can select a file using the system picker.
- [ ] File metadata and local content are stored correctly.
- [ ] A connected peer receives the file.
- [ ] The receiving file's hash matches the sender's hash.
- [ ] Large files are transferred without loading the full file into memory.
- [ ] Failed transfers can retry.
- [ ] Interrupted transfers do not create falsely completed attachments.

## History

- [ ] Uploaded and received items appear in history.
- [ ] History can be searched.
- [ ] Type and source filters work.
- [ ] Material detail screens show correct metadata.
- [ ] Local history persists after leaving a room and reopening the app.

## Mesh and synchronization

- [ ] A device can receive a material from a peer.
- [ ] The receiving device can relay that material to another reachable peer.
- [ ] The application prevents infinite forwarding loops.
- [ ] A reconnecting peer can request missing items.
- [ ] A peer does not request an item it already has and has verified.
- [ ] The app accurately reports partial or unknown delivery status.

## Deletion

- [ ] A user can delete an individual local copy.
- [ ] Local deletion does not falsely claim to delete remote copies.
- [ ] Clear All Data requires explicit confirmation.
- [ ] Clear All Data removes local attachments, text, history, and transfer state.
- [ ] The app handles deletion errors without falsely claiming success.
- [ ] Deleted content does not reappear because of a stale manifest when the implemented deletion policy requires it to remain deleted.
- [ ] Any classroom-wide deletion feature is explicitly separated from local clearing and documents its limitations.

## Reliability and security

- [ ] Missing permissions are handled gracefully.
- [ ] Disabling Bluetooth or Wi-Fi produces an appropriate state.
- [ ] Malformed messages are rejected.
- [ ] Unsupported or oversized files are rejected safely.
- [ ] Filenames cannot escape the app's storage directory.
- [ ] Session secrets and file contents are not written to logs.
- [ ] The app can reopen its local history without network access.

---

# 17. Testing Plan

Implement automated unit tests for:
- Material validation.
- Content hashing.
- Duplicate detection.
- Manifest comparison.
- Transfer state transitions.
- Retry policies.
- Tombstone behavior.
- Local data clearing.
- History filtering.

Implement integration tests for:
- Database persistence.
- File storage and cleanup.
- Message serialization.
- Partial transfers.
- Interrupted transfers.
- Reconnection and synchronization.

Conduct real-device tests for:
- Nearby discovery.
- Join approval.
- QR scanning.
- Two-device text sharing.
- Two-device file sharing.
- Three-device relay.
- Concurrent uploads.
- App backgrounding and foregrounding.
- Bluetooth/Wi-Fi toggling.
- Device restart.
- Insufficient storage.
- Large files.
- Full local-data clearing.
- A disconnected peer returning after a deletion event.

Simulators alone are not sufficient to validate Bluetooth and nearby-device networking.

---

# 18. Implementation Phases

## Phase 1 — Project foundation
- Create the native Android project.
- Configure dependencies.
- Implement navigation and UI theme.
- Add Room database and migrations.
- Implement app-private file storage.
- Add display-name onboarding.

**Deliverable:** An app that runs and stores local materials without networking.

## Phase 2 — Local material management
- Implement Upload File.
- Implement Paste Text.
- Implement material detail screen.
- Implement History and search.
- Implement individual deletion.
- Implement Clear All Data.

**Deliverable:** Complete local material management with reliable persistence and cleanup.

## Phase 3 — Basic peer sharing
- Integrate Nearby Connections.
- Implement discovery and connection management.
- Create and join sessions.
- Implement session validation.
- Share text and files between two devices.
- Add progress and error states.

**Deliverable:** Working offline sharing between two real Android devices.

## Phase 4 — Synchronization and relay
- Implement manifest exchange.
- Implement content hashes and deduplication.
- Implement transfer chunks and acknowledgements.
- Implement retries and reconnect behavior.
- Implement multi-hop relay logic.
- Implement tombstones and deletion synchronization as supported.

**Deliverable:** Three or more devices can share and relay materials with verified synchronization behavior.

## Phase 5 — Reliability and release
- Add foreground transfer behavior where needed.
- Review permissions and privacy.
- Optimize database queries and storage cleanup.
- Test across Android versions and device manufacturers.
- Run classroom-scale tests.
- Generate a signed-release-ready APK configuration and document how to build it.

**Deliverable:** A testable APK and complete implementation documentation.

---

# 19. Claude Code Implementation Instructions

Build the actual application, not just a design mockup or a written architecture.

Before implementation:
1. Inspect the repository and identify existing code.
2. If it is empty, initialize a native Android project.
3. Write an implementation plan and identify the networking API's platform constraints.
4. Choose and document compatible dependencies.
5. Establish the data model, storage policy, and synchronization protocol before implementing mesh behavior.

During implementation:
- Use Kotlin and Jetpack Compose.
- Keep the project modular and testable.
- Implement real working buttons and callbacks.
- Do not substitute simulated peers or fake transfer progress for real networking in the production path.
- If networking credentials, platform services, or device permissions are unavailable, show a genuine blocked state and document it.
- Use mock networking only in unit tests or a clearly labeled development mode.
- Implement local text/file management before adding peer networking.
- Implement two-device transfer before implementing relaying.
- Write tests for each phase.
- Do not claim multi-hop mesh support until a three-device relay test passes.
- Do not claim classroom-wide deletion until the deletion protocol and offline reconnection behavior have been tested.

At completion, provide:
1. The full source code.
2. Build and installation instructions.
3. A list of implemented features.
4. A list of incomplete or hardware-dependent features.
5. The selected networking architecture and its limitations.
6. Test results.
7. Instructions for producing the APK.
8. A manual real-device testing checklist.

If a feature cannot be completed with the selected Android networking API, explain the limitation and propose an alternative rather than silently replacing the requirement with simulated behavior.

---

# 20. Final Product Principle

CAMS-MESH must remain useful without internet access. The local database is the source of truth for each device's own history; nearby devices exchange materials and synchronization metadata when connections are available.

The application must be honest about connectivity, delivery, and deletion. A device cannot receive a file without a communication path to a device that has it, and an offline device cannot be guaranteed to delete a copy remotely.

**The first release priority is: reliable local storage, text and file sharing between two devices, persistent history, and a dependable Clear All Data action.** Once those work correctly, implement verified relay and classroom-scale synchronization.