@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.hash.browser.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hash.browser.Browser
import com.hash.browser.Tab
import com.hash.browser.core.TunnelStatus

enum class Overlay { None, Menu, Shields, SiteInfo, Tabs, Bookmarks, History, Tunnel, Mods, Settings }

@Composable
fun HashApp(b: Browser) {
    val fullscreen = b.customView
    if (fullscreen != null) {
        FullscreenVideo(b)
        return
    }

    var overlay by remember { mutableStateOf(Overlay.None) }
    val addressFocus = remember { FocusRequester() }
    val tab = b.current
    val bg = if (tab?.isPrivate == true) HashColors.PrivateBg else MaterialTheme.colorScheme.background

    BackHandler(enabled = tab != null && (tab.canGoBack || !tab.isHome)) { b.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab != null) PageArea(b, tab, onSearchTap = { addressFocus.requestFocus() })
            }
            if (tab != null && !tab.isHome && tab.progress < 100) {
                val p by animateFloatAsState(tab.progress / 100f, label = "progress")
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    Box(Modifier.fillMaxWidth(p).height(2.dp).background(HashColors.accent))
                }
            }
            if (b.findQuery != null) FindBar(b)
            if (tab != null) {
                AddressBar(
                    b, tab, addressFocus,
                    onShields = { overlay = Overlay.Shields },
                    onSiteInfo = { overlay = Overlay.SiteInfo },
                )
            }
            Toolbar(b, tab,
                onTabs = { b.captureThumbnail(); overlay = Overlay.Tabs },
                onMenu = { overlay = Overlay.Menu },
            )
        }

        when (overlay) {
            Overlay.Menu -> MenuSheet(b, onDismiss = { overlay = Overlay.None }, open = { overlay = it })
            Overlay.Shields -> ShieldsSheet(b, onDismiss = { overlay = Overlay.None })
            Overlay.SiteInfo -> SiteInfoSheet(b, onDismiss = { overlay = Overlay.None }, open = { overlay = it })
            else -> {}
        }
        FullOverlay(overlay == Overlay.Tabs) { TabsScreen(b, onClose = { overlay = Overlay.None }) }
        FullOverlay(overlay == Overlay.Bookmarks) { BookmarksScreen(b, onClose = { overlay = Overlay.None }) }
        FullOverlay(overlay == Overlay.History) { HistoryScreen(b, onClose = { overlay = Overlay.None }) }
        FullOverlay(overlay == Overlay.Tunnel) { TunnelScreen(b, onClose = { overlay = Overlay.None }) }
        FullOverlay(overlay == Overlay.Mods) { ModsScreen(b, onClose = { overlay = Overlay.None }) }
        FullOverlay(overlay == Overlay.Settings) { SettingsScreen(b, onClose = { overlay = Overlay.None }, open = { overlay = it }) }
        FullOverlay(b.pageSource != null) { SourceScreen(b, b.pageSource ?: "", onClose = { b.pageSource = null }) }
        FullOverlay(b.reader != null) { b.reader?.let { ReaderScreen(it, onClose = { b.reader = null }) } }

        b.pickInfo?.let { PickSheet(b, it, onDismiss = { b.pickInfo = null }) }
        b.pendingAuth?.let { AuthDialog(b, it) }
        b.pendingInstall?.let { InstallDialog(b, it) }
    }
}

@Composable
private fun FullOverlay(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 12 },
        exit = fadeOut() + slideOutVertically { it / 12 },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

// ---------------------------------------------------------------------------------- page

@Composable
private fun PageArea(b: Browser, tab: Tab, onSearchTap: () -> Unit) {
    if (tab.crashed) {
        CrashedView { b.reloadCrashed(tab) }
        return
    }
    if (!tab.isHome || tab.webView != null) {
        key(tab.id, tab.generation) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> FrameLayout(ctx) },
                update = { frame ->
                    val wv = b.webViewFor(tab)
                    if (wv.parent !== frame) {
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        frame.removeAllViews()
                        frame.addView(
                            wv,
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        )
                    }
                },
            )
        }
    }
    if (tab.isHome) HomePage(b, tab, onSearchTap)
}

@Composable
private fun CrashedView(onReload: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HashMark(72.dp)
        Spacer(Modifier.height(16.dp))
        Text("This tab crashed", style = MaterialTheme.typography.titleLarge)
        Text("The page used too much memory or hit a bug.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onReload) { Text("Reload tab") }
    }
}

@Composable
private fun HomePage(b: Browser, tab: Tab, onSearchTap: () -> Unit) {
    val private = tab.isPrivate
    val top = if (private) HashColors.PrivateBg else MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HashColors.Violet.copy(alpha = 0.18f), top, top)))
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(36.dp))
                HashMark(88.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (private) "Private tab" else "Hash",
                    fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (private) {
                    Text(
                        "Nothing you visit here is saved to history. Cookies and site data are wiped when the last private tab closes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onSearchTap)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Search or type an address", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(28.dp))
            }
            item { SpeedDialGrid(b) }
            if (!private) item {
                Spacer(Modifier.height(28.dp))
                StatsCard(b.totalBlocked)
            }
        }
    }
}

@Composable
private fun SpeedDialGrid(b: Browser) {
    val dials = b.speedDials
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        dials.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { d ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { b.loadInput(d.url) },
                                onLongClick = { b.removeSpeedDial(d.url); b.toast("Removed ${d.title}") },
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LetterTile(d.title, 52)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            d.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun LetterTile(title: String, sizeDp: Int) {
    val palette = listOf(HashColors.Cyan, HashColors.Violet, Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF2DD4BF))
    val c = palette[(title.hashCode() and 0x7fffffff) % palette.size]
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 3.5).dp))
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.45f), RoundedCornerShape((sizeDp / 3.5).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(title.take(1).uppercase(), color = c, fontWeight = FontWeight.Bold, fontSize = (sizeDp / 2.4).sp)
    }
}

@Composable
private fun StatsCard(blocked: Long) {
    val mb = blocked * 40 / 1024.0            // ~40 KB per blocked request
    val seconds = blocked * 50 / 1000         // ~50 ms per blocked request
    val shown by animateFloatAsState(
        blocked.toFloat(), animationSpec = spring(stiffness = 60f), label = "blocked",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text("Privacy stats", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row {
            Stat(Modifier.weight(1f), "%,d".format(shown.toLong()), "Ads & trackers blocked", HashColors.Cyan)
            Stat(Modifier.weight(1f), if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb), "Data saved (est.)", HashColors.Violet)
            Stat(Modifier.weight(1f), formatTime(seconds), "Time saved (est.)", HashColors.Green)
        }
    }
}

private fun formatTime(s: Long): String = when {
    s < 60 -> "${s}s"
    s < 3600 -> "${s / 60}m"
    else -> "${s / 3600}h ${(s % 3600) / 60}m"
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String, color: Color) {
    Column(modifier) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 13.sp)
    }
}

// ---------------------------------------------------------------------------------- bottom bars

@Composable
private fun AddressBar(
    b: Browser, tab: Tab, focus: FocusRequester,
    onShields: () -> Unit, onSiteInfo: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val display = when {
        tab.isHome -> ""
        else -> tab.host.removePrefix("www.").ifEmpty { tab.url }
    }
    LaunchedEffect(tab.id, tab.url, editing) {
        if (!editing) field = TextFieldValue(display)
    }
    val colors = MaterialTheme.colorScheme

    Column {
        if (editing) Suggestions(b, field.text) { url ->
            b.loadInput(url)
            focusManager.clearFocus()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(colors.surfaceVariant)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Icon(Icons.Filled.Search, null, Modifier.padding(horizontal = 10.dp).size(20.dp), tint = colors.primary)
            } else {
                IconButton(onClick = onSiteInfo, modifier = Modifier.size(38.dp)) {
                    when {
                        tab.isPrivate -> Icon(Icons.Filled.VisibilityOff, "Private", tint = HashColors.Violet, modifier = Modifier.size(18.dp))
                        tab.isHome -> Icon(Icons.Filled.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        tab.secure -> Icon(Icons.Filled.Lock, "Secure", tint = HashColors.Green, modifier = Modifier.size(18.dp))
                        else -> Icon(Icons.Filled.Public, "Not secure", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (field.text.isEmpty() && !editing) {
                    Text("Search or type an address", color = colors.onSurfaceVariant, fontSize = 15.sp)
                }
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        b.loadInput(field.text)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { st ->
                            if (st.isFocused && !editing) {
                                editing = true
                                val full = if (tab.isHome) "" else tab.url
                                field = TextFieldValue(full, TextRange(0, full.length))
                            } else if (!st.isFocused && editing) {
                                editing = false
                            }
                        },
                )
            }
            if (editing) {
                IconButton(onClick = {
                    if (field.text.isEmpty()) focusManager.clearFocus() else field = TextFieldValue("")
                }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.Close, "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            } else {
                if (!tab.isHome) {
                    IconButton(onClick = { if (tab.progress < 100) b.stop() else b.reload() }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            if (tab.progress < 100) Icons.Filled.Close else Icons.Filled.Refresh,
                            "Reload", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp),
                        )
                    }
                }
                TunnelDot(b.tunnelStatus)
                ShieldButton(tab.blocked.size, b.store.shieldsEnabled && !b.isAllowlisted(tab.host), onShields)
            }
        }
    }
}

@Composable
private fun TunnelDot(status: TunnelStatus) {
    val color = when (status) {
        is TunnelStatus.On -> HashColors.Green
        is TunnelStatus.Connecting -> HashColors.Cyan
        is TunnelStatus.Failed -> HashColors.Red
        else -> return
    }
    Box(
        Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape).background(color)
    )
}

@Composable
private fun ShieldButton(count: Int, active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (count > 0) 1f else 0.9f, animationSpec = spring(dampingRatio = 0.4f), label = "shield",
    )
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Shield, "Hash Shields",
            tint = if (active) HashColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((22 * scale).dp),
        )
        if (active && count > 0) {
            Text(
                if (count > 99) "99+" else "$count",
                color = HashColors.Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HashColors.Cyan)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun Suggestions(b: Browser, query: String, onPick: (String) -> Unit) {
    val q = query.trim().lowercase()
    val items = remember(q, b.bookmarks) {
        if (q.isEmpty()) emptyList()
        else {
            val bm = b.bookmarks.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
                .map { Triple(it.title, it.url, true) }
            val hist = b.store.history.asSequence()
                .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
                .take(8).map { Triple(it.title, it.url, false) }.toList()
            (bm + hist).distinctBy { it.second }.take(6)
        }
    }
    if (q.isEmpty()) return
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            SuggestionRow(Icons.Filled.Search, "Search “$query”", null) { onPick(query) }
            items.forEach { (title, url, bookmarked) ->
                SuggestionRow(if (bookmarked) Icons.Filled.Star else Icons.Filled.History, title, url) { onPick(url) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toolbar(b: Browser, tab: Tab?, onTabs: () -> Unit, onMenu: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { b.back() }, enabled = tab != null && (tab.canGoBack || !tab.isHome)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
        IconButton(onClick = { b.forward() }, enabled = tab?.canGoForward == true) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(HashColors.accent)
                .clickable { b.captureThumbnail(); b.newTab(null, tab?.isPrivate == true) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "New tab", tint = HashColors.Navy)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onTabs),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .border(2.dp, colors.onSurface, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${b.tabs.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, "Menu") }
    }
}

@Composable
private fun FindBar(b: Browser) {
    var text by remember { mutableStateOf(b.findQuery ?: "") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it; b.find(it) },
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Find in page", color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
        Text(b.findResult, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        IconButton(onClick = { b.findNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
        IconButton(onClick = { b.findNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
        IconButton(onClick = { b.closeFind() }) { Icon(Icons.Filled.Close, "Close") }
    }
}

@Composable
private fun FullscreenVideo(b: Browser) {
    val activity = LocalContext.current as Activity
    BackHandler { b.exitFullscreen() }
    DisposableEffect(Unit) {
        val ctrl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctrl.show(WindowInsetsCompat.Type.systemBars()) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { ctx -> FrameLayout(ctx) },
        update = { frame ->
            val v = b.customView ?: return@AndroidView
            if (v.parent !== frame) {
                (v.parent as? ViewGroup)?.removeView(v)
                frame.removeAllViews()
                frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
    )
}

@Composable
fun Favicon(tab: Tab, size: Int = 18) {
    val bmp = tab.favicon
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, Modifier.size(size.dp).clip(RoundedCornerShape(4.dp)))
    } else {
        LetterTile(tab.title.ifBlank { tab.host.ifBlank { "#" } }, size)
    }
}
