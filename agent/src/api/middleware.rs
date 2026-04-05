use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::middleware::Next;
use axum::response::Response;
use axum::extract::State;
use crate::auth::JwtConfig;
use crate::security::{ThreatTracker, ThreatAction, is_honeypot_path, gerar_zip_bomb};
use std::net::IpAddr;
use std::sync::Arc;
use tokio::sync::Mutex;

/// Estado compartilhado do sistema de defesa
#[derive(Clone)]
pub struct SecurityState {
    pub jwt_config: Arc<JwtConfig>,
    pub threat_tracker: Arc<Mutex<ThreatTracker>>,
}

/// Middleware principal de seguranca.
///
/// Regras importantes:
/// - Honeypot paths (wp-admin, .env, .git, etc) = AMEACA → conta para ban
/// - Erro de JWT (token expirado, header ausente) = ERRO NORMAL → so retorna 401
/// - IPs seguros (127.0.0.1, 192.168.*, 10.*, 172.16-17.*) = NUNCA banidos
/// - Apenas honeypot paths contam para o limiar de 5 tentativas
/// - Na 5a tentativa de honeypot: zip bomb + auto-ban via iptables
pub async fn security_middleware(
    State(security): State<SecurityState>,
    request: Request<Body>,
    next: Next,
) -> Result<Response, Response> {
    let path = request.uri().path().to_string();
    let method = request.method().clone();

    // Extrai IP do cliente
    let ip: IpAddr = request
        .headers()
        .get("X-Forwarded-For")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.split(',').next())
        .and_then(|s| s.trim().parse().ok())
        .unwrap_or_else(|| "127.0.0.1".parse().unwrap());

    let user_agent = request
        .headers()
        .get("User-Agent")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("unknown")
        .to_string();

    // ─── 1. Verifica se o IP ja esta banido ─────────────────────
    {
        let tracker = security.threat_tracker.lock().await;
        if tracker.esta_banido(&ip) {
            return Err(Response::builder()
                .status(StatusCode::FORBIDDEN)
                .body(Body::from("Forbidden"))
                .unwrap());
        }
    }

    // ─── 2. Honeypot: detecta scanners de vulnerabilidade ───────
    // APENAS honeypot paths contam como ameaca.
    // Esses paths nunca existem no agente — qualquer acesso eh bot/scanner.
    if is_honeypot_path(&path) {
        tracing::warn!(
            "🍯 HONEYPOT: {} {} de {} (UA: {})",
            method, path, ip, &user_agent[..user_agent.len().min(60)]
        );

        let mut tracker = security.threat_tracker.lock().await;
        let action = tracker.registrar_honeypot(ip, &path, &user_agent);

        return match action {
            ThreatAction::NeutralizarEBanir => {
                tracing::error!("💣 ZIP BOMB servido para {} apos acessos repetidos a honeypots", ip);
                let bomb = gerar_zip_bomb();
                Ok(Response::builder()
                    .status(200)
                    .header("Content-Type", "application/gzip")
                    .header("Content-Disposition", "attachment; filename=\"backup.tar.gz\"")
                    .header("Content-Encoding", "gzip")
                    .body(Body::from(bomb))
                    .unwrap())
            }
            ThreatAction::IpSeguro => {
                // Dev acessou honeypot sem querer — responde 404 normal
                Ok(Response::builder()
                    .status(404)
                    .body(Body::from("Not Found"))
                    .unwrap())
            }
            _ => {
                // Resposta 404 generica para nao revelar que eh honeypot
                Ok(Response::builder()
                    .status(404)
                    .body(Body::from("Not Found"))
                    .unwrap())
            }
        };
    }

    // ─── 3. Rota /health nao precisa de auth ────────────────────
    if path == "/health" {
        return Ok(next.run(request).await);
    }

    // ─── 4. Validacao JWT (erro = 401 normal, NUNCA conta para ban) ──
    let auth_header = request
        .headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok());

    let auth_header = match auth_header {
        Some(h) => h.to_string(),
        None => {
            // Sem header — erro normal, nao conta como ameaca
            return Err(Response::builder()
                .status(StatusCode::UNAUTHORIZED)
                .body(Body::from("Missing Authorization header"))
                .unwrap());
        }
    };

    let token = match auth_header.strip_prefix("Bearer ") {
        Some(t) => t.to_string(),
        None => {
            return Err(Response::builder()
                .status(StatusCode::UNAUTHORIZED)
                .body(Body::from("Invalid Bearer format"))
                .unwrap());
        }
    };

    match security.jwt_config.validate_token(&token) {
        Ok(_) => {
            // Auth OK — prossegue normalmente
            Ok(next.run(request).await)
        }
        Err(e) => {
            // Token invalido/expirado — erro normal, NAO eh ameaca.
            // Devs com token expirado apenas recebem 401 para renovar.
            tracing::warn!("Auth falhou para {} (erro normal, nao eh ameaca): {}", ip, e);

            Err(Response::builder()
                .status(StatusCode::UNAUTHORIZED)
                .body(Body::from(format!("Auth Error: {}", e)))
                .unwrap())
        }
    }
}

/// Middleware de logging
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
