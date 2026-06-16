<?php
// mu-plugins/winup-plugin-guard.php
/**
 * Plugin Name: WinUp Plugin Guard
 * Description: Analisa código de plugins na ativação. Bloqueia, remove e rebaixa para Editor o admin que ativou plugin com padrões maliciosos ou de gerenciador de arquivo.
 * Version:     1.0.0
 * Author:      WinUp Security
 * Network:     true
 */

if (!defined('ABSPATH')) exit;

define('WPGUARD_BLOCK_SCORE', 100);
define('WPGUARD_WARN_SCORE',   60);
// Tópico ntfy lido de wp_options — nunca hardcoded no código.
// Configure via: update_option('wpguard_ntfy_topic', 'seu-topico');
define('WPGUARD_NTFY_TOPIC',  get_option('wpguard_ntfy_topic', ''));
define('WPGUARD_LOG_KEY',     'wpguard_audit_log');

// ─── Padrões de risco ─────────────────────────────────────────────────────────
// [regex, score, label]
// Score >= 100 → bloqueia sozinho.
// Combinações que somem 100+ também bloqueiam.
define('WPGUARD_PATTERNS', [
    // RCE via eval ofuscado — cada um bloqueia sozinho
    ['/eval\s*\(\s*base64_decode\s*\(/i',                              100, 'eval+base64'],
    ['/eval\s*\(\s*str_rot13\s*\(/i',                                  100, 'eval+rot13'],
    ['/eval\s*\(\s*gz(?:inflate|uncompress|decode)\s*\(/i',            100, 'eval+gzip'],
    ['/assert\s*\(\s*\$_(?:POST|GET|REQUEST|COOKIE)\b/i',             100, 'assert+input'],
    ['/preg_replace\s*\(\s*[\'"][^\'"]*\/e[\'"],/i',                    80, 'preg_e_rce'],

    // Exec/shell com input de usuário
    ['/shell_exec\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',                100, 'shell_exec+input'],
    ['/passthru\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',                  100, 'passthru+input'],
    ['/popen\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',                     100, 'popen+input'],
    ['/\bsystem\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',                  100, 'system+input'],

    // Leitura/escrita de arquivo com caminho controlado pelo usuário
    ['/file_put_contents\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',         100, 'fwrite+input_path'],
    ['/readfile\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',                  100, 'readfile+input'],
    ['/unlink\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',                    100, 'unlink+input'],
    ['/file_get_contents\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',          70, 'fread+input'],

    // Eval de variável genérica (sozinho não bloqueia, mas soma)
    ['/eval\s*\(\s*\$(?!this\b)[a-zA-Z_\x7f-\xff]/i',                 70, 'eval+variable'],
    ['/create_function\s*\(\s*[\'"][^\'"]*[\'"],\s*\$/i',              70, 'create_function'],

    // Funcionalidade de file manager detectada pelo código, não pelo nome
    ['/\bfm_(?:download|delete|rename|create_dir|copy|move)\s*\(/i',  80, 'filemanager_api'],
    ['/class\s+(?:MFM_|WP_File_Manager|FileBrowser|EFM_)\b/i',        60, 'filemanager_class'],
    ['/[\'"]elfinder[\'"]/i',                                           60, 'elfinder_lib'],
]);

// ─── Hook principal ───────────────────────────────────────────────────────────

add_action('activated_plugin', 'wpguard_on_activate', 1, 2);

function wpguard_on_activate(string $plugin, bool $network_wide): void {
    $slug = dirname($plugin);
    if ($slug === '.') {
        $slug = basename($plugin, '.php');
    }

    $plugin_dir = WP_PLUGIN_DIR . '/' . $slug;
    if (!is_dir($plugin_dir)) {
        return;
    }

    $analysis = wpguard_scan_plugin($plugin_dir);

    if ($analysis['score'] < WPGUARD_WARN_SCORE) {
        return;
    }

    $user         = wp_get_current_user();
    $user_login   = $user->user_login ?? 'desconhecido';
    $user_id      = (int) ($user->ID ?? 0);
    $site_url     = get_bloginfo('url');
    $action_taken = 'alerta';

    if ($analysis['score'] >= WPGUARD_BLOCK_SCORE) {
        // 1. Desativa imediatamente (antes de qualquer código do plugin rodar de novo)
        deactivate_plugins($plugin, true);

        // 2. Remove os arquivos do plugin do disco
        wpguard_remove_dir($plugin_dir);

        // 3. Rebaixa admin → editor (nunca toca o user ID 1 nem o dono do site)
        if ($user_id > 1 && !wpguard_is_owner($user_id, $site_url)) {
            $wp_user = new WP_User($user_id);
            if ($wp_user->has_cap('administrator')) {
                $wp_user->set_role('editor');
                $action_taken = 'removido+admin_rebaixado';
            } else {
                $action_taken = 'removido';
            }
        } else {
            $action_taken = 'removido (dono protegido)';
        }
    }

    $entry = [
        'ts'      => current_time('mysql'),
        'site'    => $site_url,
        'plugin'  => $plugin,
        'score'   => $analysis['score'],
        'hits'    => $analysis['hits'],
        'files'   => $analysis['files_scanned'],
        'user'    => $user_login,
        'user_id' => $user_id,
        'action'  => $action_taken,
    ];

    wpguard_append_log($entry);
    wpguard_send_ntfy($entry);
}

// ─── Análise de código ────────────────────────────────────────────────────────

function wpguard_scan_plugin(string $dir): array {
    $total_score   = 0;
    $hits          = [];
    $files_scanned = 0;

    try {
        $iterator = new RecursiveIteratorIterator(
            new RecursiveDirectoryIterator($dir, RecursiveDirectoryIterator::SKIP_DOTS),
            RecursiveIteratorIterator::LEAVES_ONLY
        );

        foreach ($iterator as $file) {
            if ($file->getExtension() !== 'php') {
                continue;
            }

            $path    = $file->getPathname();
            $content = @file_get_contents($path);

            if ($content === false || $content === '') {
                continue;
            }

            $files_scanned++;
            $file_score = 0;
            $file_name  = basename($path);

            // Heurística 1: arquivo PHP >100KB com eval = webshell ofuscado
            if (strlen($content) > 102400 && preg_match('/\beval\s*\(/i', $content)) {
                $file_score += 80;
                $hits[] = "large_file_with_eval:$file_name";
            }

            // Heurística 2: linha única >5KB = código em uma linha (ofuscação clássica)
            foreach (explode("\n", $content) as $line) {
                if (strlen($line) > 5000) {
                    $file_score += 60;
                    $hits[] = "obfuscated_single_line:$file_name";
                    break;
                }
            }

            // Padrões específicos
            foreach (WPGUARD_PATTERNS as [$regex, $score, $label]) {
                if (preg_match($regex, $content)) {
                    $file_score += $score;
                    $hits[] = "$label:$file_name";
                }
            }

            $total_score += $file_score;

            // Se já passou do limiar, não precisa continuar
            if ($total_score >= WPGUARD_BLOCK_SCORE * 3) {
                break;
            }
        }
    } catch (Throwable $e) {
        // Não quebra o WP se o scan falhar
    }

    return [
        'score'         => $total_score,
        'hits'          => array_unique($hits),
        'files_scanned' => $files_scanned,
    ];
}

// ─── Utilitários ──────────────────────────────────────────────────────────────

function wpguard_remove_dir(string $dir): void {
    // Garante que só remove dentro de WP_PLUGIN_DIR
    $real = realpath($dir);
    $base = realpath(WP_PLUGIN_DIR);
    if (!$real || !$base || strpos($real, $base) !== 0) {
        return;
    }

    $items = @scandir($dir);
    if (!$items) {
        return;
    }

    foreach (array_diff($items, ['.', '..']) as $item) {
        $path = "$dir/$item";
        if (is_link($path)) {
            @unlink($path);
        } elseif (is_dir($path)) {
            wpguard_remove_dir($path);
        } else {
            @unlink($path);
        }
    }

    @rmdir($dir);
}

function wpguard_is_owner(int $user_id, string $site_url): bool {
    // Protege o usuário dono do e-mail admin do site
    $admin_email = get_option('admin_email');
    $user        = get_userdata($user_id);
    if ($user && $user->user_email === $admin_email) {
        return true;
    }

    // Protege o primeiro admin registrado (ID mais baixo com role admin)
    $first_admin = get_users([
        'role'    => 'administrator',
        'orderby' => 'ID',
        'order'   => 'ASC',
        'number'  => 1,
        'fields'  => 'ID',
    ]);

    return !empty($first_admin) && (int) $first_admin[0] === $user_id;
}

function wpguard_append_log(array $entry): void {
    $log = get_option(WPGUARD_LOG_KEY, []);
    array_unshift($log, $entry);
    update_option(WPGUARD_LOG_KEY, array_slice($log, 0, 200), false);
}

function wpguard_send_ntfy(array $entry): void {
    if (WPGUARD_NTFY_TOPIC === '') return;

    $blocked = $entry['score'] >= WPGUARD_BLOCK_SCORE;
    $host    = parse_url($entry['site'], PHP_URL_HOST) ?? $entry['site'];
    $hits    = implode(', ', array_slice($entry['hits'], 0, 6));

    @wp_remote_post('https://ntfy.sh/' . WPGUARD_NTFY_TOPIC, [
        'timeout'    => 5,
        'blocking'   => false,
        'headers'    => [
            'Title'    => ($blocked ? '🚫 Plugin bloqueado' : '⚠️ Plugin suspeito') . " em $host",
            'Priority' => $blocked ? 'urgent' : 'high',
            'Tags'     => $blocked ? 'rotating_light,no_entry' : 'warning',
        ],
        'body' => implode("\n", [
            "Plugin: {$entry['plugin']}",
            "Admin:  {$entry['user']}",
            "Score:  {$entry['score']}",
            "Ação:   {$entry['action']}",
            "Hits:   $hits",
        ]),
    ]);
}
