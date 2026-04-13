use crate::error::Result;
use tracing::{error, info, warn};

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

        info!(
            "Sending ntfy alert '{}' (priority: {})",
            title, priority_str
        );

        let mut attempts = 0;
        let max_retries = 3;

        while attempts <= max_retries {
            let response = self
                .client
                .post(&self.url)
                .header("Title", title)
                .header("Priority", priority_str)
                .header("Tags", tags)
                .body(message.to_string())
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
