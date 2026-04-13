package com.pocketnoc.ssh

import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.pocketnoc.config.PocketNOCConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Properties

/**
 * Gerenciador de Túnel SSH para conexão segura com o Agente Rust.
 * 
 * Implementa Local Port Forwarding: Celular (localhost:9443) -> Servidor (localhost:9443)
 */
object SshTunnelManager {
    private const val TAG = "SshTunnelManager"
    private val sessions = mutableMapOf<Int, Session>()
    private val authFailures = mutableMapOf<Int, Int>()
    private val tunnelMutex = Mutex()

    /**
     * Retorna o número de falhas de autenticação de um servidor.
     */
    fun getAuthFailures(serverId: Int): Int = authFailures[serverId] ?: 0

    /**
     * Reseta as falhas de um servidor (ex: quando o usuário troca a chave).
     */
    fun resetAuthFailures(serverId: Int) {
        authFailures[serverId] = 0
    }
    suspend fun startTunnel(
        serverId: Int,
        host: String,
        user: String,
        privateKeyContent: String,
        localPort: Int,
        sshPort: Int = 22,
        remotePort: Int = 9443
    ): Result<Boolean> = tunnelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val currentSession = sessions[serverId]
                if (currentSession?.isConnected == true) {
                    Log.i(TAG, "Túnel SSH para servidor $serverId já está conectado.")
                    return@withContext Result.success(true)
                }

                // Usamos uma instância local de JSch para cada túnel para evitar race conditions
                // com multi-threading e identidades (chaves).
                val localJsch = JSch()

                // Garante que sequências \n e \r literais sejam tratadas como quebras de linha reais
                val formattedKey = privateKeyContent.replace("\\n", "\n").replace("\\r", "\r").trim()

                // Validação básica da chave
                if (!formattedKey.startsWith("-----BEGIN")) {
                    val errorMsg = "Chave SSH inválida: Deve começar com '-----BEGIN'. Verifique o local.properties."
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                try { 
                    localJsch.addIdentity("key-$serverId", formattedKey.toByteArray(), null, null) 
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao adicionar identidade SSH (Chave): ${e.message}")
                    return@withContext Result.failure(Exception("Erro na chave SSH: ${e.message}"))
                }

                Log.i(TAG, "Conectando ao servidor SSH ($serverId): $user@$host:$sshPort...")
                val session = localJsch.getSession(user, host, sshPort)

                val config = Properties()
                // Se o modo de emergência estiver ativo, ele força "no" para não travar o usuário
                val strictChecking = if (PocketNOCConfig.emergencyMode) {
                    "no"
                } else {
                    if (PocketNOCConfig.sshStrictHostChecking) "yes" else "no"
                }
                
                config["StrictHostKeyChecking"] = strictChecking
                config["PreferredAuthentications"] = "publickey" // Força o uso da chave
                
                // Suporte para algoritmos modernos (Ubuntu 22.04+ desabilita ssh-rsa por padrão)
                val algorithms = "ssh-ed25519,ssh-rsa,rsa-sha2-256,rsa-sha2-512"
                config["PubkeyAcceptedAlgorithms"] = algorithms
                config["PubkeyAcceptedKeyTypes"] = algorithms // Para versões mais antigas do JSch
                config["server_host_key"] = algorithms
                
                session?.setConfig(config)
                
                // Resiliência: Keep-Alive para evitar que o servidor dê "Connection Reset"
                session.setServerAliveInterval(30000) // 30s
                session.setServerAliveCountMax(3)
                
                session.connect(30000) // 30s timeout (redes moveis podem ser lentas)
                
                // Se chegou aqui, a autenticação foi um sucesso! Reseta o contador.
                authFailures[serverId] = 0

                // Limpa port forwarding anterior se existir no mesmo localPort
                try { session.delPortForwardingL(localPort) } catch (e: Exception) {}
                
                session.setPortForwardingL(localPort, "127.0.0.1", remotePort)
                
                sessions[serverId] = session
                Log.i(TAG, "✅ Túnel SSH $serverId estabelecido: localhost:$localPort -> $host:$remotePort")
                Result.success(true)
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Erro desconhecido"
            
            // Detecta falha de autenticação
            if (errorMsg.contains("Auth fail", ignoreCase = true)) {
                val currentFailures = (authFailures[serverId] ?: 0) + 1
                authFailures[serverId] = currentFailures
                
                if (currentFailures >= com.pocketnoc.config.PocketNOCConfig.maxAuthFailures) {
                    val alertMsg = "🚨 ALERTA DE SEGURANÇA: $currentFailures falhas seguidas no servidor $serverId! Possível tentativa de intrusão."
                    Log.e(TAG, alertMsg)
                    return@withContext Result.failure(Exception(alertMsg))
                }
            }

            val finalError = "Falha no SSH: $errorMsg"
            Log.e(TAG, "❌ $finalError", e)
            
            // Cleanup: Garante que sessões falhas não fiquem presas em "reset"
            sessions.remove(serverId)?.disconnect()
            
            Result.failure(Exception(finalError))
        }
    }
}

    /**
     * Fecha um túnel específico ou todos.
     */
    fun stopTunnel(serverId: Int? = null) {
        if (serverId != null) {
            sessions[serverId]?.disconnect()
            sessions.remove(serverId)
            Log.i(TAG, "Túnel SSH $serverId finalizado.")
        } else {
            sessions.values.forEach { it.disconnect() }
            sessions.clear()
            Log.i(TAG, "Todos os túneis SSH finalizados.")
        }
    }

    /**
     * Verifica se um túnel específico está ativo.
     */
    fun isConnected(serverId: Int): Boolean = sessions[serverId]?.isConnected == true
}
