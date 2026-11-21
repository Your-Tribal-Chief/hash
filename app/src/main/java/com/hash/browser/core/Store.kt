package com.hash.browser.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String)
data class HistoryEntry(val title: String, val url: String, val time: Long)
data class SpeedDial(val title: String, val url: String)

enum class ProxyType { HTTP, SOCKS5 }

data class ProxyServer(
    val id: String,
    val name: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    fun rule(): String = when (type) {
        ProxyType.HTTP -> "http://$host:$port"
        ProxyType.SOCKS5 -> "socks://$host:$port"
    }
}

data class SearchEngine(val name: String, val template: String)

val SEARCH_ENGINES = listOf(
    SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    SearchEngine("Brave Search", "https://search.brave.com/search?q=%s"),
    SearchEngine("Google", "https://www.google.com/search?q=%s"),
    SearchEngine("Bing", "https://www.bing.com/search?q=%s"),
    SearchEngine("Startpage", "https://www.startpage.com/do/search?q=%s"),
)

/**
 * All persistent app data, kept in SharedPreferences as JSON.
 * Small enough that a database would be overkill.
 */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences("hash_store", Context.MODE_PRIVATE)

    // ---------- simple settings ----------
    var searchEngine: String
        get() = p.getString("search_engine", SEARCH_ENGINES[0].template)!!
        set(v) = p.edit().putString("search_engine", v).apply()

    var shieldsEnabled: Boolean
        get() = p.getBoolean("shields", true)
        set(v) = p.edit().putBoolean("shields", v).apply()

    var aggressiveShields: Boolean
        get() = p.getBoolean("shields_aggr", false)
        set(v) = p.edit().putBoolean("shields_aggr", v).apply()

    var blockPopups: Boolean
        get() = p.getBoolean("block_popups", true)
        set(v) = p.edit().putBoolean("block_popups", v).apply()

    var blockThirdPartyCookies: Boolean
        get() = p.getBoolean("block_3p_cookies", true)
        set(v) = p.edit().putBoolean("block_3p_cookies", v).apply()

    var httpsUpgrade: Boolean
        get() = p.getBoolean("https_upgrade", true)
        set(v) = p.edit().putBoolean("https_upgrade", v).apply()

    var javascriptEnabled: Boolean
        get() = p.getBoolean("js", true)
        set(v) = p.edit().putBoolean("js", v).apply()

    var desktopDefault: Boolean
        get() = p.getBoolean("desktop_default", false)
        set(v) = p.edit().putBoolean("desktop_default", v).apply()

    var remoteDebugging: Boolean
        get() = p.getBoolean("remote_debug", false)
        set(v) = p.edit().putBoolean("remote_debug", v).apply()

    var clearOnExit: Boolean
        get() = p.getBoolean("clear_on_exit", false)
        set(v) = p.edit().putBoolean("clear_on_exit", v).apply()

    var totalBlocked: Long
        get() = p.getLong("total_blocked", 0L)
        set(v) = p.edit().putLong("total_blocked", v).apply()

    var filterListUrls: List<String>
        get() = strings("filter_urls", DEFAULT_FILTER_URLS)
        set(v) = putStrings("filter_urls", v)

    var shieldsAllowlist: Set<String>
        get() = p.getStringSet("allowlist", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("allowlist", v).apply()

    var customCosmeticRules: List<String>
        get() = strings("cosmetic_custom", emptyList())
        set(v) = putStrings("cosmetic_custom", v)

    // ---------- tunnel ----------
    var tunnelEnabled: Boolean
        get() = p.getBoolean("tunnel_on", false)
        set(v) = p.edit().putBoolean("tunnel_on", v).apply()

    var tunnelServerId: String
        get() = p.getString("tunnel_server", "")!!
        set(v) = p.edit().putString("tunnel_server", v).apply()

    var killSwitch: Boolean
        get() = p.getBoolean("kill_switch", true)
        set(v) = p.edit().putBoolean("kill_switch", v).apply()

    var bypassDomains: List<String>
        get() = strings("bypass", emptyList())
        set(v) = putStrings("bypass", v)

    var proxyServers: List<ProxyServer>
        get() = arr("servers").map {
            ProxyServer(
                id = it.getString("id"),
                name = it.getString("name"),
                type = ProxyType.valueOf(it.getString("type")),
                host = it.getString("host"),
                port = it.getInt("port"),
                username = it.optString("user"),
                password = it.optString("pass"),
            )
        }
        set(v) = putArr("servers", v.map {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                .put("host", it.host).put("port", it.port)
                .put("user", it.username).put("pass", it.password)
        })

    // ---------- browsing data ----------
    var bookmarks: List<Bookmark>
        get() = arr("bookmarks").map { Bookmark(it.getString("t"), it.getString("u")) }
        set(v) = putArr("bookmarks", v.map { JSONObject().put("t", it.title).put("u", it.url) })

    var history: List<HistoryEntry>
        get() = arr("history").map {
            HistoryEntry(it.getString("t"), it.getString("u"), it.getLong("d"))
        }
        set(v) = putArr("history", v.take(800).map {
            JSONObject().put("t", it.title).put("u", it.url).put("d", it.time)
        })

    var speedDials: List<SpeedDial>
        get() {
            if (!p.contains("speed")) return DEFAULT_SPEED_DIALS
            return arr("speed").map { SpeedDial(it.getString("t"), it.getString("u")) }
        }
        set(v) = putArr("speed", v.map { JSONObject().put("t", it.title).put("u", it.url) })

    var savedTabs: List<String>
        get() = strings("tabs", emptyList())
        set(v) = putStrings("tabs", v)

    var savedTabIndex: Int
        get() = p.getInt("tab_index", 0)
        set(v) = p.edit().putInt("tab_index", v).apply()

    var userScriptsJson: String
        get() = p.getString("userscripts", "[]")!!
        set(v) = p.edit().putString("userscripts", v).apply()

    var firstRunDone: Boolean
        get() = p.getBoolean("first_run", false)
        set(v) = p.edit().putBoolean("first_run", v).apply()

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val list = history.toMutableList()
        list.removeAll { it.url == url }
        list.add(0, HistoryEntry(title.ifBlank { url }, url, System.currentTimeMillis()))
        history = list
    }

    // ---------- helpers ----------
    private fun arr(key: String): List<JSONObject> {
        val a = JSONArray(p.getString(key, "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun putArr(key: String, list: List<JSONObject>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        p.edit().putString(key, a.toString()).apply()
    }

    private fun strings(key: String, default: List<String>): List<String> {
        val raw = p.getString(key, null) ?: return default
        val a = JSONArray(raw)
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun putStrings(key: String, list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        p.edit().putString(key, a.toString()).apply()
    }

    companion object {
        val DEFAULT_FILTER_URLS = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt",
        )
        val DEFAULT_SPEED_DIALS = listOf(
            SpeedDial("YouTube", "https://m.youtube.com"),
            SpeedDial("Facebook", "https://m.facebook.com"),
            SpeedDial("Wikipedia", "https://en.m.wikipedia.org"),
            SpeedDial("GitHub", "https://github.com"),
            SpeedDial("Prothom Alo", "https://www.prothomalo.com"),
            SpeedDial("Reddit", "https://www.reddit.com"),
        )
    }
}
