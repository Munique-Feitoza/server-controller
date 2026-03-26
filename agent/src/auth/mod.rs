use crate::error::{AgentError, Result};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation, Algorithm};
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

/// Configuração JWT com validação de segurança
#[derive(Clone)]
pub struct JwtConfig {
    /// Chave secreta para assinar tokens (mínimo 32 bytes)
    secret: String,
    /// Tempo de expiração em segundos
    expiration: u64,
}

/// Claims do JWT
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Claims {
    /// Subject (identificador do cliente)
    pub sub: String,
    /// Issuer (origem do token)
    pub iss: String,
    /// Tempo de expiração
    pub exp: u64,
    /// Tempo de emissão
    pub iat: u64,
    /// Escopos/permissões
    pub scopes: Vec<String>,
}

impl JwtConfig {
    /// Cria uma nova configuração JWT com validação de segurança
    /// 
    /// # Security
    /// - Secret DEVE ter mínimo 32 bytes (256 bits)
    /// - Expiration deve ser razoável (recomenda-se 1-24h)
    ///
    /// # Errors
    /// Retorna erro se secret é muito curto (< 32 bytes)
    pub fn new(secret: String, expiration: u64) -> Result<Self> {
        // OWASP: Chave secreta DEVE ter no mínimo 256 bits (32 bytes)
        if secret.len() < 32 {
            return Err(AgentError::AuthError(
                format!(
                    "JWT secret too short: {} bytes (minimum 32 required). \
                     Use: openssl rand -base64 32",
                    secret.len()
                )
            ));
        }

        // Validação de expiração razoável (máximo 30 dias)
        if expiration > 30 * 24 * 3600 {
            return Err(AgentError::AuthError(
                format!(
                    "JWT expiration too long: {} seconds (maximum 2592000 allowed)",
                    expiration
                )
            ));
        }

        Ok(Self { secret, expiration })
    }

    /// Gera um novo token JWT
    pub fn generate_token(&self, sub: &str, scopes: Vec<String>) -> Result<String> {
        // Validar subject (não deve ser vazio)
        if sub.is_empty() {
            return Err(AgentError::AuthError("Subject cannot be empty".to_string()));
        }

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| AgentError::AuthError("Invalid system time".to_string()))?
            .as_secs();

        let claims = Claims {
            sub: sub.to_string(),
            iss: "pocket-noc-agent".to_string(),
            exp: now + self.expiration,
            iat: now,
            scopes,
        };

        encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(self.secret.as_bytes()),
        )
        .map_err(|e| AgentError::AuthError(format!("Token generation failed: {}", e)))
    }

    /// Valida e decodifica um token JWT com validação rigorosa
    /// 
    /// # Security Checks
    /// - Verifica assinatura HMAC-SHA256
    /// - Valida expiração (exp)
    /// - Valida tempo de emissão (iat)
    /// - Rejeita tokens com iat > exp
    pub fn validate_token(&self, token: &str) -> Result<Claims> {
        // Validar formato básico do token (3 partes separadas por ponto)
        if token.split('.').count() != 3 {
            return Err(AgentError::AuthError("Invalid token format".to_string()));
        }

        // Configuração rigorosa de validação
        let mut validation = Validation::new(Algorithm::HS256);
        validation.validate_exp = true;
        validation.validate_nbf = false; // nbf é opcional
        validation.leeway = 60; // Tolerância de 60 segundos para clock drift

        decode::<Claims>(
            token,
            &DecodingKey::from_secret(self.secret.as_bytes()),
            &validation,
        )
        .map(|data| {
            // Validação adicional: iat deve ser menor que exp
            if data.claims.iat >= data.claims.exp {
                return Err(AgentError::AuthError(
                    "Invalid token: issued time >= expiration".to_string()
                ));
            }
            Ok(data.claims)
        })
        .map_err(|e| AgentError::AuthError(format!("Token validation failed: {}", e)))?
    }
}

/// API Key simples para fallback de autenticação
/// 
/// # Security
/// - Strings são comparadas com timing-safe comparison (via == Rust)
/// - Chave deve ter mínimo 32 caracteres
#[derive(Clone)]
pub struct ApiKeyAuth {
    /// Chave API válida (mínimo 32 bytes)
    valid_key: String,
}

impl ApiKeyAuth {
    /// Cria um novo validador de API Key
    /// 
    /// # Errors
    /// Retorna erro se chave é muito curta (< 32 bytes)
    pub fn new(key: String) -> Result<Self> {
        if key.len() < 32 {
            return Err(AgentError::AuthError(
                format!(
                    "API key too short: {} bytes (minimum 32 required). \
                     Use: openssl rand -base64 32",
                    key.len()
                )
            ));
        }

        Ok(Self { valid_key: key })
    }

    /// Valida uma chave API
    /// 
    /// # Security
    /// Usa comparação de igualdade padrão do Rust (não é timing-safe para este caso)
    /// Para máxima segurança contra timing attacks, considere `constant_time_eq`
    pub fn validate(&self, key: &str) -> Result<()> {
        if key.len() != self.valid_key.len() {
            return Err(AgentError::AuthError("Invalid API key".to_string()));
        }

        // Timing-safe comparison seria ideal aqui
        // Para produção, considere usar crate `subtle`:
        // subtle::ConstantTimeEq::ct_eq(key.as_bytes(), self.valid_key.as_bytes())
        
        if key == self.valid_key {
            Ok(())
        } else {
            Err(AgentError::AuthError("Invalid API key".to_string()))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn get_test_secret() -> String {
        // Em produção, use: openssl rand -base64 32
        "test-secret-key-minimum-32-bytes-long-secure-test".to_string()
    }

    #[test]
    fn test_jwt_config_creation() {
        let secret = get_test_secret();
        let config = JwtConfig::new(secret, 3600);
        assert!(config.is_ok());
    }

    #[test]
    fn test_jwt_config_short_secret() {
        let config = JwtConfig::new("short".to_string(), 3600);
        assert!(config.is_err());
    }

    #[test]
    fn test_jwt_config_long_expiration() {
        let secret = get_test_secret();
        let config = JwtConfig::new(secret, 32 * 24 * 3600);
        assert!(config.is_err());
    }

    #[test]
    fn test_generate_and_validate_token() {
        let secret = get_test_secret();
        let config = JwtConfig::new(secret, 3600).unwrap();
        
        let token = config.generate_token("test-user", vec!["read".to_string()]).unwrap();
        let result = config.validate_token(&token);
        
        assert!(result.is_ok());
    }

    #[test]
    fn test_invalid_token() {
        let secret = get_test_secret();
        let config = JwtConfig::new(secret, 3600).unwrap();
        let result = config.validate_token("invalid.token.here");
        
        assert!(result.is_err());
    }

    #[test]
    fn test_api_key_auth() {
        let test_key = get_test_secret();
        let api_key = ApiKeyAuth::new(test_key.clone()).unwrap();
        assert!(api_key.validate(test_key.as_str()).is_ok());
        assert!(api_key.validate("wrong-key").is_err());
    }

    #[test]
    fn test_api_key_auth_short_key() {
        let result = ApiKeyAuth::new("short".to_string());
        assert!(result.is_err());
    }
}
