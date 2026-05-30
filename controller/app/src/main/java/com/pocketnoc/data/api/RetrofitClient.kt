package com.pocketnoc.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Fábrica de Retrofit. SEM estado mutável compartilhado — cada chamada constrói um cliente
 * independente e thread-safe.
 *
 * O singleton anterior (`instance`/`currentUrl`/`currentToken` em campos mutáveis estáticos
 * sem sincronização) abria uma race com os 4 servidores em polling concorrente e prendia o
 * token de auth no momento da criação. O cache de "1 service por servidor" é responsabilidade
 * do ServerRepository.apiCache; auth dinâmica é responsabilidade do interceptor.
 */
object RetrofitClient {

    /**
     * @param baseUrl         URL base (já normalizada, terminando em '/').
     * @param authInterceptor interceptor de autenticação opcional. `null` = sem header
     *                        Authorization (ex.: a API do dashboard autentica via query param).
     */
    fun create(baseUrl: String, authInterceptor: Interceptor? = null): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC não loga headers — evita vazar o JWT no logcat. BODY vazaria.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        authInterceptor?.let { clientBuilder.addInterceptor(it) }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
