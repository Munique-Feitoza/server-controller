use pocket_noc_agent::commands::{CommandExecutor, default_emergency_commands};
use pocket_noc_agent::auth::JwtConfig;

#[test]
fn test_jwt_config_creation() {
    let secret = "this-is-a-32-char-test-secret-ok";
    let config = JwtConfig::new(secret.to_string(), 3600);
    assert!(config.is_ok(), "JWT config should be created with 32-char secret");
}

#[test]
fn test_jwt_config_rejects_short_secret() {
    let short = "short";
    let config = JwtConfig::new(short.to_string(), 3600);
    assert!(config.is_err(), "JWT config should reject short secrets");
}

#[test]
fn test_jwt_token_generation_and_validation() {
    let secret = "this-is-a-32-char-test-secret-ok";
    let config = JwtConfig::new(secret.to_string(), 3600).unwrap();
    let token = config.generate_token().unwrap();
    assert!(!token.is_empty(), "Token should not be empty");

    let result = config.validate_token(&token);
    assert!(result.is_ok(), "Valid token should pass validation");
}

#[test]
fn test_jwt_rejects_invalid_token() {
    let secret = "this-is-a-32-char-test-secret-ok";
    let config = JwtConfig::new(secret.to_string(), 3600).unwrap();
    let result = config.validate_token("invalid.token.here");
    assert!(result.is_err(), "Invalid token should fail validation");
}

#[test]
fn test_command_executor_lists_commands() {
    let commands = default_emergency_commands();
    let executor = CommandExecutor::new(commands);
    let list = executor.list_commands();
    assert!(!list.is_empty(), "Should have default commands");
}

#[test]
fn test_command_executor_rejects_unknown_command() {
    let commands = default_emergency_commands();
    let executor = CommandExecutor::new(commands);
    let result = executor.execute_command("nonexistent_command");
    assert!(result.is_err(), "Should reject unknown command ID");
}

#[test]
fn test_default_commands_have_expected_ids() {
    let commands = default_emergency_commands();
    let ids: Vec<String> = commands.iter().map(|c| c.id.clone()).collect();
    assert!(ids.contains(&"restart_nginx".to_string()), "Should contain restart_nginx");
    assert!(ids.contains(&"restart_docker".to_string()), "Should contain restart_docker");
    assert!(ids.contains(&"disk_usage".to_string()), "Should contain disk_usage");
}
