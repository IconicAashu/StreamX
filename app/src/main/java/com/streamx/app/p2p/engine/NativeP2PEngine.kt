package com.streamx.app.p2p.engine

import com.streamx.app.p2p.model.PiecePriority
import com.streamx.app.p2p.model.TorrentState
import com.streamx.app.p2p.model.TorrentStatus
import com.streamx.app.utils.StreamXLog

/**
 * Native P2P Libtorrent C++ Engine JNI Bridge.
 * Connects high-level Kotlin calls to native libtorrent streaming core with graceful fallback.
 */
class NativeP2PEngine {

    companion object {
        private const val TAG = "NativeP2PEngine"
        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("streamx_p2p")
                isNativeLoaded = true
                StreamXLog.i(TAG, "Native libtorrent C++ engine (libstreamx_p2p.so) loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
                StreamXLog.w(TAG, "Native library libstreamx_p2p.so not loaded in current environment: ${e.message}. Using managed engine fallback.")
            } catch (e: Exception) {
                isNativeLoaded = false
                StreamXLog.e(TAG, "Unexpected error loading native library: ${e.message}")
            }
        }

        fun isNativeAvailable(): Boolean = isNativeLoaded
    }

    private var isInitialized = false

    // JNI Native Declarations
    private external fun nativeInitialize(downloadDir: String): Boolean
    private external fun nativeShutdown()
    private external fun nativeAddMagnet(magnetUri: String): String
    private external fun nativeAddTorrentFile(filePath: String): String
    private external fun nativeRemoveTorrent(infoHash: String, deleteFiles: Boolean): Boolean
    private external fun nativePauseTorrent(infoHash: String): Boolean
    private external fun nativeResumeTorrent(infoHash: String): Boolean
    private external fun nativeSelectFile(infoHash: String, fileIndex: Int): Boolean
    private external fun nativeUpdatePlaybackPosition(infoHash: String, fileIndex: Int, byteOffset: Long, bufferAheadBytes: Long)
    private external fun nativeReadBytes(infoHash: String, fileIndex: Int, byteOffset: Long, length: Int): ByteArray?
    private external fun nativeGetStats(infoHash: String): LongArray?

    fun initialize(downloadDir: String): Boolean {
        if (!isNativeLoaded) {
            isInitialized = true
            return true
        }
        return try {
            val ok = nativeInitialize(downloadDir)
            isInitialized = ok
            ok
        } catch (e: Throwable) {
            StreamXLog.e(TAG, "nativeInitialize failed: ${e.message}")
            false
        }
    }

    fun shutdown() {
        if (isNativeLoaded && isInitialized) {
            try {
                nativeShutdown()
            } catch (e: Throwable) {
                StreamXLog.e(TAG, "nativeShutdown failed: ${e.message}")
            }
        }
        isInitialized = false
    }

    fun addMagnet(magnetUri: String): String? {
        if (!isNativeLoaded) return null
        return try {
            val hash = nativeAddMagnet(magnetUri)
            hash.ifBlank { null }
        } catch (e: Throwable) {
            StreamXLog.e(TAG, "nativeAddMagnet failed: ${e.message}")
            null
        }
    }

    fun addTorrentFile(filePath: String): String? {
        if (!isNativeLoaded) return null
        return try {
            val hash = nativeAddTorrentFile(filePath)
            hash.ifBlank { null }
        } catch (e: Throwable) {
            StreamXLog.e(TAG, "nativeAddTorrentFile failed: ${e.message}")
            null
        }
    }

    fun removeTorrent(infoHash: String, deleteFiles: Boolean = false): Boolean {
        if (!isNativeLoaded) return true
        return try {
            nativeRemoveTorrent(infoHash, deleteFiles)
        } catch (e: Throwable) {
            StreamXLog.e(TAG, "nativeRemoveTorrent failed: ${e.message}")
            false
        }
    }

    fun pauseTorrent(infoHash: String): Boolean {
        if (!isNativeLoaded) return true
        return try {
            nativePauseTorrent(infoHash)
        } catch (e: Throwable) {
            false
        }
    }

    fun resumeTorrent(infoHash: String): Boolean {
        if (!isNativeLoaded) return true
        return try {
            nativeResumeTorrent(infoHash)
        } catch (e: Throwable) {
            false
        }
    }

    fun selectFile(infoHash: String, fileIndex: Int): Boolean {
        if (!isNativeLoaded) return true
        return try {
            nativeSelectFile(infoHash, fileIndex)
        } catch (e: Throwable) {
            false
        }
    }

    fun updatePlaybackPosition(infoHash: String, fileIndex: Int, byteOffset: Long, bufferAheadBytes: Long = 15 * 1024 * 1024L) {
        if (!isNativeLoaded) return
        try {
            nativeUpdatePlaybackPosition(infoHash, fileIndex, byteOffset, bufferAheadBytes)
        } catch (e: Throwable) {
            StreamXLog.e(TAG, "nativeUpdatePlaybackPosition failed: ${e.message}")
        }
    }

    fun readBytes(infoHash: String, fileIndex: Int, byteOffset: Long, length: Int): ByteArray? {
        if (!isNativeLoaded) return null
        return try {
            nativeReadBytes(infoHash, fileIndex, byteOffset, length)
        } catch (e: Throwable) {
            StreamXLog.e(TAG, "nativeReadBytes failed: ${e.message}")
            null
        }
    }

    fun getStats(infoHash: String): TorrentStatus? {
        if (!isNativeLoaded) return null
        return try {
            val array = nativeGetStats(infoHash) ?: return null
            if (array.size < 8) return null

            val stateVal = array[7].toInt()
            val state = TorrentState.values().getOrNull(stateVal) ?: TorrentState.DOWNLOADING
            val totalPieces = array[5].toInt()
            val piecesDone = array[4].toInt()
            val progress = if (totalPieces > 0) piecesDone.toFloat() / totalPieces.toFloat() else 0.0f

            TorrentStatus(
                infoHash = infoHash,
                name = "P2P Stream ($infoHash)",
                state = state,
                progress = progress,
                downloadRate = array[0],
                uploadRate = array[1],
                peersCount = array[2].toInt(),
                seedsCount = array[3].toInt(),
                piecesDownloaded = piecesDone,
                totalPieces = totalPieces,
                bufferedBytes = array[6]
            )
        } catch (e: Throwable) {
            null
        }
    }
}
