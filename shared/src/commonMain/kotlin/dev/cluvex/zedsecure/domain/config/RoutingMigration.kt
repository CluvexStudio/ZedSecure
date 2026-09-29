package dev.cluvex.zedsecure.domain.config

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RoutingPresetType
import dev.cluvex.zedsecure.domain.model.RoutingPresets
import dev.cluvex.zedsecure.domain.model.RulesetItem

object RoutingMigration {
    fun effectiveRulesets(settings: AppSettings): List<RulesetItem> =
        if (settings.routingMigrated) settings.rulesets
        else settings.rulesets.ifEmpty { fromLegacy(settings) }

    fun seedIfFresh(settings: AppSettings): AppSettings? {
        if (settings.routingSeeded) return null
        val untouched = settings.rulesets.isEmpty() &&
            settings.customProxyRules.isBlank() &&
            settings.customDirectRules.isBlank() &&
            settings.customBlockRules.isBlank()
        return settings.copy(
            rulesets = if (untouched) RoutingPresets.rules(RoutingPresetType.IranWhitelist)
            else settings.rulesets,

            routingSeeded = true,
            routingMigrated = if (untouched) true else settings.routingMigrated,
        )
    }

    fun fromLegacy(settings: AppSettings): List<RulesetItem> = buildList {
        addAll(rulesFrom(settings.customBlockRules, RulesetItem.OUTBOUND_BLOCK, "Custom block"))
        addAll(rulesFrom(settings.customDirectRules, RulesetItem.OUTBOUND_DIRECT, "Custom direct"))
        addAll(rulesFrom(settings.customProxyRules, RulesetItem.OUTBOUND_PROXY, "Custom proxy"))
    }

    private fun rulesFrom(raw: String, tag: String, remarks: String): List<RulesetItem> {
        val entries = raw.split('\n', ',').map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (entries.isEmpty()) return emptyList()
        val (ips, domains) = entries.partition { looksLikeAddress(it) }
        return buildList {
            if (domains.isNotEmpty()) add(
                RulesetItem(
                    id = "legacy-$tag-domain",
                    remarks = remarks,
                    outboundTag = tag,
                    domain = domains,
                ),
            )
            if (ips.isNotEmpty()) add(
                RulesetItem(
                    id = "legacy-$tag-ip",
                    remarks = if (domains.isEmpty()) remarks else "$remarks (IPs)",
                    outboundTag = tag,
                    ip = ips,
                ),
            )
        }
    }

    fun looksLikeAddress(entry: String): Boolean {
        if (entry.startsWith("geoip:")) return true
        if (entry.startsWith("geosite:") || entry.startsWith("domain:")) return false
        val host = entry.substringBefore('/')
        val v4 = host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }
        val v6 = host.contains(':') && host.all { it.isDigit() || it == ':' || it in 'a'..'f' || it in 'A'..'F' }
        return v4 || v6
    }
}
