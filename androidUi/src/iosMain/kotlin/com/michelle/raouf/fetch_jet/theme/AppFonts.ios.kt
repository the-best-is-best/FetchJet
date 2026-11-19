package com.michelle.raouf.fetch_jet.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import com.michelle.raouf.fetch_jet.core.i18n.start.AppFontFamily

@Composable
actual fun appFontFamily(family: AppFontFamily): FontFamily = FontFamily.Default

@Composable
actual fun appLogoPainter(): Painter = ColorPainter(Color.Transparent)
