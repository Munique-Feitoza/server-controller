package com.pocketnoc.data.local

import android.util.Log
import com.pocketnoc.config.PocketNOCConfig
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.JwtUtils
import kotlinx.coroutines.flow.first

/**
 * Bootstrap dos servidores a partir do local.properties (via BuildConfig).
 *
 * Idempotente POR IP (sshHost): a cada onCreate da Application percorre 1..N
 * e adiciona o que ainda não existe. Permite estender o local.properties (ex:
 * 5º host) e ver o novo aparecer no app sem precisar limpar dados. Não toca
 * em servidores que o user adicionou/editou manualmente.
 */
object ServerSeed {

    private const val TAG = "ServerSeed"

    suspend fun seedIfEmpty(repository: ServerRepository) {
        val secret = PocketNOCConfig.sharedSecret
        if (secret.isBlank()) {
            Log.w(TAG, "POCKET_NOC_SECRET vazio no local.properties — seed pulado")
            return
        }
        val existing = repository.getAllServers().first()
        val knownHosts: Set<String> = existing.mapNotNull { it.sshHost }.toSet()
        val sshKey = PocketNOCConfig.sshKeyContent
        val token = JwtUtils.generateToken(secret)

        for (i in 1..5) {
            val ip = when (i) {
                1 -> PocketNOCConfig.server1
                2 -> PocketNOCConfig.server2
                3 -> PocketNOCConfig.server3
                4 -> PocketNOCConfig.server4
                5 -> PocketNOCConfig.server5
                else -> ""
            }
            if (ip.isBlank()) continue
            if (knownHosts.contains(ip)) {
                Log.d(TAG, "seed: $ip já cadastrado, pulando")
                continue
            }
            val localPort  = PocketNOCConfig.getLocalPort(i)
            val remotePort = PocketNOCConfig.getRemotePort(i)
            val sshUser    = PocketNOCConfig.getSshUser(i)
            val sshPort    = PocketNOCConfig.getSshPort(i)
            val name       = PocketNOCConfig.getServerName(i)
            repository.addServer(
                ServerEntity(
                    name        = name,
                    url         = "http://localhost:$localPort",
                    token       = token,
                    secret      = secret,
                    sshUser     = sshUser,
                    sshHost     = ip,
                    sshKeyPath  = sshKey,
                    sshPort     = sshPort,
                    remotePort  = remotePort,
                    localPort   = localPort
                )
            )
            Log.i(TAG, "seed: $name ($ip:$sshPort -> local $localPort) adicionado")
        }
    }
}
