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

    private fun servePortalHtml(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>External User Material Upload - CAMS-MESH</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f8fafc; color: #0f172a; margin: 0; padding: 20px; }
                    .container { max-width: 500px; margin: 20px auto; background: white; padding: 28px; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }
                    .badge { display: inline-block; background: #e0f2fe; color: #0369a1; padding: 4px 10px; border-radius: 20px; font-size: 12px; font-weight: 600; margin-bottom: 12px; }
                    h1 { font-size: 22px; margin-top: 0; color: #1e293b; }
                    p { font-size: 14px; color: #64748b; margin-bottom: 24px; line-height: 1.4; }
                    label { display: block; font-weight: 600; font-size: 13px; margin-bottom: 6px; color: #334155; }
                    input[type="text"], textarea, input[type="file"] { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1.5px solid #cbd5e1; border-radius: 8px; font-size: 14px; margin-bottom: 18px; outline: none; }
                    input:focus, textarea:focus { border-color: #2563eb; }
                    button { width: 100%; background: #2563eb; color: white; padding: 14px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
                    button:hover { background: #1d4ed8; }
                </style>
            </head>
            <body>
                <div class="container">
                    <span class="badge">Offline Mesh Network</span>
                    <h1>External User Upload</h1>
                    <p>No app installation required. Any file (PDF, slide, doc, picture) or text note uploaded here is instantly shared across the mesh.</p>
                    
                    <form action="/upload" method="post" enctype="multipart/form-data">
                        <label for="title">Title (Optional)</label>
                        <input type="text" id="title" name="title" placeholder="e.g. Lecture Notes">

                        <label for="file">Select File</label>
                        <input type="file" id="file" name="file">

                        <label for="note">Or Paste Text / Note</label>
                        <textarea id="note" name="note" rows="4" placeholder="Type or paste questions, instructions, or notes here..."></textarea>

                        <button type="submit">Broadcast to Users</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
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
