package com.hash.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Patterns
import android.view.View
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.hash.browser.core.AdBlocker
import com.hash.browser.core.Js
import com.hash.browser.core.ProxyServer
import com.hash.browser.core.RunAt
import com.hash.browser.core.ScriptRepo
import com.hash.browser.core.Store
import com.hash.browser.core.Tunnel
import com.hash.browser.core.TunnelStatus
import com.hash.browser.core.UserScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/** One open tab. Compose reads the mutable fields; WebView callbacks write them on the main thread. */
class Tab(val id: Long, val isPrivate: Boolean) {
    var webView: WebView? = null
    var generation by mutableIntStateOf(0)       // bumps when the WebView is recreated
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var crashed by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)
    val blocked = mutableStateListOf<String>()
    @Volatile var pageHost: String? = null
    var pendingUrl: String? = null               // url to load once the WebView exists

    val isHome get() = url.isEmpty()
    val host: String get() = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
    val secure get() = url.startsWith("https://")
}

data class PickInfo(val json: JSONObject) {
    val selector: String get() = json.optString("selector")
}

data class AuthRequest(val handler: HttpAuthHandler, val host: String, val realm: String)
data class ReaderContent(val title: String, val paragraphs: List<String>)
data class PendingInstall(val script: UserScript)

class Browser(private val activity: Activity) {
    val store = Store(activity)
    val adBlocker = AdBlocker(activity)
    val scripts = ScriptRepo(store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicLong(1)

    val tabs = mutableStateListOf<Tab>()
    var currentIndex by mutableIntStateOf(0)
    val current: Tab? get() = tabs.getOrNull(currentIndex)

    var totalBlocked by mutableStateOf(store.totalBlocked)
    var shieldsRuleCount by mutableIntStateOf(0)
    var tunnelStatus by mutableStateOf<TunnelStatus>(TunnelStatus.Off)
    var installedScripts by mutableStateOf(scripts.all())

    // UI requests
    var customView by mutableStateOf<View?>(null)
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var pickInfo by mutableStateOf<PickInfo?>(null)
    var pendingAuth by mutableStateOf<AuthRequest?>(null)
    var pendingInstall by mutableStateOf<PendingInstall?>(null)
    var reader by mutableStateOf<ReaderContent?>(null)
    var pageSource by mutableStateOf<String?>(null)
    var findQuery by mutableStateOf<String?>(null)
    var findResult by mutableStateOf("")
    var bookmarks by mutableStateOf(store.bookmarks)
    var speedDials by mutableStateOf(store.speedDials)

    var fileCallback: ValueCallback<Array<Uri>>? = null
    var launchFileChooser: ((Intent) -> Unit)? = null

    @Volatile private var allowlist: Set<String> = store.shieldsAllowlist
    @Volatile private var shieldsOn = store.shieldsEnabled
    private val noUpgradeHosts = HashSet<String>()
    private var erudaSource: String? = null

    init {
        WebView.setWebContentsDebuggingEnabled(store.remoteDebugging)
        reloadFilters()
        if (store.tunnelEnabled) {
            store.proxyServers.firstOrNull { it.id == store.tunnelServerId }?.let { connectTunnel(it) }
        }
    }

    // ------------------------------------------------------------------ tabs

    fun restoreOrStart(initialUrl: String?) {
        val saved = store.savedTabs
        if (saved.isNotEmpty()) {
            saved.forEach { u -> newTab(u.ifBlank { null }, private = false, select = false) }
            currentIndex = store.savedTabIndex.coerceIn(0, tabs.size - 1)
        }
        if (initialUrl != null) newTab(initialUrl, private = false)
        if (tabs.isEmpty()) newTab(null, private = false)
    }

    fun saveTabs() {
        val normal = tabs.filter { !it.isPrivate }
        store.savedTabs = normal.map { it.url }
        store.savedTabIndex = normal.indexOf(current).coerceAtLeast(0)
    }

    fun newTab(url: String?, private: Boolean, select: Boolean = true): Tab {
        val tab = Tab(ids.getAndIncrement(), private)
        tab.desktop = store.desktopDefault
        tabs.add(tab)
        if (select) currentIndex = tabs.size - 1
        if (url != null) {
            tab.url = url
            tab.pendingUrl = url
        }
        return tab
    }

    fun selectTab(tab: Tab) {
        captureThumbnail()
        val i = tabs.indexOf(tab)
        if (i >= 0) currentIndex = i
    }

    fun closeTab(tab: Tab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        destroyWebView(tab)
        tabs.removeAt(i)
        if (tab.isPrivate && tabs.none { it.isPrivate }) wipePrivateProfile()
        if (i < currentIndex) currentIndex--
        if (tabs.isEmpty()) newTab(null, private = false)
        currentIndex = currentIndex.coerceIn(0, tabs.size - 1)
    }

    fun closeAll(private: Boolean) {
        tabs.filter { it.isPrivate == private }.forEach { destroyWebView(it); tabs.remove(it) }
        if (private) wipePrivateProfile()
        if (tabs.isEmpty()) newTab(null, private = false)
        currentIndex = currentIndex.coerceIn(0, tabs.size - 1)
    }

    /** Returns the tab's WebView, creating it (and loading any pending url) when first needed. */
    fun webViewFor(tab: Tab): WebView {
        tab.webView?.let { return it }
        val wv = createWebView(tab)
        tab.webView = wv
        tab.pendingUrl?.let { load(tab, it) }
        tab.pendingUrl = null
        return wv
    }

    private fun destroyWebView(tab: Tab) {
        tab.webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        }
        tab.webView = null
    }

    fun captureThumbnail() {
        val tab = current ?: return
        val wv = tab.webView ?: return
        if (wv.width <= 0 || wv.height <= 0 || tab.isHome) return
        try {
            val scale = 0.35f
            val bmp = Bitmap.createBitmap((wv.width * scale).toInt(), (wv.height * scale).toInt(), Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            tab.thumbnail = bmp
        } catch (e: Throwable) { /* out of memory or detached view; skip */ }
    }

    // ------------------------------------------------------------------ navigation

    fun loadInput(input: String) {
        val tab = current ?: newTab(null, false)
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") || text.startsWith("about:") ||
                text.startsWith("file://") || text.startsWith("data:") -> text
            !text.contains(' ') && (Patterns.WEB_URL.matcher(text).matches() || text.startsWith("localhost")) ->
                "https://$text"
            else -> store.searchEngine.replace("%s", Uri.encode(text))
        }
        tab.url = url
        if (tab.webView == null) tab.pendingUrl = url else load(tab, url)
    }

    private fun load(tab: Tab, url: String) {
        tab.webView?.loadUrl(url, mapOf("DNT" to "1", "Sec-GPC" to "1"))
    }

    fun goHome() {
        val tab = current ?: return
        tab.webView?.let { it.stopLoading(); it.loadUrl("about:blank") }
        tab.url = ""
        tab.title = ""
        tab.blocked.clear()
    }

    fun back(): Boolean {
        val tab = current ?: return false
        val wv = tab.webView
        if (wv != null && wv.canGoBack()) {
            val list = wv.copyBackForwardList()
            val prev = list.getItemAtIndex(list.currentIndex - 1)?.url
            if (prev == "about:blank") goHome() else wv.goBack()
            return true
        }
        if (!tab.isHome) { goHome(); return true }
        return false
    }

    fun forward() { current?.webView?.goForward() }
    fun reload() { current?.webView?.reload() }
    fun stop() { current?.webView?.stopLoading() }

    fun reloadCrashed(tab: Tab) {
        val url = tab.url
        destroyWebView(tab)
        tab.crashed = false
        tab.pendingUrl = url.ifBlank { null }
        tab.generation++
    }

    fun toggleDesktop() {
        val tab = current ?: return
        tab.desktop = !tab.desktop
        tab.webView?.let {
            applyUserAgent(it, tab.desktop)
            it.reload()
        }
    }

    // ------------------------------------------------------------------ webview setup

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: Tab): WebView {
        val wv = WebView(activity)
        if (tab.isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
                WebViewCompat.setProfile(wv, PRIVATE_PROFILE)
            } catch (e: Exception) { /* fall back to the default profile */ }
        }
        wv.settings.apply {
            javaScriptEnabled = store.javascriptEnabled
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = !store.blockPopups
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            if (tab.isPrivate) {
                cacheMode = WebSettings.LOAD_NO_CACHE
                @Suppress("DEPRECATION")
                saveFormData = false
            }
        }
        applyUserAgent(wv, tab.desktop)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !store.blockThirdPartyCookies)
        wv.setBackgroundColor(android.graphics.Color.WHITE)
        wv.webViewClient = Client(tab)
        wv.webChromeClient = Chrome(tab)
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }
        wv.setFindListener { active, count, done ->
            if (done) findResult = if (count == 0) "0/0" else "${active + 1}/$count"
        }
        return wv
    }

    private fun applyUserAgent(wv: WebView, desktop: Boolean) {
        val base = WebSettings.getDefaultUserAgent(activity)
        // Drop the "wv" marker so sites serve the normal Chrome experience.
        val mobile = base.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        val chrome = Regex("Chrome/[\\d.]+").find(base)?.value ?: "Chrome/120.0.0.0"
        wv.settings.userAgentString = if (desktop)
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        else mobile
    }

    private fun shieldsActiveFor(host: String?): Boolean =
        shieldsOn && host != null && allowlist.none { host == it || host.endsWith(".$it") }

    private inner class Client(val tab: Tab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val scheme = request.url.scheme?.lowercase() ?: return false
            if (scheme == "http" || scheme == "https") {
                if (url.substringBefore('?').endsWith(".user.js")) {
                    offerInstall(url)
                    return true
                }
                if (scheme == "http" && request.isForMainFrame && store.httpsUpgrade) {
                    val host = request.url.host ?: ""
                    if (host.isNotEmpty() && host !in noUpgradeHosts && !isLocal(host)) {
                        view.loadUrl("https://" + url.removePrefix("http://"))
                        return true
                    }
                }
                return false
            }
            if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return false
            openExternal(url)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url == "about:blank") return
            tab.url = url
            tab.pageHost = Uri.parse(url).host
            tab.blocked.clear()
            tab.favicon = favicon
            tab.progress = 5
            view.evaluateJavascript(Js.PRIVACY_SIGNALS, null)
            runScripts(view, url, RunAt.START)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url == "about:blank") { tab.progress = 100; return }
            tab.progress = 100
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            val host = Uri.parse(url).host
            if (host != null && shieldsActiveFor(host)) {
                val css = adBlocker.cosmeticCss(host, store.aggressiveShields)
                if (css.isNotEmpty()) view.evaluateJavascript(Js.injectCss("hash-shields", css), null)
            }
            runScripts(view, url, RunAt.END)
            main.postDelayed({ if (tab.url == url) runScripts(view, url, RunAt.IDLE) }, 600)
            if (!tab.isPrivate) store.addHistory(view.title ?: "", url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (url != "about:blank") tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) return null
            val pageHost = tab.pageHost
            if (!shieldsActiveFor(pageHost)) return null
            val url = request.url.toString()
            if (!adBlocker.shouldBlock(url, pageHost)) return null
            main.post {
                if (tab.blocked.size < 500) tab.blocked.add(url)
                totalBlocked++
                if (totalBlocked % 10 == 0L) store.totalBlocked = totalBlocked
            }
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val u = request.url
            // An auto-upgraded http site may have no https; fall back once.
            if (u.scheme == "https" && store.httpsUpgrade) {
                val host = u.host ?: return
                if (host !in noUpgradeHosts) {
                    noUpgradeHosts.add(host)
                    val code = error.errorCode
                    if (code == WebViewClient.ERROR_CONNECT || code == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ||
                        code == WebViewClient.ERROR_TIMEOUT
                    ) view.loadUrl("http://" + u.toString().removePrefix("https://"))
                }
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            val proxy = Tunnel.activeServer
            if (proxy != null && proxy.host.equals(host, true) && proxy.username.isNotEmpty()) {
                handler.proceed(proxy.username, proxy.password)
            } else {
                pendingAuth = AuthRequest(handler, host, realm)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (tab.webView === view) {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
                tab.webView = null
            }
            tab.crashed = true
            return true
        }
    }

    private inner class Chrome(val tab: Tab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view.url != "about:blank") tab.title = title ?: ""
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            tab.favicon = icon
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (store.blockPopups && !isUserGesture) {
                toast("Pop-up blocked")
                return false
            }
            val t = newTab(null, tab.isPrivate)
            t.url = "…"
            val wv = webViewFor(t)
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = wv
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            tabs.firstOrNull { it.webView === window }?.let { closeTab(it) }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customViewCallback?.onCustomViewHidden()
            customView = view
            customViewCallback = callback
        }

        override fun onHideCustomView() {
            customView = null
            customViewCallback = null
        }

        override fun onShowFileChooser(
            webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                launchFileChooser?.invoke(fileChooserParams.createIntent())
                true
            } catch (e: Exception) {
                fileCallback = null
                false
            }
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
            if (message == "hash:pick" && defaultValue != null) {
                try { pickInfo = PickInfo(JSONObject(defaultValue)) } catch (e: Exception) { }
                result.confirm("")
                return true
            }
            return false
        }
    }

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    // ------------------------------------------------------------------ shields

    fun reloadFilters() {
        scope.launch {
            withContext(Dispatchers.IO) { adBlocker.load(store.customCosmeticRules) }
            shieldsRuleCount = adBlocker.ruleCount
        }
    }

    fun updateFilterLists(onDone: (Int) -> Unit) {
        scope.launch {
            val urls = store.filterListUrls
            val ok = withContext(Dispatchers.IO) { adBlocker.downloadLists(urls) }
            withContext(Dispatchers.IO) { adBlocker.load(store.customCosmeticRules) }
            shieldsRuleCount = adBlocker.ruleCount
            onDone(ok)
        }
    }

    fun setShieldsEnabled(on: Boolean) {
        store.shieldsEnabled = on
        shieldsOn = on
        reload()
    }

    fun isAllowlisted(host: String) = allowlist.contains(host)

    fun setSiteShields(host: String, enabled: Boolean) {
        val set = allowlist.toMutableSet()
        if (enabled) set.remove(host) else set.add(host)
        allowlist = set
        store.shieldsAllowlist = set
        reload()
    }

    fun removeAllowlisted(host: String) {
        val set = allowlist.toMutableSet().apply { remove(host) }
        allowlist = set
        store.shieldsAllowlist = set
    }

    fun allowlistedHosts(): List<String> = allowlist.sorted()

    fun applyCookieSetting() {
        val block = store.blockThirdPartyCookies
        tabs.forEach { t -> t.webView?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, !block) } }
    }

    fun applyPopupSetting() {
        tabs.forEach { t -> t.webView?.settings?.javaScriptCanOpenWindowsAutomatically = !store.blockPopups }
    }

    fun applyJsSetting() {
        tabs.forEach { t -> t.webView?.settings?.javaScriptEnabled = store.javascriptEnabled }
        reload()
    }

    // ------------------------------------------------------------------ tunnel

    fun connectTunnel(server: ProxyServer) {
        tunnelStatus = TunnelStatus.Connecting
        scope.launch {
            val result = Tunnel.connect(server, store.bypassDomains, store.killSwitch)
            tunnelStatus = result
            store.tunnelEnabled = result is TunnelStatus.On || (result as? TunnelStatus.Failed)?.killSwitchActive == true
            store.tunnelServerId = server.id
            if (result is TunnelStatus.On) reload()
        }
    }

    fun disconnectTunnel() {
        scope.launch {
            Tunnel.clear()
            tunnelStatus = TunnelStatus.Off
            store.tunnelEnabled = false
            reload()
        }
    }

    // ------------------------------------------------------------------ user scripts

    private fun runScripts(view: WebView, url: String, at: RunAt) {
        if (!url.startsWith("http")) return
        installedScripts.filter { it.runAt == at && it.appliesTo(url) }.forEach {
            view.evaluateJavascript(UserScript.wrap(it), null)
        }
    }

    fun refreshScripts() { installedScripts = scripts.all() }

    fun setScriptEnabled(s: UserScript, enabled: Boolean) {
        scripts.upsert(s.copy(enabled = enabled))
        refreshScripts()
    }

    fun saveScript(s: UserScript) { scripts.upsert(s); refreshScripts() }
    fun deleteScript(s: UserScript) { scripts.delete(s.id); refreshScripts() }

    fun offerInstall(url: String) {
        scope.launch {
            val code = withContext(Dispatchers.IO) {
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 15000
                    c.readTimeout = 20000
                    c.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) { null }
            }
            val script = code?.let { UserScript.parse(it, url) }
            if (script == null) toast("Could not read a userscript at that address")
            else pendingInstall = PendingInstall(script)
        }
    }

    fun installFromCode(code: String): Boolean {
        val s = UserScript.parse(code) ?: return false
        saveScript(s)
        return true
    }

    // ------------------------------------------------------------------ inspect & tools

    fun openDevtools(panel: String? = null) {
        val wv = current?.webView ?: return toast("Open a page first")
        val src = erudaSource ?: activity.assets.open("eruda.js").bufferedReader().use { it.readText() }
            .also { erudaSource = it }
        wv.evaluateJavascript(Js.eruda(src, panel), null)
    }

    fun startPicker() {
        val wv = current?.webView ?: return toast("Open a page first")
        wv.evaluateJavascript(Js.PICKER, null)
    }

    fun inspectPicked(selector: String) {
        openDevtools("elements")
        main.postDelayed({ current?.webView?.evaluateJavascript(Js.erudaSelect(selector), null) }, 400)
    }

    fun hidePicked(selector: String) {
        val tab = current ?: return
        val host = tab.host
        if (host.isEmpty()) return
        val rule = "$host##$selector"
        store.customCosmeticRules = store.customCosmeticRules + rule
        tab.webView?.evaluateJavascript(Js.injectCss("hash-hide-" + selector.hashCode(), "$selector{display:none!important}"), null)
        reloadFilters()
        toast("Hidden on $host")
    }

    fun customRules(): List<String> = store.customCosmeticRules

    fun removeCustomRule(rule: String) {
        store.customCosmeticRules = store.customCosmeticRules - rule
        reloadFilters()
    }

    fun viewSource() {
        val wv = current?.webView ?: return toast("Open a page first")
        wv.evaluateJavascript(Js.PAGE_SOURCE) { pageSource = Js.decodeString(it) ?: "" }
    }

    fun openReader() {
        val wv = current?.webView ?: return toast("Open a page first")
        wv.evaluateJavascript(Js.READER) { raw ->
            val json = Js.decodeString(raw)
            if (json == null) { toast("Reader mode isn't available here"); return@evaluateJavascript }
            val o = JSONObject(json)
            val arr = o.getJSONArray("paras")
            val paras = (0 until arr.length()).map { arr.getString(it) }
            if (paras.size < 3) toast("Reader mode isn't available here")
            else reader = ReaderContent(o.optString("title"), paras)
        }
    }

    fun find(query: String) {
        findQuery = query
        current?.webView?.findAllAsync(query)
    }

    fun findNext(forward: Boolean) { current?.webView?.findNext(forward) }

    fun closeFind() {
        findQuery = null
        findResult = ""
        current?.webView?.clearMatches()
    }

    // ------------------------------------------------------------------ bookmarks / speed dial

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark() {
        val tab = current ?: return
        if (tab.isHome) return
        val list = store.bookmarks.toMutableList()
        if (list.any { it.url == tab.url }) list.removeAll { it.url == tab.url }
        else list.add(0, com.hash.browser.core.Bookmark(tab.title.ifBlank { tab.url }, tab.url))
        store.bookmarks = list
        bookmarks = list
    }

    fun removeBookmark(url: String) {
        val list = store.bookmarks.filter { it.url != url }
        store.bookmarks = list
        bookmarks = list
    }

    fun addToHome() {
        val tab = current ?: return
        if (tab.isHome) return
        val list = speedDials.toMutableList()
        if (list.none { it.url == tab.url }) {
            list.add(com.hash.browser.core.SpeedDial(tab.title.ifBlank { tab.host }.take(24), tab.url))
            store.speedDials = list
            speedDials = list
            toast("Added to home")
        }
    }

    fun removeSpeedDial(url: String) {
        val list = speedDials.filter { it.url != url }
        store.speedDials = list
        speedDials = list
    }

    // ------------------------------------------------------------------ data & misc

    fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean) {
        if (history) store.history = emptyList()
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        if (cache) tabs.forEach { it.webView?.clearCache(true) }
        if (cache && tabs.all { it.webView == null }) WebView(activity).apply { clearCache(true); destroy() }
        toast("Cleared")
    }

    fun setRemoteDebugging(on: Boolean) {
        store.remoteDebugging = on
        WebView.setWebContentsDebuggingEnabled(on)
    }

    private fun wipePrivateProfile() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return
        }
        main.postDelayed({
            try { ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE) } catch (e: Exception) { }
        }, 500)
    }

    fun shareCurrent() {
        val tab = current ?: return
        if (tab.isHome) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tab.url)
            putExtra(Intent.EXTRA_SUBJECT, tab.title)
        }
        activity.startActivity(Intent.createChooser(send, "Share"))
    }

    fun copy(text: String, label: String = "Copied") {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("hash", text))
        toast(label)
    }

    fun openSystemDownloads() {
        try { activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
        catch (e: ActivityNotFoundException) { toast("No downloads app found") }
    }

    fun openDefaultBrowserSettings() {
        try { activity.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        catch (e: Exception) { toast("Open Settings › Apps › Default apps") }
    }

    private fun download(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast("This file type can't be downloaded yet")
            return
        }
        try {
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "HashBrowser/$name")
            }
            (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            toast("Downloading $name")
        } catch (e: Exception) {
            toast("Download failed: ${e.message}")
        }
    }

    private fun openExternal(url: String) {
        try {
            val intent = if (url.startsWith("intent:")) {
                val i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                i.addCategory(Intent.CATEGORY_BROWSABLE)
                i.component = null
                i.selector = null
                if (activity.packageManager.resolveActivity(i, 0) == null) {
                    i.getStringExtra("browser_fallback_url")?.let { loadInput(it) }
                    return
                }
                i
            } else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        } catch (e: Exception) {
            toast("No app can open this link")
        }
    }

    private fun isLocal(host: String) =
        host == "localhost" || host.endsWith(".local") || host.startsWith("192.168.") ||
            host.startsWith("10.") || host.startsWith("127.") || host.startsWith("100.")

    fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    fun onDestroy(finishing: Boolean) {
        store.totalBlocked = totalBlocked
        if (finishing && store.clearOnExit) clearBrowsingData(history = true, cookies = true, cache = true)
        tabs.forEach { destroyWebView(it) }
    }

    companion object {
        const val PRIVATE_PROFILE = "hash_private"
    }
}
