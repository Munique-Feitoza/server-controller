use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;
use std::fmt;

/// Erros gerais da aplicação
#[derive(Debug)]
pub enum AgentError {
    /// Erro ao ler dados de telemetria
    TelemetryError(String),
    /// Erro ao verificar serviços
    ServiceError(String),
    /// Erro ao executar comandos
    CommandError(String),
    /// Erro de autenticação
    AuthError(String),
    /// Erro de configuração
    ConfigError(String),
    /// Erro interno do servidor
    InternalError(String),
    /// Recurso não encontrado
    NotFound(String),
}

impl fmt::Display for AgentError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::TelemetryError(msg) => write!(f, "Telemetry error: {}", msg),
            Self::ServiceError(msg) => write!(f, "Service error: {}", msg),
            Self::CommandError(msg) => write!(f, "Command error: {}", msg),
            Self::AuthError(msg) => write!(f, "Auth error: {}", msg),
            Self::ConfigError(msg) => write!(f, "Config error: {}", msg),
            Self::InternalError(msg) => write!(f, "Internal error: {}", msg),
            Self::NotFound(msg) => write!(f, "Not found: {}", msg),
        }
    }
}

impl std::error::Error for AgentError {}

impl IntoResponse for AgentError {
    fn into_response(self) -> Response {
        let (status, error_message) = match self {
            AgentError::TelemetryError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            AgentError::ServiceError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            AgentError::CommandError(msg) => (StatusCode::BAD_REQUEST, msg),
            AgentError::AuthError(msg) => (StatusCode::UNAUTHORIZED, msg),
            AgentError::ConfigError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            AgentError::InternalError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            AgentError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
        };

        let body = Json(json!({
            "error": error_message,
            "status": status.as_u16(),
        }));

        (status, body).into_response()
    }
}

/// Tipo de resultado padrão para esta aplicação
pub type Result<T> = std::result::Result<T, AgentError>;
