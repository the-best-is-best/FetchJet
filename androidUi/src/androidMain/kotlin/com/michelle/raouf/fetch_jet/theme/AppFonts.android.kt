package com.michelle.raouf.fetch_jet.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.michelle.raouf.fetch_jet.core.i18n.start.AppFontFamily
import com.michelle.raouf.fetch_jet.sharedUI.R

private fun family(regular: Int, medium: Int, semiBold: Int, bold: Int): FontFamily = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semiBold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

@Composable
actual fun appFontFamily(family: AppFontFamily): FontFamily = remember(family) {
    when (family) {
        AppFontFamily.BASE -> family(R.font.fetchjetsans_regular, R.font.fetchjetsans_medium, R.font.fetchjetsans_semibold, R.font.fetchjetsans_bold)
        AppFontFamily.SC -> family(R.font.fetchjetsanssc_regular, R.font.fetchjetsanssc_medium, R.font.fetchjetsanssc_semibold, R.font.fetchjetsanssc_bold)
        AppFontFamily.JP -> family(R.font.fetchjetsansjp_regular, R.font.fetchjetsansjp_medium, R.font.fetchjetsansjp_semibold, R.font.fetchjetsansjp_bold)
        AppFontFamily.KR -> family(R.font.fetchjetsanskr_regular, R.font.fetchjetsanskr_medium, R.font.fetchjetsanskr_semibold, R.font.fetchjetsanskr_bold)
    }
}

@Composable
actual fun appLogoPainter(): Painter = painterResource(R.drawable.ic_fetchjet_logo)
