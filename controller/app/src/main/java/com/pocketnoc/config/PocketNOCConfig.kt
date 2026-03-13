package com.pocketnoc.config

import com.pocketnoc.BuildConfig

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
    
    val server1: String get() = BuildConfig.POCKET_NOC_SERVER_1
    val server2: String get() = BuildConfig.POCKET_NOC_SERVER_2
    val server3: String get() = BuildConfig.POCKET_NOC_SERVER_3
    val server4: String get() = BuildConfig.POCKET_NOC_SERVER_4
    
    /**
     * Segredo para geração de tokens JWT
     */
    val secret: String
        get() = BuildConfig.POCKET_NOC_SECRET
    
    /**
     * Usar HTTPS (recomendado em produção)
     */
    val useHttps: Boolean = BuildConfig.USE_HTTPS

    /**
     * MODO DE EMERGÊNCIA: Permite ignorar falhas de SSL/Segurança (CUIDADO)
     */
    val emergencyMode: Boolean = BuildConfig.EMERGENCY_MODE

    /**
     * Verificação estrita de hosts SSH
     */
    val sshStrictHostChecking: Boolean = BuildConfig.SSH_STRICT_HOST_CHECKING

    /**
     * Limite de falhas de autenticação
     */
    val maxAuthFailures: Int = BuildConfig.MAX_AUTH_FAILURES
    val maxCpuThreshold: Int = BuildConfig.MAX_CPU_THRESHOLD

    // Helpers para obter config de SSH por índice (1 a 4)
    fun getSshUser(index: Int): String = when(index) {
        1 -> BuildConfig.SSH_USER_1
        2 -> BuildConfig.SSH_USER_2
        3 -> BuildConfig.SSH_USER_3
        4 -> BuildConfig.SSH_USER_4
        else -> ""
    }

    fun getServerName(index: Int): String = when(index) {
        1 -> BuildConfig.POCKET_NOC_SERVER_NAME_1
        2 -> BuildConfig.POCKET_NOC_SERVER_NAME_2
        3 -> BuildConfig.POCKET_NOC_SERVER_NAME_3
        4 -> BuildConfig.POCKET_NOC_SERVER_NAME_4
        else -> "Winup $index"
    }

    fun getSshHost(index: Int): String = when(index) {
        1 -> BuildConfig.SSH_HOST_1
        2 -> BuildConfig.SSH_HOST_2
        3 -> BuildConfig.SSH_HOST_3
        4 -> BuildConfig.SSH_HOST_4
        else -> ""
    }

    fun getSshPort(index: Int): Int = when(index) {
        1 -> BuildConfig.SSH_SERVICE_PORT_1.toIntOrNull() ?: 22
        2 -> BuildConfig.SSH_SERVICE_PORT_2.toIntOrNull() ?: 22
        3 -> BuildConfig.SSH_SERVICE_PORT_3.toIntOrNull() ?: 2222
        4 -> BuildConfig.SSH_SERVICE_PORT_4.toIntOrNull() ?: 22
        else -> 22
    }

    val sshKeyContent: String = BuildConfig.SSH_KEY_CONTENT_GLOBAL

    fun getLocalPort(index: Int): Int = when(index) {
        1 -> BuildConfig.LOCAL_FORWARD_PORT_1.toIntOrNull() ?: 9443
        2 -> BuildConfig.LOCAL_FORWARD_PORT_2.toIntOrNull() ?: 9444
        3 -> BuildConfig.LOCAL_FORWARD_PORT_3.toIntOrNull() ?: 9445
        4 -> BuildConfig.LOCAL_FORWARD_PORT_4.toIntOrNull() ?: 9446
        else -> 9443
    }

    fun getRemotePort(index: Int): Int = when(index) {
        1 -> BuildConfig.REMOTE_AGENT_PORT_1.toIntOrNull() ?: 9443
        2 -> BuildConfig.REMOTE_AGENT_PORT_2.toIntOrNull() ?: 9443
        3 -> BuildConfig.REMOTE_AGENT_PORT_3.toIntOrNull() ?: 9443
        4 -> BuildConfig.REMOTE_AGENT_PORT_4.toIntOrNull() ?: 9443
        else -> 9443
    }
    
    /**
     * Timeout de requisições em segundos
     */
    const val REQUEST_TIMEOUT_SECONDS = 30L
    
    /**
     * Intervalo de polling de telemetria em milissegundos
     */
    const val TELEMETRY_POLL_INTERVAL_MS = 5000L // 5 segundos
}
