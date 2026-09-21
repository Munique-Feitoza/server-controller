<?php
/**
 * Plugin Name: WinUp No User Enumeration
 * Description: Bloqueia enumeracao de usuarios (REST /wp/v2/users, ?author=N, oEmbed, erro de login) para visitantes nao autenticados. Nao afeta usuarios logados (editor continua funcionando).
 * Version:     1.0.0
 * Author:      WinUp Security
 * Network:     true
 */

defined('ABSPATH') || exit;

/* 1) REST API: esconde /wp/v2/users de quem NAO esta logado (admin/editor seguem normais) */
add_filter('rest_endpoints', function ($endpoints) {
    if (is_user_logged_in()) {
        return $endpoints;
    }
    foreach (array('/wp/v2/users', '/wp/v2/users/(?P<id>[\d]+)') as $route) {
        if (isset($endpoints[$route])) {
            unset($endpoints[$route]);
        }
    }
    return $endpoints;
});

/* 2) ?author=<id> numerico = enumeracao pura -> redireciona pra home (so p/ anonimo) */
add_action('parse_request', function ($wp) {
    if (is_admin() || is_user_logged_in()) {
        return;
    }
    $author = isset($_GET['author']) ? $_GET['author'] : (isset($wp->query_vars['author']) ? $wp->query_vars['author'] : '');
    if ($author !== '' && preg_match('/^\d+$/', (string) $author)) {
        wp_safe_redirect(home_url('/'), 301);
        exit;
    }
}, 0);

/* 3) oEmbed nao expoe nome/URL do autor */
add_filter('oembed_response_data', function ($data) {
    unset($data['author_url'], $data['author_name']);
    return $data;
});

/* 4) Desativa o sitemap de autores (WP 5.5+) */
add_filter('wp_sitemaps_add_provider', function ($provider, $name) {
    return ('users' === $name) ? false : $provider;
}, 10, 2);

/* 5) Mensagem de erro de login generica (nao revela se o usuario existe) */
add_filter('login_errors', function () {
    return 'Credenciais invalidas.';
});
