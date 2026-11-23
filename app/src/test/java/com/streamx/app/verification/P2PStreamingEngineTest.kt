package com.streamx.app.verification

import com.streamx.app.data.playback.p2p.P2PPlaybackProvider
import com.streamx.app.p2p.engine.P2PSessionManager
import com.streamx.app.p2p.engine.StreamingPieceScheduler
import com.streamx.app.p2p.model.PiecePriority
import com.streamx.app.p2p.parser.BencodeParser
import com.streamx.app.p2p.parser.MagnetUriParser
import com.streamx.app.p2p.safety.P2PSafetyManager
import com.streamx.app.p2p.server.LocalStreamHttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

/**
 * Comprehensive verification of Phase 2 Native P2P Streaming Engine:
 * - Magnet URI parsing (hex and base32 BTIH, display name, multi-trackers)
 * - Bencode parser & path traversal security
 * - Streaming-aware piece scheduler (moov/header, sliding buffer window, seek reprioritization, tail cues)
 * - Local loopback HTTP streaming server (127.0.0.1, byte-range requests, 206 Partial Content, 416 Out of Range)
 * - Synthetic developer-owned test video & torrent generation
 * - P2PPlaybackProvider integration with Media3 ExoPlayer flow
 * - Legal safety audit & unverified source disclaimers
 */
class P2PStreamingEngineTest {

    private lateinit var tempDir: File
    private lateinit var sessionManager: P2PSessionManager

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("streamx_p2p_test").toFile()
        sessionManager = P2PSessionManager(tempDir)
    }

    @After
    fun tearDown() {
        sessionManager.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun testMagnetUriParsing() {
        // 1. Standard 40-character hex BTIH
        val hexMagnet = "magnet:?xt=urn:btih:c12074d0ac218f3a426d115c573f00c0a0144999&dn=Developer+Test+Video&tr=http%3A%2F%2Ftracker.com%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80"
        val parsedHex = MagnetUriParser.parse(hexMagnet)
        assertTrue(parsedHex.isSuccess)
        val infoHex = parsedHex.getOrThrow()
        assertEquals("c12074d0ac218f3a426d115c573f00c0a0144999", infoHex.infoHash)
        assertEquals("Developer Test Video", infoHex.displayName)
        assertEquals(2, infoHex.trackers.size)
        assertTrue(infoHex.trackers.contains("http://tracker.com/announce"))

        // 2. Base32 32-character BTIH (RFC 4648)
        val base32Magnet = "magnet:?xt=urn:btih:YNCXJUE4EGF3UQTVCFOFALFAYCQBIEMZ&dn=Base32+Sample"
        val parsedBase32 = MagnetUriParser.parse(base32Magnet)
        assertTrue(parsedBase32.isSuccess)
        val infoBase32 = parsedBase32.getOrThrow()
        assertEquals(40, infoBase32.infoHash.length)
        assertEquals("Base32 Sample", infoBase32.displayName)

        // 3. Invalid schemes
        val badScheme = MagnetUriParser.parse("http://example.com/torrent.torrent")
        assertTrue(badScheme.isFailure)

        val missingXt = MagnetUriParser.parse("magnet:?dn=NoHashTorrent")
        assertTrue(missingXt.isFailure)
    }

    @Test
    fun testBencodeParserAndPathTraversalProtection() {
        // 1. Basic types
        val strEncoded = BencodeParser.encode("STREAMX")
        assertEquals("7:STREAMX", String(strEncoded))

        val intEncoded = BencodeParser.encode(1048576L)
        assertEquals("i1048576e", String(intEncoded))

        val listEncoded = BencodeParser.encode(listOf("mp4", "mkv"))
        assertEquals("l3:mp43:mkve", String(listEncoded))

        // 2. Path traversal security audit
        val maliciousDict = linkedMapOf<String, Any>(
            "announce" to "http://tracker.org",
            "info" to linkedMapOf<String, Any>(
                "piece length" to 65536L,
                "pieces" to ByteArray(20),
                "name" to "malicious_torrent",
                "files" to listOf(
                    linkedMapOf("length" to 100L, "path" to listOf("..", "..", "etc", "passwd")),
                    linkedMapOf("length" to 200L, "path" to listOf("video.mp4"))
                )
            )
        )
        val maliciousBytes = BencodeParser.encode(maliciousDict)
        val parseMalicious = BencodeParser.parseTorrent(maliciousBytes)

        // Must reject path traversal attempt
        assertTrue(parseMalicious.isFailure)
        assertTrue(parseMalicious.exceptionOrNull() is SecurityException ||
                parseMalicious.exceptionOrNull()?.message?.contains("Path traversal") == true)
    }

    @Test
    fun testStreamingPieceSchedulerPrioritization() {
        // Generate synthetic test video and torrent
        val syntheticVideo = P2PSafetyManager.generateSyntheticTestVideo(durationSeconds = 30)
        val (_, metadata) = P2PSafetyManager.generateSyntheticTestTorrent(syntheticVideo, "sample_feature.mp4")

        val scheduler = StreamingPieceScheduler(metadata, selectedFileIndex = 0)

        // 1. Verify container header pieces (moov/ftyp) are marked CRITICAL
        assertEquals(PiecePriority.CRITICAL, scheduler.getPiecePriority(scheduler.fileStartPiece))

        // 2. Verify container tail pieces (index/cues) are marked CRITICAL
        assertEquals(PiecePriority.CRITICAL, scheduler.getPiecePriority(scheduler.fileEndPiece))

        // 3. Verify sliding playback window
        // Seek to 50% through the file
        val seekOffset = metadata.totalSize / 2
        scheduler.onSeek(seekOffset, bufferAheadBytes = 32 * 1024L * 2)

        val targetPiece = (seekOffset / metadata.pieceLength).toInt()
        assertEquals(PiecePriority.CRITICAL, scheduler.getPiecePriority(targetPiece))

        // 4. Mark piece completed and verify byte reading
        val testChunk = ByteArray(metadata.pieceLength) { 0x42.toByte() }
        scheduler.markPieceCompleted(0, testChunk)
        assertTrue(scheduler.isPieceCompleted(0))

        val read = scheduler.readBytes(0L, 10)
        assertNotNull(read)
        assertEquals(10, read!!.size)
        assertEquals(0x42.toByte(), read[0])
    }

    @Test
    fun testLocalLoopbackHttpServerByteRangeRequests() {
        val testData = ByteArray(1024 * 64) { (it % 256).toByte() }
        val torrentId = "test_torrent_hash_123"
        val fileIndex = 0

        val server = LocalStreamHttpServer(
            dataProvider = { id, fIdx, offset, len ->
                if (offset >= testData.size) null
                else {
                    val toRead = Math.min(len.toLong(), testData.size - offset).toInt()
                    testData.copyOfRange(offset.toInt(), (offset + toRead).toInt())
                }
            },
            fileInfoProvider = { id, fIdx ->
                Pair(testData.size.toLong(), "video/mp4")
            }
        )

        val port = server.start()
        assertTrue(port > 0)

        try {
            // 1. Full GET (200 OK)
            val fullUrl = URL(server.getStreamUrl(torrentId, fileIndex))
            val connFull = fullUrl.openConnection() as HttpURLConnection
            connFull.requestMethod = "GET"
            connFull.connect()

            assertEquals(200, connFull.responseCode)
            assertEquals("video/mp4", connFull.contentType)
            assertEquals(testData.size.toLong(), connFull.contentLengthLong)
            assertEquals("bytes", connFull.getHeaderField("Accept-Ranges"))

            val body = connFull.inputStream.readBytes()
            assertEquals(testData.size, body.size)
            assertArrayEquals(testData, body)
            connFull.disconnect()

            // 2. HEAD Request
            val connHead = fullUrl.openConnection() as HttpURLConnection
            connHead.requestMethod = "HEAD"
            connHead.connect()

            assertEquals(200, connHead.responseCode)
            assertEquals("video/mp4", connHead.contentType)
            assertEquals(testData.size.toLong(), connHead.contentLengthLong)
            connHead.disconnect()

            // 3. Partial Content (206 Partial Content): Range: bytes=0-1023
            val connRange = fullUrl.openConnection() as HttpURLConnection
            connRange.setRequestProperty("Range", "bytes=0-1023")
            connRange.connect()

            assertEquals(206, connRange.responseCode)
            assertEquals(1024L, connRange.contentLengthLong)
            assertEquals("bytes 0-1023/${testData.size}", connRange.getHeaderField("Content-Range"))
            val rangeBytes = connRange.inputStream.readBytes()
            assertEquals(1024, rangeBytes.size)
            assertArrayEquals(testData.copyOfRange(0, 1024), rangeBytes)
            connRange.disconnect()

            // 4. Seek Range: bytes=32768- (Seek to 32 KB)
            val connSeek = fullUrl.openConnection() as HttpURLConnection
            connSeek.setRequestProperty("Range", "bytes=32768-")
            connSeek.connect()

            assertEquals(206, connSeek.responseCode)
            val expectedSeekLen = (testData.size - 32768).toLong()
            assertEquals(expectedSeekLen, connSeek.contentLengthLong)
            assertEquals("bytes 32768-${testData.size - 1}/${testData.size}", connSeek.getHeaderField("Content-Range"))
            connSeek.disconnect()

            // 5. Invalid Range (416 Range Not Satisfiable)
            val connInvalidRange = fullUrl.openConnection() as HttpURLConnection
            connInvalidRange.setRequestProperty("Range", "bytes=99999999-")
            connInvalidRange.connect()

            assertEquals(416, connInvalidRange.responseCode)
            connInvalidRange.disconnect()
        } finally {
            server.stop()
        }
    }

    @Test
    fun testSyntheticDeveloperTestVideoAndTorrent() {
        val videoBytes = P2PSafetyManager.generateSyntheticTestVideo(durationSeconds = 12)
        assertTrue(videoBytes.size > 64 * 1024)

        // Check ISO BMFF atom headers
        val ftypTag = String(videoBytes.copyOfRange(4, 8), Charsets.US_ASCII)
        assertEquals("ftyp", ftypTag)

        val (torrentBytes, metadata) = P2PSafetyManager.generateSyntheticTestTorrent(videoBytes, "developer_test.mp4")
        assertTrue(torrentBytes.isNotEmpty())
        assertEquals(40, metadata.infoHash.length)
        assertEquals("developer_test.mp4", metadata.name)
        assertEquals(videoBytes.size.toLong(), metadata.totalSize)
        assertTrue(metadata.files.isNotEmpty())
        assertTrue(metadata.files.first().isVideo)
    }

    @Test
    fun testP2PPlaybackProviderRouting() = runBlocking {
        val provider = P2PPlaybackProvider(sessionManager)

        // 1. canHandle routing
        assertTrue(provider.canHandle("p2p_c12074d0ac218f3a426d115c573f00c0a0144999"))
        assertTrue(provider.canHandle("p2p_hash_file_0"))
        assertTrue(provider.canHandle("magnet:?xt=urn:btih:c12074d0ac218f3a426d115c573f00c0a0144999&dn=Test"))
        assertTrue(provider.canHandle("/storage/test.torrent"))

        // Must reject non-p2p providers
        assertFalse(provider.canHandle("tt0137523"))
        assertFalse(provider.canHandle("jellyfin_item_42"))

        // 2. Stream resolution flow
        val videoBytes = P2PSafetyManager.generateSyntheticTestVideo(10)
        val (_, metadata) = P2PSafetyManager.generateSyntheticTestTorrent(videoBytes, "test_stream.mp4")
        sessionManager.registerTorrent(metadata)

        val sources = provider.getStreams("p2p_${metadata.infoHash}")
        assertEquals(1, sources.size)

        val source = sources.first()
        assertTrue(source.title.contains("P2P:"))
        assertTrue(source.streamUrl.startsWith("http://127.0.0.1:"))
        assertTrue(source.streamUrl.contains("/stream?id=${metadata.infoHash}"))
        assertFalse(source.isLiveOrHls)
        assertTrue(source.licenseNotice.contains("Loopback"))
    }

    @Test
    fun testSafetyAuditAndDisclaimerRequirements() {
        val syntheticVideo = P2PSafetyManager.generateSyntheticTestVideo(5)
        val (_, metadata) = P2PSafetyManager.generateSyntheticTestTorrent(syntheticVideo, "safe_test.mp4")

        val audit = P2PSafetyManager.auditTorrent(metadata)
        assertTrue(audit.isPathSafe)
        assertTrue(audit.requiresUserConfirmation)
        assertTrue(audit.warningMessage?.contains("decentralized peer-to-peer swarm") == true)
        assertTrue(audit.sourceAttribution.contains("BitTorrent Swarm"))

        // Disclaimer verification: no automated copyright ownership claims
        assertTrue(P2PSafetyManager.LEGAL_DISCLAIMER.contains("A torrent hash alone cannot establish whether content is legally distributable."))
    }
}
