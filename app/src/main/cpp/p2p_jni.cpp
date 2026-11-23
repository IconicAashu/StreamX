#include "p2p_streaming_engine.h"
#include <jni.h>
#include <string>
#include <vector>

using namespace streamx::p2p;

static std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeInitialize(
        JNIEnv* env, jobject /* this */, jstring jDownloadDir) {
    std::string downloadDir = jstringToString(env, jDownloadDir);
    return StreamingEngine::getInstance().initialize(downloadDir) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeShutdown(
        JNIEnv* /* env */, jobject /* this */) {
    StreamingEngine::getInstance().shutdown();
}

JNIEXPORT jstring JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeAddMagnet(
        JNIEnv* env, jobject /* this */, jstring jMagnetUri) {
    std::string magnetUri = jstringToString(env, jMagnetUri);
    std::string infoHash = StreamingEngine::getInstance().addMagnet(magnetUri);
    return env->NewStringUTF(infoHash.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeAddTorrentFile(
        JNIEnv* env, jobject /* this */, jstring jFilePath) {
    std::string filePath = jstringToString(env, jFilePath);
    std::string infoHash = StreamingEngine::getInstance().addTorrentFile(filePath);
    return env->NewStringUTF(infoHash.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeRemoveTorrent(
        JNIEnv* env, jobject /* this */, jstring jInfoHash, jboolean jDeleteFiles) {
    std::string infoHash = jstringToString(env, jInfoHash);
    return StreamingEngine::getInstance().removeTorrent(infoHash, jDeleteFiles == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativePauseTorrent(
        JNIEnv* env, jobject /* this */, jstring jInfoHash) {
    std::string infoHash = jstringToString(env, jInfoHash);
    return StreamingEngine::getInstance().pauseTorrent(infoHash) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeResumeTorrent(
        JNIEnv* env, jobject /* this */, jstring jInfoHash) {
    std::string infoHash = jstringToString(env, jInfoHash);
    return StreamingEngine::getInstance().resumeTorrent(infoHash) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeSelectFile(
        JNIEnv* env, jobject /* this */, jstring jInfoHash, jint jFileIndex) {
    std::string infoHash = jstringToString(env, jInfoHash);
    return StreamingEngine::getInstance().selectFileForStreaming(infoHash, jFileIndex) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeUpdatePlaybackPosition(
        JNIEnv* env, jobject /* this */, jstring jInfoHash, jint jFileIndex, jlong jByteOffset, jlong jBufferAheadBytes) {
    std::string infoHash = jstringToString(env, jInfoHash);
    StreamingEngine::getInstance().updatePlaybackPosition(infoHash, jFileIndex, jByteOffset, jBufferAheadBytes);
}

JNIEXPORT jbyteArray JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeReadBytes(
        JNIEnv* env, jobject /* this */, jstring jInfoHash, jint jFileIndex, jlong jByteOffset, jint jLength) {
    std::string infoHash = jstringToString(env, jInfoHash);
    std::vector<uint8_t> buffer;
    bool success = StreamingEngine::getInstance().readStreamBytes(infoHash, jFileIndex, jByteOffset, jLength, buffer);
    if (!success || buffer.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(buffer.size());
    env->SetByteArrayRegion(result, 0, buffer.size(), reinterpret_cast<const jbyte*>(buffer.data()));
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_streamx_app_p2p_engine_NativeP2PEngine_nativeGetStats(
        JNIEnv* env, jobject /* this */, jstring jInfoHash) {
    std::string infoHash = jstringToString(env, jInfoHash);
    StreamingStats stats;
    if (!StreamingEngine::getInstance().getStreamingStats(infoHash, stats)) {
        return nullptr;
    }

    // Pack into jlong array: [downloadRate, uploadRate, peers, seeds, piecesDone, totalPieces, bufferedBytes, state]
    jlong values[8];
    values[0] = stats.downloadRate;
    values[1] = stats.uploadRate;
    values[2] = stats.peersCount;
    values[3] = stats.seedsCount;
    values[4] = stats.piecesCompleted;
    values[5] = stats.totalPieces;
    values[6] = stats.bufferedBytes;
    values[7] = static_cast<jlong>(stats.state);

    jlongArray result = env->NewLongArray(8);
    env->SetLongArrayRegion(result, 0, 8, values);
    return result;
}

} // extern "C"
