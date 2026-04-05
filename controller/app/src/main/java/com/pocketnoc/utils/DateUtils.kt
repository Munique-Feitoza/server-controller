package com.pocketnoc.utils

import java.text.SimpleDateFormat
import java.util.*

private val BRASILIA = TimeZone.getTimeZone("America/Sao_Paulo")
private val LOCALE_BR = Locale("pt", "BR")

/** "dd/MM HH:mm" no fuso de Brasília — ex: 02/04 16:30 */
fun formatAlertTimestamp(millis: Long): String =
    SimpleDateFormat("dd/MM HH:mm", LOCALE_BR)
        .apply { timeZone = BRASILIA }
        .format(Date(millis))

/** "HH:mm:ss" no fuso de Brasília — ex: 16:30:00 */
fun formatTimeOnly(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", LOCALE_BR)
        .apply { timeZone = BRASILIA }
        .format(Date(millis))

/**
 * Converte uma string ISO 8601 UTC do agente Rust (ex: "2026-04-02T19:30:00Z")
 * para horário de Brasília formatado como "dd/MM HH:mm" (ex: "02/04 16:30").
 */
fun formatIsoToBrasilia(isoUtc: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(isoUtc)
            ?: return isoUtc.take(16).replace("T", " ")
        SimpleDateFormat("dd/MM HH:mm", LOCALE_BR)
            .apply { timeZone = BRASILIA }
            .format(date)
    } catch (_: Exception) {
        isoUtc.take(16).replace("T", " ")
    }
}
