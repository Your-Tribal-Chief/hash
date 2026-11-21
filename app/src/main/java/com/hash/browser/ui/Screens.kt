@file:OptIn(ExperimentalMaterial3Api::class)

package com.hash.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hash.browser.Browser
import com.hash.browser.BuildConfig
import com.hash.browser.ReaderContent
import com.hash.browser.core.SEARCH_ENGINES
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FullScreenPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        content()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
    )
}

// ---------------------------------------------------------------------------------- tabs

@Composable
fun TabsScreen(b: Browser, onClose: () -> Unit) {
    var showPrivate by remember { mutableStateOf(b.current?.isPrivate == true) }
    val list = b.tabs.filter { it.isPrivate == showPrivate }
    FullScreenPage(
        title = "Tabs",
        onBack = onClose,
        actions = {
            TextButton(onClick = { b.closeAll(showPrivate) }) { Text("Close all") }
            IconButton(onClick = { b.newTab(null, showPrivate); onClose() }) { Icon(Icons.Filled.Add, "New tab") }
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showPrivate, onClick = { showPrivate = false },
                label = { Text("Tabs (${b.tabs.count { !it.isPrivate }})") })
            FilterChip(selected = showPrivate, onClick = { showPrivate = true },
                label = { Text("Private (${b.tabs.count { it.isPrivate }})") },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, null, Modifier.size(16.dp)) })
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ${if (showPrivate) "private " else ""}tabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@FullScreenPage
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { tab ->
                val selected = tab === b.current
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) HashColors.Cyan else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp),
                        )
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { b.selectTab(tab); onClose() }
                ) {
                    Row(Modifier.padding(start = 10.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Favicon(tab, 16)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tab.title.ifBlank { if (tab.isHome) "New tab" else tab.host },
                            Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { b.closeTab(tab) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Close tab", Modifier.size(16.dp))
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.8f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val thumb = tab.thumbnail
                        if (thumb != null && !tab.isHome) {
                            Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                        } else {
                            HashMark(48.dp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- bookmarks & history

@Composable
fun BookmarksScreen(b: Browser, onClose: () -> Unit) {
    FullScreenPage("Bookmarks", onClose) {
        if (b.bookmarks.isEmpty()) {
            EmptyState("No bookmarks yet", "Use Menu › Bookmark on any page to save it here.")
            return@FullScreenPage
        }
        LazyColumn {
            items(b.bookmarks, key = { it.url }) { bm ->
                LinkRow(bm.title, bm.url, onClick = { b.loadInput(bm.url); onClose() }) {
                    IconButton(onClick = { b.removeBookmark(bm.url) }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(b: Browser, onClose: () -> Unit) {
    var entries by remember { mutableStateOf(b.store.history) }
    var query by remember { mutableStateOf("") }
    val day = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()) }
    val time = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    FullScreenPage("History", onClose, actions = {
        TextButton(onClick = { b.store.history = emptyList(); entries = emptyList() }) { Text("Clear all") }
    }) {
        OutlinedTextField(
            query, { query = it }, placeholder = { Text("Search history") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val q = query.lowercase()
        val filtered = entries.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        if (filtered.isEmpty()) {
            EmptyState("Nothing here", "Pages you visit in normal tabs show up here.")
            return@FullScreenPage
        }
        val grouped = filtered.groupBy { day.format(Date(it.time)) }
        LazyColumn {
            grouped.forEach { (label, list) ->
                item(key = "h_$label") { SectionTitle(label) }
                items(list, key = { it.url + it.time }) { h ->
                    LinkRow(h.title, h.url, onClick = { b.loadInput(h.url); onClose() }) {
                        Text(time.format(Date(h.time)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = {
                            val next = b.store.history.filter { it.url != h.url }
                            b.store.history = next
                            entries = next
                        }) { Icon(Icons.Filled.Close, "Remove", Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun LinkRow(title: String, url: String, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(title.ifBlank { url.removePrefix("https://").removePrefix("www.") }, 34)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { url }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HashMark(56.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------------------------- settings

@Composable
fun SettingsScreen(b: Browser, onClose: () -> Unit, open: (Overlay) -> Unit) {
    val s = b.store
    var engine by remember { mutableStateOf(s.searchEngine) }
    var js by remember { mutableStateOf(s.javascriptEnabled) }
    var https by remember { mutableStateOf(s.httpsUpgrade) }
    var desktop by remember { mutableStateOf(s.desktopDefault) }
    var remote by remember { mutableStateOf(s.remoteDebugging) }
    var clearExit by remember { mutableStateOf(s.clearOnExit) }
    var showClear by remember { mutableStateOf(false) }
    var showCustomEngine by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    FullScreenPage("Settings", onClose) {
        LazyColumn {
            item {
                SectionTitle("Search engine")
                SEARCH_ENGINES.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { engine = e.template; s.searchEngine = e.template }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = engine == e.template, onClick = { engine = e.template; s.searchEngine = e.template })
                        Text(e.name)
                    }
                }
                val custom = SEARCH_ENGINES.none { it.template == engine }
                Row(
                    Modifier.fillMaxWidth().clickable { showCustomEngine = true }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = custom, onClick = { showCustomEngine = true })
                    Text(if (custom) "Custom: $engine" else "Custom…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                SectionTitle("Privacy & security")
                SettingLink("Hash Shields", "Ad and tracker blocking") { onClose(); open(Overlay.Shields) }
                SettingLink("Hash Tunnel", "Route browsing through your proxy server") { open(Overlay.Tunnel) }
                ToggleRow("Upgrade to HTTPS", "Try the secure version of every site first", https) { https = it; s.httpsUpgrade = it }
                ToggleRow("Clear data on exit", "Wipe history, cookies and cache when the app closes", clearExit) { clearExit = it; s.clearOnExit = it }
                SettingLink("Clear browsing data", "History, cookies, site data, cache") { showClear = true }
                SettingLink("Custom hiding rules", "${b.customRules().size} rule(s) from “Hide it”") { showRules = true }

                SectionTitle("Content")
                ToggleRow("JavaScript", null, js) { js = it; s.javascriptEnabled = it; b.applyJsSetting() }
                ToggleRow("Desktop site by default", null, desktop) { desktop = it; s.desktopDefault = it }
                SettingLink("Hash Mods", "Userscript extensions") { open(Overlay.Mods) }

                SectionTitle("Developer")
                ToggleRow(
                    "Remote debugging",
                    "Inspect pages from a computer at chrome://inspect over USB",
                    remote,
                ) { remote = it; b.setRemoteDebugging(it) }

                SectionTitle("General")
                SettingLink("Set as default browser", "Open links from other apps in Hash") { b.openDefaultBrowserSettings() }
                SettingLink("Downloads", "Files are saved to Downloads/HashBrowser") { b.openSystemDownloads() }

                SectionTitle("About")
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    HashMark(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Hash Browser ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Uses Android System WebView, EasyList & EasyPrivacy filters (GPLv3 / CC BY-SA), and Eruda (MIT).",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showClear) ClearDataDialog(b) { showClear = false }
    if (showCustomEngine) {
        var text by remember { mutableStateOf("https://example.com/search?q=%s") }
        AlertDialog(
            onDismissRequest = { showCustomEngine = false },
            title = { Text("Custom search engine") },
            text = {
                Column {
                    Text("Use %s where the search words go.", fontSize = 13.sp)
                    OutlinedTextField(text, { text = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = text.contains("%s"), onClick = {
                    engine = text; s.searchEngine = text; showCustomEngine = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCustomEngine = false }) { Text("Cancel") } },
        )
    }
    if (showRules) {
        var rules by remember { mutableStateOf(b.customRules()) }
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("Custom hiding rules") },
            text = {
                if (rules.isEmpty()) Text("Use Menu › Tap inspect › Hide it to hide an element on a site.")
                else LazyColumn {
                    items(rules) { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { b.removeCustomRule(r); rules = b.customRules() }) {
                                Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("Done") } },
        )
    }
}

@Composable
fun SettingLink(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, fontSize = 15.sp)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClearDataDialog(b: Browser, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column {
                CheckRow("Browsing history", history) { history = it }
                CheckRow("Cookies and site data", cookies) { cookies = it }
                CheckRow("Cached images and files", cache) { cache = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { b.clearBrowsingData(history, cookies, cache); onDismiss() }) { Text("Clear", color = HashColors.Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

// ---------------------------------------------------------------------------------- source & reader

@Composable
fun SourceScreen(b: Browser, source: String, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val lines = remember(source) { source.lines() }
    val shown = remember(lines, query) {
        if (query.isBlank()) lines.withIndex().toList()
        else lines.withIndex().filter { it.value.contains(query, ignoreCase = true) }
    }
    FullScreenPage("Page source", onClose, actions = {
        IconButton(onClick = { b.copy(source, "Source copied") }) { Icon(Icons.Filled.ContentCopy, "Copy") }
    }) {
        OutlinedTextField(
            query, { query = it }, placeholder = { Text("Filter lines") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            "${shown.size} of ${lines.size} lines · ${"%,d".format(source.length)} chars",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        HorizontalDivider()
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown.size) { i ->
                    val (n, line) = shown[i]
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            "${n + 1}".padStart(5), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(highlightHtml(line), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderScreen(content: ReaderContent, onClose: () -> Unit) {
    var size by remember { mutableStateOf(18) }
    FullScreenPage("Reader", onClose, actions = {
        TextButton(onClick = { size = (size - 2).coerceAtLeast(12) }) { Text("A−") }
        TextButton(onClick = { size = (size + 2).coerceAtMost(30) }) { Text("A+") }
    }) {
        SelectionContainer {
            LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                item {
                    Text(content.title, fontSize = (size + 8).sp, fontWeight = FontWeight.Bold, lineHeight = (size + 14).sp)
                    Spacer(Modifier.height(16.dp))
                }
                items(content.paragraphs) { p ->
                    if (p.startsWith("#")) {
                        Text(p.removePrefix("#"), fontSize = (size + 3).sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    } else {
                        Text(p, fontSize = size.sp, lineHeight = (size * 1.6).sp, fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            }
        }
    }
}
