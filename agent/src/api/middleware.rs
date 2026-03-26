use axum::middleware::Next;
use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::response::Response;
use axum::extract::State;
use crate::auth::JwtConfig;
use std::sync::Arc;

/// Middleware de validação JWT para rotas protegidas
/// 
/// Retorna 401 Unauthorized se token está missing, inválido ou expirado
pub async fn jwt_middleware(
    State(jwt_config): axum::extract::State<Arc<JwtConfig>>,
    request: Request<Body>,
    next: Next,
) -> Result<Response, (StatusCode, String)> {
    // Extrai Authorization header
    let auth_header = request
        .headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .ok_or_else(|| (StatusCode::UNAUTHORIZED, "Missing Authorization header".to_string()))?;

    // Valida formato "Bearer <token>"
    let token = auth_header
        .strip_prefix("Bearer ")
        .ok_or_else(|| (StatusCode::UNAUTHORIZED, "Invalid Bearer format".to_string()))?;

    // Valida token JWT
    jwt_config.validate_token(token)
        .map_err(|e| {
            tracing::error!("Auth failed: {e}");
            (StatusCode::UNAUTHORIZED, format!("Auth Error: {e}"))
        })?;

    // Token é válido, continua
    Ok(next.run(request).await)
}

/// Middleware que registra requisições
pub async fn logging_middleware(
    request: Request<Body>,
    next: Next,
) -> Response {
    let method = request.method().clone();
    let uri = request.uri().clone();

    let start = std::time::Instant::now();
    let response = next.run(request).await;
    let elapsed = start.elapsed();

    tracing::info!(
        method = %method,
        uri = %uri,
        status = response.status().as_u16(),
        elapsed_ms = elapsed.as_millis(),
        "HTTP request handled"
    );

    response
}
