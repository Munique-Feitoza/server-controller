use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::middleware::Next;
use axum::response::Response;
use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::Mutex;

/// Estado compartilhado do Rate Limiter.
/// Chave: IP → (contagem de requisições, timestamp do início da janela)
#[derive(Clone)]
pub struct RateLimiter {
    pub state: Arc<Mutex<HashMap<IpAddr, (u64, Instant)>>>,
    pub max_requests: u64,
    pub window_secs: u64,
}

impl RateLimiter {
    pub fn new(max_requests: u64, window_secs: u64) -> Self {
        Self {
            state: Arc::new(Mutex::new(HashMap::new())),
            max_requests,
            window_secs,
        }
    }

    pub fn from_env() -> Self {
        let max_req = std::env::var("RATE_LIMIT_PER_MINUTE")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(60);
        Self::new(max_req, 60)
    }
}

/// Middleware de limitação de taxa por IP.
/// Retorna 429 Too Many Requests quando o limite é excedido.
pub async fn rate_limit_middleware(
    axum::extract::State(limiter): axum::extract::State<RateLimiter>,
    request: Request<Body>,
    next: Next,
) -> Result<Response, (StatusCode, String)> {
    // Extrai o IP do cliente (ConnectInfo ou header X-Forwarded-For)
    let ip = request
        .headers()
        .get("X-Forwarded-For")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.split(',').next())
        .and_then(|s| s.trim().parse::<IpAddr>().ok())
        .unwrap_or_else(|| "127.0.0.1".parse().unwrap());

    let now = Instant::now();
    let mut state = limiter.state.lock().await;

    let entry = state.entry(ip).or_insert((0, now));

    // Se a janela expirou, reinicia a contagem
    if now.duration_since(entry.1).as_secs() >= limiter.window_secs {
        *entry = (0, now);
    }

    entry.0 += 1;

    if entry.0 > limiter.max_requests {
        tracing::warn!(
            ip = %ip,
            count = entry.0,
            "Rate limit exceeded"
        );
        return Err((
            StatusCode::TOO_MANY_REQUESTS,
            format!(
                "Rate limit exceeded: {} requests per {} seconds",
                limiter.max_requests, limiter.window_secs
            ),
        ));
    }

    drop(state); // Libera o lock antes de processar a requisição
    Ok(next.run(request).await)
}
