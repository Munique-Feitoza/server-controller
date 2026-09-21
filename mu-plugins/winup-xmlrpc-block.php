<?php
/**
 * Plugin Name: WinUp XML-RPC Block
 * Description: Bloqueia XML-RPC (xmlrpc.php) — vetor de brute-force/pingback. 403 direto antes de processar + desabilita a API + remove pingback.
 * Version:     1.1.0
 * Author:      WinUp Security
 * Network:     true
 */
defined('ABSPATH') || exit;

/* 1) Bloqueio duro: requisicao direta ao xmlrpc.php -> 403 antes de qualquer processamento */
$winup_scr = isset($_SERVER['SCRIPT_NAME']) ? $_SERVER['SCRIPT_NAME'] : '';
$winup_uri = isset($_SERVER['REQUEST_URI']) ? strtok($_SERVER['REQUEST_URI'], '?') : '';
if (substr($winup_scr, -11) === '/xmlrpc.php' || substr($winup_uri, -11) === '/xmlrpc.php') {
    http_response_code(403);
    exit('Forbidden');
}

/* 2) Desabilita a API XML-RPC (fallback) */
add_filter('xmlrpc_enabled', '__return_false');

/* 3) Remove pingback (reflection/DDoS) e o header X-Pingback */
add_filter('wp_headers', function ($headers) {
    unset($headers['X-Pingback']);
    return $headers;
});
add_filter('xmlrpc_methods', function ($methods) {
    unset($methods['pingback.ping'], $methods['pingback.extensions.getPingbacks']);
    return $methods;
});
