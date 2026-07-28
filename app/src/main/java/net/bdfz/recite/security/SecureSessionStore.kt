package net.bdfz.recite.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AppSession(
    val slug: String,
    val displayName: String,
    val cookie: String,
)

class SecureSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)

    fun read(): AppSession? {
        val encoded = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_SIZE_BITS, bytes.copyOfRange(0, IV_SIZE)),
            )
            val json = JSONObject(
                String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), Charsets.UTF_8),
            )
            AppSession(
                slug = json.getString("slug"),
                displayName = json.optString("displayName", json.getString("slug")),
                cookie = json.getString("cookie"),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun write(session: AppSession) {
        val clearText = JSONObject()
            .put("slug", session.slug)
            .put("displayName", session.displayName)
            .put("cookie", session.cookie)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(clearText)
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(KEY_SESSION, encoded).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_SESSION = "session"
        const val KEY_ALIAS = "net.bdfz.recite.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_SIZE_BITS = 128
    }
}
