package dev.cluvex.zedsecure.core

object RouteSplit {
    fun defaultExcluding(excluded: String): List<Pair<String, Int>> {
        val octets = excluded.trim().split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 } || excluded.count { it == '.' } != 3) {
            return emptyList()
        }
        val target = octets.fold(0L) { acc, o -> (acc shl 8) or o.toLong() }
        val routes = ArrayList<Pair<String, Int>>(32)
        for (prefix in 1..32) {
            val bit = 1L shl (32 - prefix)
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val sibling = (target xor bit) and mask
            routes += format(sibling) to prefix
        }
        return routes
    }

    private fun format(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    fun covers(route: Pair<String, Int>, ip: String): Boolean {
        fun toLong(s: String) = s.split('.').fold(0L) { acc, o -> (acc shl 8) or o.toLong() }
        val mask = if (route.second == 0) 0L else (0xFFFFFFFFL shl (32 - route.second)) and 0xFFFFFFFFL
        return (toLong(ip) and mask) == (toLong(route.first) and mask)
    }
}
