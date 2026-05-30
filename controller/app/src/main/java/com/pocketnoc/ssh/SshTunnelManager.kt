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
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador de Túnel SSH para conexão segura com o Agente Rust.
 *
 * Local Port Forwarding: Celular (localhost:localPort) -> Servidor (127.0.0.1:remotePort).
 *
 * Lock POR SERVIDOR (não global): os 4 túneis sobem em PARALELO. Antes, um único `tunnelMutex`
 * global mantido durante o `connect()` de 30s serializava tudo — um host lento/offline travava
 * os outros 3 por até 30s, e por isso era difícil ter os 4 conectados ao mesmo tempo.
 * O estado compartilhado (sessions/authFailures) é thread-safe via ConcurrentHashMap.
 */
object SshTunnelManager {
    private const val TAG = "SshTunnelManager"
    private val sessions = ConcurrentHashMap<Int, Session>()
    private val authFailures = ConcurrentHashMap<Int, Int>()
    private val serverLocks = ConcurrentHashMap<Int, Mutex>()

    /** Lock dedicado por servidor: serializa chamadas concorrentes ao MESMO servidor
     *  (ex.: poll do Dashboard + sub do serviço de alertas), sem bloquear os outros. */
    private fun lockFor(serverId: Int): Mutex = serverLocks.computeIfAbsent(serverId) { Mutex() }

    /** Retorna o número de falhas de autenticação de um servidor. */
    fun getAuthFailures(serverId: Int): Int = authFailures[serverId] ?: 0

    /** Reseta as falhas de um servidor (ex: quando o usuário troca a chave). */
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
    ): Result<Boolean> = lockFor(serverId).withLock {
        withContext(Dispatchers.IO) {
            try {
                val currentSession = sessions[serverId]
                if (currentSession?.isConnected == true) {
                    Log.i(TAG, "Túnel SSH para servidor $serverId já está conectado.")
                    return@withContext Result.success(true)
                }

                // Instância local de JSch por túnel: evita race de identidades (chaves) entre
                // conexões paralelas.
                val localJsch = JSch()

                // Garante que sequências \n e \r literais virem quebras de linha reais.
                val formattedKey = privateKeyContent.replace("\\n", "\n").replace("\\r", "\r").trim()

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
                // StrictHostKeyChecking sempre habilitado por seguranca (desativar abre MITM).
                config["StrictHostKeyChecking"] = if (PocketNOCConfig.sshStrictHostChecking) "yes" else "no"
                config["PreferredAuthentications"] = "publickey" // Força o uso da chave

                // Algoritmos modernos (Ubuntu 22.04+ desabilita ssh-rsa por padrão)
                val algorithms = "ssh-ed25519,ssh-rsa,rsa-sha2-256,rsa-sha2-512"
                config["PubkeyAcceptedAlgorithms"] = algorithms
                config["PubkeyAcceptedKeyTypes"] = algorithms // Para versões mais antigas do JSch
                config["server_host_key"] = algorithms

                session?.setConfig(config)

                // Keep-Alive: evita "Connection Reset" do servidor.
                session.setServerAliveInterval(30000) // 30s
                session.setServerAliveCountMax(3)

                session.connect(30000) // 30s timeout (redes moveis podem ser lentas)

                // Autenticação OK → reseta o contador de falhas.
                authFailures[serverId] = 0

                // Limpa port forwarding anterior se existir no mesmo localPort.
                try { session.delPortForwardingL(localPort) } catch (e: Exception) {}

                session.setPortForwardingL(localPort, "127.0.0.1", remotePort)

                sessions[serverId] = session
                Log.i(TAG, "✅ Túnel SSH $serverId estabelecido: localhost:$localPort -> $host:$remotePort")
                Result.success(true)
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Erro desconhecido"

                // Detecta falha de autenticação e conta tentativas (por servidor).
                if (errorMsg.contains("Auth fail", ignoreCase = true)) {
                    val currentFailures = (authFailures[serverId] ?: 0) + 1
                    authFailures[serverId] = currentFailures

                    if (currentFailures >= PocketNOCConfig.maxAuthFailures) {
                        val alertMsg = "🚨 ALERTA DE SEGURANÇA: $currentFailures falhas seguidas no servidor $serverId! Possível tentativa de intrusão."
                        Log.e(TAG, alertMsg)
                        return@withContext Result.failure(Exception(alertMsg))
                    }
                }

                val finalError = "Falha no SSH: $errorMsg"
                Log.e(TAG, "❌ $finalError", e)

                // Cleanup: não deixa sessão falha presa no mapa.
                sessions.remove(serverId)?.disconnect()

                Result.failure(Exception(finalError))
            }
        }
    }

    /** Fecha um túnel específico ou todos. */
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

    /** Verifica se um túnel específico está ativo. */
    fun isConnected(serverId: Int): Boolean = sessions[serverId]?.isConnected == true
}
