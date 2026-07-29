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
    private val lastFeedbackBody = AtomicReference("")
    private val lastFeedbackCookie = AtomicReference("")
    private val lastRankingMethod = AtomicReference("")
    private val lastRankingCookie = AtomicReference("")

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
        server.createContext("/api/feedback") { exchange ->
            lastFeedbackBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            lastFeedbackCookie.set(exchange.requestHeaders.getFirst("Cookie").orEmpty())
            exchange.respond(
                200,
                """{"ok":true,"feedbackId":"feedback-1","notification":{"channel":"telegram","sent":true}}""",
            )
        }
        server.createContext("/api/rankings") { exchange ->
            lastRankingMethod.set(exchange.requestMethod)
            lastRankingCookie.set(exchange.requestHeaders.getFirst("Cookie").orEmpty())
            exchange.respond(
                200,
                """
                {
                  "daily":[
                    {"position":1,"displayName":"學子·A1B2","totalPoints":375,"todayPoints":5,"rankName":"巔峰","isMe":true},
                    {"position":2,"displayName":"","totalPoints":999,"todayPoints":999,"rankName":"unknown","isMe":false}
                  ],
                  "total":[
                    {"position":3,"displayName":"學子·C3D4","totalPoints":315,"todayPoints":1,"rankName":"殿堂","isMe":false}
                  ],
                  "meDaily":{"position":1,"displayName":"學子·A1B2","totalPoints":375,"todayPoints":5,"rankName":"巔峰","isMe":true},
                  "meTotal":null,
                  "generatedAt":"2026-07-29T05:00:00.000Z"
                }
                """.trimIndent(),
            )
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun loginKeepsOnlyCookiePairAndNeverPasswordInSession() {
        val client = ReciteApiClient(userCenterUrl = baseUrl)
        val session = client.login("CaseUser01", "not-stored-password")

        assertEquals("reader-1", session.slug)
        assertEquals("Reader One", session.displayName)
        assertEquals("bdfz_uc_session=opaque-session", session.cookie)
        assertFalse(session.toString().contains("not-stored-password"))
        val body = JSONObject(lastRequestBody.get())
        assertEquals("CaseUser01", body.getString("username"))
        assertEquals("not-stored-password", body.getString("password"))
    }

    @Test
    fun feedbackUsesReciteSiteAndExistingSession() {
        val client = ReciteApiClient(userCenterUrl = baseUrl)
        val session = client.login("CaseUser01", "not-stored-password")
        val receipt = client.submitFeedback(
            session = session,
            category = "content",
            title = "篇目文字",
            description = "這是一條測試反饋。",
        )

        assertEquals("feedback-1", receipt.feedbackId)
        assertTrue(receipt.notificationSent)
        assertEquals("bdfz_uc_session=opaque-session", lastFeedbackCookie.get())
        val body = JSONObject(lastFeedbackBody.get())
        assertEquals("recite", body.getString("siteKey"))
        assertEquals("content", body.getString("category"))
        assertEquals("篇目文字", body.getString("title"))
        assertEquals("這是一條測試反饋。", body.getString("description"))
        assertEquals("android", body.getJSONObject("clientContext").getString("platform"))
    }

    @Test
    fun authenticatedLeaderboardSyncUsesPostAndParsesRanksSafely() {
        val client = ReciteApiClient(userCenterUrl = baseUrl, reciteApiUrl = baseUrl)
        val session = client.login("CaseUser01", "not-stored-password")

        val snapshot = client.loadLeaderboard(session, syncCurrentUser = true)

        assertEquals("POST", lastRankingMethod.get())
        assertEquals("bdfz_uc_session=opaque-session", lastRankingCookie.get())
        assertEquals(1, snapshot.daily.size)
        assertEquals("巔峰", snapshot.daily.first().rank.name)
        assertTrue(snapshot.daily.first().isMe)
        assertEquals("殿堂", snapshot.total.first().rank.name)
        assertEquals("2026-07-29T05:00:00.000Z", snapshot.generatedAt)
    }

    @Test
    fun anonymousLeaderboardUsesGetWithoutCookie() {
        val client = ReciteApiClient(userCenterUrl = baseUrl, reciteApiUrl = baseUrl)

        client.loadLeaderboard(session = null, syncCurrentUser = false)

        assertEquals("GET", lastRankingMethod.get())
        assertEquals("", lastRankingCookie.get())
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
