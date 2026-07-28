package net.bdfz.recite.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import net.bdfz.recite.BuildConfig
import net.bdfz.recite.network.ReciteApiClient
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val minimumSupportedVersionCode: Int,
    val sha256: String,
    val downloadUrl: String,
    val publishedAt: String,
    val notes: List<String>,
) {
    val required: Boolean
        get() = BuildConfig.VERSION_CODE < minimumSupportedVersionCode
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class AppUpdateManager(
    private val context: Context,
    private val apiClient: ReciteApiClient,
) {
    fun shouldCheckAutomatically(): Boolean {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return false
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0)
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS
    }

    fun check(): UpdateState {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return UpdateState.Current
        return runCatching {
            val payload = apiClient.getJson(BuildConfig.UPDATE_MANIFEST_URL)
            val info = parseAndValidate(payload)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
            if (info.versionCode > BuildConfig.VERSION_CODE) {
                UpdateState.Available(info)
            } else {
                UpdateState.Current
            }
        }.getOrElse { UpdateState.Error(it.message ?: "更新檢查失敗。") }
    }

    fun download(info: UpdateInfo): UpdateState {
        return runCatching {
            val bytes = apiClient.download(info.downloadUrl)
            val actual = sha256(bytes)
            require(actual == info.sha256) { "更新檔案校驗失敗，已停止安裝。" }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            directory.listFiles()?.forEach { file ->
                if (file.name != "langlang-${info.versionCode}.apk") file.delete()
            }
            val file = File(directory, "langlang-${info.versionCode}.apk")
            file.writeBytes(bytes)
            UpdateState.Ready(info, file)
        }.getOrElse { UpdateState.Error(it.message ?: "更新下載失敗。") }
    }

    fun install(apk: File) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun parseAndValidate(payload: JSONObject): UpdateInfo {
        val version = payload.getString("version")
        require(VERSION_PATTERN.matches(version)) { "更新版本格式無效。" }
        val versionCode = payload.getInt("versionCode")
        val minimumSupportedVersionCode = payload.optInt("minimumSupportedVersionCode", 1)
        val sha256 = payload.getString("sha256").lowercase()
        require(SHA_PATTERN.matches(sha256)) { "更新雜湊格式無效。" }
        val downloadUrl = payload.getString("downloadUrl")
        val uri = Uri.parse(downloadUrl)
        require(uri.scheme == "https" && uri.host == "img.bdfz.net") {
            "更新來源不是受信任的第一方網域。"
        }
        require(
            uri.path?.startsWith("/apps/recite-android/releases/") == true &&
                uri.path?.endsWith(".apk") == true,
        ) {
            "更新檔案不是不可變版本路徑。"
        }
        val notesJson = payload.optJSONArray("notes")
        val notes = buildList {
            if (notesJson != null) {
                repeat(notesJson.length()) { add(notesJson.optString(it).take(240)) }
            }
        }
        return UpdateInfo(
            version = version,
            versionCode = versionCode,
            minimumSupportedVersionCode = minimumSupportedVersionCode,
            sha256 = sha256,
            downloadUrl = downloadUrl,
            publishedAt = payload.optString("publishedAt"),
            notes = notes,
        )
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS = "app_update"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val APK_MIME = "application/vnd.android.package-archive"
        val VERSION_PATTERN = Regex("""\d+\.\d+\.\d+""")
        val SHA_PATTERN = Regex("""[a-f0-9]{64}""")
    }
}
