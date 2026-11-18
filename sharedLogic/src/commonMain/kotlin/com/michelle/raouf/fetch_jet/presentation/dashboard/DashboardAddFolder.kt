package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.core.settings.FileCategory
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun DashboardUiState.addTypeFolder(): String? =
    if (sortIntoFolders) FileCategory.folderFor(
        fileNameOverride.ifEmpty { suggestedFileName },
        isMedia = detectedEngine == EngineType.MEDIA_STREAM,
        isTorrent = detectedEngine == EngineType.BITTORRENT
    ) else null

internal fun DashboardUiState.addFolderCategory(): String =
    if (!isAddDialogVisible || inputUrl.isBlank() || linkList.isNotEmpty()) ""
    else FileCategory.categoryFor(
        fileNameOverride.ifEmpty { suggestedFileName },
        isMedia = detectedEngine == EngineType.MEDIA_STREAM,
        isTorrent = detectedEngine == EngineType.BITTORRENT
    )

internal fun DashboardViewModel.baseFolder(path: String): String {
    val resolver = queueRepository.pathResolver
    return FileCategory.stripTypeFolders(
        path,
        listOf(resolver.getDownloadsDirectory(), resolver.getDocumentsDirectory(), resolver.getDesktopDirectory())
    )
}

internal fun DashboardUiState.addSiteRule(): com.michelle.raouf.fetch_jet.core.settings.SiteFolderRule? {
    if (!isAddDialogVisible) return null
    if (linkList.isNotEmpty()) {
        val rules = linkList.map { BehaviorSettings.siteFolderFor(it.url) ?: return null }
        return rules.distinct().singleOrNull()
    }
    return if (inputUrl.isBlank()) null else BehaviorSettings.siteFolderFor(inputUrl)
}

internal fun DashboardUiState.ruleSiteFor(path: String): String =
    addSiteRule()?.takeIf { com.michelle.raouf.fetch_jet.core.settings.SiteFolderRules.samePath(it.folder, path) }?.site.orEmpty()

internal fun DashboardViewModel.suggestedAddFolder(state: DashboardUiState): String {
    state.addSiteRule()?.let { return it.folder }
    BehaviorSettings.categoryFolder(state.addFolderCategory())?.let { return it }
    val base = baseFolder(defaultDestinationPath)
    val type = state.addTypeFolder() ?: return base
    val sep = if (base.contains('\\')) "\\" else "/"
    return base + sep + type
}

internal fun DashboardViewModel.observeAddFolder() {
    scope.launch {
        _uiState.map { listOf(it.isAddDialogVisible, it.addFolderCategory(), it.addTypeFolder(), it.addSiteRule()) }
            .distinctUntilChanged()
            .collect {
                if (!_uiState.value.isAddDialogVisible) addFolderPickedFor = ""
                _uiState.update { state ->
                    val category = state.addFolderCategory()
                    val picked = addFolderPickedFor
                    val target = when {
                        !state.isAddDialogVisible -> defaultDestinationPath
                        picked.isNotEmpty() && (picked == category || BehaviorSettings.categoryFolder(category) == null) ->
                            state.destinationPath

                        else -> suggestedAddFolder(state)
                    }
                    val rule = state.addSiteRule()?.takeIf {
                        com.michelle.raouf.fetch_jet.core.settings.SiteFolderRules.samePath(it.folder, target)
                    }?.site.orEmpty()
                    if (target == state.destinationPath && rule == state.addFolderRuleSite) state
                    else state.copy(destinationPath = target, addFolderRuleSite = rule)
                }
            }
    }
}

internal fun DashboardViewModel.setDestinationFolder(path: String) {
    val state = _uiState.value
    val category = state.addFolderCategory()
    if (category.isEmpty()) {
        defaultDestinationPath = path
        _uiState.update { it.copy(destinationPath = path, addFolderRuleSite = it.ruleSiteFor(path)) }
        persistCurrentSettings()
        return
    }
    val base = baseFolder(path)
    val typeFolderOnly = base == baseFolder(state.destinationPath) &&
            (state.sortIntoFolders || path.trimEnd('/', '\\') == base)
    if (typeFolderOnly && base != baseFolder(defaultDestinationPath)) return
    if (!typeFolderOnly) addFolderPickedFor = category
    _uiState.update { it.copy(destinationPath = path, addFolderRuleSite = it.ruleSiteFor(path)) }
}

internal fun DashboardViewModel.rememberAddFolder() {
    val state = _uiState.value
    val category = state.addFolderCategory()
    val path = state.destinationPath
    if (category.isEmpty() || path.isBlank()) return
    if (state.addSiteRule()?.let { com.michelle.raouf.fetch_jet.core.settings.SiteFolderRules.samePath(it.folder, path) } == true) return
    val custom = baseFolder(path) != baseFolder(defaultDestinationPath)
    BehaviorSettings.setCategoryFolder(category, if (custom) path else "")
}

internal fun DashboardViewModel.destinationAllowed(): Boolean {
    val state = _uiState.value
    if (state.detectedEngine == EngineType.BITTORRENT || state.inputUrl.isBlank()) return true
    val folder = state.destinationPath.trim()
    if (folder.isBlank() || com.michelle.raouf.fetch_jet.core.filesystem.AllFilesAccess.canWriteInto(folder)) return true
    val strings = com.tbib.kmp_localization.LocalizationManager.Strings
    val needsPermission = com.michelle.raouf.fetch_jet.core.filesystem.AllFilesAccess.needed()
    if (needsPermission) com.michelle.raouf.fetch_jet.core.filesystem.AllFilesAccess.openSettings()
    scope.launch {
        _uiEffect.emit(
            DashboardUiEffect.ShowToast(if (needsPermission) strings.storageNeedsAllFiles else strings.folderNotWritable(folder))
        )
    }
    return false
}
