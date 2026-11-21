package com.hash.browser

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.hash.browser.ui.HashApp
import com.hash.browser.ui.HashTheme

class MainActivity : ComponentActivity() {

    private lateinit var browser: Browser

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        browser.fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        browser.fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        browser = Browser(this)
        browser.launchFileChooser = { fileChooser.launch(it) }
        browser.restoreOrStart(urlFrom(intent))
        setContent {
            HashTheme {
                HashApp(browser)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        urlFrom(intent)?.let { browser.newTab(it, private = false) }
    }

    override fun onStop() {
        super.onStop()
        browser.saveTabs()
        browser.store.totalBlocked = browser.totalBlocked
    }

    override fun onDestroy() {
        browser.onDestroy(isFinishing)
        super.onDestroy()
    }

    private fun urlFrom(intent: Intent?): String? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { q ->
                browser.store.searchEngine.replace("%s", android.net.Uri.encode(q))
            }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val link = Regex("https?://\\S+").find(text)?.value
                link ?: browser.store.searchEngine.replace("%s", android.net.Uri.encode(text))
            }
            else -> null
        }
    }
}
