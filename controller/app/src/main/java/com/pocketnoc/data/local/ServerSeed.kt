package com.pocketnoc.data.local

import android.util.Log
import com.pocketnoc.config.PocketNOCConfig
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.utils.JwtUtils
import kotlinx.coroutines.flow.first

/**
 * Bootstrap dos 4 servidores a partir do local.properties (via BuildConfig).
 * Roda 1x no onCreate da Application — só insere se a tabela `servers` estiver vazia.
 * Preserva qualquer servidor que o user adicionou manualmente em rebuilds futuras.
 */
object ServerSeed {

    private const val TAG = "ServerSeed"

    suspend fun seedIfEmpty(repository: ServerRepository) {
        val existing = repository.getAllServers().first()
        if (existing.isNotEmpty()) {
            Log.d(TAG, "DB já tem ${existing.size} servidor(es), pulando seed")
            return
        }
        val secret = PocketNOCConfig.sharedSecret
        if (secret.isBlank()) {
            Log.w(TAG, "POCKET_NOC_SECRET vazio no local.properties — seed pulado")
            return
        }
        val sshKey = PocketNOCConfig.sshKeyContent
        val token = JwtUtils.generateToken(secret)

        for (i in 1..4) {
            val ip   = when (i) { 1 -> PocketNOCConfig.server1; 2 -> PocketNOCConfig.server2; 3 -> PocketNOCConfig.server3; 4 -> PocketNOCConfig.server4; else -> "" }
            if (ip.isBlank()) continue
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
            Log.i(TAG, "seed: $name ($ip:$sshPort -> local $localPort)")
        }
    }
}
