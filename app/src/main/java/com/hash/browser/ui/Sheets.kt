@file:OptIn(ExperimentalMaterial3Api::class)

package com.hash.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hash.browser.AuthRequest
import com.hash.browser.Browser
import com.hash.browser.PendingInstall
import com.hash.browser.PickInfo
import com.hash.browser.core.TunnelStatus

@Composable
fun HashSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

private data class MenuItem(val icon: ImageVector, val label: String, val active: Boolean = false, val onClick: () -> Unit)

@Composable
fun MenuSheet(b: Browser, onDismiss: () -> Unit, open: (Overlay) -> Unit) {
    val tab = b.current
    fun act(f: () -> Unit): () -> Unit = { onDismiss(); f() }
    val bookmarked = tab != null && b.isBookmarked(tab.url)
    val items = listOf(
        MenuItem(Icons.Filled.Add, "New tab", onClick = act { b.newTab(null, false) }),
        MenuItem(Icons.Filled.VisibilityOff, "Private tab", onClick = act { b.newTab(null, true) }),
        MenuItem(Icons.Filled.Home, "Home", onClick = act { b.goHome() }),
        MenuItem(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            if (bookmarked) "Bookmarked" else "Bookmark", bookmarked, act { b.toggleBookmark() }),
        MenuItem(Icons.Filled.Bookmarks, "Bookmarks", onClick = act { open(Overlay.Bookmarks) }),
        MenuItem(Icons.Filled.History, "History", onClick = act { open(Overlay.History) }),
        MenuItem(Icons.Filled.Download, "Downloads", onClick = act { b.openSystemDownloads() }),
        MenuItem(Icons.Filled.AddToHomeScreen, "Add to home", onClick = act { b.addToHome() }),
        MenuItem(Icons.Filled.Shield, "Shields", onClick = act { open(Overlay.Shields) }),
        MenuItem(Icons.Filled.VpnKey, "Tunnel", b.tunnelStatus is TunnelStatus.On, act { open(Overlay.Tunnel) }),
        MenuItem(Icons.Filled.Extension, "Mods", onClick = act { open(Overlay.Mods) }),
        MenuItem(Icons.Filled.BugReport, "Inspect", onClick = act { b.openDevtools() }),
        MenuItem(Icons.Filled.TouchApp, "Tap inspect", onClick = act { b.startPicker() }),
        MenuItem(Icons.Filled.Code, "View source", onClick = act { b.viewSource() }),
        MenuItem(Icons.Filled.FindInPage, "Find", onClick = act { b.find("") }),
        MenuItem(Icons.Filled.DesktopWindows, "Desktop site", tab?.desktop == true, act { b.toggleDesktop() }),
        MenuItem(Icons.Filled.ChromeReaderMode, "Reader", onClick = act { b.openReader() }),
        MenuItem(Icons.Filled.Share, "Share", onClick = act { b.shareCurrent() }),
        MenuItem(Icons.Filled.Settings, "Settings", onClick = act { open(Overlay.Settings) }),
    )
    HashSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HashMark(26.dp)
            Spacer(Modifier.width(8.dp))
            Text("Hash Browser", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                row.forEach { item -> MenuCell(item, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MenuCell(item: MenuItem, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (item.active) HashColors.Cyan.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, item.label, tint = if (item.active) HashColors.Cyan else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(item.label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun hashSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = HashColors.Navy,
    checkedTrackColor = HashColors.Cyan,
)

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = hashSwitchColors())
    }
}

@Composable
fun ShieldsSheet(b: Browser, onDismiss: () -> Unit) {
    val tab = b.current
    val host = tab?.host ?: ""
    var globalOn by remember { mutableStateOf(b.store.shieldsEnabled) }
    var siteOn by remember { mutableStateOf(host.isEmpty() || !b.isAllowlisted(host)) }
    var aggressive by remember { mutableStateOf(b.store.aggressiveShields) }
    var popups by remember { mutableStateOf(b.store.blockPopups) }
    var cookies by remember { mutableStateOf(b.store.blockThirdPartyCookies) }
    var showList by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }

    HashSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = if (globalOn && siteOn) HashColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hash Shields", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (host.isEmpty()) "New tab" else host, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${tab?.blocked?.size ?: 0}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = HashColors.Cyan)
                Text("blocked here", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (host.isNotEmpty()) {
            ToggleRow("Shields for this site", if (siteOn) "Ads and trackers are blocked" else "Turned off for $host", siteOn) {
                siteOn = it
                b.setSiteShields(host, it)
            }
        }
        ToggleRow("Shields everywhere", "${"%,d".format(b.shieldsRuleCount)} filter rules loaded", globalOn) {
            globalOn = it
            b.setShieldsEnabled(it)
        }
        ToggleRow("Aggressive blocking", "Applies every generic hiding rule. Can break some layouts.", aggressive) {
            aggressive = it
            b.store.aggressiveShields = it
            b.reload()
        }
        ToggleRow("Block pop-ups", null, popups) {
            popups = it
            b.store.blockPopups = it
            b.applyPopupSetting()
        }
        ToggleRow("Block third-party cookies", null, cookies) {
            cookies = it
            b.store.blockThirdPartyCookies = it
            b.applyCookieSetting()
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { showList = !showList }) {
                Text(if (showList) "Hide blocked list" else "Show blocked requests")
            }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !updating, onClick = {
                updating = true
                b.toast("Updating filter lists…")
                b.updateFilterLists { ok ->
                    updating = false
                    b.toast(if (ok > 0) "Updated $ok list(s)" else "Couldn't download lists")
                }
            }) { Text(if (updating) "Updating…" else "Update lists") }
        }
        if (showList && tab != null) {
            LazyColumn(Modifier.heightIn(max = 260.dp).padding(horizontal = 20.dp)) {
                items(tab.blocked.toList()) { u ->
                    Text(u, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
fun SiteInfoSheet(b: Browser, onDismiss: () -> Unit, open: (Overlay) -> Unit) {
    val tab = b.current ?: return
    HashSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (tab.secure) Icons.Filled.Lock else Icons.Filled.Public, null,
                    tint = if (tab.secure) HashColors.Green else HashColors.Red,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (tab.isHome) "New tab" else if (tab.secure) "Connection is secure" else "Connection is not secure",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!tab.isHome) {
                Text(tab.url, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (tab.secure) "Information you send to this site is encrypted."
                    else "Avoid entering passwords or card details on this site.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text("Trackers blocked on this page: ${tab.blocked.size}", fontSize = 13.sp)
                if (tab.isPrivate) Text("Private tab — not saved to history.", fontSize = 13.sp, color = HashColors.Violet)
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!tab.isHome) {
                TextButton(onClick = { b.copy(tab.url, "Link copied"); onDismiss() }) { Text("Copy link") }
            }
            TextButton(onClick = { onDismiss(); open(Overlay.Shields) }) { Text("Shields") }
        }
    }
}

@Composable
fun PickSheet(b: Browser, info: PickInfo, onDismiss: () -> Unit) {
    val j = info.json
    HashSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Element", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)
            ) {
                Text(info.selector, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = HashColors.Cyan)
            }
            Spacer(Modifier.height(8.dp))
            val rows = listOf(
                "tag" to j.optString("tag"),
                "id" to j.optString("id"),
                "class" to j.optString("classes"),
                "size" to "${j.optInt("width")} × ${j.optInt("height")}",
                "display" to j.optString("display"),
                "position" to j.optString("position"),
                "color" to j.optString("color"),
                "background" to j.optString("background"),
                "font" to j.optString("font"),
                "margin" to j.optString("margin"),
                "padding" to j.optString("padding"),
            ).filter { it.second.isNotBlank() }
            rows.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(k, Modifier.width(92.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text(v, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val text = j.optString("text")
            if (text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("“$text”", fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { b.inspectPicked(info.selector); onDismiss() }) { Text("Open in Elements") }
            TextButton(onClick = { b.copy(info.selector, "Selector copied") }) { Text("Copy selector") }
            TextButton(onClick = { b.hidePicked(info.selector); onDismiss() }) { Text("Hide it", color = HashColors.Red) }
        }
    }
}

@Composable
fun AuthDialog(b: Browser, req: AuthRequest) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { req.handler.cancel(); b.pendingAuth = null },
        title = { Text("Sign in to ${req.host}") },
        text = {
            Column {
                if (req.realm.isNotBlank()) Text(req.realm, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { req.handler.proceed(user, pass); b.pendingAuth = null }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = { req.handler.cancel(); b.pendingAuth = null }) { Text("Cancel") } },
    )
}

@Composable
fun InstallDialog(b: Browser, p: PendingInstall) {
    val s = p.script
    AlertDialog(
        onDismissRequest = { b.pendingInstall = null },
        title = { Text("Install mod?") },
        text = {
            Column {
                Text(s.name, fontWeight = FontWeight.Bold)
                if (s.version.isNotBlank()) Text("Version ${s.version}", fontSize = 12.sp)
                if (s.description.isNotBlank()) Text(s.description, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Text("Runs on:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (s.matches + s.includes).take(6).forEach {
                    Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                Text("Only install scripts you trust — a mod can read and change the pages it runs on.",
                    fontSize = 12.sp, color = Color(0xFFFBBF24))
            }
        },
        confirmButton = {
            TextButton(onClick = { b.saveScript(s); b.pendingInstall = null; b.toast("Installed ${s.name}") }) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = { b.pendingInstall = null }) { Text("Cancel") } },
    )
}
