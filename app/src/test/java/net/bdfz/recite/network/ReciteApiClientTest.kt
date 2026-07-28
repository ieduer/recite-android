package net.bdfz.recite.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class ReciteApiClientTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val lastRequestBody = AtomicReference("")

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        baseUrl = "http://127.0.0.1:${server.address.port}"
        server.createContext("/api/login") { exchange ->
            lastRequestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.responseHeaders.add(
                "Set-Cookie",
                "bdfz_uc_session=opaque-session; Path=/; HttpOnly; Secure; SameSite=Lax",
            )
            exchange.respond(
                200,
                """{"ok":true,"user":{"slug":"reader-1","displayName":"Reader One"}}""",
            )
        }
        server.createContext("/api/register") { exchange ->
            lastRequestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.respond(200, """{"ok":true,"slug":"reader-1"}""")
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun loginKeepsOnlyCookiePairAndNeverPasswordInSession() {
        val client = ReciteApiClient(userCenterUrl = baseUrl, stublogsUrl = baseUrl)
        val session = client.login("reader-1", "not-stored-password")

        assertEquals("reader-1", session.slug)
        assertEquals("Reader One", session.displayName)
        assertEquals("bdfz_uc_session=opaque-session", session.cookie)
        assertFalse(session.toString().contains("not-stored-password"))
        assertEquals("not-stored-password", JSONObject(lastRequestBody.get()).getString("password"))
    }

    @Test
    fun registrationUsesExistingStublogsContract() {
        val client = ReciteApiClient(userCenterUrl = baseUrl, stublogsUrl = baseUrl)
        client.registerUsername("reader-1", "Reader One", "a-secure-password", "invite")

        val body = JSONObject(lastRequestBody.get())
        assertEquals("reader-1", body.getString("slug"))
        assertEquals("Reader One", body.getString("displayName"))
        assertEquals("a-secure-password", body.getString("adminPassword"))
        assertTrue(body.has("inviteCode"))
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
