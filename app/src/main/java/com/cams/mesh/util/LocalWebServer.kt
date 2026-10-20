package com.cams.mesh.util

import android.content.Context
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.data.repository.MaterialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class LocalWebServer(
    private val context: Context,
    private val materialRepository: MaterialRepository,
    private val onMaterialUploaded: (MaterialEntity) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(port: Int = 8080): Int {
        if (isRunning) return serverSocket?.localPort ?: port
        try {
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port))
            serverSocket = server
            isRunning = true

            scope.launch {
                while (isRunning) {
                    try {
                        val clientSocket = server.accept()
                        scope.launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            return server.localPort
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun isServerRunning(): Boolean = isRunning

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return "127.0.0.1"
            
            // Prioritize hotspot and Wi-Fi interfaces first
            val sortedInterfaces = interfaces.sortedByDescending { intf ->
                val name = intf.name.lowercase()
                when {
                    name.startsWith("ap") || name.startsWith("wlan") || name.startsWith("swlan") || name.startsWith("rndis") -> 2
                    !intf.isLoopback && intf.isUp -> 1
                    else -> 0
                }
            }

            for (intf in sortedInterfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val input = BufferedInputStream(s.getInputStream())
                val output = BufferedOutputStream(s.getOutputStream())

                // Parse request line and headers
                val headerBytes = ByteArrayOutputStream()
                var prev = -1
                var b: Int
                var endOfHeaders = false

                // Read until \r\n\r\n
                val buffer = ByteArray(4096)
                var bytesRead = 0
                val stream = ByteArrayOutputStream()
                
                // Read headers safely
                var lastFour = 0
                while (input.read().also { b = it } != -1) {
                    headerBytes.write(b)
                    lastFour = (lastFour shl 8) or (b and 0xFF)
                    if (lastFour == 0x0D0A0D0A) { // \r\n\r\n
                        break
                    }
                }

                val headerText = headerBytes.toString("UTF-8")
                val lines = headerText.split("\r\n")
                if (lines.isEmpty() || lines[0].isBlank()) return

                val requestLine = lines[0].split(" ")
                val method = if (requestLine.isNotEmpty()) requestLine[0] else "GET"
                val path = if (requestLine.size > 1) requestLine[1] else "/"

                var contentLength = 0
                var contentType = ""
                for (line in lines) {
                    val lower = line.lowercase()
                    if (lower.startsWith("content-length:")) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    } else if (lower.startsWith("content-type:")) {
                        contentType = line.substring(13).trim()
                    }
                }

                if (method.equals("POST", ignoreCase = true) && path.startsWith("/upload")) {
                    handlePostUpload(contentType, contentLength, input, output)
                } else if (method.equals("GET", ignoreCase = true) && path.startsWith("/download")) {
                    handleDownload(path, output)
                } else if ((method.equals("POST", ignoreCase = true) || method.equals("GET", ignoreCase = true)) && path.startsWith("/delete")) {
                    handleDeleteMaterial(path, output)
                } else {
                    servePortalHtml(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handlePostUpload(
        contentType: String,
        contentLength: Int,
        input: InputStream,
        output: OutputStream
    ) {
        if (!contentType.contains("multipart/form-data")) {
            sendResponse(output, 400, "text/plain", "Bad Request: multipart form required".toByteArray())
            return
        }

        val boundary = contentType.split("boundary=").getOrNull(1)?.trim() ?: run {
            sendResponse(output, 400, "text/plain", "Missing boundary".toByteArray())
            return
        }

        // Read remaining body
        val bodyBytes = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val r = input.read(bodyBytes, totalRead, contentLength - totalRead)
            if (r == -1) break
            totalRead += r
        }

        // Parse multipart form
        val bodyString = String(bodyBytes, Charsets.ISO_8859_1)
        val parts = bodyString.split("--$boundary")

        var title = "External Material"
        var noteText = ""
        var fileName = ""
        var fileData: ByteArray? = null

        for (part in parts) {
            if (part.contains("name=\"title\"")) {
                val value = part.split("\r\n\r\n").getOrNull(1)?.substringBefore("\r\n") ?: ""
                if (value.isNotBlank()) title = value.trim()
            } else if (part.contains("name=\"note\"")) {
                val value = part.split("\r\n\r\n").getOrNull(1)?.substringBefore("\r\n") ?: ""
                noteText = value.trim()
            } else if (part.contains("name=\"file\"") && part.contains("filename=\"")) {
                val match = Regex("filename=\"([^\"]+)\"").find(part)
                fileName = match?.groupValues?.get(1) ?: "uploaded_file"
                
                val headerEndIndex = part.indexOf("\r\n\r\n")
                if (headerEndIndex != -1 && fileName.isNotBlank()) {
                    val rawContent = part.substring(headerEndIndex + 4).removeSuffix("\r\n")
                    if (rawContent.isNotEmpty()) {
                        fileData = rawContent.toByteArray(Charsets.ISO_8859_1)
                    }
                }
            }
        }

        scope.launch {
            try {
                if (fileData != null && fileData.isNotEmpty() && fileName.isNotBlank()) {
                    // Save as file material
                    val fileStorage = FileStorageManager(context)
                    val temp = File(context.cacheDir, "web_${System.currentTimeMillis()}_$fileName")
                    temp.writeBytes(fileData)
                    val hash = HashUtil.calculateSha256(temp)
                    val materialId = UUID.randomUUID().toString()
                    val target = fileStorage.finalizeReceivedFile(temp, materialId, fileName, hash)

                    val entity = MaterialEntity(
                        materialId = materialId,
                        sessionId = null,
                        title = if (title.isNotBlank() && title != "External Material") title else fileName,
                        contentType = "FILE",
                        mimeType = getMimeType(fileName),
                        originalFileName = fileName,
                        localStoragePath = target.absolutePath,
                        textContent = null,
                        fileSizeBytes = fileData.size.toLong(),
                        sha256Hash = hash,
                        uploaderDeviceId = "external_web",
                        uploaderDisplayName = "External User (Web Portal)",
                        createdAt = System.currentTimeMillis(),
                        receivedAt = System.currentTimeMillis(),
                        isUploadedByLocalUser = true,
                        localAvailability = "AVAILABLE",
                        transferState = "COMPLETE"
                    )
                    materialRepository.insertMaterialDirect(entity)
                    onMaterialUploaded(entity)
                } else if (noteText.isNotBlank()) {
                    // Save as text material
                    val material = materialRepository.createTextMaterial(
                        title = title,
                        content = noteText,
                        sessionId = null,
                        localDeviceId = "external_web",
                        localDisplayName = "External User (Web Portal)"
                    )
                    onMaterialUploaded(material)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val successHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Uploaded Successfully - CAMS-MESH</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0fdf4; color: #166534; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                    .card { background: white; border-radius: 16px; padding: 32px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); text-align: center; max-width: 440px; width: 100%; }
                    h2 { color: #15803d; margin-top: 0; }
                    p { color: #374151; font-size: 15px; line-height: 1.5; }
                    .credit { font-weight: bold; color: #1e3a8a; margin-top: 16px; font-size: 14px; }
                    a { display: inline-block; margin-top: 24px; background: #2563eb; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>✓ Material Uploaded!</h2>
                    <p>Your content has been received and uploaded to CAMS-MESH.</p>
                    <div class="credit">CAMS-MESH an app built by OKASHA NADEEM</div>
                    <a href="/">Upload Another File</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendResponse(output, 200, "text/html; charset=UTF-8", successHtml.toByteArray(Charsets.UTF_8))
    }

    private fun handleDownload(path: String, output: OutputStream) {
        try {
            val query = if (path.contains("?")) path.substringAfter("?") else ""
            val id = query.split("&")
                .firstOrNull { it.startsWith("id=") }
                ?.substringAfter("id=")
                ?.trim() ?: ""

            if (id.isBlank()) {
                sendResponse(output, 400, "text/plain", "Missing id parameter".toByteArray())
                return
            }

            kotlinx.coroutines.runBlocking {
                val material = materialRepository.getMaterialById(id)
                if (material == null) {
                    sendResponse(output, 404, "text/plain", "Material not found".toByteArray())
                    return@runBlocking
                }

                if (material.contentType == "TEXT") {
                    val textBytes = (material.textContent ?: "").toByteArray(Charsets.UTF_8)
                    val statusMsg = "OK"
                    val header = "HTTP/1.1 200 $statusMsg\r\n" +
                            "Content-Type: text/plain; charset=UTF-8\r\n" +
                            "Content-Length: ${textBytes.size}\r\n" +
                            "Content-Disposition: inline; filename=\"${material.title.replace("\"", "")}.txt\"\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(textBytes)
                    output.flush()
                    return@runBlocking
                }

                val filePath = material.localStoragePath
                if (filePath.isNullOrBlank()) {
                    sendResponse(output, 404, "text/plain", "File path missing".toByteArray())
                    return@runBlocking
                }

                val file = File(filePath)
                if (!file.exists() || !file.canRead()) {
                    sendResponse(output, 404, "text/plain", "File not available on disk".toByteArray())
                    return@runBlocking
                }

                val mime = material.mimeType ?: getMimeType(material.originalFileName ?: file.name)
                val safeFileName = (material.originalFileName ?: file.name).replace("\"", "")
                val fileLength = file.length()

                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $mime\r\n" +
                        "Content-Length: $fileLength\r\n" +
                        "Content-Disposition: attachment; filename=\"$safeFileName\"\r\n" +
                        "Connection: close\r\n\r\n"

                output.write(header.toByteArray(Charsets.UTF_8))

                file.inputStream().use { fileIn ->
                    val buf = ByteArray(16384)
                    var r: Int
                    while (fileIn.read(buf).also { r = it } != -1) {
                        output.write(buf, 0, r)
                    }
                }
                output.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sendResponse(output, 500, "text/plain", "Internal server error".toByteArray())
            } catch (_: Exception) {}
        }
    }

    private fun handleDeleteMaterial(path: String, output: OutputStream) {
        try {
            val query = if (path.contains("?")) path.substringAfter("?") else ""
            val id = query.split("&")
                .firstOrNull { it.startsWith("id=") }
                ?.substringAfter("id=")
                ?.trim() ?: ""

            if (id.isBlank()) {
                sendResponse(output, 400, "text/plain", "Missing id parameter".toByteArray())
                return
            }

            kotlinx.coroutines.runBlocking {
                val material = materialRepository.getMaterialById(id)
                if (material != null) {
                    materialRepository.deleteLocalCopy(id, "external_web")
                }
            }

            // Redirect back to home
            val redirectHeader = "HTTP/1.1 303 See Other\r\n" +
                    "Location: /\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(redirectHeader.toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sendResponse(output, 500, "text/plain", "Internal server error".toByteArray())
            } catch (_: Exception) {}
        }
    }

    private fun servePortalHtml(output: OutputStream) {
        val activeMaterials = try {
            kotlinx.coroutines.runBlocking {
                materialRepository.getAllActiveMaterials()
            }
        } catch (_: Exception) {
            emptyList<MaterialEntity>()
        }

        val materialsHtml = StringBuilder()
        if (activeMaterials.isEmpty()) {
            materialsHtml.append("""
                <div class="empty-state">
                    <p>No materials or notes broadcasted yet. When the host or peers share documents or notes, they will appear here in real-time.</p>
                </div>
            """.trimIndent())
        } else {
            materialsHtml.append("""<div class="material-grid">""")
            for (m in activeMaterials) {
                val isText = m.contentType == "TEXT"
                val titleEscaped = escapeHtml(m.title)
                val senderEscaped = escapeHtml(m.uploaderDisplayName)
                val typeBadge = if (isText) "Text Note" else "File"
                val badgeClass = if (isText) "badge-text" else "badge-file"

                materialsHtml.append("""
                    <div class="material-card">
                        <div class="card-header">
                            <span class="type-tag $badgeClass">$typeBadge</span>
                            <span class="card-sender">$senderEscaped</span>
                        </div>
                        <h3 class="card-title">$titleEscaped</h3>
                """.trimIndent())

                if (isText && !m.textContent.isNullOrBlank()) {
                    val preview = escapeHtml(m.textContent.take(280))
                    val ellipsis = if (m.textContent.length > 280) "..." else ""
                    materialsHtml.append("""
                        <div class="card-preview">$preview$ellipsis</div>
                        <div class="card-actions">
                            <a href="/download?id=${m.materialId}" class="btn btn-outline" download="${titleEscaped}.txt">Download Note</a>
                            <a href="/delete?id=${m.materialId}" class="btn btn-danger" onclick="return confirm('Are you sure you want to remove this item?')">Remove</a>
                        </div>
                    """.trimIndent())
                } else {
                    val sizeStr = formatFileSize(m.fileSizeBytes)
                    val fileName = escapeHtml(m.originalFileName ?: "file")
                    materialsHtml.append("""
                        <div class="card-file-info">
                            <span class="file-name" title="$fileName">$fileName</span>
                            <span class="file-size">$sizeStr</span>
                        </div>
                        <div class="card-actions">
                            <a href="/download?id=${m.materialId}" class="btn btn-primary" download>Download File</a>
                            <a href="/delete?id=${m.materialId}" class="btn btn-danger" onclick="return confirm('Are you sure you want to remove this file?')">Remove</a>
                        </div>
                    """.trimIndent())
                }

                materialsHtml.append("</div>")
            }
            materialsHtml.append("</div>")
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>CAMS-MESH Portal</title>
                <style>
                    :root {
                        --primary: #2563eb;
                        --primary-hover: #1d4ed8;
                        --bg: #f8fafc;
                        --card-bg: #ffffff;
                        --text: #0f172a;
                        --text-muted: #64748b;
                        --border: #e2e8f0;
                    }
                    * { box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        margin: 0;
                        padding: 16px;
                    }
                    .top-bar {
                        max-width: 900px;
                        margin: 0 auto 20px auto;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 12px 18px;
                        background: #ffffff;
                        border-radius: 12px;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                    }
                    .brand {
                        font-weight: 800;
                        font-size: 18px;
                        color: var(--primary);
                        letter-spacing: -0.5px;
                    }
                    .brand-credit {
                        font-size: 11px;
                        color: var(--text-muted);
                        display: block;
                    }
                    .mesh-badge {
                        background: #dbeafe;
                        color: #1e40af;
                        padding: 4px 10px;
                        border-radius: 20px;
                        font-size: 12px;
                        font-weight: 600;
                    }
                    .main-layout {
                        max-width: 900px;
                        margin: 0 auto;
                        display: grid;
                        grid-template-columns: 1fr;
                        gap: 24px;
                    }
                    @media (min-width: 768px) {
                        .main-layout {
                            grid-template-columns: 340px 1fr;
                        }
                    }
                    .panel {
                        background: var(--card-bg);
                        border-radius: 16px;
                        padding: 24px;
                        box-shadow: 0 4px 16px rgba(0,0,0,0.04);
                        border: 1px solid var(--border);
                        height: fit-content;
                    }
                    h2 {
                        font-size: 18px;
                        margin: 0 0 8px 0;
                        color: var(--text);
                    }
                    p.desc {
                        font-size: 13px;
                        color: var(--text-muted);
                        margin-top: 0;
                        margin-bottom: 20px;
                        line-height: 1.4;
                    }
                    label {
                        display: block;
                        font-size: 12px;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        color: #475569;
                        margin-bottom: 6px;
                    }
                    input[type="text"], textarea, input[type="file"] {
                        width: 100%;
                        padding: 10px 12px;
                        border: 1px solid var(--border);
                        border-radius: 8px;
                        font-size: 14px;
                        margin-bottom: 16px;
                        outline: none;
                        font-family: inherit;
                        transition: border-color 0.15s;
                    }
                    input:focus, textarea:focus {
                        border-color: var(--primary);
                    }
                    .btn {
                        display: inline-block;
                        text-align: center;
                        text-decoration: none;
                        padding: 10px 18px;
                        border-radius: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        border: none;
                        transition: background 0.15s;
                    }
                    .btn-primary {
                        background: var(--primary);
                        color: white;
                        width: 100%;
                    }
                    .btn-primary:hover {
                        background: var(--primary-hover);
                    }
                    .btn-outline {
                        background: #f1f5f9;
                        color: #1e293b;
                        width: 100%;
                        border: 1px solid var(--border);
                    }
                    .btn-outline:hover {
                        background: #e2e8f0;
                    }
                    .btn-danger {
                        background: #fee2e2;
                        color: #dc2626;
                        border: 1px solid #fecaca;
                        padding: 10px 14px;
                    }
                    .btn-danger:hover {
                        background: #fecaca;
                    }
                    .card-actions {
                        display: flex;
                        gap: 8px;
                        align-items: center;
                    }
                    .card-actions .btn {
                        flex: 1;
                    }
                    .card-actions .btn-danger {
                        flex: 0 0 auto;
                    }
                    .material-grid {
                        display: flex;
                        flex-direction: column;
                        gap: 12px;
                    }
                    .material-card {
                        background: #ffffff;
                        border: 1px solid var(--border);
                        border-radius: 12px;
                        padding: 16px;
                        transition: transform 0.15s, box-shadow 0.15s;
                    }
                    .material-card:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 4px 12px rgba(0,0,0,0.06);
                    }
                    .card-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 8px;
                    }
                    .type-tag {
                        font-size: 11px;
                        font-weight: 700;
                        text-transform: uppercase;
                        padding: 2px 8px;
                        border-radius: 6px;
                    }
                    .badge-file { background: #e0f2fe; color: #0284c7; }
                    .badge-text { background: #fef3c7; color: #d97706; }
                    .card-sender {
                        font-size: 12px;
                        color: var(--text-muted);
                        max-width: 140px;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .card-title {
                        font-size: 16px;
                        margin: 0 0 10px 0;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .card-preview {
                        background: #f8fafc;
                        padding: 10px;
                        border-radius: 6px;
                        font-size: 13px;
                        color: #334155;
                        white-space: pre-wrap;
                        word-break: break-word;
                        margin-bottom: 12px;
                        border-left: 3px solid #cbd5e1;
                        max-height: 100px;
                        overflow: hidden;
                        display: -webkit-box;
                        -webkit-line-clamp: 3;
                        -webkit-box-orient: vertical;
                    }
                    .card-file-info {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        font-size: 13px;
                        color: var(--text-muted);
                        margin-bottom: 12px;
                        gap: 8px;
                    }
                    .file-name {
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        flex: 1;
                    }
                    .file-size {
                        white-space: nowrap;
                        flex-shrink: 0;
                    }
                    .empty-state {
                        text-align: center;
                        padding: 40px 20px;
                        color: var(--text-muted);
                        font-size: 14px;
                    }
                    footer {
                        max-width: 900px;
                        margin: 32px auto 16px auto;
                        text-align: center;
                        font-size: 12px;
                        color: var(--text-muted);
                    }
                </style>
            </head>
            <body>
                <div class="top-bar">
                    <div>
                        <span class="brand">CAMS-MESH</span>
                        <span class="brand-credit">Built by OKASHA NADEEM</span>
                    </div>
                    <span class="mesh-badge">Two-Way Web Portal</span>
                </div>

                <div class="main-layout">
                    <!-- Column 1: Upload / Broadcast to App -->
                    <div class="panel">
                        <h2>Upload to Mesh</h2>
                        <p class="desc">Send documents, images, slides, or notes from your browser directly into the classroom mesh.</p>
                        
                        <form action="/upload" method="post" enctype="multipart/form-data">
                            <label for="title">Title (Optional)</label>
                            <input type="text" id="title" name="title" placeholder="e.g. Chapter 4 Reference">

                            <label for="file">Select File</label>
                            <input type="file" id="file" name="file">

                            <label for="note">Or Enter Note / Text</label>
                            <textarea id="note" name="note" rows="3" placeholder="Write or paste your note here..."></textarea>

                            <button type="submit" class="btn btn-primary">Send to App Users</button>
                        </form>
                    </div>

                    <!-- Column 2: Available Materials from App Users -->
                    <div class="panel">
                        <h2>Available Materials (${activeMaterials.size})</h2>
                        <p class="desc">Materials shared by the host and room peers are available for direct download below:</p>
                        $materialsHtml
                    </div>
                </div>

                <footer>
                    CAMS-MESH an app built by OKASHA NADEEM &bull; Decentralized Offline Classroom Mesh
                </footer>
            </body>
            </html>
        """.trimIndent()

        sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }

    private fun sendResponse(output: OutputStream, status: Int, contentType: String, data: ByteArray) {
        val statusMsg = if (status == 200) "OK" else "Error"
        val header = "HTTP/1.1 $status $statusMsg\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(data)
        output.flush()
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            fileName.endsWith(".docx", ignoreCase = true) || fileName.endsWith(".doc", ignoreCase = true) -> "application/msword"
            fileName.endsWith(".pptx", ignoreCase = true) || fileName.endsWith(".ppt", ignoreCase = true) -> "application/vnd.ms-powerpoint"
            else -> "application/octet-stream"
        }
    }
}
