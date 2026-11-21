package com.hash.browser.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RunAt { START, END, IDLE }

data class UserScript(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val matches: List<String>,
    val includes: List<String>,
    val excludes: List<String>,
    val runAt: RunAt,
    val code: String,
    val enabled: Boolean,
    val builtIn: Boolean = false,
    val sourceUrl: String = "",
) {
    private val includeRegexes by lazy { (matches.map { matchPatternToRegex(it) } + includes.map { globToRegex(it) }) }
    private val excludeRegexes by lazy { excludes.map { globToRegex(it) } }

    fun appliesTo(url: String): Boolean {
        if (!enabled) return false
        if (excludeRegexes.any { it.containsMatchIn(url) }) return false
        if (includeRegexes.isEmpty()) return false
        return includeRegexes.any { it.containsMatchIn(url) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("version", version).put("description", description)
        .put("matches", JSONArray(matches)).put("includes", JSONArray(includes))
        .put("excludes", JSONArray(excludes)).put("runAt", runAt.name).put("code", code)
        .put("enabled", enabled).put("builtIn", builtIn).put("sourceUrl", sourceUrl)

    companion object {
        fun fromJson(o: JSONObject): UserScript {
            fun list(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it) }
            }
            return UserScript(
                id = o.getString("id"), name = o.getString("name"),
                version = o.optString("version"), description = o.optString("description"),
                matches = list("matches"), includes = list("includes"), excludes = list("excludes"),
                runAt = RunAt.valueOf(o.optString("runAt", "END")), code = o.getString("code"),
                enabled = o.optBoolean("enabled", true), builtIn = o.optBoolean("builtIn", false),
                sourceUrl = o.optString("sourceUrl"),
            )
        }

        /** Parse a Greasemonkey/Tampermonkey-style script. Returns null if no metadata block. */
        fun parse(code: String, sourceUrl: String = "", enabled: Boolean = true): UserScript? {
            val start = code.indexOf("==UserScript==")
            val end = code.indexOf("==/UserScript==")
            if (start < 0 || end < start) return null
            val meta = code.substring(start, end)
            var name = ""
            var version = ""
            var desc = ""
            var runAt = RunAt.END
            val matches = ArrayList<String>()
            val includes = ArrayList<String>()
            val excludes = ArrayList<String>()
            Regex("@(\\S+)\\s+(.+)").findAll(meta).forEach { m ->
                val key = m.groupValues[1]
                val value = m.groupValues[2].trim()
                when (key) {
                    "name" -> if (name.isEmpty()) name = value
                    "version" -> version = value
                    "description" -> if (desc.isEmpty()) desc = value
                    "match" -> matches.add(value)
                    "include" -> includes.add(value)
                    "exclude", "exclude-match" -> excludes.add(value)
                    "run-at" -> runAt = when (value) {
                        "document-start" -> RunAt.START
                        "document-idle" -> RunAt.IDLE
                        else -> RunAt.END
                    }
                }
            }
            if (name.isEmpty()) name = "Untitled script"
            return UserScript(
                UUID.randomUUID().toString(), name, version, desc,
                matches, includes, excludes, runAt, code, enabled, false, sourceUrl,
            )
        }

        fun matchPatternToRegex(pattern: String): Regex {
            if (pattern == "<all_urls>") return Regex("^(https?|file)://")
            val scheme = pattern.substringBefore("://", "*")
            val rest = pattern.substringAfter("://")
            val host = rest.substringBefore('/')
            val path = "/" + rest.substringAfter('/', "")
            val sb = StringBuilder("^")
            sb.append(if (scheme == "*") "https?" else Regex.escape(scheme)).append("://")
            when {
                host == "*" -> sb.append("[^/]+")
                host.startsWith("*.") -> sb.append("([^/]+\\.)?").append(Regex.escape(host.substring(2)))
                else -> sb.append(Regex.escape(host))
            }
            sb.append(path.split('*').joinToString(".*") { Regex.escape(it) })
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }

        fun globToRegex(glob: String): Regex {
            if (glob.length > 2 && glob.startsWith("/") && glob.endsWith("/")) {
                return try { Regex(glob.substring(1, glob.length - 1), RegexOption.IGNORE_CASE) }
                catch (e: Exception) { Regex("(?!)") }
            }
            val body = glob.split('*').joinToString(".*") { Regex.escape(it) }
            return Regex("^$body$", RegexOption.IGNORE_CASE)
        }

        /** GM_* API shim, run before each script. Storage is namespaced per script. */
        fun shim(script: UserScript): String {
            val ns = JSONObject.quote("hashgm_" + script.id + "_")
            val info = JSONObject().put("script", JSONObject().put("name", script.name).put("version", script.version)).toString()
            return """
                var GM_info = $info;
                function GM_addStyle(css){var s=document.createElement('style');s.textContent=css;(document.head||document.documentElement).appendChild(s);return s;}
                function GM_setValue(k,v){try{localStorage.setItem($ns+k,JSON.stringify(v));}catch(e){}}
                function GM_getValue(k,d){try{var v=localStorage.getItem($ns+k);return v===null?d:JSON.parse(v);}catch(e){return d;}}
                function GM_deleteValue(k){try{localStorage.removeItem($ns+k);}catch(e){}}
                function GM_listValues(){var o=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k.indexOf($ns)===0)o.push(k.substring($ns.length));}}catch(e){}return o;}
                function GM_log(m){console.log('[userscript] '+m);}
                function GM_openInTab(u){window.open(u,'_blank');}
                function GM_setClipboard(t){try{navigator.clipboard.writeText(t);}catch(e){}}
                function GM_xmlhttpRequest(d){fetch(d.url,{method:d.method||'GET',headers:d.headers||{},body:d.data}).then(function(r){return r.text().then(function(t){var o={status:r.status,statusText:r.statusText,responseText:t,response:t,finalUrl:r.url};d.onload&&d.onload(o);});}).catch(function(e){d.onerror&&d.onerror(e);});}
                var GM = {info:GM_info,addStyle:GM_addStyle,setValue:function(k,v){GM_setValue(k,v);return Promise.resolve();},getValue:function(k,d){return Promise.resolve(GM_getValue(k,d));},deleteValue:function(k){GM_deleteValue(k);return Promise.resolve();},xmlHttpRequest:GM_xmlhttpRequest,openInTab:GM_openInTab,setClipboard:GM_setClipboard};
                var unsafeWindow = window;
            """.trimIndent()
        }

        fun wrap(script: UserScript): String =
            "(function(){try{\n" + shim(script) + "\n" + script.code + "\n}catch(e){console.error('[userscript ' + " +
                JSONObject.quote(script.name) + " + ']', e);}})();"

        val BUILT_INS: List<UserScript> by lazy {
            listOf(
                parse(DARK_MODE, enabled = false)!!.copy(id = "builtin-dark", builtIn = true),
                parse(FORCE_SELECT, enabled = false)!!.copy(id = "builtin-select", builtIn = true),
                parse(VIDEO_SPEED, enabled = false)!!.copy(id = "builtin-speed", builtIn = true),
            )
        }

        private const val DARK_MODE = """// ==UserScript==
// @name         Dark mode for every site
// @description  Inverts page colors and re-inverts images and video.
// @version      1.0
// @match        *://*/*
// @run-at       document-start
// ==/UserScript==
(function(){
  var css = 'html{filter:invert(0.92) hue-rotate(180deg)!important;background:#111!important}' +
            'img,video,picture,canvas,svg image,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)!important}';
  var s = document.createElement('style'); s.textContent = css;
  (document.head || document.documentElement).appendChild(s);
})();"""

        private const val FORCE_SELECT = """// ==UserScript==
// @name         Force enable text selection
// @description  Re-enables selecting, copying and right-click on sites that block them.
// @version      1.0
// @match        *://*/*
// @run-at       document-end
// ==/UserScript==
(function(){
  var s = document.createElement('style');
  s.textContent = '*{-webkit-user-select:text!important;user-select:text!important}';
  document.head.appendChild(s);
  ['copy','cut','paste','selectstart','contextmenu','mousedown'].forEach(function(ev){
    document.addEventListener(ev, function(e){ e.stopPropagation(); }, true);
  });
  document.oncontextmenu = null; document.onselectstart = null; document.oncopy = null;
})();"""

        private const val VIDEO_SPEED = """// ==UserScript==
// @name         Video speed controls
// @description  Adds a small speed button to every video on the page.
// @version      1.0
// @match        *://*/*
// @run-at       document-idle
// ==/UserScript==
(function(){
  var speeds = [1, 1.25, 1.5, 1.75, 2, 2.5, 3, 0.5, 0.75];
  function attach(v){
    if (v.dataset.hashSpeed) return; v.dataset.hashSpeed = '1';
    var b = document.createElement('div');
    b.textContent = '1x';
    b.style.cssText = 'position:absolute;z-index:2147483647;top:8px;left:8px;padding:4px 10px;border-radius:14px;' +
      'background:rgba(11,16,32,.8);color:#22D3EE;font:600 13px sans-serif;cursor:pointer';
    var i = 0;
    b.addEventListener('click', function(e){ e.stopPropagation(); e.preventDefault();
      i = (i + 1) % speeds.length; v.playbackRate = speeds[i]; b.textContent = speeds[i] + 'x'; }, true);
    var p = v.parentElement; if (!p) return;
    if (getComputedStyle(p).position === 'static') p.style.position = 'relative';
    p.appendChild(b);
  }
  function scan(){ document.querySelectorAll('video').forEach(attach); }
  scan(); new MutationObserver(scan).observe(document.documentElement, {childList:true, subtree:true});
})();"""
    }
}

/** Loads and saves the installed scripts (built-ins merged in). */
class ScriptRepo(private val store: Store) {
    fun all(): List<UserScript> {
        val saved = try {
            val a = JSONArray(store.userScriptsJson)
            (0 until a.length()).map { UserScript.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
        val savedIds = saved.map { it.id }.toSet()
        return saved + UserScript.BUILT_INS.filter { it.id !in savedIds }
    }

    fun save(list: List<UserScript>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        store.userScriptsJson = a.toString()
    }

    fun upsert(script: UserScript) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == script.id }
        if (i >= 0) list[i] = script else list.add(0, script)
        save(list)
    }

    fun delete(id: String) = save(all().filter { it.id != id })
}
