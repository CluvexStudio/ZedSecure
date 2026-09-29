package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object ShareLink {
    fun build(s: ServerConfig): String = when (s.protocol) {
        Protocol.VMESS -> buildVmess(s)
        Protocol.VLESS -> buildUri("vless", s.userId, s)
        Protocol.TROJAN -> buildUri("trojan", s.userId, s)
        Protocol.SHADOWSOCKS -> buildShadowsocks(s)
        Protocol.SOCKS, Protocol.HTTP -> buildUserPass(s)
        Protocol.HYSTERIA -> buildUri("hysteria2", s.secretKey.ifBlank { s.userId }, s)
        Protocol.WIREGUARD, Protocol.AMNEZIAWG -> buildWireguard(s)
    }

    private fun buildUserPass(s: ServerConfig): String {
        val scheme = s.protocol.id
        val host = if (s.address.contains(":")) "[${s.address}]" else s.address
        val fragment = if (s.remark.isBlank()) "" else "#${enc(s.remark)}"
        val creds = if (s.username.isBlank() && s.userId.isBlank()) {
            ""
        } else {
            Base64.encode("${s.username}:${s.userId}".toByteArray()) + "@"
        }
        return "$scheme://$creds$host:${s.port}$fragment"
    }

    private fun buildWireguard(s: ServerConfig): String {
        val host = if (s.address.contains(":")) "[${s.address}]" else s.address
        val fragment = if (s.remark.isBlank()) "" else "#${enc(s.remark)}"
        val params = buildList {
            if (s.peerPublicKey.isNotBlank()) add("publickey" to s.peerPublicKey)
            s.preSharedKey?.takeIf { it.isNotBlank() }?.let { add("presharedkey" to it) }
            if (s.localAddresses.isNotEmpty()) add("address" to s.localAddresses.joinToString(","))
            s.reserved?.takeIf { it.isNotBlank() }?.let { add("reserved" to it) }
            s.wireguardMtu?.let { add("mtu" to it.toString()) }
            s.wireguardKeepalive?.let { add("keepalive" to it.toString()) }
            if (s.dnsServers.isNotEmpty()) add("dns" to s.dnsServers.joinToString(","))
            if (s.allowedIps.isNotEmpty()) add("allowedips" to s.allowedIps.joinToString(","))
            addAll(s.awg.entries)
        }.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val scheme = if (s.protocol == Protocol.AMNEZIAWG) "awg" else "wireguard"
        return "$scheme://${enc(s.secretKey)}@$host:${s.port}?$params$fragment"
    }

    private fun buildVmess(s: ServerConfig): String {
        val net = s.transport.network
        val isGrpc = net == "grpc"
        val isKcp = net == "kcp" || net == "mkcp"
        val obj = buildJsonObject {
            put("v", "2")
            put("ps", s.remark)
            put("add", s.address)
            put("port", s.port.toString())
            put("id", s.userId)
            put("aid", (s.alterId ?: 0).toString())
            put("scy", s.vmessSecurity)
            put("net", net)

            put("type", (if (isGrpc) s.transport.mode else s.transport.headerType) ?: "none")
            put("host", (if (isGrpc) s.transport.authority else s.transport.host) ?: "")
            put("path", (when { isGrpc -> s.transport.serviceName; isKcp -> s.transport.seed; else -> s.transport.path }) ?: "")

            if (!isGrpc) s.transport.mode?.takeIf { it.isNotBlank() }?.let { put("mode", it) }
            s.transport.xhttpExtra?.takeIf { it.isNotBlank() }?.let { put("extra", it) }
            s.transport.finalMask?.takeIf { it.isNotBlank() }?.let { put("fm", it) }
            put("tls", s.tls.security)
            put("sni", s.tls.sni ?: "")
            put("alpn", s.tls.alpn ?: "")
            put("fp", s.tls.fingerprint ?: "")
            put("cs", s.tls.cipherSuites ?: "")

            s.tls.publicKey?.takeIf { it.isNotBlank() }?.let { put("pbk", it) }
            s.tls.shortId?.takeIf { it.isNotBlank() }?.let { put("sid", it) }
            s.tls.spiderX?.takeIf { it.isNotBlank() }?.let { put("spx", it) }
            s.tls.echConfigList?.takeIf { it.isNotBlank() }?.let { put("ech", it) }
            if (s.tls.allowInsecure) put("allowInsecure", "1")
        }
        val payload = Json.encodeToString(JsonObject.serializer(), obj)
        return "vmess://" + Base64.encode(payload.toByteArray())
    }

    private fun commonStreamParams(s: ServerConfig): List<Pair<String, String>> = buildList {
        add("type" to s.transport.network)
        if (s.tls.security.isNotBlank()) add("security" to s.tls.security)
        s.transport.host?.takeIf { it.isNotBlank() }?.let { add("host" to it) }
        s.transport.path?.takeIf { it.isNotBlank() }?.let { add("path" to it) }
        s.transport.serviceName?.takeIf { it.isNotBlank() }?.let { add("serviceName" to it) }
        s.transport.mode?.takeIf { it.isNotBlank() }?.let { add("mode" to it) }
        s.transport.authority?.takeIf { it.isNotBlank() }?.let { add("authority" to it) }
        s.transport.xhttpExtra?.takeIf { it.isNotBlank() }?.let { add("extra" to it) }
        s.transport.kcpMtu?.let { add("mtu" to it.toString()) }
        s.transport.kcpTti?.let { add("tti" to it.toString()) }
        s.transport.headerType?.takeIf { it.isNotBlank() }?.let { add("headerType" to it) }
        s.tls.sni?.takeIf { it.isNotBlank() }?.let { add("sni" to it) }
        s.tls.alpn?.takeIf { it.isNotBlank() }?.let { add("alpn" to it) }
        s.tls.fingerprint?.takeIf { it.isNotBlank() }?.let { add("fp" to it) }
        s.tls.publicKey?.takeIf { it.isNotBlank() }?.let { add("pbk" to it) }
        s.tls.shortId?.takeIf { it.isNotBlank() }?.let { add("sid" to it) }
        s.tls.spiderX?.takeIf { it.isNotBlank() }?.let { add("spx" to it) }
        s.tls.echConfigList?.takeIf { it.isNotBlank() }?.let { add("ech" to it) }
        s.tls.cipherSuites?.takeIf { it.isNotBlank() }?.let { add("cs" to it) }
        s.transport.finalMask?.takeIf { it.isNotBlank() }?.let { add("fm" to it) }

        if (s.protocol != Protocol.HYSTERIA) {
            s.pinnedCertSha256?.takeIf { it.isNotBlank() }?.let { add("pcs" to it) }
        }
        s.tls.verifyPeerCertByName?.takeIf { it.isNotBlank() }?.let { add("vcn" to it) }
        s.tls.mldsa65Verify?.takeIf { it.isNotBlank() }?.let { add("pqv" to it) }
        if (s.tls.allowInsecure) add("allowInsecure" to "1")
    }

    private fun buildUri(scheme: String, credential: String, s: ServerConfig): String {
        val params = buildList {
            addAll(commonStreamParams(s))
            if (scheme == "vless") {
                add("encryption" to s.encryption.ifBlank { "none" })
                s.flow?.takeIf { it.isNotBlank() }?.let { add("flow" to it) }
            }
            if (scheme == "hysteria2") {
                s.obfsPassword?.takeIf { it.isNotBlank() }?.let {
                    add("obfs" to "salamander")
                    add("obfs-password" to it)
                }
                s.portHopping?.takeIf { it.isNotBlank() }?.let { add("mport" to it) }
                s.pinnedCertSha256?.takeIf { it.isNotBlank() }?.let { add("pinSHA256" to it) }
                s.bandwidthUp?.takeIf { it.isNotBlank() }?.let { add("upmbps" to it) }
                s.bandwidthDown?.takeIf { it.isNotBlank() }?.let { add("downmbps" to it) }
            }
        }.joinToString("&") { (k, v) -> "$k=${enc(v)}" }

        val host = if (s.address.contains(":")) "[${s.address}]" else s.address
        val fragment = if (s.remark.isBlank()) "" else "#${enc(s.remark)}"
        return "$scheme://${enc(credential)}@$host:${s.port}?$params$fragment"
    }

    private fun buildShadowsocks(s: ServerConfig): String {
        val creds = Base64.encode("${s.shadowsocksMethod ?: "aes-256-gcm"}:${s.userId}".toByteArray())
        val host = if (s.address.contains(":")) "[${s.address}]" else s.address
        val fragment = if (s.remark.isBlank()) "" else "#${enc(s.remark)}"

        val query = commonStreamParams(s)
            .filterNot { it.first == "type" && it.second == "tcp" }
            .joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val q = if (query.isBlank()) "" else "?$query"
        return "ss://$creds@$host:${s.port}$q$fragment"
    }

    private fun enc(value: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
        val sb = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            if (c < 128 && unreserved.indexOf(c.toChar()) >= 0) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
        return sb.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
