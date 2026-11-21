@file:OptIn(ExperimentalMaterial3Api::class)

package com.hash.browser.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hash.browser.Browser
import com.hash.browser.core.ProxyServer
import com.hash.browser.core.ProxyType
import com.hash.browser.core.Tunnel
import com.hash.browser.core.TunnelStatus
import com.hash.browser.core.UserScript
import java.util.UUID

// ---------------------------------------------------------------------------------- tunnel

@Composable
fun TunnelScreen(b: Browser, onClose: () -> Unit) {
    val s = b.store
    var servers by remember { mutableStateOf(s.proxyServers) }
    var selectedId by remember { mutableStateOf(s.tunnelServerId.ifEmpty { servers.firstOrNull()?.id ?: "" }) }
    var killSwitch by remember { mutableStateOf(s.killSwitch) }
    var bypass by remember { mutableStateOf(s.bypassDomains.joinToString("\n")) }
    var editing by remember { mutableStateOf<ProxyServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    val status = b.tunnelStatus
    val selected = servers.firstOrNull { it.id == selectedId }

    FullScreenPage("Hash Tunnel", onClose, actions = {
        IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add server") }
    }) {
        LazyColumn {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TunnelOrb(status) {
                        when {
                            status is TunnelStatus.On || status is TunnelStatus.Connecting ||
                                (status is TunnelStatus.Failed && status.killSwitchActive) -> b.disconnectTunnel()
                            selected == null -> b.toast("Add a server first")
                            else -> selected?.let { b.connectTunnel(it) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val (title, body) = when (status) {
                        is TunnelStatus.Off -> "Not connected" to "Tap to route this browser through ${selected?.name ?: "a server"}."
                        is TunnelStatus.Connecting -> "Connecting…" to "Checking the server responds."
                        is TunnelStatus.On -> "Connected via ${status.server.name}" to "Exit IP ${status.exitIp} · ${status.latencyMs} ms"
                        is TunnelStatus.Failed -> "Connection failed" to (status.message +
                            if (status.killSwitchActive) "\nKill switch is on — pages are blocked until you disconnect." else "")
                    }
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(body, textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!Tunnel.supported) {
                        Text("Your Android System WebView is too old for proxy routing. Update it from the Play Store.",
                            color = HashColors.Red, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Text(
                    "Hash Tunnel sends only this browser's traffic through an HTTP or SOCKS5 server you provide " +
                        "(your own VPS, a work proxy, a Tor/SSH SOCKS tunnel…). It is not a system VPN, so apps like Tailscale keep working.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                SectionTitle("Servers")
            }
            if (servers.isEmpty()) item {
                Text("No servers yet. Tap + to add one, e.g. socks5://user:pass@203.0.113.5:1080",
                    fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            items(servers, key = { it.id }) { srv ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedId = srv.id }.padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = srv.id == selectedId, onClick = { selectedId = srv.id })
                    Column(Modifier.weight(1f)) {
                        Text(srv.name, fontWeight = FontWeight.Medium)
                        Text("${srv.type.name} · ${srv.host}:${srv.port}${if (srv.username.isNotEmpty()) " · auth" else ""}",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = srv }) { Icon(Icons.Filled.Edit, "Edit", Modifier.size(18.dp)) }
                    IconButton(onClick = {
                        servers = servers.filter { it.id != srv.id }
                        s.proxyServers = servers
                    }) { Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp)) }
                }
            }
            item {
                SectionTitle("Options")
                ToggleRow("Kill switch", "If the server stops working, block pages instead of loading them directly", killSwitch) {
                    killSwitch = it
                    s.killSwitch = it
                }
                OutlinedTextField(
                    bypass,
                    { bypass = it; s.bypassDomains = it.lines().map { l -> l.trim() }.filter { l -> l.isNotEmpty() } },
                    label = { Text("Bypass list (one per line)") },
                    placeholder = { Text("*.bank.com.bd") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 90.dp),
                )
                Text("Changes apply the next time you connect.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (adding || editing != null) {
        ServerDialog(editing, onDismiss = { adding = false; editing = null }) { srv ->
            servers = if (servers.any { it.id == srv.id }) servers.map { if (it.id == srv.id) srv else it } else servers + srv
            s.proxyServers = servers
            if (selectedId.isEmpty()) selectedId = srv.id
            adding = false
            editing = null
        }
    }
}

@Composable
private fun TunnelOrb(status: TunnelStatus, onClick: () -> Unit) {
    val on = status is TunnelStatus.On
    val connecting = status is TunnelStatus.Connecting
    val failed = status is TunnelStatus.Failed
    val transition = rememberInfiniteTransition(label = "orb")
    val flow by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "flow",
    )
    val ring = when {
        on -> HashColors.Green
        failed -> HashColors.Red
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(HashColors.Violet.copy(alpha = if (on) 0.35f else 0.15f), Color.Transparent)))
            .border(3.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(92.dp)) {
            val s = size.width / 108f
            val w = 8.8f * s
            fun p(x: Float, y: Float) = Offset(x * s, y * s)
            val dim = Color(0xFF3A4270)
            val lit = if (on) HashColors.Ink else dim
            drawLine(lit, p(28.2f, 44f), p(76.2f, 44f), w, StrokeCap.Round)
            drawLine(lit, p(31.8f, 64f), p(79.8f, 64f), w, StrokeCap.Round)
            drawLine(lit, p(68.6f, 26f), p(59.8f, 82f), w, StrokeCap.Round)
            drawLine(
                Brush.linearGradient(listOf(HashColors.Cyan, HashColors.Violet), p(48.2f, 26f), p(39.4f, 82f)),
                p(48.2f, 26f), p(39.4f, 82f), w, StrokeCap.Round,
            )
            if (connecting) {
                // a bright pulse travelling along the strokes while connecting
                val t = flow
                fun along(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                drawCircle(HashColors.Cyan, w * 0.9f, along(p(28.2f, 44f), p(76.2f, 44f), t))
                drawCircle(HashColors.Violet, w * 0.9f, along(p(79.8f, 64f), p(31.8f, 64f), t))
            }
        }
        if (!connecting) {
            Icon(
                Icons.Filled.PowerSettingsNew, null,
                tint = if (on) HashColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun ServerDialog(existing: ProxyServer?, onDismiss: () -> Unit, onSave: (ProxyServer) -> Unit) {
    var uri by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: ProxyType.SOCKS5) }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var pass by remember { mutableStateOf(existing?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add server" else "Edit server") },
        text = {
            LazyColumn {
                item {
                    if (existing == null) {
                        OutlinedTextField(
                            uri,
                            { v ->
                                uri = v
                                Tunnel.parseUri(v)?.let { p ->
                                    type = p.type; host = p.host; port = p.port.toString()
                                    user = p.username; pass = p.password
                                    if (name.isEmpty()) name = p.host
                                }
                            },
                            label = { Text("Paste a proxy URI (optional)") },
                            placeholder = { Text("socks5://user:pass@host:1080") },
                            singleLine = true,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == ProxyType.SOCKS5, onClick = { type = ProxyType.SOCKS5 }, label = { Text("SOCKS5") })
                        FilterChip(selected = type == ProxyType.HTTP, onClick = { type = ProxyType.HTTP }, label = { Text("HTTP") })
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(host, { host = it.trim() }, label = { Text("Host") }, singleLine = true)
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(user, { user = it }, label = { Text("Username (optional)") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password (optional)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (type == ProxyType.SOCKS5 && user.isNotEmpty()) {
                        Text(
                            "Note: WebView can't send SOCKS5 passwords. Use an HTTP proxy for authenticated servers, or a SOCKS5 server without auth (e.g. an SSH tunnel).",
                            fontSize = 11.sp, color = Color(0xFFFBBF24), modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val portNum = port.toIntOrNull()
            TextButton(enabled = host.isNotBlank() && portNum != null && portNum in 1..65535, onClick = {
                onSave(
                    ProxyServer(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { host }, type = type, host = host, port = portNum ?: 0,
                        username = user, password = pass,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------- mods

@Composable
fun ModsScreen(b: Browser, onClose: () -> Unit) {
    var installUrl by remember { mutableStateOf(false) }
    var pasteCode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserScript?>(null) }
    val scripts = b.installedScripts

    FullScreenPage("Hash Mods", onClose, actions = {
        TextButton(onClick = { installUrl = true }) { Text("From URL") }
        IconButton(onClick = { pasteCode = true }) { Icon(Icons.Filled.Add, "Paste script") }
    }) {
        LazyColumn {
            item {
                Text(
                    "Mods are userscripts (Greasemonkey / Tampermonkey format). Install from Greasy Fork or OpenUserJS by opening any .user.js link, or paste code with +.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                TextButton(onClick = { b.loadInput("https://greasyfork.org"); onClose() },
                    modifier = Modifier.padding(horizontal = 8.dp)) { Text("Browse Greasy Fork →") }
            }
            items(scripts, key = { it.id }) { sc ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sc.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            if (sc.builtIn) {
                                Text("BUILT-IN", fontSize = 9.sp, color = HashColors.Violet, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        if (sc.description.isNotBlank()) {
                            Text(sc.description, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            (if (sc.version.isNotBlank()) "v${sc.version} · " else "") + (sc.matches + sc.includes).take(2).joinToString(", "),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = sc }) { Icon(Icons.Filled.Edit, "Edit", Modifier.size(18.dp)) }
                    if (!sc.builtIn) {
                        IconButton(onClick = { b.deleteScript(sc) }) { Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp)) }
                    }
                    Switch(checked = sc.enabled, onCheckedChange = { b.setScriptEnabled(sc, it) }, colors = hashSwitchColors())
                }
                HorizontalDivider(Modifier.padding(start = 20.dp))
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (installUrl) {
        var url by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { installUrl = false },
            title = { Text("Install from URL") },
            text = { OutlinedTextField(url, { url = it }, label = { Text(".user.js address") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { b.offerInstall(url.trim()); installUrl = false }) { Text("Fetch") } },
            dismissButton = { TextButton(onClick = { installUrl = false }) { Text("Cancel") } },
        )
    }
    if (pasteCode) {
        CodeDialog(
            title = "New mod",
            initial = "// ==UserScript==\n// @name        My mod\n// @match       *://*/*\n// @run-at      document-end\n// ==/UserScript==\n\n",
            onDismiss = { pasteCode = false },
        ) { code ->
            if (b.installFromCode(code)) { pasteCode = false; b.toast("Mod installed") }
            else b.toast("Add a ==UserScript== header with @name and @match")
        }
    }
    editing?.let { sc ->
        CodeDialog(title = sc.name, initial = sc.code, onDismiss = { editing = null }) { code ->
            val parsed = UserScript.parse(code, sc.sourceUrl, sc.enabled)
            if (parsed == null) b.toast("The ==UserScript== header is missing")
            else {
                b.saveScript(parsed.copy(id = sc.id, builtIn = sc.builtIn))
                editing = null
            }
        }
    }
}

@Composable
private fun CodeDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var code by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(
                code, { code = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(code) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
