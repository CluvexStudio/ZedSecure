package dev.cluvex.zedsecure.core

data class ConnectionFault(
    val cause: Cause,

    val raw: String,
) {
    enum class Cause {
        Timeout,

        ServerClosed,

        Refused,

        Reset,

        Unreachable,

        DnsFailed,

        TlsHandshake,

        Certificate,

        Reality,

        Auth,

        Transport,

        Unknown,
    }

    companion object {
        private val RULES: List<Pair<Cause, List<String>>> = listOf(
            Cause.Reality to listOf("reality"),
            Cause.Certificate to listOf("x509:", "certificate signed by unknown authority",
                "certificate is not valid", "certificate has expired", "certificate is valid for"),
            Cause.TlsHandshake to listOf("tls: handshake failure", "remote error: tls",
                "tls: first record does not look like", "tls: bad certificate", "handshake failure"),
            Cause.Auth to listOf("invalid user", "authentication failed", "auth failed",
                "permission denied", "unauthorized", "status code 401", "status code 403",
                "wrong password", "invalid password", "bad password"),
            Cause.Transport to listOf("websocket: bad handshake", "bad handshake", "status code 404",
                "http2: ", "grpc: ", "unexpected status", "protocol error"),
            Cause.DnsFailed to listOf("no such host", "server misbehaving", "lookup "),
            Cause.Timeout to listOf("context deadline exceeded", "i/o timeout", "timed out",
                "timeout", "deadline exceeded"),
            Cause.Refused to listOf("connection refused"),
            Cause.Reset to listOf("connection reset", "broken pipe", "reset by peer",
                "forcibly closed"),
            Cause.Unreachable to listOf("no route to host", "network is unreachable",
                "host is unreachable", "network unreachable"),
            Cause.ServerClosed to listOf("unexpected eof", "eof"),
        )

        fun classify(raw: String?): ConnectionFault {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return ConnectionFault(Cause.Unknown, "")
            val lower = text.lowercase()
            val cause = RULES.firstOrNull { (_, needles) -> needles.any { matches(lower, it) } }?.first
                ?: Cause.Unknown
            return ConnectionFault(cause, text)
        }

        private fun matches(haystack: String, needle: String): Boolean {
            if (needle != "eof") return haystack.contains(needle)
            var from = 0
            while (true) {
                val at = haystack.indexOf("eof", from)
                if (at < 0) return false
                val before = if (at == 0) ' ' else haystack[at - 1]
                val after = if (at + 3 >= haystack.length) ' ' else haystack[at + 3]
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
                from = at + 1
            }
        }
    }
}
