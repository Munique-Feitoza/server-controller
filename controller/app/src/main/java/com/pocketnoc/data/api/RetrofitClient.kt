package com.pocketnoc.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var instance: Retrofit? = null
    private var currentUrl: String? = null
    private var currentToken: String? = null

    fun getInstance(baseUrl: String, token: String? = null): Retrofit {
        if (instance != null && currentUrl == baseUrl && currentToken == token) {
            return instance!!
        }

        currentUrl = baseUrl
        currentToken = token

        val logging = HttpLoggingInterceptor().apply {
            // BODY logaria token JWT no header. HEADERS so loga keys.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
            token?.let {
                request.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(request.build())
        }

        // TLS validacao padrao do sistema. App fala HTTP localhost (tunel SSH),
        // entao TLS so eh exercitado se algum dia o baseUrl for HTTPS — e nesse caso,
        // queremos validacao de cert real, nao trustAll que abre MITM.
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also { instance = it }
    }

    fun resetInstance() {
        instance = null
        currentUrl = null
        currentToken = null
    }
}
