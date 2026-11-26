package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.tbib.kmp_localization.LocalizationManager

fun DownloadTask.shownName(): String = if (hidden) LocalizationManager.Strings.hiddenDownloadName else fileName

fun DownloadTask.logName(): String = if (hidden) "hidden download $id" else fileName
