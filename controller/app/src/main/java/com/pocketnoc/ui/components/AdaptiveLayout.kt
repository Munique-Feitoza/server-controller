package com.pocketnoc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Determina se o dispositivo tem tela larga (tablet/foldable).
 * Usa a largura em dp do dispositivo atual.
 */
@Composable
fun isWideScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp >= 600
}

/**
 * Layout adaptativo que exibe dois painéis lado a lado em tablets
 * ou empilha em portrait/phone.
 */
@Composable
fun TwoPaneLayout(
    modifier: Modifier = Modifier,
    listPane: @Composable () -> Unit,
    detailPane: (@Composable () -> Unit)? = null
) {
    val wide = isWideScreen()

    if (wide && detailPane != null) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
            ) {
                listPane()
            }
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(start = 8.dp)
            ) {
                detailPane()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            listPane()
        }
    }
}

/**
 * Grid responsivo: 2 colunas em phone, 3 em tablet, 4 em landscape tablet.
 */
@Composable
fun responsiveColumns(): Int {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp >= 900 -> 4
        configuration.screenWidthDp >= 600 -> 3
        else -> 2
    }
}

/**
 * Padding responsivo baseado no tamanho da tela.
 */
@Composable
fun responsiveHorizontalPadding(): PaddingValues {
    val configuration = LocalConfiguration.current
    val padding = when {
        configuration.screenWidthDp >= 900 -> 32.dp
        configuration.screenWidthDp >= 600 -> 24.dp
        else -> 16.dp
    }
    return PaddingValues(horizontal = padding)
}
