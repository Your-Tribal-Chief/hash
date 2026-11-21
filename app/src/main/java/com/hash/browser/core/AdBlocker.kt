package com.hash.browser.core

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A small Adblock Plus / EasyList engine.
 *
 * Network rules are split two ways so a lookup stays fast:
 *  - pure host rules (||ads.example.com^) go into a HashSet, checked by walking host suffixes
 *  - everything else is indexed by one literal token, so each request only tests the few
 *    rules that share a token with its URL
 * Element-hiding rules (##selector) are kept per domain and turned into CSS.
 */
class AdBlocker(private val context: Context) {

    private class PatternRule(val pattern: String, val thirdPartyOnly: Boolean) {
        private var compiled: Regex? = null
        private var broken = false

        fun matches(url: String): Boolean {
            if (broken) return false
            val r = compiled ?: try {
                toRegex(pattern).also { compiled = it }
            } catch (e: Exception) {
                broken = true
                return false
            }
            return r.containsMatchIn(url)
        }
    }

    private class RuleSet {
        val hosts = HashSet<String>()
        val hostsThirdParty = HashSet<String>()
        val byToken = HashMap<String, MutableList<PatternRule>>()
        val untokened = ArrayList<PatternRule>()

        fun addPattern(rule: PatternRule) {
            val token = pickToken(rule.pattern)
            if (token == null) untokened.add(rule)
            else byToken.getOrPut(token) { ArrayList(2) }.add(rule)
        }

        fun match(url: String, host: String, thirdParty: Boolean, urlTokens: List<String>): Boolean {
            var h = host
            while (true) {
                if (hosts.contains(h)) return true
                if (thirdParty && hostsThirdParty.contains(h)) return true
                val dot = h.indexOf('.')
                if (dot < 0) break
                h = h.substring(dot + 1)
            }
            for (t in urlTokens) {
                val list = byToken[t] ?: continue
                for (r in list) {
                    if (r.thirdPartyOnly && !thirdParty) continue
                    if (r.matches(url)) return true
                }
            }
            for (r in untokened) {
                if (r.thirdPartyOnly && !thirdParty) continue
                if (r.matches(url)) return true
            }
            return false
        }
    }

    @Volatile private var block = RuleSet()
    @Volatile private var allow = RuleSet()
    @Volatile private var genericHide: List<String> = emptyList()
    @Volatile private var domainHide: Map<String, List<String>> = emptyMap()
    @Volatile var ruleCount = 0
        private set
    @Volatile var loaded = false
        private set

    /** Parse bundled lists, downloaded lists, and user rules. Call off the main thread. */
    fun load(customCosmetic: List<String>) {
        val newBlock = RuleSet()
        val newAllow = RuleSet()
        val generic = ArrayList<String>()
        val perDomain = HashMap<String, MutableList<String>>()
        var count = 0

        fun consume(lines: Sequence<String>) {
            for (raw in lines) {
                if (parseLine(raw.trim(), newBlock, newAllow, generic, perDomain)) count++
            }
        }

        try {
            context.assets.list("filters")?.forEach { name ->
                context.assets.open("filters/$name").bufferedReader().useLines { consume(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "bundled lists", e)
        }
        listDir().listFiles()?.forEach { f ->
            try {
                f.bufferedReader().useLines { consume(it) }
            } catch (e: Exception) {
                Log.w(TAG, "list ${f.name}", e)
            }
        }
        consume(customCosmetic.asSequence())

        block = newBlock
        allow = newAllow
        genericHide = generic
        domainHide = perDomain
        ruleCount = count
        loaded = true
    }

    /** Download each list URL into app storage. Returns how many succeeded. */
    fun downloadLists(urls: List<String>): Int {
        val dir = listDir()
        var ok = 0
        urls.forEachIndexed { i, u ->
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { input ->
                    File(dir, "list_$i.txt").outputStream().use { input.copyTo(it) }
                }
                ok++
            } catch (e: Exception) {
                Log.w(TAG, "download $u", e)
            }
        }
        return ok
    }

    fun shouldBlock(url: String, pageHost: String?): Boolean {
        if (!loaded) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        val host = try { Uri.parse(lower).host } catch (e: Exception) { null } ?: return false
        val thirdParty = pageHost != null && baseDomain(host) != baseDomain(pageHost)
        val tokens = tokenize(lower)
        if (!block.match(lower, host, thirdParty, tokens)) return false
        return !allow.match(lower, host, thirdParty, tokens)
    }

    /** CSS that hides ad elements on [host]. */
    fun cosmeticCss(host: String, aggressive: Boolean): String {
        val selectors = ArrayList<String>()
        var h = host.lowercase()
        while (true) {
            domainHide[h]?.let { selectors.addAll(it) }
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        val generic = genericHide
        selectors.addAll(if (aggressive) generic else generic.take(1500))
        if (selectors.isEmpty()) return ""
        // Chunk selectors: one invalid selector only drops its own small group.
        return selectors.chunked(40).joinToString("\n") {
            it.joinToString(",") + "{display:none!important}"
        }
    }

    private fun listDir(): File = File(context.filesDir, "filters").apply { mkdirs() }

    companion object {
        private const val TAG = "HashShields"

        private val UNSUPPORTED_OPTIONS = setOf(
            "popup", "document", "csp", "redirect", "redirect-rule", "removeparam",
            "first-party", "1p", "~third-party", "elemhide", "generichide", "genericblock",
            "rewrite", "replace", "header", "permissions", "urltransform", "badfilter",
        )
        private val HOST_ONLY = Regex("^[a-z0-9.-]+\\^?\\|?$")

        /** Returns true if the line produced a rule. */
        private fun parseLine(
            line: String,
            block: RuleSet,
            allow: RuleSet,
            generic: MutableList<String>,
            perDomain: MutableMap<String, MutableList<String>>,
        ): Boolean {
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return false

            // ---- element hiding ----
            val hideIdx = line.indexOf("##")
            if (hideIdx >= 0) {
                val selector = line.substring(hideIdx + 2)
                if (selector.isEmpty() || selector.contains("+js(") || selector.contains(":style(") ||
                    selector.contains(":-abp-") || selector.contains(":remove(")
                ) return false
                val domains = line.substring(0, hideIdx)
                if (domains.isEmpty()) {
                    generic.add(selector)
                } else {
                    domains.split(',').map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() && !it.startsWith("~") }
                        .forEach { perDomain.getOrPut(it) { ArrayList() }.add(selector) }
                }
                return true
            }
            if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#") || line.contains("#%#")) {
                return false
            }

            // ---- network rules ----
            var text = line
            val target: RuleSet
            if (text.startsWith("@@")) {
                target = allow
                text = text.substring(2)
            } else {
                target = block
            }
            if (text.startsWith("/") && text.endsWith("/") && text.length > 2) return false // regex rules

            var thirdParty = false
            val dollar = text.lastIndexOf('$')
            if (dollar >= 0 && !text.substring(dollar).contains("/")) {
                val opts = text.substring(dollar + 1).lowercase().split(',')
                text = text.substring(0, dollar)
                for (o in opts) {
                    val name = o.substringBefore('=')
                    if (name == "domain" || name == "from" || name == "to") return false
                    if (o in UNSUPPORTED_OPTIONS || name in UNSUPPORTED_OPTIONS) return false
                    if (o == "third-party" || o == "3p") thirdParty = true
                }
            }
            text = text.lowercase()
            if (text.isEmpty() || text == "*" || text.length < 4) return false

            if (text.startsWith("||")) {
                val rest = text.substring(2)
                if (HOST_ONLY.matches(rest)) {
                    val host = rest.trimEnd('|').trimEnd('^')
                    if (host.isNotEmpty()) {
                        if (thirdParty) target.hostsThirdParty.add(host) else target.hosts.add(host)
                        return true
                    }
                }
            }
            target.addPattern(PatternRule(text, thirdParty))
            return true
        }

        private fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%'

        /** A literal run of 3+ chars that is fully delimited, so it equals a URL token. */
        private fun pickToken(pattern: String): String? {
            var best: String? = null
            var i = 0
            val n = pattern.length
            while (i < n) {
                if (!isTokenChar(pattern[i])) { i++; continue }
                val start = i
                while (i < n && isTokenChar(pattern[i])) i++
                val before = if (start == 0) null else pattern[start - 1]
                val after = if (i >= n) null else pattern[i]
                // An unanchored start/end, or a wildcard next to the run, means the URL's
                // token could be longer than this literal, so it can't be used as a key.
                val leftOk = before != null && before != '*'
                val rightOk = after != null && after != '*'
                if (leftOk && rightOk && i - start >= 3) {
                    val tok = pattern.substring(start, i)
                    if (best == null || tok.length > best.length) best = tok
                }
            }
            return best
        }

        private fun tokenize(url: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            val n = url.length
            while (i < n) {
                if (!isTokenChar(url[i])) { i++; continue }
                val s = i
                while (i < n && isTokenChar(url[i])) i++
                if (i - s >= 3) out.add(url.substring(s, i))
            }
            return out
        }

        private fun toRegex(pattern: String): Regex {
            val sb = StringBuilder()
            var p = pattern
            var endAnchor = false
            when {
                p.startsWith("||") -> { sb.append("^[a-z][a-z0-9+.-]*://([^/?#]*\\.)?"); p = p.substring(2) }
                p.startsWith("|") -> { sb.append("^"); p = p.substring(1) }
            }
            if (p.endsWith("|")) { endAnchor = true; p = p.dropLast(1) }
            for (c in p) {
                when (c) {
                    '*' -> sb.append(".*")
                    '^' -> sb.append("(?:[^a-z0-9_.%-]|$)")
                    else -> {
                        if (c.isLetterOrDigit()) sb.append(c) else sb.append('\\').append(c)
                    }
                }
            }
            if (endAnchor) sb.append("$")
            return Regex(sb.toString())
        }

        fun baseDomain(host: String): String {
            val parts = host.split('.')
            if (parts.size <= 2) return host
            // keep three labels for short second-level suffixes like com.bd, co.uk
            val sld = parts[parts.size - 2]
            return if (sld.length <= 3 && parts.size >= 3 && parts.last().length == 2)
                parts.takeLast(3).joinToString(".")
            else parts.takeLast(2).joinToString(".")
        }
    }
}
