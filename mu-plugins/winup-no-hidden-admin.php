<?php
// wp-content/mu-plugins/winup-no-hidden-admin.php
/**
 * Plugin Name: WinUp — No Hidden Admin
 * Description: Barra a criacao de admin via REST no permission-check (filtro editable_roles) — NENHUM usuario e criado. Backstops: bloqueio no INSERT e bloqueia PROMOCAO pra admin (mantem role anterior). Permite via /wp-admin/user-new.php, /wp-admin/admin-ajax.php (admin logado + referer wp-admin), WP-CLI, ou REST autenticada por APPLICATION PASSWORD (fluxo dos devs: n8n/Forge/Adx). Criacao de admin via REST por SESSAO (cookie+nonce, vetor batch/v1) e negada. Tambem bloqueia add_role() — caminho usado pelo REST que nao dispara set_user_role. Allowlist opcional de nome de app-pass: option wpguard_rest_admin_apps (CSV). Escape hatch total: define('WINUP_NHA_ALLOW_REST_ADMIN', true). Push agrupado: max 1 notificacao por tipo a cada 15 min (filtro winup_nha_janela_alerta); o log de auditoria registra tudo.
 * Author:      WinUp
 * Version:     1.5.5
 */

declare(strict_types=1);

if (!defined('ABSPATH')) exit;

// Topico ntfy lido da option compartilhada (mesma dos WinUp Content/Plugin Guard),
// pra nao versionar o canal de alertas no codigo-fonte. Setar via:
//   wp option update wpguard_ntfy_topic '<topico>'
const WINUP_NHA_AUDIT = WP_CONTENT_DIR . '/uploads/winup-nha-audit.log';

function winup_nha_ntfy_url(): string {
    $topic = (string) get_option('wpguard_ntfy_topic', '');
    return $topic === '' ? '' : 'https://ntfy.sh/' . rawurlencode($topic);
}

function winup_nha_ctx(): array {
    return [
        'host' => $_SERVER['HTTP_HOST']             ?? 'cli',
        'uri'  => $_SERVER['REQUEST_URI']           ?? 'cli',
        'ip'   => $_SERVER['HTTP_CF_CONNECTING_IP'] ?? ($_SERVER['HTTP_X_FORWARDED_FOR'] ?? ($_SERVER['REMOTE_ADDR'] ?? '?')),
    ];
}

function winup_nha_apppass_name(int $user_id, string $uuid): string {
    if (!class_exists('WP_Application_Passwords')) return '';
    $item = WP_Application_Passwords::get_user_application_password($user_id, $uuid);
    return is_array($item) && isset($item['name']) ? (string) $item['name'] : '';
}

function winup_nha_authorized(): bool {
    if (defined('WP_CLI') && WP_CLI) return true;

    $uri = $_SERVER['REQUEST_URI'] ?? '';
    if (strpos($uri, '/wp-admin/user-new.php')         !== false) return true;
    if (strpos($uri, '/wp-admin/user-edit.php')        !== false) return true;
    if (strpos($uri, '/wp-admin/network/user-new.php') !== false) return true;

    // admin-ajax.php: aceita se ja ha um admin LOGADO E o request veio do wp-admin do mesmo host (referer).
    // Cobre tooling interno (ex.: plugins de gestao de user) sem afrouxar contra activation_hook
    // de plugin malicioso, que tipicamente dispara em /wp-admin/plugins.php?action=activate.
    if (strpos($uri, '/wp-admin/admin-ajax.php') !== false) {
        $u = wp_get_current_user();
        if ($u && $u->exists() && user_can($u, 'create_users')) {
            $referer = $_SERVER['HTTP_REFERER'] ?? '';
            $host    = $_SERVER['HTTP_HOST']    ?? '';
            if ($host !== '' && strpos($referer, '://' . $host . '/wp-admin/') !== false) {
                return true;
            }
        }
    }

    // REST/batch: separa o fluxo LEGITIMO dos devs do vetor do ATACANTE.
    //   - Devs/automacao criam admin via REST autenticando com APPLICATION PASSWORD
    //     (n8n/Forge/Adx/Prj/Lova/Edit) — rest_get_authenticated_app_password() devolve o
    //     UUID da app-pass usada. PERMITIDO.
    //   - Atacante entra com a senha compartilhada valida, abre SESSAO (cookie + nonce) e
    //     dispara POST /?rest_route=/batch/v1 pra criar admins-backup. Sessao NAO tem
    //     app-pass -> rest_get_authenticated_app_password() = null. NEGADO.
    // Allowlist opcional por NOME da app-pass (option wpguard_rest_admin_apps, CSV):
    // vazio = qualquer app-pass autenticada e aceita; preenchido = so os nomes listados.
    if (defined('REST_REQUEST') && REST_REQUEST) {
        if (function_exists('rest_get_authenticated_app_password')) {
            $uuid = rest_get_authenticated_app_password();
            if ($uuid) {
                $u = wp_get_current_user();
                if ($u && $u->exists() && user_can($u, 'create_users')) {
                    $allow = array_filter(array_map('trim', explode(',', (string) get_option('wpguard_rest_admin_apps', ''))));
                    if (!$allow) return true;
                    $name = winup_nha_apppass_name((int) $u->ID, (string) $uuid);
                    if ($name !== '' && in_array($name, $allow, true)) return true;
                }
            }
        }
        // Escape hatch total (default OFF): libera criacao via sessao autenticada.
        if (defined('WINUP_NHA_ALLOW_REST_ADMIN') && WINUP_NHA_ALLOW_REST_ADMIN) {
            $u = wp_get_current_user();
            if ($u && $u->exists() && user_can($u, 'create_users')) return true;
        }
        return false;
    }
    return false;
}

// Horario de Brasilia sempre, independente do fuso do servidor (que roda em UTC)
// e da configuracao de cada site. Alerta que chega atrasado precisa se auto-datar.
function winup_nha_ts(): string {
    try {
        return (new DateTime('now', new DateTimeZone('America/Sao_Paulo')))->format('d/m/Y H:i:s');
    } catch (Throwable $e) {
        return gmdate('d/m/Y H:i:s') . ' UTC';
    }
}

function winup_nha_alert(string $type, string $detail): void {
    $c  = winup_nha_ctx();
    $ts = winup_nha_ts();
    $msg = sprintf('[%s] %s host=%s uri=%s ip=%s', $type, $detail, $c['host'], $c['uri'], $c['ip']);

    // O log de auditoria registra SEMPRE — nada de forense se perde no agrupamento.
    @file_put_contents(WINUP_NHA_AUDIT, '['.$ts.' BRT] '.$msg."\n", FILE_APPEND | LOCK_EX);

    $url = winup_nha_ntfy_url();
    if ($url === '') return;

    // Agrupamento do push: no maximo 1 notificacao por TIPO de evento por janela.
    // Sem isto, um ataque em rajada (o udenze acumulou 40.899 bloqueios) vira uma
    // notificacao por tentativa e inutiliza o canal de alerta justamente na hora
    // em que ele mais importa. As opcoes usam autoload=false: gravacao direta na
    // linha, sem invalidar o cache de alloptions.
    $slug   = preg_replace('/[^a-z0-9_]/', '', strtolower($type));
    $k_ts   = 'winup_nha_thr_' . $slug;
    $k_cnt  = 'winup_nha_cnt_' . $slug;
    $janela = (int) apply_filters('winup_nha_janela_alerta', 900); // 15 min
    $agora  = time();
    $ultimo = (int) get_option($k_ts, 0);
    $supr   = (int) get_option($k_cnt, 0);

    if ($agora - $ultimo < $janela) {
        update_option($k_cnt, $supr + 1, false);
        return; // agrupado — segue no log, nao notifica
    }
    update_option($k_ts, $agora, false);
    update_option($k_cnt, 0, false);

    $extra = $supr > 0
        ? sprintf("\n\n+ %d evento(s) do mesmo tipo agrupados nos ultimos %d min (veja o log completo em wp-content/uploads/winup-nha-audit.log).", $supr, (int) ($janela / 60))
        : '';
    $titulo = '[WinUp] ADMIN BLOQUEADO ('.$c['host'].') '.$ts;
    if ($supr > 0) $titulo .= ' +'.$supr;

    @wp_remote_post($url, [
        'timeout' => 3, 'blocking' => false,
        'headers' => [
            'Title'    => $titulo,
            'Priority' => 'urgent',
            'Tags'     => 'no_entry,rotating_light',
        ],
        // timestamp tambem no corpo: se a notificacao atrasar, da pra saber
        // na hora se o evento e de agora ou de horas atras.
        'body' => 'OCORREU EM: '.$ts.' (Brasilia)'."\n".$msg.$extra,
    ]);
}

// (0) Barra ANTES de qualquer escrita: retira 'administrator' da lista de papeis
// atribuiveis quando a requisicao e REST e nao esta autorizada.
//
// WP_REST_Users_Controller::create_item() executa nesta ordem:
//     check_role_update( $request['roles'] )   <-- consulta get_editable_roles()
//     if ( is_wp_error(...) ) return;          <-- aborta a requisicao inteira
//     prepare_item_for_database(); wp_insert_user(); add_role();
// get_editable_roles() aplica o filtro 'editable_roles'. Removendo 'administrator'
// dele, check_role_update() devolve WP_Error e o core responde 403 SEM criar nada.
//
// Ate a 1.5.4 o usuario era criado e so depois tinha o papel removido (item 2b),
// o que deixava contas orfas no banco. Aqui a criacao nem acontece.
// Restrito a REST: o dropdown de papel do wp-admin nao e afetado.
add_filter('editable_roles', function ($roles) {
    if (!defined('REST_REQUEST') || !REST_REQUEST) return $roles;
    if (!is_array($roles) || !isset($roles['administrator'])) return $roles;
    if (winup_nha_authorized()) return $roles;
    unset($roles['administrator']);
    return $roles;
}, PHP_INT_MAX);

// (1) Bloqueia CRIACAO antes do INSERT no banco
add_filter('wp_pre_insert_user_data', function ($data, $update, $user_id, $userdata) {
    if ($update) return $data;
    if (!is_array($userdata)) return $data;
    $role = $userdata['role'] ?? '';
    if ($role !== 'administrator') return $data;
    if (winup_nha_authorized()) return $data;
    winup_nha_alert('create_blocked', sprintf(
        'login=%s email=%s', $userdata['user_login'] ?? '?', $userdata['user_email'] ?? '?'
    ));
    return [];
}, 1, 4);

// (2) Bloqueia PROMOCAO pra admin — restaura role anterior
add_action('set_user_role', function (int $user_id, string $new_role, array $old_roles): void {
    if ($new_role !== 'administrator') return;
    if (in_array('administrator', $old_roles, true)) return;
    if (winup_nha_authorized()) return;
    $u = get_userdata($user_id);
    if (!$u) return;
    $back = !empty($old_roles) ? (string)$old_roles[0] : 'subscriber';
    $u->set_role($back);
    winup_nha_alert('promotion_blocked', sprintf(
        'user=%s id=%d revertido_para=%s', $u->user_login, $u->ID, $back
    ));
}, 1, 3);

// (2b) Bloqueia CONCESSAO do papel admin via add_role().
//
// Sem isto ha um bypass completo dos itens (1) e (2): o WP_REST_Users_Controller
// cria o usuario SEM papel e so depois atribui —
//     $user_id = wp_insert_user( ... );              // sem 'role' no userdata
//     array_map( array($user,'add_role'), $roles );  // dispara 'add_user_role'
// Como (1) inspeciona $userdata['role'] (ausente) e WP_User::add_role() dispara
// 'add_user_role' e NAO 'set_user_role', nenhum dos dois via a promocao.
// Explorado ativamente via POST /wp-json/batch/v1 em 22/07/2026.
//
// set_role() tambem dispara 'add_user_role', entao os fluxos legitimos
// (wp-admin/user-new.php, WP-CLI) passam por aqui — mas winup_nha_authorized()
// os aprova, como nos demais hooks. remove_role() dispara 'remove_user_role',
// logo nao ha recursao.
add_action('add_user_role', function (int $user_id, string $role): void {
    if ($role !== 'administrator') return;
    if (winup_nha_authorized()) return;
    $u = get_userdata($user_id);
    if (!$u) return;
    $u->remove_role('administrator');
    winup_nha_alert('add_role_blocked', sprintf(
        'user=%s id=%d papel_administrator_removido', $u->user_login, $u->ID
    ));
}, 1, 2);

// (3) Strip de pre_user_query que esconde um admin REAL via "ID NOT IN (<id>)".
// So dispara se algum ID excluido for administrador. Excluir ID inexistente
// (ex.: scanner REST ?exclude=999999) ou nao-admin e benigno -> ignora.
add_action('pre_user_query', function (\WP_User_Query $q): void {
    static $reentrant = false;
    if ($reentrant) return;

    global $wpdb;
    $tbl = preg_quote($wpdb->users, '/');
    $re  = '/\s+AND\s+(?:' . $tbl . '|[A-Za-z_]*users)\.ID\s+NOT\s+IN\s*\(\s*([0-9,\s]*)\)/i';
    if (!preg_match($re, $q->query_where, $m)) return;

    $excluded = array_values(array_filter(array_map('intval', explode(',', $m[1]))));
    if (!$excluded) return;

    $reentrant = true;
    $hidden_admins = get_users(['role' => 'administrator', 'include' => $excluded, 'fields' => 'ID']);
    $reentrant = false;
    if (!$hidden_admins) return; // nenhum admin escondido -> benigno

    $q->query_where = preg_replace($re, '', $q->query_where);
    winup_nha_alert('pre_user_query_strip', 'admin_oculto_ids=' . implode(',', $hidden_admins));
}, PHP_INT_MAX);

// (4) Strip de exclude em users_list_table_query_args
add_filter('users_list_table_query_args', function ($args) {
    if (is_array($args) && isset($args['exclude'])) unset($args['exclude']);
    return $args;
}, PHP_INT_MAX);
