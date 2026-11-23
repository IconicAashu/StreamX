package com.streamx.app.p2p.parser

import com.streamx.app.p2p.model.TorrentFileEntry
import com.streamx.app.p2p.model.TorrentMetadata
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Safe, robust Bencode decoder and encoder with security guards:
 * - Recursion depth limit (protects against stack overflow DOS)
 * - Path traversal validation (rejects '..' and leading '/')
 * - Raw info dictionary slice extraction for accurate SHA-1 info_hash calculation
 */
object BencodeParser {

    private const val MAX_DEPTH = 32
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "mov", "ts", "m4v", "flv", "wmv", "vob")

    fun parseTorrent(bytes: ByteArray): Result<TorrentMetadata> {
        return try {
            val stream = bytes.inputStream()
            val parsed = decode(stream, 0) as? Map<String, Any?>
                ?: return Result.failure(IllegalArgumentException("Invalid torrent format: root must be a bencode dictionary"))

            val infoMap = parsed["info"] as? Map<String, Any?>
                ?: return Result.failure(IllegalArgumentException("Torrent missing required 'info' dictionary"))

            // Extract raw bytes of 'info' dictionary to compute SHA-1 info_hash
            val rawInfoBytes = extractInfoBytes(bytes)
                ?: return Result.failure(IllegalArgumentException("Failed to locate raw 'info' dictionary for info_hash"))

            val infoHash = sha1Hex(rawInfoBytes)

            val name = (infoMap["name.utf-8"] as? String)
                ?: (infoMap["name"] as? String)
                ?: "Torrent"

            val pieceLength = (infoMap["piece length"] as? Number)?.toInt()
                ?: return Result.failure(IllegalArgumentException("Torrent missing required 'piece length'"))

            val piecesBytes = when (val p = infoMap["pieces"]) {
                is ByteArray -> p
                is String -> p.toByteArray(StandardCharsets.ISO_8859_1)
                else -> null
            } ?: return Result.failure(IllegalArgumentException("Torrent missing required 'pieces' byte array"))

            val pieceCount = piecesBytes.size / 20

            val isPrivate = ((infoMap["private"] as? Number)?.toInt() ?: 0) == 1
            val creationDate = (parsed["creation date"] as? Number)?.toLong()

            // Collect trackers
            val trackers = mutableListOf<String>()
            (parsed["announce"] as? String)?.takeIf { it.isNotBlank() }?.let { trackers.add(it) }
            val announceList = parsed["announce-list"] as? List<*>
            announceList?.forEach { tier ->
                (tier as? List<*>)?.forEach { tr ->
                    (tr as? String)?.takeIf { it.isNotBlank() && !trackers.contains(it) }?.let { trackers.add(it) }
                }
            }

            // Parse file list
            val files = mutableListOf<TorrentFileEntry>()
            val rawFiles = infoMap["files"] as? List<*>

            if (rawFiles != null) {
                // Multi-file torrent
                for ((index, item) in rawFiles.withIndex()) {
                    val fileDict = item as? Map<*, *> ?: continue
                    val length = (fileDict["length"] as? Number)?.toLong() ?: continue
                    val pathList = (fileDict["path.utf-8"] as? List<*>) ?: (fileDict["path"] as? List<*>) ?: continue

                    // Build path with strict directory traversal protection
                    val sanitizedSegments = mutableListOf<String>()
                    for (segment in pathList) {
                        val segStr = segment.toString().trim()
                        if (segStr == ".." || segStr.contains("../") || segStr.contains("..\\") || segStr.startsWith("/") || segStr.startsWith("\\")) {
                            throw SecurityException("Path traversal attempt in torrent file: $segStr")
                        }
                        if (segStr.isNotBlank() && segStr != ".") {
                            sanitizedSegments.add(segStr)
                        }
                    }

                    val relativePath = sanitizedSegments.joinToString("/")
                    val ext = relativePath.substringAfterLast('.', "").lowercase()
                    val isVideo = ext in VIDEO_EXTENSIONS

                    files.add(
                        TorrentFileEntry(
                            index = index,
                            path = relativePath,
                            size = length,
                            isVideo = isVideo
                        )
                    )
                }
            } else {
                // Single-file torrent
                val length = (infoMap["length"] as? Number)?.toLong()
                    ?: return Result.failure(IllegalArgumentException("Single-file torrent missing 'length'"))
                val ext = name.substringAfterLast('.', "").lowercase()
                files.add(
                    TorrentFileEntry(
                        index = 0,
                        path = name,
                        size = length,
                        isVideo = ext in VIDEO_EXTENSIONS
                    )
                )
            }

            val totalSize = files.sumOf { it.size }

            Result.success(
                TorrentMetadata(
                    infoHash = infoHash,
                    name = name,
                    totalSize = totalSize,
                    pieceLength = pieceLength,
                    pieceCount = pieceCount,
                    files = files,
                    trackers = trackers,
                    isPrivate = isPrivate,
                    creationDate = creationDate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decode(stream: InputStream, depth: Int, currentKey: String? = null): Any? {
        if (depth > MAX_DEPTH) {
            throw IllegalArgumentException("Exceeded maximum Bencode recursion depth ($MAX_DEPTH)")
        }

        val indicator = stream.read()
        if (indicator == -1) return null

        return when (indicator.toChar()) {
            'i' -> {
                // Integer: i<integer>e
                val sb = StringBuilder()
                while (true) {
                    val c = stream.read()
                    if (c == -1) throw IllegalArgumentException("Premature EOF reading integer")
                    if (c.toChar() == 'e') break
                    sb.append(c.toChar())
                }
                sb.toString().toLong()
            }
            'l' -> {
                // List: l<items>e
                val list = mutableListOf<Any?>()
                while (true) {
                    stream.mark(1)
                    val next = stream.read()
                    if (next == -1) throw IllegalArgumentException("Premature EOF reading list")
                    if (next.toChar() == 'e') break
                    stream.reset()
                    list.add(decode(stream, depth + 1))
                }
                list
            }
            'd' -> {
                // Dictionary: d<key><value>e
                val map = linkedMapOf<String, Any?>()
                while (true) {
                    stream.mark(1)
                    val next = stream.read()
                    if (next == -1) throw IllegalArgumentException("Premature EOF reading dictionary")
                    if (next.toChar() == 'e') break
                    stream.reset()

                    val keyObj = decode(stream, depth + 1)
                    val key = when (keyObj) {
                        is String -> keyObj
                        is ByteArray -> String(keyObj, StandardCharsets.UTF_8)
                        else -> throw IllegalArgumentException("Dictionary key must be string")
                    }
                    val value = decode(stream, depth + 1, currentKey = key)
                    map[key] = value
                }
                map
            }
            in '0'..'9' -> {
                // Byte string: <length>:<contents>
                val lenSb = StringBuilder()
                lenSb.append(indicator.toChar())
                while (true) {
                    val c = stream.read()
                    if (c == -1) throw IllegalArgumentException("Premature EOF reading string length")
                    if (c.toChar() == ':') break
                    lenSb.append(c.toChar())
                }
                val length = lenSb.toString().toInt()
                if (length < 0) throw IllegalArgumentException("Negative string length")
                val buf = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = stream.read(buf, read, length - read)
                    if (count == -1) throw IllegalArgumentException("Premature EOF reading byte string body")
                    read += count
                }

                // If this is the "pieces" binary hash field, strictly retain as ByteArray
                if (currentKey == "pieces") {
                    buf
                } else {
                    try {
                        val str = String(buf, StandardCharsets.UTF_8)
                        // If it contains null bytes, retain as ByteArray
                        if (buf.contains(0.toByte())) buf else str
                    } catch (_: Exception) {
                        buf
                    }
                }
            }
            else -> throw IllegalArgumentException("Unexpected Bencode indicator: ${indicator.toChar()}")
        }
    }

    /**
     * Extracts exact byte sequence of 'info' dictionary from raw torrent bytes for SHA-1 hashing.
     */
    fun extractInfoBytes(bytes: ByteArray): ByteArray? {
        val needle = "4:info".toByteArray(StandardCharsets.US_ASCII)
        var pos = -1
        for (i in 0..(bytes.size - needle.size)) {
            var match = true
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                pos = i + needle.size
                break
            }
        }
        if (pos == -1 || pos >= bytes.size) return null

        // Must start with 'd'
        if (bytes[pos] != 'd'.code.toByte()) return null

        // Find matching 'e' that closes the dictionary
        val stream = bytes.inputStream()
        stream.skip(pos.toLong())
        val startStreamPos = pos

        var depth = 0
        var current = pos
        while (current < bytes.size) {
            val c = bytes[current].toInt().toChar()
            when (c) {
                'd', 'l' -> {
                    depth++
                    current++
                }
                'i' -> {
                    current++
                    while (current < bytes.size && bytes[current].toInt().toChar() != 'e') {
                        current++
                    }
                    if (current < bytes.size) current++ // skip 'e'
                }
                'e' -> {
                    depth--
                    current++
                    if (depth == 0) {
                        val len = current - startStreamPos
                        val out = ByteArray(len)
                        System.arraycopy(bytes, startStreamPos, out, 0, len)
                        return out
                    }
                }
                in '0'..'9' -> {
                    val lenSb = StringBuilder()
                    while (current < bytes.size && bytes[current].toInt().toChar() != ':') {
                        lenSb.append(bytes[current].toInt().toChar())
                        current++
                    }
                    current++ // skip ':'
                    val len = lenSb.toString().toIntOrNull() ?: return null
                    current += len
                }
                else -> return null
            }
        }
        return null
    }

    fun sha1Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(data)
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val i = b.toInt() and 0xFF
            sb.append(hexChars[i shr 4])
            sb.append(hexChars[i and 0x0F])
        }
        return sb.toString()
    }

    /**
     * Bencode encoder for generating synthetic developer test torrents.
     */
    fun encode(obj: Any?): ByteArray {
        val baos = ByteArrayOutputStream()
        encodeToStream(obj, baos)
        return baos.toByteArray()
    }

    private fun encodeToStream(obj: Any?, out: ByteArrayOutputStream) {
        when (obj) {
            is Long -> {
                out.write('i'.code)
                out.write(obj.toString().toByteArray(StandardCharsets.US_ASCII))
                out.write('e'.code)
            }
            is Int -> {
                out.write('i'.code)
                out.write(obj.toString().toByteArray(StandardCharsets.US_ASCII))
                out.write('e'.code)
            }
            is String -> {
                val b = obj.toByteArray(StandardCharsets.UTF_8)
                out.write(b.size.toString().toByteArray(StandardCharsets.US_ASCII))
                out.write(':'.code)
                out.write(b)
            }
            is ByteArray -> {
                out.write(obj.size.toString().toByteArray(StandardCharsets.US_ASCII))
                out.write(':'.code)
                out.write(obj)
            }
            is List<*> -> {
                out.write('l'.code)
                for (item in obj) {
                    encodeToStream(item, out)
                }
                out.write('e'.code)
            }
            is Map<*, *> -> {
                out.write('d'.code)
                // Keys must be sorted byte-wise
                val sortedKeys = obj.keys.map { it.toString() }.sorted()
                for (key in sortedKeys) {
                    encodeToStream(key, out)
                    encodeToStream(obj[key], out)
                }
                out.write('e'.code)
            }
            else -> throw IllegalArgumentException("Unsupported type for Bencode encoding: ${obj?.javaClass}")
        }
    }
}
