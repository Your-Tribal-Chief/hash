package com.hash.browser.core

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.resume

sealed class TunnelStatus {
    object Off : TunnelStatus()
    object Connecting : TunnelStatus()
    data class On(val server: ProxyServer, val exitIp: String, val latencyMs: Long) : TunnelStatus()
    data class Failed(val message: String, val killSwitchActive: Boolean) : TunnelStatus()
}

/**
 * Hash Tunnel: routes this browser's traffic (only this app) through a user-provided
 * HTTP or SOCKS5 proxy using WebView's proxy override. It does not create a system VPN,
 * so it can run alongside other VPN apps such as Tailscale.
 */
object Tunnel {

    val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /** Credentials used when a proxy asks for authentication. */
    @Volatile var activeServer: ProxyServer? = null
        private set

    private val direct = Executor { it.run() }

    suspend fun connect(server: ProxyServer, bypass: List<String>, killSwitch: Boolean): TunnelStatus {
        if (!supported) return TunnelStatus.Failed("This device's WebView does not support proxy override. Update Android System WebView.", false)

        // Check the server works before routing pages through it.
        val check = withContext(Dispatchers.IO) { healthCheck(server) }
        if (check is HealthResult.Fail) {
            return if (killSwitch) {
                applyBlackhole()
                TunnelStatus.Failed(check.message, true)
            } else {
                clear()
                TunnelStatus.Failed(check.message, false)
            }
        }
        check as HealthResult.Ok

        val builder = ProxyConfig.Builder().addProxyRule(server.rule())
        bypass.map { it.trim() }.filter { it.isNotEmpty() }.forEach { builder.addBypassRule(it) }
        builder.addBypassRule("<local>")
        if (!killSwitch) builder.addDirect()
        apply(builder.build())
        activeServer = server
        return TunnelStatus.On(server, check.ip, check.latency)
    }

    suspend fun clear() {
        activeServer = null
        if (!supported) return
        suspendCancellableCoroutine<Unit> { cont ->
            ProxyController.getInstance().clearProxyOverride(direct) { if (cont.isActive) cont.resume(Unit) }
        }
    }

    /** Kill switch: point everything at a dead proxy so no page loads directly. */
    private suspend fun applyBlackhole() {
        activeServer = null
        apply(ProxyConfig.Builder().addProxyRule("http://127.0.0.1:9").build())
    }

    private suspend fun apply(config: ProxyConfig) {
        suspendCancellableCoroutine<Unit> { cont ->
            ProxyController.getInstance().setProxyOverride(config, direct) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private sealed class HealthResult {
        data class Ok(val ip: String, val latency: Long) : HealthResult()
        data class Fail(val message: String) : HealthResult()
    }

    private fun healthCheck(server: ProxyServer): HealthResult {
        val type = if (server.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val proxy = Proxy(type, InetSocketAddress.createUnresolved(server.host, server.port))
        if (server.username.isNotEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(server.username, server.password.toCharArray())
            })
        }
        val start = System.currentTimeMillis()
        return try {
            val conn = URL("https://api.ipify.org").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (server.username.isNotEmpty() && server.type == ProxyType.HTTP) {
                val token = android.util.Base64.encodeToString(
                    "${server.username}:${server.password}".toByteArray(), android.util.Base64.NO_WRAP
                )
                conn.setRequestProperty("Proxy-Authorization", "Basic $token")
            }
            val code = conn.responseCode
            if (code == 407) return HealthResult.Fail("Authentication failed — check username and password.")
            if (code !in 200..299) return HealthResult.Fail("Server answered with HTTP $code.")
            val ip = conn.inputStream.bufferedReader().use { it.readText().trim() }
            HealthResult.Ok(ip, System.currentTimeMillis() - start)
        } catch (e: java.net.SocketTimeoutException) {
            HealthResult.Fail("Timed out — the server did not respond.")
        } catch (e: java.net.ConnectException) {
            HealthResult.Fail("Server unreachable — check host and port.")
        } catch (e: Exception) {
            HealthResult.Fail("Connection failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Parse socks5://user:pass@host:port or http://host:port. */
    fun parseUri(uri: String, name: String? = null): ProxyServer? {
        val s = uri.trim()
        val type = when {
            s.startsWith("socks5://") || s.startsWith("socks://") -> ProxyType.SOCKS5
            s.startsWith("http://") || s.startsWith("https://") -> ProxyType.HTTP
            else -> return null
        }
        val rest = s.substringAfter("://").trimEnd('/')
        val auth = if (rest.contains('@')) rest.substringBeforeLast('@') else ""
        val hostPort = rest.substringAfterLast('@')
        val host = hostPort.substringBeforeLast(':')
        val port = hostPort.substringAfterLast(':', "").toIntOrNull()
            ?: if (type == ProxyType.SOCKS5) 1080 else 8080
        if (host.isBlank()) return null
        return ProxyServer(
            id = UUID.randomUUID().toString(),
            name = name ?: host,
            type = type,
            host = host,
            port = port,
            username = auth.substringBefore(':', ""),
            password = if (auth.contains(':')) auth.substringAfter(':') else "",
        )
    }
}
