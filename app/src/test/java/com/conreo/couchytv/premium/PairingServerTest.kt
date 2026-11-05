package com.conreo.couchytv.premium

import java.net.InetAddress
import java.net.Socket
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class PairingServerTest {
    private fun request(server: PairingServer, path: String, body: String, token: String? = null,
                        origin: String = server.url, host: String = URI(server.url).authority, length: Int = body.length): String {
        val uri = URI(server.url)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 2000
            val auth = token?.let { "Authorization: Bearer $it\r\n" }.orEmpty()
            socket.getOutputStream().write(("POST $path HTTP/1.1\r\nHost: $host\r\nOrigin: $origin\r\nContent-Length: $length\r\n${auth}\r\n$body").toByteArray())
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }
    private fun session(onImage: (ByteArray) -> Unit = {}) = PairingServer(InetAddress.getLoopbackAddress(), onImage = onImage)

    @Test fun pairedUploadRequiresTokenAndClosesAfterSuccess() {
        var received = ""
        session { received = it.toString(Charsets.UTF_8) }.use { server ->
            assertTrue(request(server, "/icon", "image").startsWith("HTTP/1.1 403"))
            assertTrue(request(server, "/pair", "wrong").startsWith("HTTP/1.1 403"))
            val pair = request(server, "/pair", server.code)
            assertTrue(pair.startsWith("HTTP/1.1 200"))
            val token = pair.substringAfter("\r\n\r\n")
            assertEquals(48, token.length)
            assertTrue(request(server, "/icon", "image", token).startsWith("HTTP/1.1 200"))
            assertEquals("image", received)
            assertTrue(runCatching { request(server, "/icon", "again", token) }.isFailure)
        }
    }
    @Test fun rejectsCrossOriginAndDnsRebinding() {
        session().use { server ->
            assertTrue(request(server, "/pair", server.code, origin = "https://evil.example").startsWith("HTTP/1.1 403"))
            assertTrue(request(server, "/pair", server.code, host = "evil.example").startsWith("HTTP/1.1 403"))
        }
    }
    @Test fun rejectsOversizedBodyBeforeReadingIt() {
        session().use { server -> assertTrue(request(server, "/icon", "", length = 4 * 1024 * 1024 + 1).startsWith("HTTP/1.1 413")) }
    }
    @Test fun closesAfterPairingAttemptLimit() {
        session().use { server ->
            repeat(8) { assertTrue(request(server, "/pair", "bad").startsWith("HTTP/1.1 403")) }
            assertTrue(request(server, "/pair", server.code).startsWith("HTTP/1.1 429"))
            assertTrue(runCatching { request(server, "/pair", server.code) }.isFailure)
        }
    }
    @Test fun invalidImageCanBeRetriedWithoutSaving() {
        var calls = 0
        session { calls++; error("Invalid image") }.use { server ->
            val token = request(server, "/pair", server.code).substringAfter("\r\n\r\n")
            assertTrue(request(server, "/icon", "bad", token).startsWith("HTTP/1.1 400"))
            assertTrue(request(server, "/icon", "bad", token).startsWith("HTTP/1.1 400"))
            assertEquals(2, calls)
        }
    }
    @Test fun manualCancellationClosesSocket() {
        val server = session(); server.close(); server.close()
        assertTrue(runCatching { request(server, "/pair", server.code) }.isFailure)
    }
}
