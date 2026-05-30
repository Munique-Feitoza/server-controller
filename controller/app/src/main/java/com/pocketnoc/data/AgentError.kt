package com.pocketnoc.data

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Erro de domínio da comunicação com o agent. Preserva o TIPO da falha (rede, timeout, auth,
 * túnel, ameaça) em vez de achatar tudo numa `Exception("Failed to...")` genérica — a UI passa
 * a poder distinguir "tente de novo" de "credencial inválida" de "intrusão detectada", e cada
 * caso já carrega uma mensagem amigável em pt-BR.
 */
sealed class AgentError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Host inalcançável, conexão recusada, túnel caído. */
    class Network(cause: Throwable? = null) : AgentError("Sem conexão com o agente", cause)

    /** O agent não respondeu a tempo. */
    class Timeout(cause: Throwable? = null) : AgentError("O agente demorou a responder", cause)

    /** Token/secret inválido ou expirado (HTTP 401/403). */
    class Unauthorized(cause: Throwable? = null) :
        AgentError("Não autorizado — verifique o secret do servidor", cause)

    /** O agent respondeu com erro HTTP (>= 400, exceto 401/403). */
    class Server(val code: Int, cause: Throwable? = null) :
        AgentError("O agente retornou erro $code", cause)

    /** Falha ao subir/usar o túnel SSH. */
    class Tunnel(message: String, cause: Throwable? = null) : AgentError(message, cause)

    /** Intrusão detectada: o SSH falhou autenticação repetidamente. Mantém o prefixo
     *  "ALERTA DE SEGURANÇA" no texto por compatibilidade com filtros existentes. */
    class SecurityThreat(val serverName: String, val failures: Int) :
        AgentError("ALERTA DE SEGURANÇA: $failures falhas de autenticação em $serverName")

    /** Configuração faltando (ex.: servidor sem secret cadastrado). */
    class Misconfigured(message: String) : AgentError(message)

    /** Qualquer outra falha não classificada. */
    class Unknown(cause: Throwable) : AgentError(cause.message ?: "Erro desconhecido", cause)
}

/**
 * Mapeia uma exceção crua (Retrofit/OkHttp/IO) para o erro de domínio.
 * Um [AgentError] já tipado passa direto (idempotente).
 *
 * Ordem importa: SocketTimeoutException é subtipo de IOException, então vem antes.
 */
fun Throwable.toAgentError(): AgentError = when (this) {
    is AgentError -> this
    is HttpException -> if (code() == 401 || code() == 403) AgentError.Unauthorized(this)
                        else AgentError.Server(code(), this)
    is SocketTimeoutException -> AgentError.Timeout(this)
    is IOException -> AgentError.Network(this)
    else -> AgentError.Unknown(this)
}
