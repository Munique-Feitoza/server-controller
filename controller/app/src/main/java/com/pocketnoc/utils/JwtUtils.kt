package com.pocketnoc.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtUtils {
    fun generateToken(secret: String, username: String = "admin-pocket-noc"): String {
        val algorithm = Algorithm.HMAC256(secret)
        return JWT.create()
            .withSubject(username)
            .withIssuer("pocket-noc-android")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) // 7 dias
            .withArrayClaim("scopes", arrayOf("admin", "read", "write"))
            .sign(algorithm)
    }
}
