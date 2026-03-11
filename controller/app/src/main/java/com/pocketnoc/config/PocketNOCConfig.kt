package com.pocketnoc.config

/**
 * Configuração segura do aplicativo
 * 
 * Os valores reais devem ser carregados de:
 * 1. local.properties (não versionado)
 * 2. BuildConfig (gerado pelo Gradle)
 * 3. Variáveis de ambiente
 * 
 * NUNCA hardcode secrets no código-fonte!
 */
object PocketNOCConfig {
    
    /**
     * URL do servidor Pocket NOC
     * Carregada via BuildConfig.SERVER_URL
     * Padrão: http://localhost:9443
     */
    val serverUrl: String
        get() = BuildConfig.POCKET_NOC_SERVER_URL
    
    /**
     * Token JWT para autenticação
     * Carregada via BuildConfig.JWT_TOKEN
     * 
     * ⚠️ IMPORTANTE: Em produção:
     * - Armazenar no Android Keystore (criptografado)
     * - Ou usar OAuth2 / OpenID Connect
     * - Nunca armazenar em SharedPreferences sem encriptação
     */
    val jwtToken: String
        get() = BuildConfig.POCKET_NOC_JWT_TOKEN
    
    /**
     * Usar HTTPS (recomendado em produção)
     */
    val useHttps: Boolean
        get() = BuildConfig.USE_HTTPS
    
    /**
     * Timeout de requisições em segundos
     */
    const val REQUEST_TIMEOUT_SECONDS = 30L
    
    /**
     * Intervalo de polling de telemetria em milissegundos
     */
    const val TELEMETRY_POLL_INTERVAL_MS = 5000L // 5 segundos
}
