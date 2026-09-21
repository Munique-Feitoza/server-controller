<?php
// mu-plugins/winup-content-guard.php
/**
 * Plugin Name: WinUp Content Guard
 * Description: Bloqueio de conteúdo de cassino/apostas + detecção de credencial comprometida (login de IP novo, app password criada/usada de IP novo, XML-RPC de IP novo). Na detecção de conteúdo: post deletado + senha/sessões/app-passwords resetados. Na detecção de acesso suspeito: alerta ntfy.
 * Version: 2.1.0
 * Author:      WinUp Security
 * Network:     true
 */

defined('ABSPATH') || exit;

// Reutiliza o mesmo tópico ntfy do WinUp Plugin Guard.
// Configure via: update_option('wpguard_ntfy_topic', 'seu-topico');
define('WCGUARD_LOG_KEY',       'wcguard_audit_log');
define('WCGUARD_KNOWN_IPS_META', 'wcguard_known_ips');
define('WCGUARD_MAX_KNOWN_IPS',  10);

/**
 * Marcas de cassino/apostas + termos de gambling com contexto obrigatório.
 * Standalone "bet", "slot", "poker" ignorados para evitar falsos positivos em
 * artigos de notícia que mencionam o setor sem promovê-lo.
 */
// A fronteira usa \p{L}\p{N} (não [a-zA-ZÀ-ÿ]) porque a campanha de set/2026 veio em
// cirílico, grego, árabe e tailandês — classe latina não delimita esses alfabetos.
define('WCGUARD_PATTERN', '/(?<![\p{L}\p{N}])(?:
    cassino|casino|tigrinho|fortune[\s_]tiger|

    sportingbet|brazino\d*|blaze[\s\-]?bet|bodog|betano|betfair|
    estrela[\s]?bet|parimatch|betsson|1xbet|22bet|megapari|mostbet|
    pixbet|melbet|novibet|superbet|luckybet|esportes\s+da\s+sorte|
    betboo|bwin|unibet|vbet|ggbet|pinnacle[\s\-]?bet|

    site[\s\-]de[\s\-]apostas?|
    plataforma[\s\-]de[\s\-](?:apostas?|cassino)|
    jogo[\s]+de[\s]+azar|
    apostas?[\s]+esportivas?|
    slot[\s]+(?:online|gr[áa]tis|machine?|maquina)|
    roleta[\s]+(?:online|ao[\s]+vivo)|
    b[ôo]nus[\s]+de[\s]+(?:boas.vindas|cadastro|apostas?)|

    # --- marcas vistas na campanha de setembro 2026 (injecao de SEO no banco) ---
    vavada|pokerdom|aviamasters?|eye[\s]+of[\s]+horus|epicstar|
    kasyno|kaszin[oó]|nettikasino|ruletti|kasiino|
    sportwetten|
    deneme[\s]+bonusu|bahis[\s]+siteleri|
    gamstop|gamban|bonuskod|tragamonedas|
    scommesse[\s]+sportive|
    # NAO incluir aqui: "casa de apostas" (noticia BR usa o termo direto), nem
    # "buchmacher" ou "wettanbieter" (jornalismo DE usa direto). O guard DELETA o
    # post e reseta a senha do autor — falso positivo derruba conteudo legitimo.
    # ATENCAO: nao usar o caractere de barra em comentario aqui dentro; ele fecha
    # o delimitador do padrao e quebra o preg_match em todos os sites.

    # cirilico
    казино|букмекер|1хбет|1вин|покердом|вавада|мостбет|
    игровые[\s]+автоматы|ставки[\s]+на[\s]+спорт|

    # grego, arabe, tailandes, japones
    καζίνο|στοίχημα|φρουτάκια|
    كازينو|مراهنات|
    เดิมพัน|การพนัน|คาสิโน|
    カジノ
)(?![\p{L}\p{N}])/ixu');

// ─── Content Guard ────────────────────────────────────────────────────────────

final class WinUp_Content_Guard {

    private static ?int $flagged_uid   = null;
    private static bool $in_quarantine = false;

    public static function init(): void {
        add_filter('wp_insert_post_data', [self::class, 'intercept_post'],   1, 2);
        add_action('wp_insert_post',      [self::class, 'quarantine_post'], 99, 3);
        add_filter('preprocess_comment',  [self::class, 'intercept_comment'], 1);
    }

    public static function intercept_post(array $data, array $raw): array {
        if (self::$in_quarantine) {
            return $data;
        }
        if (!in_array($data['post_status'], ['publish', 'future', 'pending'], true)) {
            return $data;
        }

        // Verifica título + início do conteúdo (300 chars).
        // Spam de cassino sempre tem a marca no título ou na abertura.
        // Evita falsos positivos em notícias/fofoca que mencionam cassino incidentalmente.
        $title   = strip_tags($data['post_title'] ?? '');
        $opening = mb_strimwidth(strip_tags($data['post_content'] ?? ''), 0, 300);
        $haystack = $title . ' ' . $opening;
        if (!preg_match(WCGUARD_PATTERN, $haystack)) {
            return $data;
        }

        $uid = (int)($raw['post_author'] ?? get_current_user_id());
        if (wcguard_is_protected($uid)) {
            return $data;
        }

        $data['post_status'] = 'draft';
        self::$flagged_uid   = $uid;
        return $data;
    }

    public static function quarantine_post(int $post_id, \WP_Post $post, bool $update): void {
        if (self::$flagged_uid === null || self::$in_quarantine) {
            return;
        }
        if ((int)$post->post_author !== self::$flagged_uid) {
            return;
        }

        self::$in_quarantine = true;
        $uid                 = self::$flagged_uid;
        self::$flagged_uid   = null;

        wp_trash_post($post_id);

        // Reseta todas as credenciais: senha + sessões + app passwords
        $new_pass = wp_generate_password(24, true, false);
        wp_set_password($new_pass, $uid);
        WP_Session_Tokens::get_instance($uid)->destroy_all();
        if (class_exists('WP_Application_Passwords')) {
            WP_Application_Passwords::delete_all_application_passwords($uid);
        }

        $entry = wcguard_build_entry('casino_post_blocked', $uid, [
            'ip'      => sanitize_text_field($_SERVER['REMOTE_ADDR'] ?? ''),
            'title'   => mb_strimwidth(strip_tags($post->post_title), 0, 120),
            'snippet' => mb_strimwidth(strip_tags($post->post_content), 0, 200),
            'action'  => 'post_deletado+senha+sessoes+app_passwords_resetados',
        ]);
        wcguard_log($entry);
        wcguard_ntfy($entry, 'high', '🎰 Casino inject bloqueado');

        self::$in_quarantine = false;
    }

    public static function intercept_comment(array $commentdata): array {
        $haystack = mb_strimwidth(strip_tags($commentdata['comment_content'] ?? ''), 0, 300);
        if (!preg_match(WCGUARD_PATTERN, $haystack)) {
            return $commentdata;
        }

        $commentdata['comment_approved'] = 'spam';

        $uid = (int)($commentdata['user_id'] ?? 0);
        if ($uid > 0 && !wcguard_is_protected($uid)) {
            $new_pass = wp_generate_password(24, true, false);
            wp_set_password($new_pass, $uid);
            WP_Session_Tokens::get_instance($uid)->destroy_all();
            if (class_exists('WP_Application_Passwords')) {
                WP_Application_Passwords::delete_all_application_passwords($uid);
            }
        }

        $entry = wcguard_build_entry('casino_comment_blocked', $uid, [
            'ip'      => $commentdata['comment_author_IP'] ?? '',
            'author'  => $commentdata['comment_author'] ?? 'guest',
            'snippet' => mb_strimwidth($haystack, 0, 200),
            'action'  => 'comment_spam+senha+sessoes+app_passwords_resetados',
        ]);
        wcguard_log($entry);
        wcguard_ntfy($entry, 'default', '🎰 Casino comment bloqueado');

        return $commentdata;
    }
}

// ─── Credential Guard ─────────────────────────────────────────────────────────

final class WinUp_Credential_Guard {

    public static function init(): void {
        // App password criada — persistência plantada
        add_action('wp_create_application_password', [self::class, 'on_app_password_created'], 10, 4);

        // App password usada com sucesso — vetor XML-RPC/REST confirmado
        add_action('application_password_used', [self::class, 'on_app_password_used'], 10, 2);
    }

    public static function on_app_password_created(int $user_id, array $item, string $password, array $args): void {
        $ip = sanitize_text_field($_SERVER['REMOTE_ADDR'] ?? '');

        $entry = wcguard_build_entry('app_password_created', $user_id, [
            'ip'     => $ip,
            'detail' => 'App password criada: ' . ($item['name'] ?? '?'),
            'action' => 'alerta_apenas',
        ]);
        wcguard_log($entry);
        wcguard_ntfy($entry, 'high', '🔑 App password criada');
    }

    public static function on_app_password_used(\WP_User $user, array $item): void {
        $ip = sanitize_text_field($_SERVER['REMOTE_ADDR'] ?? '');
        if (self::is_known_ip($user->ID, $ip)) {
            return;
        }

        self::register_ip($user->ID, $ip);

        $entry = wcguard_build_entry('app_password_used_new_ip', $user->ID, [
            'ip'     => $ip,
            'detail' => 'App password usada de IP novo: ' . ($item['name'] ?? '?'),
            'action' => 'alerta_apenas — considere revogar em /wp-admin/profile.php',
        ]);
        wcguard_log($entry);
        wcguard_ntfy($entry, 'urgent', '🚨 App password de IP novo');
    }

    // ── IP registry por usuário ──────────────────────────────────────────────

    private static function is_known_ip(int $uid, string $ip): bool {
        if ($ip === '' || $uid <= 0) {
            return true; // Sem info: não alerta para evitar flood
        }
        $known = get_user_meta($uid, WCGUARD_KNOWN_IPS_META, true);
        return is_array($known) && in_array($ip, $known, true);
    }

    private static function register_ip(int $uid, string $ip): void {
        if ($ip === '' || $uid <= 0) {
            return;
        }
        $known = get_user_meta($uid, WCGUARD_KNOWN_IPS_META, true);
        if (!is_array($known)) {
            $known = [];
        }
        if (!in_array($ip, $known, true)) {
            $known[] = $ip;
        }
        // Mantém só os últimos N IPs para não crescer indefinidamente
        if (count($known) > WCGUARD_MAX_KNOWN_IPS) {
            $known = array_slice($known, -WCGUARD_MAX_KNOWN_IPS);
        }
        update_user_meta($uid, WCGUARD_KNOWN_IPS_META, $known);
    }
}

// ─── Utilitários compartilhados ───────────────────────────────────────────────

function wcguard_is_protected(int $uid): bool {
    if ($uid <= 0) {
        return false;
    }
    $user = get_userdata($uid);
    if ($user && $user->user_email === get_option('admin_email')) {
        return true;
    }
    $first = get_users([
        'role'    => 'administrator',
        'orderby' => 'ID',
        'order'   => 'ASC',
        'number'  => 1,
        'fields'  => 'ID',
    ]);
    return !empty($first) && (int)$first[0] === $uid;
}

function wcguard_build_entry(string $event, int $uid, array $extra = []): array {
    $user = $uid > 0 ? get_userdata($uid) : null;
    return array_merge([
        'event'   => $event,
        'ts'      => current_time('mysql'),
        'site'    => home_url(),
        'user'    => $user->user_login ?? ($extra['author'] ?? 'guest'),
        'email'   => $user->user_email ?? '',
        'user_id' => $uid,
        'ip'      => '',
        'title'   => '',
        'snippet' => '',
        'detail'  => '',
        'action'  => '',
    ], $extra);
}

function wcguard_log(array $entry): void {
    $log = get_option(WCGUARD_LOG_KEY, []);
    array_unshift($log, $entry);
    update_option(WCGUARD_LOG_KEY, array_slice($log, 0, 300), false);
}

function wcguard_ntfy(array $entry, string $priority, string $title_prefix): void {
    $topic = get_option('wpguard_ntfy_topic', '');
    if ($topic === '') {
        return;
    }

    $host = parse_url($entry['site'], PHP_URL_HOST) ?? $entry['site'];

    $lines = ["Site:  {$entry['site']}", "User:  {$entry['user']} (ID {$entry['user_id']})"];
    if ($entry['email'])   $lines[] = "Email: {$entry['email']}";
    if ($entry['ip'])      $lines[] = "IP:    {$entry['ip']}";
    if ($entry['detail'])  $lines[] = "Info:  {$entry['detail']}";
    if ($entry['title'])   $lines[] = "Título:{$entry['title']}";
    if ($entry['snippet']) $lines[] = "Trecho:{$entry['snippet']}";
    if ($entry['action'])  $lines[] = "Ação:  {$entry['action']}";

    @wp_remote_post("https://ntfy.sh/{$topic}", [
        'timeout'  => 5,
        'blocking' => false,
        'headers'  => [
            'Title'    => "{$title_prefix} em {$host}",
            'Priority' => $priority,
            'Tags'     => 'security,rotating_light',
        ],
        'body' => implode("\n", $lines),
    ]);
}

// ─── Boot ─────────────────────────────────────────────────────────────────────

WinUp_Content_Guard::init();
WinUp_Credential_Guard::init();
