package net.bdfz.recite.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import net.bdfz.recite.BuildConfig
import net.bdfz.recite.network.ReciteApiClient
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class UpdateInfo(
    val schema: String,
    val appId: String,
    val version: String,
    val versionCode: Int,
    val minAndroidApi: Int,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val publishedAt: String,
    val releaseNotes: List<String>,
    val mandatory: Boolean,
) {
    val required: Boolean
        get() = mandatory
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
            val info = parseUpdateManifest(payload, BuildConfig.APPLICATION_ID)
            require(info.minAndroidApi <= Build.VERSION.SDK_INT) {
                "此更新需要 Android API ${info.minAndroidApi} 或以上。"
            }
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
            val bytes = apiClient.download(info.apkUrl, info.size, MAX_APK_BYTES)
            val actual = sha256(bytes)
            require(actual == info.sha256) { "更新檔案校驗失敗，已停止安裝。" }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            directory.listFiles()?.forEach { file ->
                if (file.name != "langlang-${info.versionCode}.apk") file.delete()
            }
            val file = File(directory, "langlang-${info.versionCode}.apk")
            file.writeBytes(bytes)
            require(file.length() == info.size) { "更新檔案大小校驗失敗。" }
            verifyDownloadedApk(info, file)
            UpdateState.Ready(info, file)
        }.getOrElse { UpdateState.Error(it.message ?: "更新下載失敗。") }
    }

    fun install(info: UpdateInfo, apk: File): UpdateState {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return UpdateState.Current
        return runCatching {
            val updatesDirectory = File(context.cacheDir, "updates").canonicalFile
            require(apk.canonicalFile.parentFile == updatesDirectory) {
                "安裝檔案不在受控更新目錄。"
            }
            verifyDownloadedApk(info, apk)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return@runCatching UpdateState.Ready(info, apk)
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
            UpdateState.Ready(info, apk)
        }.getOrElse { UpdateState.Error(it.message ?: "更新安裝驗證失敗。") }
    }

    private fun verifyDownloadedApk(info: UpdateInfo, apk: File) {
        require(apk.isFile && apk.length() == info.size) { "更新檔案大小校驗失敗。" }
        require(sha256(apk) == info.sha256) { "更新檔案雜湊校驗失敗。" }

        val archive = packageInfo(apk)
            ?: error("無法讀取更新 APK 的套件資訊。")
        require(archive.packageName == context.packageName && archive.packageName == info.appId) {
            "更新 APK 不是目前的 App，已停止安裝。"
        }
        require(versionCode(archive) == info.versionCode.toLong()) {
            "更新 APK 版本與清單不一致。"
        }
        require(info.versionCode > BuildConfig.VERSION_CODE) {
            "更新 APK 版本必須高於目前版本。"
        }

        val installed = installedPackageInfo()
        require(signingCertificates(archive) == signingCertificates(installed)) {
            "更新 APK 簽章與目前 App 不一致，已停止安裝。"
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        require(signatures.isNotEmpty()) { "無法驗證 APK 簽章。" }
        return signatures.map { sha256(it.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

    private companion object {
        const val PREFS = "app_update"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val APK_MIME = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 200L * 1024L * 1024L
    }
}

internal fun parseUpdateManifest(
    payload: JSONObject,
    expectedAppId: String,
    nowMs: Long = System.currentTimeMillis(),
): UpdateInfo {
    require(payload.getString("schema") == UPDATE_SCHEMA) { "更新清單格式不受支援。" }
    val appId = payload.getString("appId")
    require(appId == expectedAppId) { "更新清單不屬於目前的 App。" }

    val version = payload.getString("version")
    require(VERSION_PATTERN.matches(version)) { "更新版本格式無效。" }
    val versionCode = payload.getInt("versionCode")
    require(versionCode > 0) { "更新版本代碼無效。" }
    val minAndroidApi = payload.getInt("minAndroidApi")
    require(minAndroidApi in 23..100) { "更新最低 Android API 無效。" }

    val apkUrl = payload.getString("apkUrl")
    val uri = runCatching { URI(apkUrl) }.getOrElse { error("更新 URL 無效。") }
    require(
        uri.scheme == "https" &&
            uri.host == "img.bdfz.net" &&
            uri.port == -1 &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.userInfo == null,
    ) {
        "更新來源不是受信任的第一方 HTTPS 網域。"
    }
    val expectedPath = Regex(
        """^/apps/recite-android/releases/v${Regex.escape(version)}/[a-f0-9]{8,64}/[^/]+\.apk$""",
    )
    require(expectedPath.matches(uri.path.orEmpty())) {
        "更新檔案不是內容尋址的不可變版本路徑。"
    }

    val sha256 = payload.getString("sha256").lowercase()
    require(SHA_PATTERN.matches(sha256)) { "更新雜湊格式無效。" }
    val size = payload.getLong("size")
    require(size in 1..MAX_MANIFEST_APK_BYTES) { "更新檔案大小無效。" }

    val publishedAt = payload.getString("publishedAt")
    require(PUBLISHED_AT_PATTERN.matches(publishedAt)) { "更新發布時間格式無效。" }
    val publishedAtMs = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(publishedAt)?.time
    }.getOrNull() ?: error("更新發布時間格式無效。")
    require(publishedAtMs <= nowMs + MAX_CLOCK_SKEW_MS) {
        "更新發布時間超出允許範圍。"
    }

    val notesJson = payload.getJSONArray("releaseNotes")
    require(notesJson.length() in 1..10) { "更新說明不可為空或過多。" }
    val releaseNotes = buildList(notesJson.length()) {
        repeat(notesJson.length()) { index ->
            val note = notesJson.getString(index).trim()
            require(note.isNotEmpty() && note.length <= 240) { "更新說明格式無效。" }
            add(note)
        }
    }
    val mandatory = payload.getBoolean("mandatory")

    if (payload.has("downloadUrl")) {
        require(payload.getString("downloadUrl") == apkUrl) { "兼容下載地址與正式地址不一致。" }
    }
    if (payload.has("notes")) {
        val legacyNotes = payload.getJSONArray("notes")
        require(legacyNotes.length() == releaseNotes.size) { "兼容更新說明不一致。" }
        releaseNotes.forEachIndexed { index, note ->
            require(legacyNotes.getString(index) == note) { "兼容更新說明不一致。" }
        }
    }
    if (payload.has("minimumSupportedVersionCode")) {
        val minimum = payload.getInt("minimumSupportedVersionCode")
        require(minimum in 1..versionCode) { "兼容最低版本代碼無效。" }
    }

    return UpdateInfo(
        schema = UPDATE_SCHEMA,
        appId = appId,
        version = version,
        versionCode = versionCode,
        minAndroidApi = minAndroidApi,
        apkUrl = apkUrl,
        sha256 = sha256,
        size = size,
        publishedAt = publishedAt,
        releaseNotes = releaseNotes,
        mandatory = mandatory,
    )
}

private const val UPDATE_SCHEMA = "bdfz-android-update-v1"
private const val MAX_MANIFEST_APK_BYTES = 200L * 1024L * 1024L
private const val MAX_CLOCK_SKEW_MS = 15L * 60L * 1000L
private val VERSION_PATTERN = Regex("""\d+\.\d+\.\d+""")
private val SHA_PATTERN = Regex("""[a-f0-9]{64}""")
private val PUBLISHED_AT_PATTERN = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")
