package com.pocketnoc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Shapes reutilizaveis do PocketNOC.
 * Evita RoundedCornerShape(N.dp) espalhados por todo o codigo.
 */
object AppShapes {
    val small  = RoundedCornerShape(Dimens.RadiusSm)
    val medium = RoundedCornerShape(Dimens.RadiusMd)
    val large  = RoundedCornerShape(Dimens.RadiusLg)
    val xl     = RoundedCornerShape(Dimens.RadiusXl)
    val card   = RoundedCornerShape(Dimens.RadiusCard)
    val panel  = RoundedCornerShape(Dimens.RadiusPanel)
    val sheet  = RoundedCornerShape(Dimens.RadiusSheet)
    val pill   = RoundedCornerShape(50)
}
