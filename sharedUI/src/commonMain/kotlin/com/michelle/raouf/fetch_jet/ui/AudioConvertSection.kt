package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_extractor.AUDIO_CONVERT_FORMATS
import com.tbib.kmp_localization.LocalizationManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioConvertSection(
    selected: String?,
    nativeFormat: String?,
    forAll: Boolean,
    onSelected: (String?) -> Unit
) {
    val native = nativeFormat?.lowercase()?.ifBlank { null }
    Text(
        text = if (forAll) LocalizationManager.Strings.audioConvertAllTo else LocalizationManager.Strings.audioConvertTo,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AudioFormatChip(
            native?.uppercase() ?: LocalizationManager.Strings.audioOriginal,
            selected == null || selected == native
        ) { onSelected(null) }
        AUDIO_CONVERT_FORMATS.filter { it != native }.forEach { f ->
            AudioFormatChip(f.uppercase(), selected == f) { onSelected(f) }
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun AudioFormatChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
