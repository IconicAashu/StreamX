package com.streamx.app.network.provider

import com.streamx.app.domain.model.ProviderCategory
import com.streamx.app.domain.model.ProviderIntegrationStatus

/**
 * Detects the architecture and protocol of a repository or manifest URL.
 * Supports auto-detection between Stremio, Nuvio, CloudStream, and Media Server endpoints.
 */
object RepositoryTypeDetector {

    enum class DetectionConfidence {
        DEFINITIVE,
        PROBABLE,
        UNKNOWN
    }

    data class DetectionResult(
        val category: ProviderCategory,
        val integrationStatus: ProviderIntegrationStatus,
        val confidence: DetectionConfidence,
        val normalizedUrl: String,
        val explanation: String
    )

    fun detect(rawUrl: String, responseBodySnippet: String? = null): DetectionResult {
        val trimmed = rawUrl.trim()
        val lowerUrl = trimmed.lowercase()

        // 1. CloudStream Extension Repository Detection
        if (lowerUrl.endsWith("repo.json") ||
            lowerUrl.endsWith("plugins.json") ||
            lowerUrl.endsWith(".cs3") ||
            lowerUrl.contains("cloudstreamrepo.com") ||
            (responseBodySnippet?.contains("\"pluginLists\"") == true)
        ) {
            return DetectionResult(
                category = ProviderCategory.CLOUDSTREAM_EXTENSION,
                integrationStatus = ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
                confidence = DetectionConfidence.DEFINITIVE,
                normalizedUrl = trimmed,
                explanation = "CloudStream Extension: Requires Dalvik (.cs3) Sandbox Runtime. Direct in-process DEX execution disabled."
            )
        }

        // 2. Nuvio Native Plugin Repository Detection
        if (lowerUrl.contains("nuvioplugin.com") ||
            lowerUrl.contains("nuvio-providers") ||
            lowerUrl.contains("nuvioplugin") ||
            lowerUrl.contains("nuvio") ||
            (responseBodySnippet?.contains("\"plugins\"") == true && responseBodySnippet.contains("\"scriptUrl\""))
        ) {
            return DetectionResult(
                category = ProviderCategory.NUVIO_NATIVE,
                integrationStatus = ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
                confidence = DetectionConfidence.DEFINITIVE,
                normalizedUrl = trimmed,
                explanation = "Nuvio Native Plugin: Requires Hermes/QuickJS Sandboxed Engine. Direct in-process JavaScript execution disabled."
            )
        }

        // 3. Jellyfin / Media Server Detection
        if (lowerUrl.contains(":8096") ||
            lowerUrl.contains("/system/info") ||
            lowerUrl.contains("jellyfin")
        ) {
            return DetectionResult(
                category = ProviderCategory.MEDIA_SERVER,
                integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
                confidence = DetectionConfidence.PROBABLE,
                normalizedUrl = trimmed,
                explanation = "Media Server Integration: Dedicated Jellyfin Client API."
            )
        }

        // 4. Stremio Add-on Detection
        if (lowerUrl.startsWith("stremio://") ||
            lowerUrl.endsWith("/manifest.json") ||
            lowerUrl.contains("v3-cinemeta.strem.io") ||
            lowerUrl.contains("stremio-addons.net") ||
            lowerUrl.contains(".strem.fun")
        ) {
            val normalized = if (lowerUrl.startsWith("stremio://")) {
                "https://" + trimmed.substring(10)
            } else trimmed

            return DetectionResult(
                category = ProviderCategory.STREMIO_ADDON,
                integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
                confidence = DetectionConfidence.DEFINITIVE,
                normalizedUrl = normalized,
                explanation = "Stremio JSON/REST Protocol: Native manifest discovery, metadata, and stream queries supported."
            )
        }

        // Fallback: If JSON response body contains Stremio fields
        if (responseBodySnippet != null) {
            if (responseBodySnippet.contains("\"resources\"") && responseBodySnippet.contains("\"id\"")) {
                return DetectionResult(
                    category = ProviderCategory.STREMIO_ADDON,
                    integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
                    confidence = DetectionConfidence.PROBABLE,
                    normalizedUrl = trimmed,
                    explanation = "Detected Stremio Add-on manifest structure from response body."
                )
            }
        }

        return DetectionResult(
            category = ProviderCategory.STREMIO_ADDON,
            integrationStatus = ProviderIntegrationStatus.UNSUPPORTED,
            confidence = DetectionConfidence.UNKNOWN,
            normalizedUrl = trimmed,
            explanation = "Unrecognized repository format or protocol."
        )
    }
}
