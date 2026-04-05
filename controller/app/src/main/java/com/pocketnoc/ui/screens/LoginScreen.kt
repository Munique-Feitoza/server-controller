package com.pocketnoc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.pocketnoc.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String, String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    var serverName by remember { mutableStateOf("") }
    var serverUrl  by remember { mutableStateOf("") }
    var secretKey  by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }

    // Validação inline
    val secretError = secretKey.length in 1..31  // vazio ok (não toca), < 32 é inválido

    val canConnect = serverName.isNotBlank() && serverUrl.isNotBlank() &&
            (secretKey.isEmpty() || secretKey.length >= 32)

    // Pulso no ícone de cadeado
    val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
    val lockGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "lock_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.Space4xl - Dimens.SpaceXs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Ícone de segurança
            Box(
                modifier = Modifier
                    .size(Dimens.Icon3xl + Dimens.SpaceXxl)
                    .shadow(Dimens.SpaceXxl, AppShapes.sheet, spotColor = colors.primary.copy(alpha = lockGlow))
                    .clip(AppShapes.sheet)
                    .background(colors.surfaceVariant)
                    .border(Dimens.BorderThin, colors.primary.copy(alpha = lockGlow * 0.8f), AppShapes.sheet),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (canConnect) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = lockGlow),
                    modifier = Modifier.size(Dimens.ActionButton)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceXxl))

            Text(
                "POCKET NOC",
                style = MaterialTheme.typography.displaySmall,
                color = colors.primary
            )
            Text(
                "ADICIONAR SERVIDOR",
                style = MaterialTheme.typography.labelSmall,
                color = colors.outlineVariant,
                letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
            )

            Spacer(modifier = Modifier.height(Dimens.Icon2xl))

            // Card do formulário
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(Dimens.SpaceXxl, AppShapes.sheet, spotColor = colors.primary.copy(alpha = 0.25f))
                    .clip(AppShapes.sheet)
                    .background(colors.surfaceVariant)
                    .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.2f), AppShapes.sheet)
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.Space3xl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)
                ) {

                    // Nome do servidor
                    NocTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = "Nome do Servidor",
                        placeholder = "ex: Prod-01",
                        leadingIcon = Icons.Default.Storage,
                        accentColor = colors.tertiary,
                        isError = false
                    )

                    // URL
                    NocTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = "Endereço (IP:Porta)",
                        placeholder = "ex: 192.168.1.1:9443",
                        leadingIcon = Icons.Default.Language,
                        accentColor = colors.primary,
                        isError = false,
                        keyboardType = KeyboardType.Uri
                    )

                    // Secret
                    NocTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = "JWT Secret (mín. 32 chars)",
                        placeholder = "POCKET_NOC_SECRET do servidor",
                        leadingIcon = Icons.Default.Key,
                        accentColor = ext.magenta,
                        isError = secretError,
                        isPassword = !secretVisible,
                        trailingIcon = {
                            IconButton(onClick = { secretVisible = !secretVisible }) {
                                Icon(
                                    imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(Dimens.IconMd - Dimens.SpaceXxs)
                                )
                            }
                        }
                    )

                    if (secretError) {
                        Text(
                            "Secret deve ter pelo menos 32 caracteres",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusColors.critical
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.SpaceXs))

                    // Botão conectar
                    Button(
                        onClick = { onLoginSuccess(serverName, serverUrl, secretKey) },
                        enabled = canConnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeightLg - Dimens.SpaceXs),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            disabledContainerColor = colors.surface
                        ),
                        shape = AppShapes.xl
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = colors.background, modifier = Modifier.size(Dimens.IconMd - Dimens.SpaceXxs))
                        Spacer(modifier = Modifier.width(Dimens.SpaceMd))
                        Text(
                            "CONECTAR & SALVAR",
                            color = if (canConnect) colors.background else colors.outlineVariant,
                            style = MaterialTheme.typography.titleSmall,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Space3xl))

            Text(
                "O agente precisa estar rodando no servidor\ncom SSH túnel ativo para se conectar.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.outlineVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NocTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    accentColor: Color,
    isError: Boolean,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant) },
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, tint = accentColor.copy(alpha = 0.8f), modifier = Modifier.size(Dimens.IconMd - Dimens.SpaceXxs))
        },
        trailingIcon = trailingIcon,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = accentColor.copy(alpha = 0.3f),
            errorBorderColor = StatusColors.critical,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            cursorColor = accentColor,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}
