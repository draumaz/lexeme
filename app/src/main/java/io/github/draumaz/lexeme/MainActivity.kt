package io.github.draumaz.lexeme

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.draumaz.lexeme.data.ThemeMode
import io.github.draumaz.lexeme.ui.LexemeRoot
import io.github.draumaz.lexeme.ui.LexemeViewModel
import io.github.draumaz.lexeme.ui.theme.LexemeTheme

class MainActivity : ComponentActivity() {
    private val vm: LexemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        val settings = (application as LexemeApp).settings
        setContent {
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Forced light or dark has to reach the system bar icons too.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LexemeTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                LexemeRoot(vm, settings, openExternal = ::openExternal, share = ::share)
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
                // A path, not a form: "+" is literal (C++), only %XX escapes decode.
                val title = uri.path?.removePrefix("/wiki/")?.replace('_', ' ') ?: return
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
