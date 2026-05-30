package com.pocketnoc.utils

/**
 * Dedup de notificações por janela de tempo. A chave é um hash estável do conteúdo: a mesma
 * chave dentro da janela conta como duplicata (o chamador reaproveita o mesmo notification id,
 * então o sistema atualiza a notif existente em vez de empilhar uma nova).
 *
 * LRU com teto de entradas, thread-safe, em memória (resetar no kill do processo é aceitável).
 * Centraliza a lógica antes duplicada em [com.pocketnoc.notifications.NtfySubscriberService] e
 * [SecurityNotificationManager].
 */
class AlertDedup(
    private val windowMs: Long = 10 * 60 * 1000L,
    private val maxEntries: Int = 512,
) {
    private val seen = LinkedHashMap<Int, Long>(64, 0.75f, /* accessOrder = */ true)
    private val lock = Any()

    /** Retorna true se [key] já foi vista dentro da janela; senão registra agora e retorna false. */
    fun isDuplicate(key: Int, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val last = seen[key]
        if (last != null && nowMs - last < windowMs) return@synchronized true
        seen[key] = nowMs
        if (seen.size > maxEntries) {
            val it = seen.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() } // remove o LRU
        }
        false
    }
}
