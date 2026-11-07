package io.github.draumaz.lexeme

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.net.toUri
import io.github.draumaz.lexeme.ui.LexemeRoot
import io.github.draumaz.lexeme.ui.LexemeViewModel
import io.github.draumaz.lexeme.ui.theme.LexemeTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    private val vm: LexemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            LexemeTheme {
                LexemeRoot(vm, openExternal = ::openExternal, share = ::share)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Look up" from another app's text selection, shared text, or a wiktionary.org link. */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let(vm::open)
            Intent.ACTION_SEND ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::open)
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val path = uri.encodedPath?.removePrefix("/wiki/") ?: return
                val title = URLDecoder.decode(path, "UTF-8").replace('_', ' ')
                vm.open(title, uri.fragment)
            }
        }
    }

    private fun openExternal(url: String) {
        try {
            // Resolve against a generic web URL so wiktionary.org links go to a browser
            // instead of bouncing back into this app's own link handler.
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                selector = Intent(Intent.ACTION_VIEW, "https://example.org".toUri())
                    .addCategory(Intent.CATEGORY_BROWSABLE)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun share(url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, null))
    }
}
