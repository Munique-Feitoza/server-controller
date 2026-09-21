// domain-monitor/examples/cal_check.rs
//! Diagnóstico rápido da integração com o Google Calendar.
//!
//! Roda em segundos (NÃO faz WHOIS): autentica a Service Account, tenta LER o
//! calendário e cria/apaga um evento de teste para confirmar a permissão de
//! escrita. Imprime um diagnóstico claro do que estiver faltando.
//!
//!   cargo run --example cal_check
//!
//! Lê as mesmas variáveis do .env: GOOGLE_CREDENTIALS e CALENDAR_ID.

use anyhow::{anyhow, bail, Context, Result};
use chrono::{Duration, Utc};

const SCOPE: &str = "https://www.googleapis.com/auth/calendar.events";
const CAL_API: &str = "https://www.googleapis.com/calendar/v3/calendars";

/// Percent-encoding para o id do calendário no path da URL (igual ao main.rs).
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

#[tokio::main]
async fn main() {
    let _ = dotenvy::dotenv();
    if let Err(erro) = executar().await {
        eprintln!("\n❌ FALHOU: {erro:#}");
        std::process::exit(1);
    }
}

async fn executar() -> Result<()> {
    let credenciais =
        std::env::var("GOOGLE_CREDENTIALS").unwrap_or_else(|_| "credentials.json".to_string());
    let calendar_id = std::env::var("CALENDAR_ID").unwrap_or_default().trim().to_string();
    if calendar_id.is_empty() {
        bail!("CALENDAR_ID não está definido no .env — preencha com o e-mail do seu calendário (ex.: voce@gmail.com).");
    }
    println!("🔑 Credenciais : {credenciais}");
    println!("📅 Calendário  : {calendar_id}\n");

    // ---- Autenticação ----
    let chave = yup_oauth2::read_service_account_key(&credenciais)
        .await
        .with_context(|| format!("não consegui ler o credentials.json em '{credenciais}'"))?;
    let sa_email = chave.client_email.clone();
    let autenticador = yup_oauth2::ServiceAccountAuthenticator::builder(chave)
        .build()
        .await
        .context("falha ao construir o autenticador da Service Account")?;
    let token = autenticador
        .token(&[SCOPE])
        .await
        .context("falha ao obter o token (a Google Calendar API está ATIVADA no projeto?)")?;
    let token = token
        .token()
        .ok_or_else(|| anyhow!("o Google devolveu um token vazio"))?
        .to_string();
    println!("✅ Autenticou como Service Account:\n   {sa_email}\n");

    let http = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()?;

    // ---- 1) LEITURA: lista 1 evento (checa API ativa + compartilhamento) ----
    let url_get = format!("{CAL_API}/{}/events?maxResults=1", encode_path(&calendar_id));
    let resp = http
        .get(&url_get)
        .bearer_auth(&token)
        .send()
        .await
        .context("falha de rede no teste de leitura")?;
    let status = resp.status();
    let corpo = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        diagnosticar(status.as_u16(), &corpo, &sa_email, &calendar_id);
        bail!("teste de LEITURA falhou (HTTP {status})");
    }
    println!("✅ LEITURA ok — a API está ativa e o calendário está compartilhado.");

    // ---- 2) ESCRITA: cria evento de teste amanhã e apaga em seguida ----
    let amanha = (Utc::now() + Duration::days(1)).date_naive();
    let depois = amanha + Duration::days(1);
    let evento = serde_json::json!({
        "summary": "✅ Teste domain-monitor (pode ignorar/apagar)",
        "start": { "date": amanha.format("%Y-%m-%d").to_string() },
        "end":   { "date": depois.format("%Y-%m-%d").to_string() },
        "transparency": "transparent",
        "reminders": { "useDefault": false }
    });
    let url_post = format!("{CAL_API}/{}/events", encode_path(&calendar_id));
    let resp = http
        .post(&url_post)
        .bearer_auth(&token)
        .json(&evento)
        .send()
        .await
        .context("falha de rede no teste de escrita")?;
    let status = resp.status();
    let corpo = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        diagnosticar(status.as_u16(), &corpo, &sa_email, &calendar_id);
        bail!("teste de ESCRITA falhou (HTTP {status}) — provável falta da permissão 'Fazer alterações nos eventos'");
    }
    let criado: serde_json::Value = serde_json::from_str(&corpo).unwrap_or_default();
    let evento_id = criado["id"].as_str().unwrap_or_default().to_string();
    println!("✅ ESCRITA ok — criou um evento de teste para {amanha}.");

    // ---- Limpeza: apaga o evento de teste ----
    if !evento_id.is_empty() {
        let url_del = format!(
            "{CAL_API}/{}/events/{}",
            encode_path(&calendar_id),
            encode_path(&evento_id),
        );
        let resp = http.delete(&url_del).bearer_auth(&token).send().await?;
        if resp.status().is_success() || resp.status().as_u16() == 410 {
            println!("🧹 Evento de teste removido.");
        } else {
            println!("⚠️  Não removi o evento de teste (id {evento_id}); pode apagar na mão.");
        }
    }

    println!("\n🎉 TUDO CERTO! O domain-monitor já consegue criar os avisos na sua agenda.");
    println!("   Falta só garantir a NOTIFICAÇÃO PADRÃO de eventos de dia inteiro no");
    println!("   calendário (Configurações → seu calendário → Notificações de eventos");
    println!("   de dia inteiro) — é ela que faz o celular avisar todo dia.");
    Ok(())
}

/// Traduz o erro HTTP do Google em uma ação concreta.
fn diagnosticar(codigo: u16, corpo: &str, sa_email: &str, calendar_id: &str) {
    eprintln!("\n--- diagnóstico ---");
    match codigo {
        403 => {
            if corpo.contains("accessNotConfigured")
                || corpo.contains("has not been used")
                || corpo.contains("is disabled")
                || corpo.contains("SERVICE_DISABLED")
            {
                eprintln!("• A Google Calendar API está DESATIVADA no projeto. Ative aqui e espere ~1 min:");
                eprintln!("  https://console.cloud.google.com/apis/library/calendar-json.googleapis.com");
            } else {
                eprintln!("• Acesso negado (sem permissão de escrita). Compartilhe o calendário");
                eprintln!("  '{calendar_id}' com:");
                eprintln!("    {sa_email}");
                eprintln!("  e marque a permissão 'Fazer alterações nos eventos'.");
            }
        }
        404 => {
            eprintln!("• Calendário '{calendar_id}' NÃO encontrado pela Service Account.");
            eprintln!("  1) Confirme se o CALENDAR_ID está exatamente certo (o e-mail do calendário).");
            eprintln!("  2) Compartilhe esse calendário com:");
            eprintln!("       {sa_email}");
            eprintln!("     (permissão 'Fazer alterações nos eventos').");
        }
        401 => eprintln!("• Token inválido/expirado — confira o credentials.json."),
        _ => {}
    }
    eprintln!("• Resposta crua do Google: {corpo}");
}
