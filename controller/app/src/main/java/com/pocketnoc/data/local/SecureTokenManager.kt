package com.pocketnoc.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador seguro de tokens usando EncryptedSharedPreferences.
 * Se a chave de criptografia corromper (reinstalacao, backup restore),
 * limpa e recria automaticamente sem crashar o app.
 */
@Singleton
class SecureTokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SecureTokenManager"
        private const val PREFS_NAME = "pocket_noc_secure_tokens"
        private const val TOKEN_PREFIX = "token_"
        private const val SECRET_PREFIX = "secret_"
        private const val SSH_KEY_PREFIX = "ssh_key_"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Chave criptografica corrompida, recriando storage: ${e.message}")

            // Limpa o arquivo corrompido
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().clear().apply()
                val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_NAME.xml")
                if (prefsFile.exists()) prefsFile.delete()
            } catch (cleanup: Exception) {
                Log.e(TAG, "Falha na limpeza: ${cleanup.message}")
            }

            // Tenta criar novamente
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback para SharedPreferences normal: ${e2.message}")
                // Ultimo recurso: SharedPreferences sem criptografia
                context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    fun saveToken(serverId: Int, token: String) {
        encryptedPrefs.edit().putString("$TOKEN_PREFIX$serverId", token).apply()
    }

    fun getToken(serverId: Int): String? {
        return try {
            encryptedPrefs.getString("$TOKEN_PREFIX$serverId", null)
        } catch (e: Exception) { null }
    }

    fun saveSecret(serverId: Int, secret: String) {
        encryptedPrefs.edit().putString("$SECRET_PREFIX$serverId", secret).apply()
    }

    fun getSecret(serverId: Int): String? {
        return try {
            encryptedPrefs.getString("$SECRET_PREFIX$serverId", null)
        } catch (e: Exception) { null }
    }

    fun saveSshKey(serverId: Int, keyContent: String) {
        encryptedPrefs.edit().putString("$SSH_KEY_PREFIX$serverId", keyContent).apply()
    }

    fun getSshKey(serverId: Int): String? {
        return try {
            encryptedPrefs.getString("$SSH_KEY_PREFIX$serverId", null)
        } catch (e: Exception) { null }
    }

    fun clearServerCredentials(serverId: Int) {
        encryptedPrefs.edit()
            .remove("$TOKEN_PREFIX$serverId")
            .remove("$SECRET_PREFIX$serverId")
            .remove("$SSH_KEY_PREFIX$serverId")
            .apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    fun hasCredentials(serverId: Int): Boolean {
        return try {
            encryptedPrefs.contains("$TOKEN_PREFIX$serverId") ||
                encryptedPrefs.contains("$SECRET_PREFIX$serverId")
        } catch (e: Exception) { false }
    }
}
