use crate::error::Result;
use tracing::{info, error};

/// Cliente para envio de notificações via ntfy.sh
pub struct NtfyClient {
    topic: String,
    client: reqwest::Client,
}

impl NtfyClient {
    /// Cria um novo cliente com um tópico específico
    pub fn new(topic: &str) -> Self {
        Self {
            topic: topic.to_string(),
            client: reqwest::Client::new(),
        }
    }

    /// Envia um alerta para o tópico configurado
    /// 
    /// # Padrão OMNI-DEV:
    /// Aqui aplicamos o padrão Command/Wrapper para simplificar disparos HTTP
    /// em ambientes de monitoramento.
    pub async fn send_alert(&self, title: &str, message: &str, priority: u8, tags: &str) -> Result<()> {
        let url = format!("https://ntfy.sh/{}", self.topic);
        
        let priority_str = match priority {
            5 => "urgent",
            4 => "high",
            3 => "default",
            2 => "low",
            _ => "min",
        };

        info!("Sending ntfy alert: {} to topic {}", title, self.topic);

        let response = self.client.post(&url)
            .header("Title", title)
            .header("Priority", priority_str)
            .header("Tags", tags)
            .body(message.to_string())
            .send()
            .await;

        match response {
            Ok(resp) if resp.status().is_success() => {
                info!("Ntfy notification sent successfully");
                Ok(())
            },
            Ok(resp) => {
                let status = resp.status();
                error!("Failed to send ntfy notification. Status: {}", status);
                Err(crate::error::AgentError::InternalError(format!("Ntfy error: {}", status)))
            },
            Err(e) => {
                error!("Network error sending ntfy notification: {}", e);
                Err(crate::error::AgentError::InternalError(format!("Network error: {}", e)))
            }
        }
    }
}
