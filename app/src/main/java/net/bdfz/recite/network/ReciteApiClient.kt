package net.bdfz.recite.network

import net.bdfz.recite.BuildConfig
import net.bdfz.recite.data.PieceProgressEntity
import net.bdfz.recite.ranking.LeaderboardEntry
import net.bdfz.recite.ranking.LeaderboardSnapshot
import net.bdfz.recite.ranking.ReciteRanks
import net.bdfz.recite.security.AppSession
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReciteApiClient(
    private val userCenterUrl: String = BuildConfig.USER_CENTER_URL,
    private val reciteApiUrl: String = BuildConfig.RECITE_API_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    fun login(username: String, password: String): AppSession {
        val body = JSONObject()
            .put("username", username.trim())
            .put("password", password)
        val response = executeJson(
            Request.Builder()
                .url("${userCenterUrl.trimEnd('/')}/api/login")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
        val payload = response.payload
        val cookie = response.headers
            .values("Set-Cookie")
            .asSequence()
            .map { it.substringBefore(';').trim() }
            .firstOrNull { it.startsWith("bdfz_uc_session=") }
            ?: throw ApiException("登入成功，但伺服器沒有返回 App 會話。")
        val user = payload.optJSONObject("user") ?: JSONObject()
        val slug = user.optString("slug").ifBlank { username.trim() }
        return AppSession(
            slug = slug,
            displayName = user.optString("displayName", slug).ifBlank { slug },
            cookie = cookie,
        )
    }

    fun logout(session: AppSession) {
        executeJson(
            Request.Builder()
                .url("${userCenterUrl.trimEnd('/')}/api/logout")
                .header("Cookie", session.cookie)
                .post(ByteArray(0).toRequestBody(null))
                .build(),
        )
    }

    fun pullProgress(session: AppSession): List<PieceProgressEntity> {
        val response = executeJson(
            Request.Builder()
                .url("${userCenterUrl.trimEnd('/')}/api/progress?site=recite")
                .header("Cookie", session.cookie)
                .get()
                .build(),
        )
        val items = response.payload.optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            repeat(items.length()) { index ->
                val item = items.getJSONObject(index)
                val meta = item.optJSONObject("meta") ?: JSONObject()
                val remotePercent = meta.optInt(
                    "progressPercent",
                    item.optInt("progressPercent", item.optInt("score", 0)),
                )
                val state = item.optString("state")
                val sealed = meta.optBoolean("sealed") ||
                    state in setOf("completed", "complete", "done", "passed") ||
                    remotePercent >= 100
                add(
                    PieceProgressEntity(
                        pieceId = item.getString("itemKey"),
                        stage = if (sealed) 5 else maxOf(meta.optInt("stage"), remotePercent / 20),
                        readPercent = meta.optInt("readPercent"),
                        peeks = meta.optInt("peeks"),
                        quizBest = meta.optInt("quizBest"),
                        dictBest = meta.optInt("dictBest"),
                        voiceBest = meta.optInt("voiceBest"),
                        examBest = meta.optInt("examBest"),
                        clozeAttempts = meta.optInt("cloze1Attempts") +
                            meta.optInt("cloze2Attempts") +
                            meta.optInt("cloze3Attempts"),
                        quizAttempts = meta.optInt("quizAttempts"),
                        dictationAttempts = meta.optInt("dictationAttempts"),
                        examAttempts = meta.optInt("examAttempts"),
                        firstStartedAt = meta.optString("firstStartedAt"),
                        lastActivityAt = meta.optString(
                            "lastActivityAt",
                            item.optString("lastActivityAt"),
                        ),
                        lastReviewedAt = meta.optString("lastReviewedAt"),
                    ),
                )
            }
        }
    }

    fun pushProgress(session: AppSession, payloadJson: String) {
        executeJson(
            Request.Builder()
                .url("${userCenterUrl.trimEnd('/')}/api/progress")
                .header("Cookie", session.cookie)
                .put(payloadJson.toRequestBody(JSON))
                .build(),
        )
    }

    fun submitFeedback(
        session: AppSession?,
        category: String,
        title: String,
        description: String,
    ): FeedbackReceipt {
        val body = JSONObject()
            .put("siteKey", "recite")
            .put("siteTitle", "琅琅 Android")
            .put("pageTitle", "Android App · 我的")
            .put("category", category)
            .put("severity", "normal")
            .put("title", title.trim())
            .put("description", description.trim())
            .put(
                "clientContext",
                JSONObject()
                    .put("platform", "android")
                    .put("applicationId", BuildConfig.APPLICATION_ID)
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE),
            )
        val request = Request.Builder()
            .url("${userCenterUrl.trimEnd('/')}/api/feedback")
            .post(body.toString().toRequestBody(JSON))
            .apply {
                if (session != null) header("Cookie", session.cookie)
            }
            .build()
        val payload = executeJson(request).payload
        return FeedbackReceipt(
            feedbackId = payload.optString("feedbackId"),
            notificationSent = payload.optJSONObject("notification")?.optBoolean("sent") == true,
        )
    }

    fun loadLeaderboard(
        session: AppSession?,
        syncCurrentUser: Boolean,
    ): LeaderboardSnapshot {
        val request = Request.Builder()
            .url("${reciteApiUrl.trimEnd('/')}/api/rankings?limit=20")
            .apply {
                if (session != null) header("Cookie", session.cookie)
                if (syncCurrentUser && session != null) {
                    post(ByteArray(0).toRequestBody(null))
                } else {
                    get()
                }
            }
            .build()
        val payload = executeJson(request).payload
        return LeaderboardSnapshot(
            daily = leaderboardEntries(payload, "daily"),
            total = leaderboardEntries(payload, "total"),
            meDaily = leaderboardEntry(payload.optJSONObject("meDaily")),
            meTotal = leaderboardEntry(payload.optJSONObject("meTotal")),
            generatedAt = payload.optString("generatedAt"),
        )
    }

    fun getJson(url: String): JSONObject {
        return executeJson(Request.Builder().url(url).get().build()).payload
    }

    fun download(url: String, expectedSize: Long, maximumSize: Long): ByteArray {
        require(expectedSize in 1..maximumSize) { "更新檔案大小無效。" }
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException("下載失敗（HTTP ${response.code}）。", response.code)
            }
            val body = response.body
            val declaredSize = body.contentLength()
            require(declaredSize == -1L || declaredSize == expectedSize) {
                "更新檔案大小與清單不一致。"
            }
            val output = ByteArrayOutputStream(expectedSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    require(total <= expectedSize && total <= maximumSize) {
                        "更新檔案超出清單大小，已停止下載。"
                    }
                    output.write(buffer, 0, read)
                }
            }
            require(total == expectedSize) { "更新檔案不完整，已停止下載。" }
            return output.toByteArray()
        }
    }

    private fun executeJson(request: Request): JsonResponse {
        try {
            client.newCall(
                request.newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "LangLang-Android/${BuildConfig.VERSION_NAME}")
                    .build(),
            ).execute().use { response ->
                val raw = response.body.string()
                val payload = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                if (!response.isSuccessful) {
                    val message = payload.optString("message")
                        .ifBlank { payload.optString("error") }
                        .ifBlank { "請求失敗（HTTP ${response.code}）。" }
                    throw ApiException(message, response.code)
                }
                return JsonResponse(payload, response.headers)
            }
        } catch (error: ApiException) {
            throw error
        } catch (error: IOException) {
            throw ApiException("網路暫時不可用，請稍後再試。", cause = error)
        }
    }

    private fun leaderboardEntries(payload: JSONObject, key: String): List<LeaderboardEntry> {
        val items = payload.optJSONArray(key) ?: return emptyList()
        return buildList(items.length()) {
            repeat(items.length()) { index ->
                leaderboardEntry(items.optJSONObject(index))?.let(::add)
            }
        }
    }

    private fun leaderboardEntry(payload: JSONObject?): LeaderboardEntry? {
        if (payload == null) return null
        val totalPoints = payload.optInt("totalPoints").coerceIn(0, ReciteRanks.MAX_POINTS)
        val position = payload.optInt("position")
        val displayName = payload.optString("displayName").trim().take(32)
        if (position <= 0 || displayName.isBlank()) return null
        return LeaderboardEntry(
            position = position,
            displayName = displayName,
            totalPoints = totalPoints,
            todayPoints = payload.optInt("todayPoints").coerceIn(0, ReciteRanks.MAX_POINTS),
            rank = ReciteRanks.fromName(payload.optString("rankName"), totalPoints),
            isMe = payload.optBoolean("isMe"),
        )
    }

    private data class JsonResponse(
        val payload: JSONObject,
        val headers: okhttp3.Headers,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

data class FeedbackReceipt(
    val feedbackId: String,
    val notificationSent: Boolean,
)

class ApiException(
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
