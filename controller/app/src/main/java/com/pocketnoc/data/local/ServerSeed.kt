package com.pocketnoc.data.local

import android.util.Log
import com.pocketnoc.data.repository.ServerRepository

/**
 * Compatibilidade com o ciclo de inicialização.
 *
 * Credenciais nunca são importadas de local.properties ou BuildConfig, pois
 * ambos podem ser incluídos em artefatos públicos ou no APK. O cadastro deve
 * ocorrer pela UI e ficar no armazenamento criptografado do aplicativo.
 */
object ServerSeed {
    private const val TAG = "ServerSeed"

    suspend fun seedIfEmpty(repository: ServerRepository) {
        Log.i(TAG, "Seed automático desativado; credenciais devem ser cadastradas pela UI")
    }
}
