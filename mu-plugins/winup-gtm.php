<?php
// mu-plugins/winup-gtm.php
declare(strict_types=1);

/**
 * Plugin Name: WinUp GTM
 * Description: Injeta o container do Google Tag Manager sem depender de plugin instalável.
 * Version:     1.0.0
 *
 * Por que mu-plugin e não plugin: o winup-plugin-guard bloqueia/remove plugins
 * instalados, e o GTM não justifica abrir exceção nele — o container é só um par
 * de snippets. Como arquivo em mu-plugins, carrega sempre e não passa pelo guard.
 *
 * O ID do container vem, nesta ordem:
 *   1. constante WINUP_GTM_ID no wp-config.php   (preferido: versionável por site)
 *   2. option `winup_gtm_id` no banco             (para ajuste pontual sem deploy)
 * Sem nenhum dos dois, o plugin fica inerte — nunca injeta marcação quebrada.
 */

if (!defined('ABSPATH')) {
    exit;
}

final class WinUp_GTM
{
    /** GTM aceita sufixo alfanumérico maiúsculo; validar evita injetar lixo no <head>. */
    private const FORMATO_ID = '/^GTM-[A-Z0-9]{4,10}$/';

    private static ?string $id = null;

    public static function boot(): void
    {
        // Prioridade 1: o GTM precisa ser o primeiro script do <head> para não
        // perder eventos disparados por scripts que carregam antes dele.
        add_action('wp_head', [self::class, 'cabecalho'], 1);
        add_action('wp_body_open', [self::class, 'corpo'], 1);
    }

    /**
     * O container só pode sair em renderização de página pública.
     *
     * Esta guarda existe por causa de um incidente real: um plugin custom
     * escrevia no `wp_head` sem checar contexto e acabava anexando texto depois
     * do JSON das respostas REST — o Gutenberg passou a recusar salvar posts
     * com "resposta não é um JSON válido". Qualquer coisa que imprima precisa
     * ficar fora de REST, AJAX, XML-RPC, feed, CLI, cron e admin.
     */
    private static function pode_imprimir(): bool
    {
        if (is_admin() || is_feed()) {
            return false;
        }
        if (wp_doing_ajax() || wp_doing_cron()) {
            return false;
        }
        if (defined('REST_REQUEST') && REST_REQUEST) {
            return false;
        }
        if (defined('XMLRPC_REQUEST') && XMLRPC_REQUEST) {
            return false;
        }
        if (defined('WP_CLI') && WP_CLI) {
            return false;
        }
        // Cobre requisições que pedem JSON sem passar pela constante REST_REQUEST.
        if (function_exists('wp_is_json_request') && wp_is_json_request()) {
            return false;
        }

        return true;
    }

    private static function id(): ?string
    {
        if (self::$id !== null) {
            return self::$id ?: null;
        }

        $bruto = '';
        if (defined('WINUP_GTM_ID')) {
            $bruto = (string) WINUP_GTM_ID;
        } elseif (function_exists('get_option')) {
            $bruto = (string) get_option('winup_gtm_id', '');
        }

        $bruto = strtoupper(trim($bruto));
        self::$id = preg_match(self::FORMATO_ID, $bruto) === 1 ? $bruto : '';

        return self::$id ?: null;
    }

    public static function cabecalho(): void
    {
        $id = self::id();
        if ($id === null || !self::pode_imprimir()) {
            return;
        }

        // rawurlencode no ID: ele já passou pela validação de formato, mas o
        // valor entra numa URL e num literal JS — escapar nos dois contextos é
        // o que impede que uma option adulterada no banco vire XSS.
        $url = esc_js(rawurlencode($id));
        $js  = esc_js($id);

        echo "\n<!-- WinUp GTM -->\n";
        echo "<script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':"
           . "new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],"
           . "j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;"
           . "j.src='https://www.googletagmanager.com/gtm.js?id='+i+dl;"
           . "f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','{$js}');</script>\n";
        unset($url);
    }

    public static function corpo(): void
    {
        $id = self::id();
        if ($id === null || !self::pode_imprimir()) {
            return;
        }

        // O noscript só atende visitante sem JavaScript. Depende do tema chamar
        // `wp_body_open()`; tema antigo que não chama simplesmente não recebe
        // este trecho, e o container continua funcionando pelo <head>.
        $url = esc_url('https://www.googletagmanager.com/ns.html?id=' . rawurlencode($id));

        echo "<noscript><iframe src=\"{$url}\" height=\"0\" width=\"0\""
           . " style=\"display:none;visibility:hidden\"></iframe></noscript>\n";
    }
}

WinUp_GTM::boot();
