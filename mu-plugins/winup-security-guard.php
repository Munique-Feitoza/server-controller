<?php
/**
 * Plugin Name: WinUp Security Guard
 * Description: Rate-limit de brute-force no login + alerta ntfy quando um admin e criado (com acao de revogacao).
 * Version:     1.5.0
 * Author:      WinUp
 *
 * mu-plugin: carrega automaticamente, sem ativacao, em todos os requests.
 */

declare(strict_types=1);

if (!defined('ABSPATH')) {
    exit;
}

/* ============================ CONFIG ============================ */
const WINUP_NTFY_TOPIC    = 'https://ntfy.sh/winup-secguard-a9Kp2mLq7wX4';
const WINUP_LOGIN_MAXFAIL = 5;        // tentativas de login antes de bloquear
const WINUP_LOGIN_WINDOW  = 900;      // janela de contagem (15 min)
const WINUP_LOGIN_BLOCK   = 1800;     // duracao do bloqueio (30 min)
const WINUP_REVOKE_TTL    = 604800;   // validade do token de revogacao (7 dias)

// Faixas da equipe que PULAM o lockout de login (CIDR v4 e v6). IPs dinamicos -> usar /24 e /64.
// Extensivel: basta acrescentar CIDRs. Cobre dono + editores conhecidos.
// O provedor do dono ja rodou por 4 blocos diferentes; manter TODOS, nao substituir.
// Whitelist desatualizada = dono e redacao travados no 429 sem entender por que.
// Espelha o ignoreip do fail2ban — ao acrescentar aqui, acrescentar la tambem.
const WINUP_LOGIN_WHITELIST = [
    '191.45.52.0/24',          // dono (bloco ATUAL, set/2026)
    '200.195.87.0/24',         // dono (ago/2026)
    '187.120.29.0/24',         // dono
    '152.237.160.0/24',        // dono (mais antigo)
    '177.128.145.0/24',        // editor (rede debitoecredito)
    '2804:13c:6ec:d600::/64',  // dono IPv6 (atual)
    '2804:13c:67d:8100::/64',  // dono IPv6 (antigo)
];

/* ============================ UTIL ============================ */
function winup_client_ip(): string
{
    foreach (['HTTP_CF_CONNECTING_IP', 'HTTP_X_FORWARDED_FOR', 'REMOTE_ADDR'] as $k) {
        if (!empty($_SERVER[$k])) {
            $ip = trim(explode(',', (string) $_SERVER[$k])[0]);
            if (filter_var($ip, FILTER_VALIDATE_IP)) {
                return $ip;
            }
        }
    }
    return '0.0.0.0';
}

// Match de CIDR seguro para IPv4 e IPv6 (via inet_pton, comparacao binaria).
function winup_ip_in_cidr(string $ip, string $cidr): bool
{
    if (strpos($cidr, '/') === false) {
        return $ip === $cidr;
    }
    [$net, $bits] = explode('/', $cidr, 2);
    $bits = (int) $bits;
    $ipb  = @inet_pton($ip);
    $netb = @inet_pton($net);
    if ($ipb === false || $netb === false || strlen($ipb) !== strlen($netb)) {
        return false;   // IP invalido ou familias diferentes (v4 vs v6)
    }
    $whole = intdiv($bits, 8);
    $rem   = $bits % 8;
    if ($whole > 0 && strncmp($ipb, $netb, $whole) !== 0) {
        return false;
    }
    if ($rem === 0) {
        return true;
    }
    $mask = chr((0xff << (8 - $rem)) & 0xff);
    return (($ipb[$whole] & $mask) === ($netb[$whole] & $mask));
}

// IP atual esta na whitelist da equipe?
function winup_login_whitelisted(): bool
{
    $ip = winup_client_ip();
    foreach (WINUP_LOGIN_WHITELIST as $cidr) {
        if (winup_ip_in_cidr($ip, $cidr)) {
            return true;
        }
    }
    return false;
}

function winup_ntfy(string $title, string $body, string $tags = 'warning', string $actions = ''): void
{
    // Dedup: nao reenvia o mesmo alerta (mesmo titulo) por 10 min.
    // Mitigacao do spam quando pool de IPs do atacante dispara N alertas identicos.
    $dd_key = 'winup_ntfy_dedup_' . md5($title);
    if (get_transient($dd_key)) { return; }
    set_transient($dd_key, 1, 600);

    $headers = [
        'Title'    => $title,
        'Priority' => 'urgent',
        'Tags'     => $tags,
    ];
    if ($actions !== '') {
        $headers['Actions'] = $actions;
    }
    // blocking=false: fire-and-forget, nunca atrasa o request do site.
    wp_remote_post(WINUP_NTFY_TOPIC, [
        'timeout'  => 8,
        'blocking' => false,
        'headers'  => $headers,
        'body'     => $body,
    ]);
}

/* ===================== RATE-LIMIT DE LOGIN ====================== */
add_filter('authenticate', 'winup_login_gate', 1);
function winup_login_gate($user)
{
    if (!isset($_POST['log'])) {            // so o formulario de login (nao API/app-pw)
        return $user;
    }
    if (winup_login_whitelisted()) {        // equipe nunca e bloqueada no login
        return $user;
    }
    $data = get_transient('winup_lf_' . md5(winup_client_ip()));
    if (is_array($data) && (int) ($data['blocked'] ?? 0) > time()) {
        status_header(429);
        nocache_headers();
        wp_die(
            'Acesso temporariamente bloqueado por excesso de tentativas de login. Tente novamente mais tarde.',
            'Bloqueado',
            ['response' => 429]
        );
    }
    return $user;
}

add_action('wp_login_failed', 'winup_login_failed');
function winup_login_failed($username): void
{
    if (winup_login_whitelisted()) {        // equipe nao acumula contador de bloqueio
        return;
    }
    $ip  = winup_client_ip();
    $key = 'winup_lf_' . md5($ip);
    $data = get_transient($key);
    if (!is_array($data)) {
        $data = ['count' => 0, 'blocked' => 0];
    }
    $data['count'] = (int) $data['count'] + 1;

    if ($data['count'] >= WINUP_LOGIN_MAXFAIL) {
        $data['blocked'] = time() + WINUP_LOGIN_BLOCK;
        $data['count']   = 0;
        // Bloqueio silencioso — brute-force de bots é ruído constante da internet.
    }
    set_transient($key, $data, max(WINUP_LOGIN_WINDOW, WINUP_LOGIN_BLOCK));
}

add_action('wp_login', 'winup_login_ok', 10, 0);
function winup_login_ok(): void
{
    delete_transient('winup_lf_' . md5(winup_client_ip()));   // sucesso zera o contador do IP
}

/* =================== ALERTA DE ADMIN CRIADO ===================== */
function winup_site_host(): string
{
    return (string) (parse_url(home_url(), PHP_URL_HOST) ?: home_url());
}

add_action('set_user_role', 'winup_watch_role', 10, 3);
function winup_watch_role($user_id, $role, $old_roles): void
{
    if ($role !== 'administrator') {
        return;
    }
    if (is_array($old_roles) && in_array('administrator', $old_roles, true)) {
        return;                                   // ja era admin, nao e criacao/promocao nova
    }
    winup_alert_new_admin((int) $user_id);
}

add_action('add_user_role', 'winup_watch_add_role', 10, 2);
function winup_watch_add_role($user_id, $role): void
{
    if ($role === 'administrator') {
        winup_alert_new_admin((int) $user_id);
    }
}

function winup_alert_new_admin(int $user_id): void
{
    // Dedup: set_user_role e add_user_role disparam ambos numa mesma criacao.
    static $alerted = [];
    if (isset($alerted[$user_id])) {
        return;
    }
    $alerted[$user_id] = true;

    $u = get_userdata($user_id);
    if (!$u) {
        return;
    }
    $site  = winup_site_host();
    $actor = wp_get_current_user();
    $by    = ($actor && $actor->ID) ? $actor->user_login . ' (ID ' . $actor->ID . ')' : 'desconhecido/automatizado';

    // Token single-use, por usuario, com validade — protege a acao de revogar.
    $token = wp_generate_password(40, false);
    set_transient('winup_revoke_' . $user_id, $token, WINUP_REVOKE_TTL);

    $revoke_url = add_query_arg(
        ['u' => $user_id, 't' => $token],
        home_url('/wp-json/winup-guard/v1/revoke')
    );

    $body = "Site: {$site}\n"
          . "Conta: {$u->user_login}\n"
          . "Email: {$u->user_email}\n"
          . "Criada por: {$by}\n"
          . "IP: " . winup_client_ip() . "\n"
          . "ID do usuario: {$user_id}";

    // Botao na notificacao -> revoga a conta recem-criada.
    $actions = 'http, Apagar este admin, ' . $revoke_url . ', method=POST, clear=true';

    winup_ntfy('Novo ADMIN criado em ' . $site, $body, 'rotating_light,key', $actions);

    // Reporta ao agente local PocketNOC -> alimenta a tela "Acessos Admin" do app,
    // onde o evento pode ser revisado e o admin removido pelo celular.
    // Agente escuta so em 127.0.0.1:9443; /webhook/security e rota publica local.
    wp_remote_post('http://127.0.0.1:9443/webhook/security', [
        'timeout'  => 5,
        'blocking' => false,
        'headers'  => ['Content-Type' => 'application/json'],
        'body'     => wp_json_encode([
            'source'   => 'winup-guard',
            'severity' => 'high',
            'event'    => 'admin_created',
            'ip'       => winup_client_ip(),
            'details'  => [
                'message'    => "Novo admin '{$u->user_login}' em {$site}",
                'site'       => $site,
                'path'       => rtrim(ABSPATH, '/'),
                'user_id'    => $user_id,
                'user_login' => $u->user_login,
                'email'      => $u->user_email,
                'created_by' => $by,
            ],
        ]),
    ]);
}

/* ============== ENDPOINT DE REVOGACAO (botao ntfy) ============== */
add_action('rest_api_init', function (): void {
    register_rest_route('winup-guard/v1', '/revoke', [
        'methods'             => ['GET', 'POST'],
        'callback'            => 'winup_revoke_cb',
        'permission_callback' => '__return_true',   // protegido pelo token single-use abaixo
    ]);
});

function winup_revoke_cb(WP_REST_Request $req): WP_REST_Response
{
    $uid = (int) $req->get_param('u');
    $tok = (string) $req->get_param('t');

    if ($uid <= 0 || $tok === '') {
        return new WP_REST_Response(['ok' => false, 'msg' => 'parametros invalidos'], 400);
    }
    $stored = get_transient('winup_revoke_' . $uid);
    if (!is_string($stored) || !hash_equals($stored, $tok)) {
        return new WP_REST_Response(['ok' => false, 'msg' => 'token invalido ou expirado'], 403);
    }
    delete_transient('winup_revoke_' . $uid);       // single-use

    $u = get_userdata($uid);
    if (!$u) {
        return new WP_REST_Response(['ok' => true, 'msg' => 'usuario ja nao existe'], 200);
    }
    if (!function_exists('wp_delete_user')) {
        require_once ABSPATH . 'wp-admin/includes/user.php';
    }
    // Reatribui o conteudo ao admin de menor ID que sobra.
    $others   = get_users([
        'role'    => 'administrator',
        'exclude' => [$uid],
        'fields'  => 'ID',
        'number'  => 1,
        'orderby' => 'ID',
        'order'   => 'ASC',
    ]);
    $reassign = $others ? (int) $others[0] : null;
    $ok = wp_delete_user($uid, $reassign);

    winup_ntfy(
        ($ok ? 'Admin removido: ' : 'FALHA ao remover admin: ') . winup_site_host(),
        ($ok ? 'Conta removida' : 'Nao foi possivel remover') . ": {$u->user_login} ({$u->user_email})\n"
            . 'Site: ' . winup_site_host(),
        $ok ? 'white_check_mark' : 'x'
    );

    return new WP_REST_Response(
        ['ok' => (bool) $ok, 'msg' => $ok ? 'admin removido' : 'falha ao remover', 'user' => $u->user_login],
        $ok ? 200 : 500
    );
}
