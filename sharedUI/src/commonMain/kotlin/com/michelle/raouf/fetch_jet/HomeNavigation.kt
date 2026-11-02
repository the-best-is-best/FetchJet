package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskSortOption
import com.michelle.raouf.fetch_jet.ui.SideFilter
import com.michelle.raouf.fetch_jet.utils.formatSpeed
import fetchjet.sharedui.generated.resources.Res
import fetchjet.sharedui.generated.resources.ic_fetchjet_logo
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun FdmBottomNavigation(
    selectedTab: DashboardTab,
    allCount: Int,
    activeCount: Int,
    completedCount: Int,
    torrentCount: Int,
    mediaCount: Int,
    onTabSelected: (DashboardTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                val items = listOfNotNull(
                    Triple(
                        DashboardTab.ALL,
                        LocalizationManager.Strings.homeTabAll,
                        "📥" to allCount
                    ),
                    Triple(
                        DashboardTab.ACTIVE,
                        LocalizationManager.Strings.homeTabActive,
                        "⚡" to activeCount
                    ),
                    Triple(
                        DashboardTab.COMPLETED,
                        LocalizationManager.Strings.homeTabDone,
                        "✅" to completedCount
                    ),
                    if (AppFeatures.torrent) Triple(
                        DashboardTab.TORRENT,
                        LocalizationManager.Strings.homeTabTorrent,
                        "🧲" to torrentCount
                    ) else null,
                    if (AppFeatures.mediaExtractor) Triple(
                        DashboardTab.MEDIA,
                        LocalizationManager.Strings.homeTabMedia,
                        "🎬" to mediaCount
                    ) else null,
                    Triple(
                        DashboardTab.SETTINGS,
                        LocalizationManager.Strings.homeSettings,
                        "⚙️" to 0
                    )
                )

                items.forEach { (tab, label, iconBadge) ->
                    val isSelected = selectedTab == tab
                    val (icon, badgeCount) = iconBadge

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        alwaysShowLabel = true,
                        icon = {
                            if (badgeCount > 0) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            "$badgeCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }) {
                                    Text(icon, fontSize = 18.sp)
                                }
                            } else {
                                Text(icon, fontSize = 18.sp)
                            }
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

/** Wide-layout sidebar: brand, state filters with counts, type filters, Settings at the bottom. */
@Composable
internal fun FjSidebar(
    tasks: List<DownloadTask>,
    current: SideFilter,
    settingsSelected: Boolean,
    onSelect: (SideFilter) -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.width(232.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, bottom = 18.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_fetchjet_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "FetchJet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            listOf(
                SideFilter.ALL,
                SideFilter.DOWNLOADING,
                SideFilter.WAITING,
                SideFilter.PAUSED,
                SideFilter.COMPLETED,
                SideFilter.FAILED
            )
                .forEach { f ->
                    FjSidebarItem(
                        f.label,
                        tasks.count { f.matches(it) },
                        !settingsSelected && current == f
                    ) { onSelect(f) }
                }
            Box(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp).fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            Text(
                LocalizationManager.Strings.homeSidebarTypeHeader,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
            )
            listOfNotNull(
                if (AppFeatures.mediaExtractor) SideFilter.MEDIA else null,
                SideFilter.FILES,
                if (AppFeatures.torrent) SideFilter.TORRENTS else null
            ).forEach { f ->
                FjSidebarItem(
                    f.label,
                    tasks.count { f.matches(it) },
                    !settingsSelected && current == f
                ) { onSelect(f) }
            }
            Spacer(modifier = Modifier.weight(1f))
            FjSidebarItem(
                LocalizationManager.Strings.homeSettings,
                null,
                settingsSelected,
                onSettings
            )
        }
    }
}

@Composable
internal fun FjSidebarItem(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            ) {
                Text(
                    count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/** Wide-layout top bar: search, live speeds, theme, Add download. */
@Composable
internal fun FjTopBar(
    showSearch: Boolean,
    compact: Boolean = false,
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    totalDlSpeed: Long,
    totalUlSpeed: Long,
    speedDisplayUnit: String,
    isDarkMode: Boolean,
    onThemeToggled: () -> Unit,
    onAdd: () -> Unit
) {
    val searchField: @Composable (Modifier) -> Unit = { m ->
        com.michelle.raouf.fetch_jet.ui.FetchJetTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = LocalizationManager.Strings.homeSearchDownloadsPlaceholder,
            leading = {
                Text(
                    "⌕",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailing = if (searchQuery.isNotEmpty()) {
                {
                    com.michelle.raouf.fetch_jet.ui.FieldIconButton(
                        "✕",
                        onClick = { onSearchChange("") })
                }
            } else null,
            height = 44.dp,
            modifier = m
        )
    }
    // Tablet: the title takes the bar and the search box sits under it, as on the phone.
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "↓ " + formatSpeed(totalDlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    "↑ " + formatSpeed(totalUlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                IconButton(onClick = onThemeToggled) {
                    Text(
                        if (isDarkMode) "☀" else "☾",
                        fontSize = 18.sp
                    )
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        "+  ${LocalizationManager.Strings.homeAdd}",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            if (showSearch) {
                Spacer(modifier = Modifier.height(10.dp))
                searchField(Modifier.fillMaxWidth())
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showSearch) {
            searchField(Modifier.weight(1f))
        } else {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "↓ " + formatSpeed(totalDlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "↑ " + formatSpeed(totalUlSpeed, speedDisplayUnit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onThemeToggled) {
            Text(if (isDarkMode) "☀" else "☾", fontSize = 18.sp)
        }
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Text("+  ${LocalizationManager.Strings.homeAddDownload}", fontWeight = FontWeight.Bold)
        }
    }
}

/** Phone header: title, total speed and the ⋮ menu with the list-wide actions. */
@Composable
internal fun FjPhoneHeader(
    tasks: List<DownloadTask>,
    showListActions: Boolean,
    totalDlSpeed: Long,
    totalUlSpeed: Long,
    speedDisplayUnit: String,
    sortOption: TaskSortOption,
    isDarkMode: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSort: (TaskSortOption) -> Unit,
    onThemeToggled: () -> Unit,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "↓ " + formatSpeed(totalDlSpeed, speedDisplayUnit),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "↑ " + formatSpeed(totalUlSpeed, speedDisplayUnit),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        var open by remember { mutableStateOf(false) }
        var sortOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) {
                Text(
                    "⋮",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false; sortOpen = false }) {
                if (showListActions) {
                    val unfinished = tasks.filter { it.status != DownloadStatus.COMPLETED }
                    DropdownMenuItem(
                        text = { Text(LocalizationManager.Strings.homePauseAll) },
                        enabled = anyPausable(unfinished),
                        onClick = { open = false; onPauseAll() }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalizationManager.Strings.homeResumeAll) },
                        enabled = anyResumable(unfinished),
                        onClick = { open = false; onResumeAll() }
                    )
                    DropdownMenuItem(
                        text = { Text("${LocalizationManager.Strings.homeSortBy(sortOption.label)}  " + if (sortOpen) "▴" else "▾") },
                        onClick = { sortOpen = !sortOpen }
                    )
                    if (sortOpen) {
                        TaskSortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (opt == sortOption) "✓  " else "     ") + opt.label,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = { open = false; sortOpen = false; onSort(opt) }
                            )
                        }
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(if (isDarkMode) LocalizationManager.Strings.homeLightTheme else LocalizationManager.Strings.homeDarkTheme) },
                    onClick = { open = false; onThemeToggled() }
                )
            }
        }
    }
}

/** Tablet navigation: an 80dp rail of state filters with counts, Settings at the bottom. */
@Composable
internal fun FjRail(
    tasks: List<DownloadTask>,
    current: SideFilter,
    settingsSelected: Boolean,
    onSelect: (SideFilter) -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.width(84.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_fetchjet_logo),
                contentDescription = null,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            listOf(
                SideFilter.ALL to LocalizationManager.Strings.homeTabAll,
                SideFilter.DOWNLOADING to LocalizationManager.Strings.homeTabActive,
                SideFilter.WAITING to LocalizationManager.Strings.homeFilterWaiting,
                SideFilter.COMPLETED to LocalizationManager.Strings.homeTabDone,
                SideFilter.FAILED to LocalizationManager.Strings.homeFilterFailed
            ).forEach { (f, label) ->
                FjRailItem(
                    label,
                    tasks.count { f.matches(it) },
                    !settingsSelected && current == f
                ) { onSelect(f) }
            }
            Spacer(modifier = Modifier.weight(1f))
            FjRailItem(
                LocalizationManager.Strings.homeSettings,
                null,
                settingsSelected,
                glyph = "⚙",
                onClick = onSettings
            )
        }
    }
}

@Composable
internal fun FjRailItem(
    label: String,
    count: Int?,
    selected: Boolean,
    glyph: String? = null,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = if (selected && count != null) accent else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.07f
            )
        ) {
            Text(
                text = count?.toString() ?: (glyph ?: ""),
                fontSize = if (glyph != null) 15.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected && count != null) MaterialTheme.colorScheme.onPrimary
                else if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 26.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
