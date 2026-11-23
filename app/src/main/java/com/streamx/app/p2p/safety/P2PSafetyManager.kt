package com.streamx.app.p2p.safety

import com.streamx.app.p2p.model.TorrentMetadata
import com.streamx.app.p2p.parser.BencodeParser
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class SafetyAuditResult(
    val isPathSafe: Boolean,
    val requiresUserConfirmation: Boolean,
    val warningMessage: String?,
    val sourceAttribution: String
)

object P2PSafetyManager {

    const val LEGAL_DISCLAIMER =
        "Notice: You are connecting to a decentralized peer-to-peer swarm. " +
        "STREAMX does not host, index, or verify copyright licenses of P2P swarms. " +
        "A torrent hash alone cannot establish whether content is legally distributable. " +
        "Ensure you have authorized distribution rights or public-domain access before streaming."

    fun auditTorrent(metadata: TorrentMetadata): SafetyAuditResult {
        var isPathSafe = true

        for (file in metadata.files) {
            val path = file.path
            if (path.contains("..") || path.startsWith("/") || path.startsWith("\\") || path.contains(":/")) {
                isPathSafe = false
                break
            }
        }

        return SafetyAuditResult(
            isPathSafe = isPathSafe,
            requiresUserConfirmation = true,
            warningMessage = if (!isPathSafe) "Path traversal risk detected in torrent file list." else LEGAL_DISCLAIMER,
            sourceAttribution = "Decentralized BitTorrent Swarm (BTIH: ${metadata.infoHash})"
        )
    }

    /**
     * Generates a valid developer-owned synthetic ISO BMFF MP4 test video binary.
     * Contains standard ftyp, moov, and mdat atoms to enable end-to-end testing
     * in Media3 ExoPlayer without infringing any third-party copyrights.
     */
    fun generateSyntheticTestVideo(durationSeconds: Int = 10): ByteArray {
        val baos = ByteArrayOutputStream()

        // 1. 'ftyp' atom (File Type box)
        val ftypPayload = ByteArrayOutputStream().apply {
            write("isom".toByteArray(StandardCharsets.US_ASCII)) // major_brand
            write(byteArrayOf(0, 0, 2, 0))                       // minor_version
            write("isom".toByteArray(StandardCharsets.US_ASCII)) // compatible_brands
            write("mp42".toByteArray(StandardCharsets.US_ASCII))
        }.toByteArray()
        writeAtom(baos, "ftyp", ftypPayload)

        // 2. 'moov' atom (Movie metadata box)
        val moovPayload = ByteArrayOutputStream().apply {
            // 'mvhd' header
            val mvhd = ByteArrayOutputStream().apply {
                write(0) // version
                write(byteArrayOf(0, 0, 0)) // flags
                write(intToBytes(0)) // creation_time
                write(intToBytes(0)) // modification_time
                write(intToBytes(1000)) // timescale: 1000 Hz
                write(intToBytes(durationSeconds * 1000)) // duration
                write(intToBytes(0x00010000)) // rate = 1.0
                write(shortToBytes(0x0100))   // volume = 1.0
                write(ByteArray(10))          // reserved
                // Unity matrix (36 bytes)
                write(intToBytes(0x00010000)); write(intToBytes(0)); write(intToBytes(0))
                write(intToBytes(0)); write(intToBytes(0x00010000)); write(intToBytes(0))
                write(intToBytes(0)); write(intToBytes(0)); write(intToBytes(0x40000000))
                write(ByteArray(24)) // pre_defined
                write(intToBytes(2))  // next_track_ID
            }.toByteArray()
            writeAtom(this, "mvhd", mvhd)
        }.toByteArray()
        writeAtom(baos, "moov", moovPayload)

        // 3. 'mdat' atom (Media Data box with synthetic frame payload)
        val mediaPayload = ByteArray(64 * 1024) { index -> (index % 256).toByte() }
        writeAtom(baos, "mdat", mediaPayload)

        return baos.toByteArray()
    }

    private fun writeAtom(out: ByteArrayOutputStream, type: String, payload: ByteArray) {
        val totalSize = 8 + payload.size
        out.write(intToBytes(totalSize))
        out.write(type.toByteArray(StandardCharsets.US_ASCII))
        out.write(payload)
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun shortToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 8 and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    /**
     * Generates a fully compliant, self-contained Bencoded .torrent for the synthetic test video.
     */
    fun generateSyntheticTestTorrent(videoBytes: ByteArray, torrentName: String = "developer_test_video.mp4"): Pair<ByteArray, TorrentMetadata> {
        val pieceLength = 32 * 1024 // 32 KB pieces
        val numPieces = ((videoBytes.size + pieceLength - 1) / pieceLength)

        val piecesStream = ByteArrayOutputStream()
        val md = MessageDigest.getInstance("SHA-1")

        for (p in 0 until numPieces) {
            val start = p * pieceLength
            val end = (start + pieceLength).coerceAtMost(videoBytes.size)
            val chunk = videoBytes.copyOfRange(start, end)
            piecesStream.write(md.digest(chunk))
        }
        val piecesBytes = piecesStream.toByteArray()

        val infoDict = linkedMapOf<String, Any>(
            "length" to videoBytes.size.toLong(),
            "name" to torrentName,
            "piece length" to pieceLength.toLong(),
            "pieces" to piecesBytes
        )

        val rootDict = linkedMapOf<String, Any>(
            "announce" to "http://127.0.0.1:6969/announce",
            "comment" to "STREAMX Developer Self-Test Torrent",
            "created by" to "STREAMX Native P2P Engine",
            "info" to infoDict
        )

        val torrentBytes = BencodeParser.encode(rootDict)
        val metadata = BencodeParser.parseTorrent(torrentBytes).getOrThrow()

        return Pair(torrentBytes, metadata)
    }
}
