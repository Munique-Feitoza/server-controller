<?php
// mu-plugins/winup-plugin-guard.php  (v2 — PROPOSTA, revisar antes de deploy)
/**
 * Plugin Name: WinUp Plugin Guard
 * Description: Detecta padrões de webshell na ativação de plugins. Alerta sempre; em sinal FORTE desativa + põe em quarentena (reversível). NUNCA deleta por heurística nem rebaixa admin.
 * Version:     2.0.0
 * Author:      WinUp Security
 * Network:     true
 */

if (!defined('ABSPATH')) exit;

define('WPGUARD_NTFY_TOPIC', get_option('wpguard_ntfy_topic', ''));
define('WPGUARD_LOG_KEY',    'wpguard_audit_log');
// true = move plugin com sinal FORTE pra quarentena (reversível). false = só desativa+alerta.
define('WPGUARD_QUARANTINE', (bool) get_option('wpguard_quarantine', true));

// ─── Allowlist: pula toda a análise ───────────────────────────────────────────
// Defaults = plugins populares que davam falso-positivo por tamanho/uso legítimo de eval.
// Estender via: update_option('wpguard_allowed_slugs', ['slug1','slug2']);
define('WPGUARD_ALLOWED_SLUGS', array_merge([
    'ad-inserter', 'wordpress-seo', 'wordpress-seo-premium', 'themeisle-companion',
    'elementor', 'elementor-pro', 'woocommerce', 'jetpack', 'contact-form-7',
    'wordfence', 'wpforms-lite', 'wpforms', 'litespeed-cache', 'lazy-blocks',
    'google-site-kit', 'all-in-one-seo-pack', 'seo-by-rank-math', 'updraftplus',
    'redirection', 'wp-mail-smtp', 'pixelyoursite', 'header-footer-code-manager',
    'duracelltomi-google-tag-manager', 'broken-link-checker', 'code-snippets',
], (array) get_option('wpguard_allowed_slugs', [])));

// ─── Banidos por POLÍTICA (file managers = vetor de ataque confirmado) ─────────
// Desativa + alerta. NÃO rebaixa admin.
define('WPGUARD_BANNED_SLUGS', [
    'wp-file-manager'       => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'wp-file-manager-pro'   => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'file-manager-advanced' => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'filemanager-advanced'  => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'wp-filemanager'        => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'file-manager'          => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'mr-file-manager'       => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
    'wd-file-manager'       => 'Gerenciador de arquivo (escrita direta no filesystem via browser). Vetor confirmado nos servidores WinUp.',
]);

// ─── Sinais FORTES: quase-certeza de webshell. Qualquer 1 → BLOQUEIA. ──────────
// Todos exigem input do usuário OU decode+eval — padrões que código legítimo não tem.
define('WPGUARD_STRONG', [
    ['/eval\s*\(\s*base64_decode\s*\(/i',                                        'eval+base64'],
    ['/eval\s*\(\s*str_rot13\s*\(/i',                                            'eval+rot13'],
    ['/eval\s*\(\s*gz(?:inflate|uncompress|decode)\s*\(/i',                      'eval+gzip'],
    ['/assert\s*\(\s*\$_(?:POST|GET|REQUEST|COOKIE)\b/i',                        'assert+input'],
    ['/(?:shell_exec|passthru|popen|proc_open|\bsystem)\s*\(\s*\$_(?:POST|GET|REQUEST|COOKIE)\b/i', 'exec+input'],
    ['/(?:file_put_contents|fwrite|fputs)\s*\(\s*\$_(?:POST|GET|REQUEST)\b/i',   'fwrite+input'],
    ['/preg_replace\s*\(\s*[\'"][^\'"]*\/e[\'"]\s*,/i',                          'preg_e_rce'],
    ['/move_uploaded_file\s*\([^,;]*,\s*\$_(?:POST|GET|REQUEST)\b/i',            'upload+input_path'],
]);

// ─── Sinais FRACOS: comuns em código legítimo. SÓ ALERTAM, nunca bloqueiam. ────
define('WPGUARD_WEAK', [
    ['/eval\s*\(\s*\$(?!this\b)[a-zA-Z_\x7f-\xff]/i',      'eval+variable'],
    ['/create_function\s*\(/i',                            'create_function'],
    ['/[\'"]elfinder[\'"]/i',                              'elfinder_lib'],
    ['/\bfm_(?:download|delete|rename|create_dir)\s*\(/i', 'filemanager_api'],
]);

// ─── Hooks ────────────────────────────────────────────────────────────────────
add_action('activated_plugin', 'wpguard_on_activate', 1, 2);
add_action('admin_notices',    'wpguard_show_notices');
add_action('network_admin_notices', 'wpguard_show_notices');

function wpguard_on_activate(string $plugin, bool $network_wide): void {
    $slug = dirname($plugin);
    if ($slug === '.') $slug = basename($plugin, '.php');

    if (in_array($slug, WPGUARD_ALLOWED_SLUGS, true)) return;

    // ── Banido por política: desativa + alerta (SEM rebaixar admin) ──
    $banned = WPGUARD_BANNED_SLUGS;
    if (isset($banned[$slug])) {
        deactivate_plugins($plugin, true);
        wpguard_report($plugin, ['banned_slug:' . $slug], 'bloqueado_politica', true);
        set_transient('wpguard_notice_' . get_current_user_id(),
            ['type' => 'error', 'slug' => $slug, 'msg' => $banned[$slug]], 60);
        return;
    }

    $dir = WP_PLUGIN_DIR . '/' . $slug;
    if (!is_dir($dir)) return;

    $scan = wpguard_scan_plugin($dir);

    // ── Sinal FORTE → desativa + quarentena (reversível). NUNCA deleta/rebaixa. ──
    if (!empty($scan['strong'])) {
        deactivate_plugins($plugin, true);
        $action = 'desativado';
        if (WPGUARD_QUARANTINE && wpguard_quarantine($dir, $slug)) {
            $action = 'desativado+quarentena';
        }
        wpguard_report($plugin, $scan['strong'], $action, true);
        set_transient('wpguard_notice_' . get_current_user_id(),
            ['type' => 'error', 'slug' => $slug,
             'msg'  => 'Padrão de webshell detectado (' . implode(', ', array_slice($scan['strong'], 0, 4)) . '). Plugin desativado e posto em quarentena para revisão manual.'], 60);
        return;
    }

    // ── Só sinais FRACOS → plugin FICA ATIVO, apenas registra/alerta. ──
    if (!empty($scan['weak'])) {
        wpguard_report($plugin, $scan['weak'], 'apenas_alerta', false);
    }
}

// ─── Análise: separa sinais fortes de fracos; sem acumular score por tamanho ───
function wpguard_scan_plugin(string $dir): array {
    $strong = [];
    $weak   = [];
    $files  = 0;

    try {
        $it = new RecursiveIteratorIterator(
            new RecursiveDirectoryIterator($dir, RecursiveDirectoryIterator::SKIP_DOTS),
            RecursiveIteratorIterator::LEAVES_ONLY
        );
        foreach ($it as $file) {
            if (strtolower($file->getExtension()) !== 'php') continue;
            $content = @file_get_contents($file->getPathname());
            if ($content === false || $content === '') continue;

            $files++;
            $name = basename($file->getPathname());

            foreach (WPGUARD_STRONG as [$rx, $label]) {
                if (preg_match($rx, $content)) $strong[] = "$label:$name";
            }
            foreach (WPGUARD_WEAK as [$rx, $label]) {
                if (preg_match($rx, $content)) $weak[] = "$label:$name";
            }
            // Achou sinal forte suficiente para decidir → para (economia).
            if (count($strong) >= 2) break;
        }
    } catch (Throwable $e) { /* nunca quebra o WP */ }

    return [
        'strong' => array_values(array_unique($strong)),
        'weak'   => array_values(array_unique($weak)),
        'files'  => $files,
    ];
}

// ─── Quarentena: move o plugin pra fora de wp-content/plugins (reversível) ─────
function wpguard_quarantine(string $dir, string $slug): bool {
    $real = realpath($dir);
    $base = realpath(WP_PLUGIN_DIR);
    if (!$real || !$base || strpos($real, $base) !== 0) return false;

    $qbase = WP_CONTENT_DIR . '/wpguard-quarantine';
    if (!is_dir($qbase) && !@mkdir($qbase, 0700, true)) return false;
    @file_put_contents($qbase . '/index.php', "<?php // silence\n");

    $stamp = gmdate('Ymd-His');
    $dest  = "$qbase/{$slug}-{$stamp}";
    return @rename($real, $dest);
}

// ─── Aviso no admin ───────────────────────────────────────────────────────────
function wpguard_show_notices(): void {
    $d = get_transient('wpguard_notice_' . get_current_user_id());
    if (!$d) return;
    delete_transient('wpguard_notice_' . get_current_user_id());
    printf(
        '<div class="notice notice-%s"><p><strong>🛡️ WinUp Plugin Guard:</strong> <code>%s</code><br>%s</p></div>',
        esc_attr($d['type'] === 'error' ? 'error' : 'warning'),
        esc_html($d['slug']),
        esc_html($d['msg'])
    );
}

// ─── Log + ntfy ───────────────────────────────────────────────────────────────
function wpguard_report(string $plugin, array $hits, string $action, bool $blocked): void {
    $user = wp_get_current_user();
    $entry = [
        'ts'      => current_time('mysql'),
        'site'    => get_bloginfo('url'),
        'plugin'  => $plugin,
        'hits'    => array_slice($hits, 0, 10),
        'user'    => $user->user_login ?? 'desconhecido',
        'user_id' => (int) ($user->ID ?? 0),
        'action'  => $action,
    ];

    $log = get_option(WPGUARD_LOG_KEY, []);
    array_unshift($log, $entry);
    update_option(WPGUARD_LOG_KEY, array_slice($log, 0, 200), false);

    if (WPGUARD_NTFY_TOPIC === '') return;
    $host = parse_url($entry['site'], PHP_URL_HOST) ?? $entry['site'];
    @wp_remote_post('https://ntfy.sh/' . WPGUARD_NTFY_TOPIC, [
        'timeout'  => 5,
        'blocking' => false,
        'headers'  => [
            'Title'    => ($blocked ? '🚫 Plugin bloqueado' : '⚠️ Plugin suspeito') . " em $host",
            'Priority' => $blocked ? 'urgent' : 'default',
            'Tags'     => $blocked ? 'rotating_light' : 'warning',
        ],
        'body' => implode("\n", [
            "Plugin: {$entry['plugin']}",
            "Admin:  {$entry['user']}",
            "Ação:   {$entry['action']}",
            "Hits:   " . implode(', ', $entry['hits']),
        ]),
    ]);
}
