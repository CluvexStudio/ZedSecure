package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.core.ConnectionFault.Cause
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFaultTest {
    private fun cause(raw: String) = ConnectionFault.classify(raw).cause

    @Test
    fun `plain EOF is the server closing the connection`() {
        assertEquals(Cause.ServerClosed, cause("EOF"))
        assertEquals(Cause.ServerClosed, cause("proxy/vless/outbound: failed to read response > EOF"))
        assertEquals(Cause.ServerClosed, cause("unexpected EOF"))
    }

    @Test
    fun `eof inside another word is not EOF`() {
        assertEquals(Cause.Unknown, cause("reported thereof"))
        assertEquals(Cause.Unknown, cause("session a3eof9c2 rejected"))
    }

    @Test
    fun `a dial that timed out is a timeout, not an unreachable host`() {
        assertEquals(Cause.Timeout, cause("dial tcp 1.2.3.4:443: i/o timeout"))
        assertEquals(Cause.Timeout, cause("context deadline exceeded"))
    }

    @Test
    fun `a lookup that timed out is a DNS failure first`() {
        assertEquals(Cause.DnsFailed, cause("dial tcp: lookup example.com on 8.8.8.8:53: i/o timeout"))
        assertEquals(Cause.DnsFailed, cause("lookup bad.example: no such host"))
    }

    @Test
    fun `a certificate failure is named as such, not as a generic TLS failure`() {
        assertEquals(Cause.Certificate, cause("remote error: tls: x509: certificate signed by unknown authority"))
        assertEquals(Cause.Certificate, cause("x509: certificate has expired or is not yet valid"))
    }

    @Test
    fun `a rejected handshake is a TLS failure`() {
        assertEquals(Cause.TlsHandshake, cause("remote error: tls: handshake failure"))
    }

    @Test
    fun `REALITY failures are recognised`() {
        assertEquals(Cause.Reality, cause("REALITY: processed invalid connection"))
    }

    @Test
    fun `network-level failures are told apart`() {
        assertEquals(Cause.Refused, cause("dial tcp 1.2.3.4:443: connect: connection refused"))
        assertEquals(Cause.Reset, cause("read tcp: connection reset by peer"))
        assertEquals(Cause.Unreachable, cause("dial tcp: connect: network is unreachable"))
    }

    @Test
    fun `transport and auth failures are recognised`() {
        assertEquals(Cause.Transport, cause("websocket: bad handshake"))
        assertEquals(Cause.Auth, cause("ssh: handshake failed: ssh: unable to authenticate, permission denied"))
    }

    @Test
    fun `the raw text is kept verbatim`() {
        val raw = "  proxy/vless: failed to read response > EOF  "
        assertEquals(raw.trim(), ConnectionFault.classify(raw).raw)
    }

    @Test
    fun `nothing to classify is unknown, not a crash`() {
        assertEquals(Cause.Unknown, cause(""))
        assertEquals(Cause.Unknown, ConnectionFault.classify(null).cause)
    }
}
