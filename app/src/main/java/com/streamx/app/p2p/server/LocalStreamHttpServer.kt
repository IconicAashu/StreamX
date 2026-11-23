package com.streamx.app.p2p.server

import com.streamx.app.utils.StreamXLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * High-performance, loopback-only HTTP streaming server for Media3 ExoPlayer.
 * Binds strictly to 127.0.0.1 (never exposed to public network interfaces).
 * Implements HTTP 1.1 byte-range requests (RFC 7233) for seamless seeking and buffering.
 */
class LocalStreamHttpServer(
    private val dataProvider: (torrentId: String, fileIndex: Int, offset: Long, length: Int) -> ByteArray?,
    private val fileInfoProvider: (torrentId: String, fileIndex: Int) -> Pair<Long, String>?, // totalSize to mimeType
    private val onSeekNotifier: (torrentId: String, fileIndex: Int, offset: Long) -> Unit = { _, _, _ -> }
) {
    companion object {
        private const val TAG = "LocalHttpServer"
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB read buffer
    }

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private val isRunning = AtomicBoolean(false)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    var port: Int = 0
        private set

    @Synchronized
    fun start(): Int {
        if (isRunning.get()) return port

        val loopback = InetAddress.getByName("127.0.0.1")
        // Bind to port 0 to allocate an available ephemeral port on loopback
        val socket = ServerSocket(0, 50, loopback)
        serverSocket = socket
        port = socket.localPort

        executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "StreamX-HttpStream-${port}").apply { isDaemon = true }
        }

        isRunning.set(true)
        StreamXLog.i(TAG, "Local loopback HTTP server started on http://127.0.0.1:$port")

        executor?.execute {
            while (isRunning.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    activeSockets.add(client)
                    executor?.execute {
                        try {
                            handleClient(client)
                        } finally {
                            activeSockets.remove(client)
                            try { client.close() } catch (_: Exception) {}
                        }
                    }
                } catch (e: SocketException) {
                    if (isRunning.get()) {
                        StreamXLog.w(TAG, "Server socket exception: ${e.message}")
                    }
                } catch (e: Exception) {
                    StreamXLog.e(TAG, "Unexpected error accepting connection: ${e.message}")
                }
            }
        }

        return port
    }

    @Synchronized
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        for (client in activeSockets) {
            try { client.close() } catch (_: Exception) {}
        }
        activeSockets.clear()

        executor?.shutdownNow()
        executor = null
        StreamXLog.i(TAG, "Local loopback HTTP server stopped")
    }

    private fun handleClient(socket: Socket) {
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = socket.getOutputStream()

        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return

        val method = parts[0].uppercase()
        val uri = parts[1]

        if (method != "GET" && method != "HEAD") {
            sendError(output, 405, "Method Not Allowed")
            return
        }

        // Parse headers
        var rangeHeader: String? = null
        var line: String?
        while (input.readLine().also { line = it } != null) {
            if (line.isNullOrBlank()) break
            val colon = line!!.indexOf(':')
            if (colon > 0) {
                val headerName = line!!.substring(0, colon).trim().lowercase()
                val headerVal = line!!.substring(colon + 1).trim()
                if (headerName == "range") {
                    rangeHeader = headerVal
                }
            }
        }

        // Parse query params: /stream?id=...&file=...
        val pathAndQuery = uri.split('?', limit = 2)
        if (!pathAndQuery[0].endsWith("/stream")) {
            sendError(output, 404, "Not Found")
            return
        }

        var torrentId: String? = null
        var fileIndex = 0

        if (pathAndQuery.size > 1) {
            for (param in pathAndQuery[1].split('&')) {
                val kv = param.split('=', limit = 2)
                if (kv.size == 2) {
                    when (kv[0].lowercase()) {
                        "id" -> torrentId = kv[1]
                        "file" -> fileIndex = kv[1].toIntOrNull() ?: 0
                    }
                }
            }
        }

        if (torrentId == null) {
            sendError(output, 400, "Missing torrent id parameter")
            return
        }

        val fileInfo = fileInfoProvider(torrentId, fileIndex)
        if (fileInfo == null) {
            sendError(output, 404, "Torrent or file not found")
            return
        }

        val totalSize = fileInfo.first
        val mimeType = fileInfo.second

        // Handle Range header (e.g. bytes=0- or bytes=1048576-2097151)
        var startByte = 0L
        var endByte = totalSize - 1

        if (rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val rangeSpec = rangeHeader.substring("bytes=".length).trim()
            val dashIndex = rangeSpec.indexOf('-')
            if (dashIndex != -1) {
                val startStr = rangeSpec.substring(0, dashIndex).trim()
                val endStr = rangeSpec.substring(dashIndex + 1).trim()

                if (startStr.isNotEmpty()) {
                    startByte = startStr.toLongOrNull() ?: 0L
                }
                if (endStr.isNotEmpty()) {
                    endByte = endStr.toLongOrNull() ?: (totalSize - 1)
                }
            }

            if (startByte > endByte || startByte >= totalSize) {
                sendRangeNotSatisfiable(output, totalSize)
                return
            }

            endByte = min(endByte, totalSize - 1)

            // Notify piece scheduler of seek
            onSeekNotifier(torrentId, fileIndex, startByte)

            // Send 206 Partial Content
            val contentLength = endByte - startByte + 1
            val headers = buildString {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Type: $mimeType\r\n")
                append("Content-Length: $contentLength\r\n")
                append("Content-Range: bytes $startByte-$endByte/$totalSize\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.US_ASCII))
            output.flush()

            if (method == "GET") {
                streamBytes(output, torrentId, fileIndex, startByte, endByte)
            }
        } else {
            // Full content (200 OK)
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mimeType\r\n")
                append("Content-Length: $totalSize\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.US_ASCII))
            output.flush()

            if (method == "GET") {
                streamBytes(output, torrentId, fileIndex, 0L, totalSize - 1)
            }
        }
    }

    private fun streamBytes(output: OutputStream, torrentId: String, fileIndex: Int, startByte: Long, endByte: Long) {
        var currentOffset = startByte
        val totalToStream = endByte - startByte + 1
        var streamed = 0L

        while (streamed < totalToStream && isRunning.get()) {
            val lengthToRead = min(CHUNK_SIZE.toLong(), totalToStream - streamed).toInt()
            val chunk = dataProvider(torrentId, fileIndex, currentOffset, lengthToRead)

            if (chunk != null && chunk.isNotEmpty()) {
                try {
                    output.write(chunk)
                    output.flush()
                    streamed += chunk.size
                    currentOffset += chunk.size
                } catch (e: SocketException) {
                    // Client disconnected (e.g. seek in player)
                    break
                }
            } else {
                // Buffer under-run: pause briefly and retry up to limit
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun sendRangeNotSatisfiable(output: OutputStream, totalSize: Long) {
        val response = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                "Content-Range: bytes */$totalSize\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        val body = "{\"error\": \"$message\"}"
        val response = "HTTP/1.1 $code $message\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n$body"
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    fun getStreamUrl(torrentId: String, fileIndex: Int): String {
        return "http://127.0.0.1:$port/stream?id=$torrentId&file=$fileIndex"
    }
}
