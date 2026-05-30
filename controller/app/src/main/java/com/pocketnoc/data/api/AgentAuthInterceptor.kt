package com.pocketnoc.data.api

import com.pocketnoc.utils.JwtUtils
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injeta `Authorization: Bearer <JWT>` em cada request ao agent.
 *
 * O token é derivado do `secret` por servidor e regenerado sozinho quando perto de expirar —
 * elimina o bug do token "preso" no cliente Retrofit cacheado (que ficaria inválido após a
 * expiração até o cache ser invalidado na mão). Thread-safe via double-checked locking.
 */
class AgentAuthInterceptor(private val secret: String) : Interceptor {

    @Volatile private var cachedToken: String? = null
    @Volatile private var refreshAtMs: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${currentToken()}")
            .build()
        return chain.proceed(request)
    }

    private fun currentToken(): String {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < refreshAtMs) return it }
        return synchronized(this) {
            val current = cachedToken
            if (current != null && now < refreshAtMs) {
                current
            } else {
                JwtUtils.generateToken(secret).also {
                    cachedToken = it
                    refreshAtMs = now + REFRESH_INTERVAL_MS
                }
            }
        }
    }

    private companion object {
        // JwtUtils emite token de 7 dias; renovamos a cada 6 dias, com 1 dia de folga.
        const val REFRESH_INTERVAL_MS = 6L * 24 * 60 * 60 * 1000
    }
}
