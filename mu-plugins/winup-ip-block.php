<?php
// mu-plugins/winup-ip-block.php
/**
 * Plugin Name: WinUp IP Block
 * Description: Bloqueia IPs de atacantes confirmados antes de qualquer processamento WordPress. IPs identificados por postar conteúdo de cassino/spam em jun/2026.
 * Version:     1.0.0
 * Author:      WinUp Security
 * Network:     true
 */
defined('ABSPATH') || exit;

// IPs reais (após Cloudflare). Atualize conforme novos atacantes são identificados.
// update_option('wcipblock_extra', ['1.2.3.4', '5.6.7.8']); para adicionar via WP.
$wcipblock_static = [
    '212.34.139.107',  // ana account — 50+ sessões, casino spam
    '85.137.252.202',  // multi-conta (admin + alice) — mesmo IP
    '178.20.210.23',   // en-gansgood app-pass "auto-bootstrap"
    '31.134.12.209',   // en-gansgood app-pass "API Auto Poster"
    '188.241.176.130', // fra-zapvagas — logins repetidos com casino
    '185.93.229.5',    // fr-visavine app-pass "post"
    '193.106.150.121', // us-zapvagas XML-RPC casino post (2026-06-24 13:26) — VPS Holanda
    '64.24.17.188',    // us-zapvagas XML-RPC casino post (2026-06-24 13:37) — EUA Oklahoma
];

// IP real: usa CF-Connecting-IP quando atrás do Cloudflare
$client_ip = $_SERVER['HTTP_CF_CONNECTING_IP']
          ?? $_SERVER['HTTP_X_FORWARDED_FOR']
          ?? $_SERVER['REMOTE_ADDR']
          ?? '';

// Se vier lista no X-Forwarded-For, pega só o primeiro
if (str_contains($client_ip, ',')) {
    $client_ip = trim(explode(',', $client_ip)[0]);
}

$blocked = $wcipblock_static;

// Permite adicionar IPs extras via wp_options sem editar o arquivo
$extra = get_option('wcipblock_extra', []);
if (is_array($extra) && !empty($extra)) {
    $blocked = array_merge($blocked, $extra);
}

if ($client_ip !== '' && in_array($client_ip, $blocked, true)) {
    http_response_code(403);
    // Loga tentativa para auditoria
    error_log("[wcipblock] Blocked: {$client_ip} → " . ($_SERVER['REQUEST_URI'] ?? ''));
    exit;
}
