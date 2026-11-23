package com.streamx.app.network.addon

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

object AddonSecurityValidator {

    const val MAX_RESPONSE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB

    /**
     * Validates whether a manifest URL is safe to fetch:
     * - Protocol must be http or https
     * - Hostname must not resolve to loopback, link-local, or private IP addresses
     */
    fun validateUrl(urlString: String): Result<String> {
        var trimmed = urlString.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Manifest URL cannot be empty"))
        }

        // Support stremio:// protocol prefix by normalizing to https://
        if (trimmed.startsWith("stremio://", ignoreCase = true)) {
            trimmed = "https://" + trimmed.substring(10)
        }

        val url = try {
            URL(trimmed)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid URL syntax: ${e.message}"))
        }

        val protocol = url.protocol.lowercase()
        if (protocol != "http" && protocol != "https") {
            return Result.failure(SecurityException("Disallowed protocol '$protocol'. Only HTTP and HTTPS are permitted."))
        }

        val host = url.host.lowercase()
        if (isBlockedHost(host)) {
            return Result.failure(SecurityException("Blocked host '$host': local and private networks are disallowed."))
        }

        // DNS resolution check
        try {
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                if (isPrivateOrLocalAddress(addr)) {
                    return Result.failure(
                        SecurityException("SSRF blocked: Host '$host' resolved to disallowed IP address ${addr.hostAddress}.")
                    )
                }
            }
        } catch (e: SecurityException) {
            return Result.failure(e)
        } catch (_: Exception) {
            // Host will be validated dynamically during connection by SafeDns
        }

        return Result.success(trimmed)
    }

    fun isBlockedHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) ||
            host.endsWith(".localhost", ignoreCase = true) ||
            host.endsWith(".local", ignoreCase = true) ||
            host.endsWith(".internal", ignoreCase = true) ||
            host.equals("127.0.0.1") ||
            host.equals("0.0.0.0") ||
            host.equals("::1")
        ) {
            return true
        }
        return false
    }

    fun isPrivateOrLocalAddress(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
            return true
        }

        val ip = addr.address
        if (addr is Inet4Address) {
            val b0 = ip[0].toInt() and 0xFF
            val b1 = ip[1].toInt() and 0xFF

            // 10.0.0.0/8
            if (b0 == 10) return true
            // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
            if (b0 == 172 && (b1 in 16..31)) return true
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true
            // 169.254.0.0/16 (Link Local)
            if (b0 == 169 && b1 == 254) return true
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true
            // 0.0.0.0/8
            if (b0 == 0) return true
            // Carrier Grade NAT: 100.64.0.0/10 (100.64 - 100.127)
            if (b0 == 100 && (b1 in 64..127)) return true
        } else if (addr is Inet6Address) {
            // Unique Local Address fc00::/7 (fc00:: - fdff::)
            val b0 = ip[0].toInt() and 0xFF
            if ((b0 and 0xFE) == 0xFC) return true
            // Link-Local fe80::/10
            if (b0 == 0xFE && (ip[1].toInt() and 0xC0) == 0x80) return true
        }

        return false
    }

    /**
     * Custom Dns implementation that intercepts all hostname resolution
     * including automatic HTTP redirects to ensure no private/loopback address is ever contacted.
     */
    class SafeDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val host = hostname.lowercase()
            if (isBlockedHost(host)) {
                throw SecurityException("SSRF blocked: Host '$hostname' is disallowed.")
            }
            val addresses = Dns.SYSTEM.lookup(hostname)
            for (addr in addresses) {
                if (isPrivateOrLocalAddress(addr)) {
                    throw SecurityException("SSRF blocked: Host '$hostname' resolves to private IP: ${addr.hostAddress}")
                }
            }
            return addresses
        }
    }

    /**
     * Interceptor to enforce SSRF checks on every outgoing request and redirect,
     * plus 5MB maximum response body size limits.
     */
    class SafeAddonInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url

            val scheme = url.scheme.lowercase()
            if (scheme != "http" && scheme != "https") {
                throw SecurityException("Invalid URL scheme: $scheme")
            }

            val host = url.host
            if (isBlockedHost(host)) {
                throw SecurityException("SSRF blocked: Host '$host' is disallowed.")
            }

            // Validate resolved IP addresses
            try {
                val addresses = InetAddress.getAllByName(host)
                for (addr in addresses) {
                    if (isPrivateOrLocalAddress(addr)) {
                        throw SecurityException("SSRF blocked: Host '$host' resolves to private IP: ${addr.hostAddress}")
                    }
                }
            } catch (e: SecurityException) {
                throw e
            } catch (_: Exception) {
                // If direct resolution is unavailable (e.g. offline unit testing), SafeDns enforces SSRF during socket connection
            }

            val response = chain.proceed(request)

            // Validate Content-Length header if present
            val contentLength = response.body?.contentLength() ?: -1L
            if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                response.close()
                throw IOException("Response size exceeds 5MB limit: $contentLength bytes")
            }

            // Stream limiter for body to enforce 5MB safely without EOFException
            val body = response.body
            if (body != null) {
                val source = body.source()
                val exceedsLimit = source.request(MAX_RESPONSE_SIZE_BYTES + 1)
                if (exceedsLimit) {
                    response.close()
                    throw IOException("Response payload exceeded maximum allowed size of 5MB")
                }
                val bodyBytes = source.readByteArray()
                val contentType = body.contentType()
                val newBody = bodyBytes.toResponseBody(contentType)
                return response.newBuilder().body(newBody).build()
            }

            return response
        }
    }

    /**
     * Builds a safe OkHttpClient dedicated for Stremio Add-on network communication.
     */
    fun createSafeAddonOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(SafeDns())
            .addInterceptor(SafeAddonInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
