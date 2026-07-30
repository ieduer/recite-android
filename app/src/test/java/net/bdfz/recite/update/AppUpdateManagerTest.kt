package net.bdfz.recite.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdateManagerTest {
    private val nowMs = 1_785_369_600_000L

    @Test
    fun canonicalManifestAcceptsMatchingAppAndLegacyAliases() {
        val info = parseUpdateManifest(validManifest(), APP_ID, nowMs)

        assertEquals("bdfz-android-update-v1", info.schema)
        assertEquals(APP_ID, info.appId)
        assertEquals(3, info.versionCode)
        assertEquals(2_512_871L, info.size)
        assertEquals(listOf("單一套件原地更新"), info.releaseNotes)
        assertFalse(info.mandatory)
    }

    @Test
    fun rejectsAnotherApplicationId() {
        val payload = validManifest().put("appId", "net.bdfz.recite")

        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(payload, APP_ID, nowMs)
        }
    }

    @Test
    fun rejectsUnknownSchemaAndMutableUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(validManifest().put("schema", "legacy"), APP_ID, nowMs)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(
                validManifest()
                    .put("apkUrl", "https://img.bdfz.net/apps/recite-android/latest.apk")
                    .put("downloadUrl", "https://img.bdfz.net/apps/recite-android/latest.apk"),
                APP_ID,
                nowMs,
            )
        }
    }

    @Test
    fun rejectsMismatchedCompatibilityFields() {
        val payload = validManifest().put(
            "downloadUrl",
            "https://img.bdfz.net/apps/recite-android/releases/v0.1.2/deadbeef/other.apk",
        )

        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(payload, APP_ID, nowMs)
        }
    }

    @Test
    fun rejectsInvalidSizeNotesAndFutureTimestamp() {
        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(validManifest().put("size", 0), APP_ID, nowMs)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(validManifest().put("releaseNotes", JSONArray()), APP_ID, nowMs)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseUpdateManifest(
                validManifest().put("publishedAt", "2026-07-30T00:16:00Z"),
                APP_ID,
                nowMs,
            )
        }
    }

    private fun validManifest(): JSONObject {
        val url =
            "https://img.bdfz.net/apps/recite-android/releases/v0.1.2/54a89337/langlang-0.1.2.apk"
        return JSONObject(
            """
            {
              "schema": "bdfz-android-update-v1",
              "appId": "$APP_ID",
              "version": "0.1.2",
              "versionCode": 3,
              "minAndroidApi": 23,
              "apkUrl": "$url",
              "sha256": "54a893373cf1a22215832fe387133d057f1fcd9c281c05835e94b5f9812317b0",
              "size": 2512871,
              "publishedAt": "2026-07-30T00:00:00Z",
              "releaseNotes": ["單一套件原地更新"],
              "mandatory": false,
              "minimumSupportedVersionCode": 1,
              "downloadUrl": "$url",
              "notes": ["單一套件原地更新"]
            }
            """.trimIndent(),
        )
    }

    private companion object {
        const val APP_ID = "net.bdfz.recite.direct"
    }
}
