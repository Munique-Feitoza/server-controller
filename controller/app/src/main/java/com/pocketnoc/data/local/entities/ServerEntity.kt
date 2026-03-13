package com.pocketnoc.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String, // Usado para a URL do Agente (ex: http://localhost:PORT)
    val token: String,
    val secret: String? = null,
    val sshUser: String? = null,
    val sshHost: String? = null,
    val sshKeyPath: String? = null,
    val sshPort: Int? = 22,
    val remotePort: Int? = 9443,
    val localPort: Int? = null,
    val securityStatus: Int = 0, // 0: Secure, 1: Warning, 2: Threat
    val osInfo: String = "Ubuntu 22.04",
    val stackInfo: String = "Nginx",
    val locationInfo: String = "Canada"
)
