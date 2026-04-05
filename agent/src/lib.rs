pub mod api;
pub mod audit;
pub mod auth;
pub mod commands;
pub mod error;
pub mod services;
pub mod telemetry;
pub mod notifications;
pub mod watchdog;

pub use error::{AgentError, Result};
pub use watchdog::{WatchdogEngine, WatchdogConfig, event::WatchdogEventStore};
pub use audit::{AuditLog, AuditEntry};
