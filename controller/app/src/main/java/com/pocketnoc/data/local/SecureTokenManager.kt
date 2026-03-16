package com.pocketnoc.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador seguro de tokens usando EncryptedSharedPreferences
 * Todos os tokens são armazenados criptografados no dispositivo
 */
@Singleton
class SecureTokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "pocket_noc_secure_tokens"
        private const val TOKEN_PREFIX = "token_"
        private const val SECRET_PREFIX = "secret_"
        private const val SSH_KEY_PREFIX = "ssh_key_"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Salva um token JWT de forma criptografada
     * @param serverId ID do servidor
     * @param token Token JWT a ser armazenado
     */
    fun saveToken(serverId: Int, token: String) {
        encryptedPrefs.edit().apply {
            putString("$TOKEN_PREFIX$serverId", token)
            apply()
        }
    }

    /**
     * Recupera um token JWT de forma segura
     * @param serverId ID do servidor
     * @return Token JWT ou null se não encontrado
     */
    fun getToken(serverId: Int): String? {
        return encryptedPrefs.getString("$TOKEN_PREFIX$serverId", null)
    }

    /**
     * Salva o segredo (secret) de forma criptografada
     * @param serverId ID do servidor
     * @param secret Segredo a ser armazenado
     */
    fun saveSecret(serverId: Int, secret: String) {
        encryptedPrefs.edit().apply {
            putString("$SECRET_PREFIX$serverId", secret)
            apply()
        }
    }

    /**
     * Recupera o segredo de forma segura
     * @param serverId ID do servidor
     * @return Segredo ou null se não encontrado
     */
    fun getSecret(serverId: Int): String? {
        return encryptedPrefs.getString("$SECRET_PREFIX$serverId", null)
    }

    /**
     * Salva o conteúdo da chave SSH de forma criptografada
     */
    fun saveSshKey(serverId: Int, keyContent: String) {
        encryptedPrefs.edit().apply {
            putString("$SSH_KEY_PREFIX$serverId", keyContent)
            apply()
        }
    }

    /**
     * Recupera a chave SSH de forma segura
     */
    fun getSshKey(serverId: Int): String? {
        return encryptedPrefs.getString("$SSH_KEY_PREFIX$serverId", null)
    }

    /**
     * Remove todos os tokens, segredos e chaves de um servidor
     * @param serverId ID do servidor
     */
    fun clearServerCredentials(serverId: Int) {
        encryptedPrefs.edit().apply {
            remove("$TOKEN_PREFIX$serverId")
            remove("$SECRET_PREFIX$serverId")
            remove("$SSH_KEY_PREFIX$serverId")
            apply()
        }
    }

    /**
     * Remove todos os dados criptografados
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Verifica se as credenciais de um servidor existem
     */
    fun hasCredentials(serverId: Int): Boolean {
        return encryptedPrefs.contains("$TOKEN_PREFIX$serverId") || 
               encryptedPrefs.contains("$SECRET_PREFIX$serverId")
    }
}
