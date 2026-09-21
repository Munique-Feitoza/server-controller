use crate::error::Result;
use tracing::{error, info, warn};

/// Horário de Brasília, formatado para leitura humana (dd/mm/aaaa hh:mm:ss).
///
/// Offset fixo de -03:00: o Brasil não adota horário de verão desde 2019, então
/// não há necessidade da dependência `chrono-tz` — o offset nunca muda.
///
/// # Por que existe
/// Os servidores rodam em UTC. Um alerta que chega atrasado no celular (o app
/// Android é suspenso pelo Android quando a tela fica apagada) fica
/// indistinguível de um incidente em curso. Carimbar o horário do evento na
/// própria mensagem resolve isso: dá para saber de imediato se é de agora ou
/// de horas atrás, sem precisar abrir o servidor.
pub fn agora_brt() -> String {
    let brt = chrono::FixedOffset::west_opt(3 * 3600).expect("offset -03:00 é válido");
    chrono::Utc::now()
        .with_timezone(&brt)
        .format("%d/%m/%Y %H:%M:%S")
        .to_string()
}

/// Cliente para envio de notificações via ntfy.sh
pub struct NtfyClient {
    url: String,
    client: reqwest::Client,
}

impl NtfyClient {
    /// Cria um novo cliente com um tópico específico
    pub fn new(topic: &str) -> Self {
        Self {
            url: format!("https://ntfy.sh/{}", topic),
            client: reqwest::Client::new(),
        }
    }

    /// Envia um alerta para o tópico configurado
    ///
    /// # Padrão OMNI-DEV:
    /// Aqui aplicamos o padrão Command/Wrapper para simplificar disparos HTTP
    /// em ambientes de monitoramento.
    pub async fn send_alert(
        &self,
        title: &str,
        message: &str,
        priority: u8,
        tags: &str,
    ) -> Result<()> {
        let priority_str = match priority {
            5 => "urgent",
            4 => "high",
            3 => "default",
            2 => "low",
            _ => "min",
        };

        // Carimbo de horário no título e no corpo. Notificação atrasada precisa
        // se auto-datar, senão um alerta antigo parece incidente em andamento.
        let momento = agora_brt();
        let titulo_datado = format!("{} • {}", title, momento);
        let corpo_datado = format!("🕐 OCORREU EM: {} (Brasília)\n\n{}", momento, message);

        info!(
            "Sending ntfy alert '{}' (priority: {}, ocorrido em {} BRT)",
            title, priority_str, momento
        );

        let mut attempts = 0;
        let max_retries = 3;

        while attempts <= max_retries {
            let response = self
                .client
                .post(&self.url)
                .header("Title", &titulo_datado)
                .header("Priority", priority_str)
                .header("Tags", tags)
                // Cache: no impede que ntfy.sh armazene a mensagem no canal.
                // Push real-time ao celular continua funcionando via FCM,
                // mas nao ha replay quando o app reconectar.
                .header("Cache", "no")
                .body(corpo_datado.clone())
                .send()
                .await;

            match response {
                Ok(resp) if resp.status().is_success() => {
                    info!("Ntfy notification sent successfully");
                    return Ok(());
                }
                Ok(resp) if resp.status() == reqwest::StatusCode::TOO_MANY_REQUESTS => {
                    attempts += 1;
                    let wait_secs = attempts * 2; // Exponencial simples: 2s, 4s, 6s...
                    warn!(
                        "Ntfy rate limited (429). Retry {}/{} in {}s...",
                        attempts, max_retries, wait_secs
                    );
                    tokio::time::sleep(tokio::time::Duration::from_secs(wait_secs)).await;
                }
                Ok(resp) => {
                    let status = resp.status();
                    error!("Failed to send ntfy notification. Status: {}", status);
                    return Err(crate::error::AgentError::InternalError(format!(
                        "Ntfy error: {}",
                        status
                    )));
                }
                Err(e) => {
                    error!("Network error sending ntfy notification: {}", e);
                    return Err(crate::error::AgentError::InternalError(format!(
                        "Network error: {}",
                        e
                    )));
                }
            }
        }

        Err(crate::error::AgentError::InternalError(
            "Max retries reached for Ntfy".to_string(),
        ))
    }
}
