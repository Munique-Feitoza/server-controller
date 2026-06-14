#![forbid(unsafe_code)]
//! domain-monitor — worker assíncrono do server-controller.
//!
//! Pipeline de execução:
//!   1. Lê o `credentials.json` e obtém um token Bearer do Google (yup-oauth2).
//!   2. Limpa a aba alvo do Google Sheets e escreve o cabeçalho.
//!   3. Busca a lista de zonas (domínios) na API da Cloudflare (com paginação).
//!   4. Para cada domínio: consulta WHOIS, extrai a data de expiração e
//!      calcula os dias restantes — com um delay entre as consultas.
//!   5. Faz um ÚNICO bulk insert (append) com todos os resultados no Sheets.
//!
//! Configuração via variáveis de ambiente:
//!   CF_API_TOKEN      (obrigatório)  Token da Cloudflare (escopo Zone:Read).
//!   SPREADSHEET_ID    (obrigatório)  ID da planilha do Google.
//!   GOOGLE_CREDENTIALS (opcional)    Caminho do credentials.json [credentials.json].
//!   SHEET_NAME        (opcional)     Nome da aba alvo [Domínios].
//!   WHOIS_SERVERS     (opcional)     Caminho do servers.json do whois-rust [servers.json].
//!   WHOIS_DELAY_MS    (opcional)     Delay entre consultas WHOIS, em ms [1500].
//!   HTTP_TIMEOUT_SECS (opcional)     Timeout das requisições HTTP, em s [30].
//!   CALENDAR_ENABLED  (opcional)     Ativa os avisos no Google Calendar [false].
//!   CALENDAR_ID       (cond.)        ID do calendário (obrigatório se ENABLED=true).
//!   CALENDAR_ALERT_DAYS (opcional)   Cria o evento quando faltam <= N dias [14].
//!   CALENDAR_NAG_DAYS (opcional)     Quantos dias seguidos avisar no fim [7].

use std::collections::HashSet;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{anyhow, bail, Context, Result};
use chrono::{DateTime, NaiveDate, NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use tracing::{error, info, warn};
use whois_rust::{WhoIs, WhoIsLookupOptions};

// ============================================================
// Constantes
// ============================================================

const GOOGLE_SCOPE_SHEETS: &str = "https://www.googleapis.com/auth/spreadsheets";
const GOOGLE_SCOPE_CALENDAR: &str = "https://www.googleapis.com/auth/calendar.events";
const CLOUDFLARE_ZONES_URL: &str = "https://api.cloudflare.com/client/v4/zones";
const SHEETS_API: &str = "https://sheets.googleapis.com/v4/spreadsheets";
const CALENDAR_API: &str = "https://www.googleapis.com/calendar/v3/calendars";

/// Marcador (extended property privada) que identifica os eventos deste worker.
const CAL_MARCADOR: &str = "domainMonitor";

const CABECALHO: [&str; 5] = [
    "Domínio",
    "Status na CF",
    "Data de Expiração",
    "Dias Restantes",
    "Última Atualização",
];

/// Timeout de segurança por consulta WHOIS — mesmo que a lib trave, o pipeline segue.
const WHOIS_TIMEOUT: Duration = Duration::from_secs(25);

// ============================================================
// Configuração (lida das variáveis de ambiente)
// ============================================================

#[derive(Debug)]
struct Config {
    cf_api_tokens: Vec<String>,
    google_credentials: String,
    spreadsheet_id: String,
    sheet_name: String,
    whois_servers: String,
    whois_delay: Duration,
    http_timeout: Duration,
    // ---- Google Calendar (avisos de expiração) ----
    calendar_enabled: bool,
    calendar_id: String,
    /// Cria o evento quando faltam <= N dias para expirar.
    calendar_alert_days: i64,
    /// Quantos dias seguidos avisar (recorrência diária) antes da expiração.
    calendar_nag_days: i64,
}

impl Config {
    fn from_env() -> Result<Self> {
        let delay_ms: u64 = env_opcional("WHOIS_DELAY_MS", "1500").parse().unwrap_or(1500);
        let timeout_s: u64 = env_opcional("HTTP_TIMEOUT_SECS", "30").parse().unwrap_or(30);

        let calendar_enabled = matches!(
            env_opcional("CALENDAR_ENABLED", "false").trim().to_lowercase().as_str(),
            "1" | "true" | "sim" | "yes"
        );
        let calendar_id = env_opcional("CALENDAR_ID", "").trim().to_string();
        if calendar_enabled && calendar_id.is_empty() {
            bail!("CALENDAR_ENABLED=true exige CALENDAR_ID (o calendário compartilhado com a Service Account)");
        }
        let calendar_alert_days: i64 = env_opcional("CALENDAR_ALERT_DAYS", "14").parse().unwrap_or(14);
        let calendar_nag_days: i64 = env_opcional("CALENDAR_NAG_DAYS", "7")
            .parse::<i64>()
            .unwrap_or(7)
            .clamp(1, 30);

        Ok(Self {
            cf_api_tokens: parse_tokens(&env_obrigatorio("CF_API_TOKEN")?)?,
            google_credentials: env_opcional("GOOGLE_CREDENTIALS", "credentials.json"),
            spreadsheet_id: env_obrigatorio("SPREADSHEET_ID")?,
            sheet_name: env_opcional("SHEET_NAME", "Domínios"),
            whois_servers: env_opcional("WHOIS_SERVERS", "servers.json"),
            whois_delay: Duration::from_millis(delay_ms),
            http_timeout: Duration::from_secs(timeout_s),
            calendar_enabled,
            calendar_id,
            calendar_alert_days,
            calendar_nag_days,
        })
    }
}

fn env_obrigatorio(chave: &str) -> Result<String> {
    std::env::var(chave).map_err(|_| anyhow!("variável de ambiente obrigatória ausente: {chave}"))
}

fn env_opcional(chave: &str, padrao: &str) -> String {
    std::env::var(chave).unwrap_or_else(|_| padrao.to_string())
}

/// Divide o valor de CF_API_TOKEN em um ou mais tokens separados por vírgula —
/// assim o worker agrega as zonas de várias contas Cloudflare numa só execução.
fn parse_tokens(bruto: &str) -> Result<Vec<String>> {
    let tokens: Vec<String> = bruto
        .split(',')
        .map(|t| t.trim().to_string())
        .filter(|t| !t.is_empty())
        .collect();
    if tokens.is_empty() {
        bail!("CF_API_TOKEN não contém nenhum token válido");
    }
    Ok(tokens)
}

// ============================================================
// Modelo de dados
// ============================================================

/// Resultado processado de um domínio — uma linha da planilha.
struct RegistroDominio {
    dominio: String,
    status_cf: String,
    expiracao: String,
    dias_restantes: String,
    /// Valor numérico dos dias restantes — usado para ordenar e para o Calendar (None = sem data).
    dias_num: Option<i64>,
    /// Data de expiração tipada — fonte para o evento no Google Calendar.
    data_exp: Option<NaiveDate>,
    atualizado_em: String,
}

impl RegistroDominio {
    /// Converte o registro em uma linha (vetor de células) para o Sheets.
    fn em_linha(&self) -> Vec<String> {
        vec![
            self.dominio.clone(),
            self.status_cf.clone(),
            self.expiracao.clone(),
            self.dias_restantes.clone(),
            self.atualizado_em.clone(),
        ]
    }
}

/// Corpo aceito pela API `values` do Google Sheets.
#[derive(Serialize)]
struct CorpoValores {
    values: Vec<Vec<String>>,
}

// ============================================================
// Cloudflare — listagem de zonas (domínios)
// ============================================================

#[derive(Debug, Deserialize)]
struct CfZona {
    name: String,
    #[serde(default)]
    status: String,
}

#[derive(Debug, Deserialize)]
struct CfPaginacao {
    #[serde(default = "uma_pagina")]
    total_pages: u32,
}

fn uma_pagina() -> u32 {
    1
}

#[derive(Debug, Deserialize)]
struct CfRespostaZonas {
    success: bool,
    #[serde(default)]
    result: Vec<CfZona>,
    result_info: Option<CfPaginacao>,
    #[serde(default)]
    errors: Vec<serde_json::Value>,
}

/// Busca todas as zonas da conta Cloudflare, percorrendo a paginação.
async fn buscar_zonas_cloudflare(http: &reqwest::Client, token: &str) -> Result<Vec<CfZona>> {
    let mut zonas: Vec<CfZona> = Vec::new();
    let mut pagina: u32 = 1;

    loop {
        let pagina_str = pagina.to_string();
        let resposta = http
            .get(CLOUDFLARE_ZONES_URL)
            .bearer_auth(token)
            .query(&[("per_page", "50"), ("page", pagina_str.as_str())])
            .send()
            .await
            .context("falha de rede ao chamar a API da Cloudflare")?;

        let status = resposta.status();
        let corpo = resposta
            .text()
            .await
            .context("não foi possível ler o corpo da resposta da Cloudflare")?;

        if !status.is_success() {
            bail!("Cloudflare respondeu com HTTP {status}: {corpo}");
        }

        let dados: CfRespostaZonas =
            serde_json::from_str(&corpo).context("JSON inesperado na resposta da Cloudflare")?;

        if !dados.success {
            bail!("Cloudflare retornou success=false: {:?}", dados.errors);
        }

        let total_paginas = dados
            .result_info
            .as_ref()
            .map(|p| p.total_pages.max(1))
            .unwrap_or(1);
        let recebidas = dados.result.len();
        zonas.extend(dados.result);

        info!(pagina, total_paginas, recebidas, "página de zonas carregada");

        if pagina >= total_paginas || recebidas == 0 {
            break;
        }
        pagina += 1;
    }

    Ok(zonas)
}

// ============================================================
// WHOIS — consulta e extração da data de expiração
// ============================================================

/// Executa a consulta WHOIS de um domínio.
///
/// `whois_rust::WhoIs::lookup` é BLOQUEANTE (socket TCP síncrono), então roda
/// dentro de `spawn_blocking` para não travar o runtime assíncrono do tokio.
async fn consultar_whois(whois: Arc<WhoIs>, dominio: String) -> Result<String> {
    tokio::task::spawn_blocking(move || {
        let opcoes = WhoIsLookupOptions::from_string(&dominio)
            .map_err(|e| anyhow!("opções WHOIS inválidas para '{dominio}': {e}"))?;
        whois
            .lookup(opcoes)
            .map_err(|e| anyhow!("consulta WHOIS falhou para '{dominio}': {e}"))
    })
    .await
    .context("a tarefa de WHOIS (spawn_blocking) foi cancelada ou entrou em panic")?
}

/// Procura, no texto bruto do WHOIS, a primeira data de expiração reconhecível.
fn extrair_data_expiracao(texto_whois: &str) -> Option<NaiveDate> {
    // Rótulos de expiração usados pelos diversos registries — do mais
    // específico para o mais genérico (.com/.net, .com.br, .uk, .ru, etc.).
    const ROTULOS: [&str; 9] = [
        "registry expiry date",
        "registrar registration expiration date",
        "expiration date",
        "expiry date",
        "expire date",
        "expires on",
        "expires",
        "expire",
        "paid-till",
    ];

    for linha in texto_whois.lines() {
        let linha_min = linha.to_lowercase();
        for rotulo in ROTULOS {
            if let Some(pos) = linha_min.find(rotulo) {
                let depois = &linha[pos + rotulo.len()..];
                if let Some(idx) = depois.find(':') {
                    let valor = depois[idx + 1..].trim();
                    if let Some(data) = tentar_parsear_data(valor) {
                        return Some(data);
                    }
                }
            }
        }
    }
    None
}

/// Tenta interpretar uma data de expiração em múltiplos formatos comuns.
fn tentar_parsear_data(bruto: &str) -> Option<NaiveDate> {
    let bruto = bruto.trim();
    if bruto.is_empty() {
        return None;
    }

    // 1) ISO 8601 / RFC 3339 completo (ex.: 2026-04-13T04:00:00Z)
    if let Ok(dt) = DateTime::parse_from_rfc3339(bruto) {
        return Some(dt.date_naive());
    }

    // 2) Data + hora sem timezone
    for fmt in ["%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S"] {
        if let Ok(dt) = NaiveDateTime::parse_from_str(bruto, fmt) {
            return Some(dt.date());
        }
    }

    // 3) Apenas data — usa o primeiro token (descarta sufixos do registry)
    let token = bruto.split_whitespace().next().unwrap_or(bruto);
    for fmt in [
        "%Y-%m-%d", "%Y%m%d", "%d-%b-%Y", "%d.%m.%Y", "%Y/%m/%d", "%d/%m/%Y", "%Y.%m.%d",
    ] {
        if let Ok(data) = NaiveDate::parse_from_str(token, fmt) {
            return Some(data);
        }
    }
    None
}

// ============================================================
// Processamento dos domínios (WHOIS + cálculo de expiração)
// ============================================================

/// Consulta o WHOIS de todos os domínios, em série e com delay anti-rate-limit.
async fn processar_dominios(
    whois: Arc<WhoIs>,
    zonas: &[CfZona],
    delay: Duration,
) -> Vec<RegistroDominio> {
    let total = zonas.len();
    let mut registros = Vec::with_capacity(total);
    let mut com_data = 0usize;

    for (indice, zona) in zonas.iter().enumerate() {
        info!(dominio = %zona.name, "processando {}/{}", indice + 1, total);

        let exp = resolver_expiracao(Arc::clone(&whois), &zona.name).await;
        if exp.dias_num.is_some() {
            com_data += 1;
        }

        registros.push(RegistroDominio {
            dominio: zona.name.clone(),
            status_cf: if zona.status.is_empty() {
                "desconhecido".to_string()
            } else {
                zona.status.clone()
            },
            expiracao: exp.texto,
            dias_restantes: exp.dias_txt,
            dias_num: exp.dias_num,
            data_exp: exp.data,
            atualizado_em: Utc::now().format("%Y-%m-%d %H:%M:%S UTC").to_string(),
        });

        // Delay entre as consultas WHOIS para evitar rate limit do servidor.
        if indice + 1 < total {
            tokio::time::sleep(delay).await;
        }
    }

    // Ordena por dias restantes (crescente): os que expiram primeiro vão para o
    // topo; domínios sem data (erro / não encontrada) vão para o fim da lista.
    registros.sort_by_key(|r| r.dias_num.unwrap_or(i64::MAX));

    info!(
        total,
        com_data,
        sem_data = total - com_data,
        "consultas WHOIS finalizadas (ordenado por dias restantes)"
    );
    registros
}

/// Resultado da resolução de expiração de um domínio (texto p/ planilha + data tipada).
struct Expiracao {
    texto: String,
    dias_txt: String,
    dias_num: Option<i64>,
    data: Option<NaiveDate>,
}

impl Expiracao {
    /// Atalho para os casos de erro/sem-data (sem expiração utilizável).
    fn indisponivel(motivo: &str) -> Self {
        Self {
            texto: motivo.to_string(),
            dias_txt: "-".to_string(),
            dias_num: None,
            data: None,
        }
    }
}

/// Resolve a data de expiração de um único domínio. Nunca entra em panic:
/// qualquer falha vira um valor textual indicativo na planilha.
async fn resolver_expiracao(whois: Arc<WhoIs>, dominio: &str) -> Expiracao {
    let consulta = tokio::time::timeout(
        WHOIS_TIMEOUT,
        consultar_whois(whois, dominio.to_string()),
    )
    .await;

    let texto = match consulta {
        Ok(Ok(texto)) => texto,
        Ok(Err(erro)) => {
            warn!(dominio, erro = %erro, "consulta WHOIS falhou");
            return Expiracao::indisponivel("Erro no WHOIS");
        }
        Err(_) => {
            warn!(dominio, "consulta WHOIS excedeu o tempo limite");
            return Expiracao::indisponivel("Timeout no WHOIS");
        }
    };

    match extrair_data_expiracao(&texto) {
        Some(data) => {
            let dias = (data - Utc::now().date_naive()).num_days();
            if dias < 0 {
                warn!(dominio, data = %data, "domínio EXPIRADO");
            } else if dias <= 30 {
                warn!(dominio, data = %data, dias, "domínio expira em breve");
            }
            Expiracao {
                texto: data.format("%Y-%m-%d").to_string(),
                dias_txt: dias.to_string(),
                dias_num: Some(dias),
                data: Some(data),
            }
        }
        None => {
            warn!(dominio, "data de expiração não encontrada no WHOIS");
            Expiracao::indisponivel("Não encontrada")
        }
    }
}

// ============================================================
// Google — autenticação via Service Account
// ============================================================

/// Lê o `credentials.json` da Service Account e devolve um token Bearer válido
/// para os escopos solicitados (Sheets e, opcionalmente, Calendar).
async fn obter_token_google(caminho_credenciais: &str, escopos: &[&str]) -> Result<String> {
    let chave = yup_oauth2::read_service_account_key(caminho_credenciais)
        .await
        .with_context(|| {
            format!("não foi possível ler o credentials.json em '{caminho_credenciais}'")
        })?;

    let autenticador = yup_oauth2::ServiceAccountAuthenticator::builder(chave)
        .build()
        .await
        .context("falha ao construir o autenticador da Service Account")?;

    let token = autenticador
        .token(escopos)
        .await
        .context("falha ao obter o token de acesso do Google")?;

    token
        .token()
        .map(str::to_string)
        .ok_or_else(|| anyhow!("o Google devolveu um token de acesso vazio"))
}

// ============================================================
// Google Sheets — limpeza, cabeçalho e bulk insert
// ============================================================

/// Percent-encoding para usar nomes de aba/intervalo no path da URL.
fn encode_path(texto: &str) -> String {
    let mut saida = String::with_capacity(texto.len());
    for byte in texto.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                saida.push(byte as char)
            }
            _ => saida.push_str(&format!("%{byte:02X}")),
        }
    }
    saida
}

/// Garante que a resposta HTTP foi bem-sucedida; caso contrário, propaga o erro.
async fn garantir_sucesso(resposta: reqwest::Response, acao: &str) -> Result<()> {
    let status = resposta.status();
    if status.is_success() {
        return Ok(());
    }
    let corpo = resposta.text().await.unwrap_or_default();
    bail!("{acao}: o Google Sheets respondeu HTTP {status} — {corpo}");
}

/// Limpa todos os valores da aba alvo.
async fn limpar_aba(http: &reqwest::Client, token: &str, cfg: &Config) -> Result<()> {
    let url = format!(
        "{SHEETS_API}/{}/values/{}:clear",
        cfg.spreadsheet_id,
        // Aspas simples: nomes de aba com espaço/acento exigem aspas na notação A1.
        encode_path(&format!("'{}'", cfg.sheet_name)),
    );
    let resposta = http
        .post(&url)
        .bearer_auth(token)
        .json(&serde_json::json!({}))
        .send()
        .await
        .context("falha de rede ao limpar a aba do Sheets")?;
    garantir_sucesso(resposta, "limpar aba").await
}

/// Escreve o cabeçalho na primeira linha da aba.
async fn escrever_cabecalho(http: &reqwest::Client, token: &str, cfg: &Config) -> Result<()> {
    let intervalo = format!("'{}'!A1", cfg.sheet_name);
    let url = format!(
        "{SHEETS_API}/{}/values/{}",
        cfg.spreadsheet_id,
        encode_path(&intervalo),
    );
    let corpo = CorpoValores {
        values: vec![CABECALHO.iter().map(|c| c.to_string()).collect()],
    };
    let resposta = http
        .put(&url)
        .bearer_auth(token)
        .query(&[("valueInputOption", "RAW")])
        .json(&corpo)
        .send()
        .await
        .context("falha de rede ao escrever o cabeçalho no Sheets")?;
    garantir_sucesso(resposta, "escrever cabeçalho").await
}

/// Grava (bulk) todas as linhas processadas a partir de A2, em uma só chamada.
async fn inserir_dados(
    http: &reqwest::Client,
    token: &str,
    cfg: &Config,
    registros: &[RegistroDominio],
) -> Result<()> {
    if registros.is_empty() {
        warn!("nenhum registro para inserir — gravação ignorada");
        return Ok(());
    }

    // Escrita determinística: values:update a partir de A2 (logo abaixo do
    // cabeçalho). Diferente de :append, NÃO depende de "detecção de tabela" —
    // grava sempre nas mesmas linhas, sem risco de duplicar os dados.
    let intervalo = format!("'{}'!A2", cfg.sheet_name);
    let url = format!(
        "{SHEETS_API}/{}/values/{}",
        cfg.spreadsheet_id,
        encode_path(&intervalo),
    );
    let corpo = CorpoValores {
        values: registros.iter().map(RegistroDominio::em_linha).collect(),
    };
    let resposta = http
        .put(&url)
        .bearer_auth(token)
        // USER_ENTERED: o Sheets interpreta os tipos (ex.: "Dias Restantes"
        // vira número de verdade, permitindo a formatação condicional).
        .query(&[("valueInputOption", "USER_ENTERED")])
        .json(&corpo)
        .send()
        .await
        .context("falha de rede ao inserir os dados no Sheets")?;
    garantir_sucesso(resposta, "bulk insert").await
}

// ============================================================
// Google Calendar — avisos diários de expiração
// ============================================================
//
// Para cada domínio dentro da janela de alerta cria-se UM evento recorrente de
// dia inteiro que se repete TODOS OS DIAS na última semana antes de expirar
// (RRULE diária, COUNT = CALENDAR_NAG_DAYS). Cada ocorrência dispara a
// notificação PADRÃO do seu calendário — por isso o evento usa `useDefault`
// (uma Service Account não consegue setar lembretes na sua conta pessoal).
//
// Idempotência e "marcar como concluído":
//   * id do evento = f(domínio, data de expiração) — determinístico.
//   * Já existe e ativo -> não mexe (preserva edições suas).
//   * Não existe        -> cria.
//   * Você apaga no celular ("Todos os eventos") -> ao tentar recriar, o Google
//     responde 409/410 (id já usado) e o worker NÃO ressuscita o evento.
//   * Renovou o domínio -> a data muda, o id muda; o evento da data antiga sai
//     da lista de "manter" e é removido na faxina.

/// Evento já presente no calendário (resultado da listagem por marcador).
struct EventoCal {
    id: String,
}

/// Deriva um id de evento estável e válido para o Calendar (base32hex: `a-v`,
/// `0-9`) a partir do domínio e da data de expiração. Mesmo par -> mesmo id.
fn evento_id(dominio: &str, expiracao: NaiveDate) -> String {
    const ALFA: &[u8; 32] = b"0123456789abcdefghijklmnopqrstuv";
    let chave = format!("dm:{dominio}:{}", expiracao.format("%Y%m%d"));
    let mut saida = String::with_capacity(2 + chave.len() * 2);
    saida.push_str("dm");
    let (mut buffer, mut bits): (u32, u32) = (0, 0);
    for &byte in chave.as_bytes() {
        buffer = (buffer << 8) | byte as u32;
        bits += 8;
        while bits >= 5 {
            bits -= 5;
            saida.push(ALFA[((buffer >> bits) & 0x1F) as usize] as char);
        }
        buffer &= (1u32 << bits) - 1; // mantém só os bits que sobraram (evita overflow)
    }
    if bits > 0 {
        saida.push(ALFA[((buffer << (5 - bits)) & 0x1F) as usize] as char);
    }
    saida
}

/// Monta o corpo JSON do evento recorrente de dia inteiro.
fn corpo_evento(
    registro: &RegistroDominio,
    expiracao: NaiveDate,
    dias_aviso: i64,
) -> serde_json::Value {
    let inicio = expiracao - chrono::Duration::days(dias_aviso - 1);
    let fim = inicio + chrono::Duration::days(1); // all-day: end.date é exclusivo
    serde_json::json!({
        "id": evento_id(&registro.dominio, expiracao),
        "summary": format!("⚠️ Renovar domínio: {} (expira {})", registro.dominio, expiracao.format("%d/%m")),
        "description": format!(
            "O domínio {} expira em {}.\nStatus na Cloudflare: {}.\n\n\
Você será lembrado todos os dias nesta última semana. Quando renovar/resolver, \
basta EXCLUIR este evento (escolha \"Todos os eventos\") — ele não volta.\n\n— domain-monitor",
            registro.dominio,
            expiracao.format("%d/%m/%Y"),
            registro.status_cf,
        ),
        "start": { "date": inicio.format("%Y-%m-%d").to_string() },
        "end": { "date": fim.format("%Y-%m-%d").to_string() },
        "recurrence": [ format!("RRULE:FREQ=DAILY;COUNT={dias_aviso}") ],
        "transparency": "transparent", // não marca o dia como "ocupado"
        "reminders": { "useDefault": true },
        "extendedProperties": { "private": { "domainMonitor": "1", "dominio": registro.dominio.clone() } }
    })
}

/// Lista os eventos ATIVOS criados por este worker (filtrados pelo marcador).
async fn listar_eventos_ativos(
    http: &reqwest::Client,
    token: &str,
    cfg: &Config,
) -> Result<Vec<EventoCal>> {
    #[derive(Deserialize)]
    struct Item {
        id: String,
    }
    #[derive(Deserialize)]
    struct Lista {
        #[serde(default)]
        items: Vec<Item>,
        #[serde(rename = "nextPageToken")]
        next_page_token: Option<String>,
    }

    let url = format!("{CALENDAR_API}/{}/events", encode_path(&cfg.calendar_id));
    let filtro_marcador = format!("{CAL_MARCADOR}=1");
    let mut eventos: Vec<EventoCal> = Vec::new();
    let mut page_token: Option<String> = None;

    loop {
        let mut req = http.get(&url).bearer_auth(token).query(&[
            ("privateExtendedProperty", filtro_marcador.as_str()),
            ("showDeleted", "false"),
            ("singleEvents", "false"),
            ("maxResults", "2500"),
        ]);
        if let Some(ref t) = page_token {
            req = req.query(&[("pageToken", t.as_str())]);
        }

        let resposta = req
            .send()
            .await
            .context("falha de rede ao listar eventos do Calendar")?;
        let status = resposta.status();
        let corpo = resposta
            .text()
            .await
            .context("não foi possível ler a resposta do Calendar")?;
        if !status.is_success() {
            bail!("listar eventos: o Google Calendar respondeu HTTP {status} — {corpo}");
        }

        let lista: Lista = serde_json::from_str(&corpo)
            .context("JSON inesperado ao listar eventos do Calendar")?;
        eventos.extend(lista.items.into_iter().map(|i| EventoCal { id: i.id }));

        match lista.next_page_token {
            Some(t) => page_token = Some(t),
            None => break,
        }
    }
    Ok(eventos)
}

/// Cria o evento. Devolve `true` se criou; `false` se o id já existia — ou seja,
/// você apagou o evento no celular ("concluído") e ele NÃO deve ser ressuscitado.
async fn criar_evento(
    http: &reqwest::Client,
    token: &str,
    cfg: &Config,
    corpo: &serde_json::Value,
) -> Result<bool> {
    let url = format!("{CALENDAR_API}/{}/events", encode_path(&cfg.calendar_id));
    let resposta = http
        .post(&url)
        .bearer_auth(token)
        .json(corpo)
        .send()
        .await
        .context("falha de rede ao criar evento no Calendar")?;
    let status = resposta.status();
    if status.is_success() {
        return Ok(true);
    }
    // 409 (Conflict) / 410 (Gone): o id já existe ou já foi apagado -> respeita.
    if matches!(status.as_u16(), 409 | 410) {
        return Ok(false);
    }
    let corpo_err = resposta.text().await.unwrap_or_default();
    bail!("criar evento: o Google Calendar respondeu HTTP {status} — {corpo_err}");
}

/// Remove (cancela) um evento pelo id. Ignora 404/410 (já não existe).
async fn remover_evento(
    http: &reqwest::Client,
    token: &str,
    cfg: &Config,
    id: &str,
) -> Result<()> {
    let url = format!(
        "{CALENDAR_API}/{}/events/{}",
        encode_path(&cfg.calendar_id),
        encode_path(id),
    );
    let resposta = http
        .delete(&url)
        .bearer_auth(token)
        .send()
        .await
        .context("falha de rede ao remover evento do Calendar")?;
    let status = resposta.status();
    if status.is_success() || matches!(status.as_u16(), 404 | 410) {
        return Ok(());
    }
    let corpo = resposta.text().await.unwrap_or_default();
    bail!("remover evento: o Google Calendar respondeu HTTP {status} — {corpo}");
}

/// Sincroniza os avisos de expiração no Google Calendar: cria os que faltam para
/// domínios na janela de alerta e remove os obsoletos (renovados/saíram da CF).
/// Falhas pontuais são registradas e não abortam o pipeline.
async fn sincronizar_calendario(
    http: &reqwest::Client,
    token: &str,
    cfg: &Config,
    registros: &[RegistroDominio],
) -> Result<()> {
    info!(calendar_id = %cfg.calendar_id, "sincronizando avisos de expiração no Google Calendar…");

    let ativos = listar_eventos_ativos(http, token, cfg).await?;
    let ids_ativos: HashSet<&str> = ativos.iter().map(|e| e.id.as_str()).collect();

    let (mut criados, mut concluidos, mut removidos, mut erros) = (0u32, 0u32, 0u32, 0u32);
    let mut manter: HashSet<String> = HashSet::new();

    // ---- Cria os eventos que faltam para os domínios na janela de alerta ----
    for registro in registros {
        let Some(data) = registro.data_exp else { continue };
        let dias = registro.dias_num.unwrap_or(i64::MAX);
        // Janela: de já vencido há até 7 dias até `calendar_alert_days` no futuro.
        if !(-7..=cfg.calendar_alert_days).contains(&dias) {
            continue;
        }

        let id = evento_id(&registro.dominio, data);
        let ja_ativo = ids_ativos.contains(id.as_str());
        manter.insert(id);
        if ja_ativo {
            continue; // já existe e ativo — não mexe
        }

        let corpo = corpo_evento(registro, data, cfg.calendar_nag_days);
        match criar_evento(http, token, cfg, &corpo).await {
            Ok(true) => {
                criados += 1;
                info!(dominio = %registro.dominio, expira = %data, "evento de expiração criado");
            }
            Ok(false) => concluidos += 1, // apagado por você no celular — mantém apagado
            Err(erro) => {
                erros += 1;
                warn!(dominio = %registro.dominio, erro = %erro, "falha ao criar evento");
            }
        }
    }

    // ---- Faxina: remove eventos nossos que não devem mais existir ----
    for evento in &ativos {
        if manter.contains(&evento.id) {
            continue;
        }
        match remover_evento(http, token, cfg, &evento.id).await {
            Ok(()) => removidos += 1,
            Err(erro) => {
                erros += 1;
                warn!(id = %evento.id, erro = %erro, "falha ao remover evento obsoleto");
            }
        }
    }

    info!(
        criados,
        dispensados = concluidos,
        removidos,
        erros,
        "Google Calendar sincronizado"
    );
    Ok(())
}

// ============================================================
// Orquestração do pipeline
// ============================================================

fn iniciar_logs() {
    let filtro = tracing_subscriber::EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info"));
    tracing_subscriber::fmt()
        .with_env_filter(filtro)
        .with_target(false)
        .with_writer(std::io::stdout)
        .init();
}

async fn executar() -> Result<()> {
    let cfg = Config::from_env().context("configuração inválida")?;
    info!(
        spreadsheet_id = %cfg.spreadsheet_id,
        aba = %cfg.sheet_name,
        "domain-monitor iniciado"
    );

    let http = reqwest::Client::builder()
        .timeout(cfg.http_timeout)
        .user_agent("domain-monitor/0.1 (server-controller)")
        .build()
        .context("falha ao construir o cliente HTTP")?;

    // ---- 1. Autenticação no Google (Sheets + Calendar, se habilitado) ----
    info!("autenticando no Google via Service Account…");
    let mut escopos: Vec<&str> = vec![GOOGLE_SCOPE_SHEETS];
    if cfg.calendar_enabled {
        escopos.push(GOOGLE_SCOPE_CALENDAR);
    }
    let token_google = obter_token_google(&cfg.google_credentials, &escopos).await?;
    info!("token de acesso do Google obtido");

    // ---- 2. Lista de domínios na Cloudflare (uma ou mais contas) ----
    let total_contas = cfg.cf_api_tokens.len();
    let mut zonas: Vec<CfZona> = Vec::new();
    for (indice, token) in cfg.cf_api_tokens.iter().enumerate() {
        info!("buscando zonas na Cloudflare — conta {}/{}…", indice + 1, total_contas);
        let zonas_conta = buscar_zonas_cloudflare(&http, token).await?;
        info!(
            conta = indice + 1,
            zonas = zonas_conta.len(),
            "zonas da conta carregadas"
        );
        zonas.extend(zonas_conta);
    }
    info!(total = zonas.len(), contas = total_contas, "zonas obtidas da Cloudflare");

    // ---- 3. WHOIS + cálculo de expiração de cada domínio ----
    let whois = WhoIs::from_path(&cfg.whois_servers).map_err(|e| {
        anyhow!(
            "falha ao carregar a base de servidores WHOIS '{}': {e}",
            cfg.whois_servers
        )
    })?;
    let registros = processar_dominios(Arc::new(whois), &zonas, cfg.whois_delay).await;

    // ---- 4. Grava no Google Sheets — limpa a aba + cabeçalho + dados, tudo
    //         só agora (fim), para a aba não ficar vazia durante o WHOIS ----
    info!(
        aba = %cfg.sheet_name,
        linhas = registros.len(),
        "limpando a aba e gravando os resultados…"
    );
    limpar_aba(&http, &token_google, &cfg).await?;
    escrever_cabecalho(&http, &token_google, &cfg).await?;
    inserir_dados(&http, &token_google, &cfg, &registros).await?;

    // ---- 5. Google Calendar: avisos diários de expiração (best-effort) ----
    if cfg.calendar_enabled {
        // Não falha o run: a planilha já foi atualizada com sucesso.
        if let Err(erro) = sincronizar_calendario(&http, &token_google, &cfg, &registros).await {
            warn!("sincronização do Google Calendar falhou: {erro:#}");
        }
    } else {
        info!("Google Calendar desativado (defina CALENDAR_ENABLED=true para ativar) — etapa ignorada");
    }

    info!("pipeline concluído com sucesso");
    Ok(())
}

#[tokio::main]
async fn main() {
    // Carrega o arquivo .env (se existir) antes de ler qualquer variável.
    let _ = dotenvy::dotenv();
    iniciar_logs();

    if let Err(erro) = executar().await {
        // `{erro:#}` imprime toda a cadeia de causas (anyhow context).
        error!("o worker falhou: {erro:#}");
        std::process::exit(1);
    }
}
