use axum::{
    extract::{Query, State, WebSocketUpgrade, ws::{Message, WebSocket}},
    response::IntoResponse,
};
use futures::{SinkExt, StreamExt};
use std::collections::HashMap;

use crate::api::handlers::AppState;
use crate::auth::JwtConfig;
use std::sync::Arc;

/// GET /ws/telemetry?token=<jwt>
///
/// WebSocket endpoint para streaming de telemetria em tempo real.
/// Autenticação via query param `token` (JWT Bearer).
/// Envia um JSON de telemetria completa a cada 5 segundos.
pub async fn ws_telemetry(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
    State(jwt_config): State<Arc<JwtConfig>>,
    Query(params): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    // Valida JWT via query param
    let token = params.get("token").cloned().unwrap_or_default();

    if let Err(e) = jwt_config.validate_token(&token) {
        tracing::error!("WebSocket auth failed: {}", e);
        return axum::http::Response::builder()
            .status(401)
            .body(axum::body::Body::from("Unauthorized"))
            .unwrap()
            .into_response();
    }

    ws.on_upgrade(move |socket| handle_telemetry_stream(socket, state))
        .into_response()
}

async fn handle_telemetry_stream(socket: WebSocket, state: AppState) {
    let (mut sender, mut receiver) = socket.split();

    tracing::info!("WebSocket client connected for telemetry streaming");

    // Task que envia telemetria a cada 5 segundos
    let send_state = state.clone();
    let mut send_task = tokio::spawn(async move {
        loop {
            let telemetry = {
                let mut collector = send_state.telemetry_collector.lock().await;
                collector.collect()
            };

            match telemetry {
                Ok(t) => {
                    let json = serde_json::to_string(&t).unwrap_or_default();
                    if sender.send(Message::Text(json)).await.is_err() {
                        break; // Client disconnected
                    }
                }
                Err(e) => {
                    let err_msg = serde_json::json!({
                        "error": format!("{}", e)
                    })
                    .to_string();
                    if sender.send(Message::Text(err_msg)).await.is_err() {
                        break;
                    }
                }
            }

            tokio::time::sleep(tokio::time::Duration::from_secs(5)).await;
        }
    });

    // Task que escuta mensagens do cliente (para graceful disconnect)
    let mut recv_task = tokio::spawn(async move {
        while let Some(msg) = receiver.next().await {
            match msg {
                Ok(Message::Close(_)) => break,
                Err(_) => break,
                _ => {} // Ignora outras mensagens
            }
        }
    });

    // Espera qualquer task terminar, então cancela a outra
    tokio::select! {
        _ = &mut send_task => recv_task.abort(),
        _ = &mut recv_task => send_task.abort(),
    }

    tracing::info!("WebSocket client disconnected");
}
