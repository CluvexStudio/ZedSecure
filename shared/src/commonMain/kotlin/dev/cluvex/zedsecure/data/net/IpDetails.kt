package dev.cluvex.zedsecure.data.net

import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.platform.dnsOverTcp
import dev.cluvex.zedsecure.platform.httpGetViaSocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

enum class IpUsage { Hosting, Isp, Mobile, Business, Education, Government, Unknown }

data class IpFlags(
    val vpn: Boolean,
    val proxy: Boolean,
    val tor: Boolean,
    val datacenter: Boolean,
    val abuser: Boolean,
    val vpnService: String? = null,
)

data class IpRegistry(
    val name: String? = null,
    val handle: String? = null,
    val range: String? = null,
    val owner: String? = null,
    val country: String? = null,
    val registered: String? = null,
    val rir: String? = null,
) {
    val isEmpty: Boolean get() = name == null && owner == null && range == null && registered == null
}

data class DnsCheck(
    val resolverIp: String,
    val isp: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val fake: Boolean = false,
)

data class IpDetails(
    val ip: String? = null,
    val ipv6: String? = null,
    val hostname: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val postal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val isp: String? = null,
    val org: String? = null,
    val asn: Long? = null,
    val asnDomain: String? = null,
    val route: String? = null,
    val usage: IpUsage = IpUsage.Unknown,
    val flags: IpFlags? = null,
    val registry: IpRegistry? = null,
    val dns: DnsCheck? = null,
    val sources: List<String> = emptyList(),
    val viaTunnel: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val fetchedAt: Long = 0L,
)

object IpDetailsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseObject(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun JsonObject?.obj(key: String): JsonObject? = runCatching { this?.get(key)?.jsonObject }.getOrNull()

    private fun JsonObject?.array(key: String): JsonArray? = runCatching { this?.get(key)?.jsonArray }.getOrNull()

    private fun JsonObject?.text(key: String): String? =
        runCatching { this?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject?.num(key: String): Double? =
        runCatching { this?.get(key)?.jsonPrimitive?.doubleOrNull }.getOrNull()

    private fun JsonObject?.flag(key: String): Boolean? =
        runCatching { this?.get(key)?.jsonPrimitive?.booleanOrNull }.getOrNull()

    private fun JsonElement.asObject(): JsonObject? = runCatching { jsonObject }.getOrNull()

    fun ipwho(body: String): IpDetails? {
        val o = parseObject(body) ?: return null
        if (o.flag("success") == false) return null
        val ip = o.text("ip") ?: return null
        val connection = o.obj("connection")
        return IpDetails(
            ip = ip,
            country = o.text("country"),
            countryCode = o.text("country_code")?.uppercase(),
            region = o.text("region"),
            city = o.text("city"),
            postal = o.text("postal"),
            latitude = o.num("latitude"),
            longitude = o.num("longitude"),
            timezone = o.obj("timezone").text("id"),
            isp = connection.text("isp"),
            org = connection.text("org"),
            asn = connection.num("asn")?.toLong()?.takeIf { it > 0 },
            asnDomain = connection.text("domain"),
            sources = listOf("ipwho.is"),
        )
    }

    fun ipapi(body: String): IpDetails? {
        val o = parseObject(body) ?: return null
        val ip = o.text("ip") ?: return null
        val asn = o.obj("asn")
        val company = o.obj("company")
        val location = o.obj("location")
        val datacenter = o.flag("is_datacenter") == true
        val type = (company.text("type") ?: asn.text("type"))?.lowercase()
        val usage = when {
            o.flag("is_mobile") == true -> IpUsage.Mobile
            datacenter || type == "hosting" -> IpUsage.Hosting
            type == "isp" -> IpUsage.Isp
            type == "business" -> IpUsage.Business
            type == "education" -> IpUsage.Education
            type == "government" -> IpUsage.Government
            else -> IpUsage.Unknown
        }
        return IpDetails(
            ip = ip,
            country = location.text("country"),
            countryCode = location.text("country_code")?.uppercase(),
            region = location.text("state"),
            city = location.text("city"),
            postal = location.text("zip"),
            latitude = location.num("latitude"),
            longitude = location.num("longitude"),
            timezone = location.text("timezone"),
            isp = company.text("name") ?: asn.text("org"),
            org = asn.text("org"),
            asn = asn.num("asn")?.toLong()?.takeIf { it > 0 },
            asnDomain = asn.text("domain") ?: company.text("domain"),
            route = asn.text("route"),
            usage = usage,
            flags = IpFlags(
                vpn = o.flag("is_vpn") == true,
                proxy = o.flag("is_proxy") == true,
                tor = o.flag("is_tor") == true,
                datacenter = datacenter,
                abuser = o.flag("is_abuser") == true,
                vpnService = o.obj("vpn").text("service"),
            ),
            registry = (o.text("rir") ?: asn.text("rir"))?.let { IpRegistry(rir = it) },
            sources = listOf("ipapi.is"),
        )
    }

    fun merge(primary: IpDetails?, extra: IpDetails?): IpDetails? {
        if (primary == null) return extra
        if (extra == null) return primary
        if (primary.ip != extra.ip) return primary
        val sameCountry = primary.countryCode == null || primary.countryCode == extra.countryCode
        return primary.copy(
            country = primary.country ?: extra.country.takeIf { sameCountry },
            countryCode = primary.countryCode ?: extra.countryCode,
            region = primary.region ?: extra.region.takeIf { sameCountry },
            city = primary.city ?: extra.city.takeIf { sameCountry },
            postal = primary.postal ?: extra.postal.takeIf { sameCountry },
            latitude = primary.latitude ?: extra.latitude.takeIf { sameCountry },
            longitude = primary.longitude ?: extra.longitude.takeIf { sameCountry },
            timezone = primary.timezone ?: extra.timezone.takeIf { sameCountry },
            isp = primary.isp ?: extra.isp,
            org = primary.org ?: extra.org,
            asn = primary.asn ?: extra.asn,
            asnDomain = primary.asnDomain ?: extra.asnDomain,
            route = primary.route ?: extra.route,
            usage = if (primary.usage == IpUsage.Unknown) extra.usage else primary.usage,
            flags = primary.flags ?: extra.flags,
            registry = primary.registry ?: extra.registry,
            sources = (primary.sources + extra.sources).distinct(),
        )
    }

    fun ptr(body: String): String? {
        val o = parseObject(body) ?: return null
        if (o.num("Status")?.toInt() != 0) return null
        return o.array("Answer")?.firstNotNullOfOrNull { answer ->
            val a = answer.asObject() ?: return@firstNotNullOfOrNull null
            if (a.num("type")?.toInt() == DnsWire.TYPE_PTR) a.text("data")?.trimEnd('.')?.takeIf { it.isNotEmpty() } else null
        }
    }

    fun rdap(body: String): IpRegistry? {
        val o = parseObject(body) ?: return null
        if (o.text("errorCode") != null) return null
        val cidrs = o.array("cidr0_cidrs")?.mapNotNull { entry ->
            val c = entry.asObject() ?: return@mapNotNull null
            val prefix = c.text("v4prefix") ?: c.text("v6prefix") ?: return@mapNotNull null
            val length = c.num("length")?.toInt() ?: return@mapNotNull null
            "$prefix/$length"
        }.orEmpty()
        val start = o.text("startAddress")
        val end = o.text("endAddress")
        val range = cidrs.joinToString(", ").ifBlank { if (start != null && end != null) "$start – $end" else "" }.ifBlank { null }
        val registered = o.array("events")?.firstNotNullOfOrNull { event ->
            val e = event.asObject() ?: return@firstNotNullOfOrNull null
            if (e.text("eventAction") == "registration") e.text("eventDate")?.take(10) else null
        }
        val registry = IpRegistry(
            name = o.text("name"),
            handle = o.text("handle"),
            range = range,
            owner = ownerOf(o),
            country = o.text("country")?.uppercase(),
            registered = registered,
            rir = rirOf(o.text("port43")),
        )
        return registry.takeUnless { it.isEmpty }
    }

    private fun ownerOf(o: JsonObject): String? {
        val entities = flatten(o.array("entities"))
        fun named(role: String) = entities.firstNotNullOfOrNull { entity ->
            val roles = entity.array("roles")?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
            if (role in roles) fullName(entity) else null
        }
        return named("registrant") ?: named("administrative")
    }

    private fun flatten(list: JsonArray?): List<JsonObject> {
        if (list == null) return emptyList()
        val out = ArrayList<JsonObject>()
        list.forEach { element ->
            val entity = element.asObject() ?: return@forEach
            out += entity
            out += flatten(entity.array("entities"))
        }
        return out
    }

    private fun fullName(entity: JsonObject): String? {
        val card = runCatching { entity["vcardArray"]?.jsonArray?.getOrNull(1)?.jsonArray }.getOrNull() ?: return null
        return card.firstNotNullOfOrNull { field ->
            val parts = runCatching { field.jsonArray }.getOrNull() ?: return@firstNotNullOfOrNull null
            val key = runCatching { parts[0].jsonPrimitive.contentOrNull }.getOrNull()
            if (key == "fn") runCatching { parts[3].jsonPrimitive.contentOrNull }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } else null
        }
    }

    private fun rirOf(port43: String?): String? {
        val host = port43?.lowercase() ?: return null
        return when {
            "ripe" in host -> "RIPE NCC"
            "arin" in host -> "ARIN"
            "apnic" in host -> "APNIC"
            "lacnic" in host -> "LACNIC"
            "afrinic" in host -> "AFRINIC"
            else -> null
        }
    }
}

class IpDetailsRepository(
    private val get: suspend (url: String) -> String = { url -> detailsGet(url) },
) {
    suspend fun fetch(): IpDetails = coroutineScope {
        val port = VpnManager.activeSocksPort
        val tunnel = port != null || VpnManager.status.value.state == ConnectionState.Connected
        val who = async(Dispatchers.IO) { runCatching { IpDetailsParser.ipwho(get(IPWHOIS_URL)) }.getOrNull() }
        val api = async(Dispatchers.IO) { runCatching { IpDetailsParser.ipapi(get(IPAPI_IS_URL)) }.getOrNull() }
        val v6 = async(Dispatchers.IO) { runCatching { get(IPV6_URL).trim().takeIf { ':' in it } }.getOrNull() }
        val dns = async(Dispatchers.IO) { if (tunnel) dnsCheck(port) else null }

        val merged = IpDetailsParser.merge(who.await(), api.await())
            ?: return@coroutineScope IpDetails(
                ipv6 = v6.await(),
                dns = dns.await(),
                viaTunnel = tunnel,
                failed = true,
                fetchedAt = currentTimeMillis(),
            )
        val ip = merged.ip
        val hostname = async(Dispatchers.IO) { ip?.let { reverse(it) } }
        val registry = async(Dispatchers.IO) { ip?.let { registry(it) } }
        val found = registry.await()
        merged.copy(
            ipv6 = v6.await()?.takeIf { it != ip },
            hostname = hostname.await(),
            registry = found?.copy(rir = found.rir ?: merged.registry?.rir) ?: merged.registry,
            dns = dns.await(),
            viaTunnel = tunnel,
            sources = (merged.sources + listOfNotNull(RDAP_SOURCE.takeIf { found != null })).distinct(),
            fetchedAt = currentTimeMillis(),
        )
    }

    private suspend fun reverse(ip: String): String? {
        val name = DnsWire.reverseName(ip) ?: return null
        return runCatching { IpDetailsParser.ptr(get("$PTR_URL$name&type=PTR")) }.getOrNull()
    }

    private suspend fun registry(ip: String): IpRegistry? =
        runCatching { IpDetailsParser.rdap(get("$RDAP_URL$ip")) }.getOrNull()

    private suspend fun dnsCheck(port: Int?): DnsCheck? {
        val id = Random.nextInt(1, 0xffff)
        val reply = runCatching {
            dnsOverTcp(DNS_PROBE_SERVER, 53, DnsWire.query(id, WHOAMI_HOST, DnsWire.TYPE_A), port, DNS_TIMEOUT_MS)
        }.getOrNull() ?: return null
        val resolver = DnsWire.answers(reply, id).firstOrNull { it.type == DnsWire.TYPE_A }?.data ?: return null
        if (DnsWire.isFakeDnsAddress(resolver)) return DnsCheck(resolverIp = resolver, fake = true)
        val geo = runCatching { IpDetailsParser.ipwho(get("$IPWHOIS_URL$resolver")) }.getOrNull()
        return DnsCheck(
            resolverIp = resolver,
            isp = geo?.isp ?: geo?.org,
            country = geo?.country,
            countryCode = geo?.countryCode,
        )
    }

    companion object {
        const val IPWHOIS_URL = "https://ipwho.is/"
        const val IPAPI_IS_URL = "https://api.ipapi.is/"
        const val IPV6_URL = "https://api64.ipify.org"
        const val PTR_URL = "https://dns.google/resolve?name="
        const val RDAP_URL = "https://rdap.org/ip/"
        const val RDAP_SOURCE = "RDAP"
        const val WHOAMI_HOST = "whoami.akamai.net"
        const val DNS_PROBE_SERVER = "1.1.1.1"
        private const val TIMEOUT_MS = 7_000
        private const val DNS_TIMEOUT_MS = 7_000

        private suspend fun detailsGet(url: String): String = httpGetViaSocks(
            url,
            socksPort = VpnManager.activeSocksPort,
            userAgent = "ZedSecure",
            connectTimeoutMs = TIMEOUT_MS,
            readTimeoutMs = TIMEOUT_MS,
            closeConnection = true,
        )
    }
}
