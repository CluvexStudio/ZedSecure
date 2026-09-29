package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.RulesetItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object RulesetTransfer {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(rules: List<RulesetItem>): String =
        json.encodeToString(ListSerializer(ExportedRule.serializer()), rules.map(ExportedRule::from))

    fun decode(text: String?): List<RulesetItem>? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching {
            json.decodeFromString(ListSerializer(ExportedRule.serializer()), trimmed)
        }.getOrNull() ?: return null

        return parsed.map { it.toItem() }.filterNot { it.isEmpty }
    }
}

@kotlinx.serialization.Serializable
private data class ExportedRule(
    val remarks: String = "",
    val outboundTag: String = RulesetItem.OUTBOUND_PROXY,
    val domain: List<String> = emptyList(),
    val ip: List<String> = emptyList(),
    val port: String = "",
    val network: String = "",
    val protocol: List<String> = emptyList(),
    val enabled: Boolean = true,
    val locked: Boolean = false,
) {
    fun toItem() = RulesetItem(
        id = RulesetItem.newId(),
        remarks = remarks,
        outboundTag = outboundTag,
        domain = domain,
        ip = ip,
        port = port,
        network = network,
        protocol = protocol,
        enabled = enabled,
        locked = locked,
    )

    companion object {
        fun from(item: RulesetItem) = ExportedRule(
            remarks = item.remarks,
            outboundTag = item.outboundTag,
            domain = item.domain,
            ip = item.ip,
            port = item.port,
            network = item.network,
            protocol = item.protocol,
            enabled = item.enabled,
            locked = item.locked,
        )
    }
}
