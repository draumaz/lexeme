package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import io.github.draumaz.lexeme.data.Link
import io.github.draumaz.lexeme.data.RichText
import io.github.draumaz.lexeme.data.SpanStyleKind

/** Where tapped links go: a Wiktionary page in-app, or anything else in the browser. */
val LocalLinkHandler = staticCompositionLocalOf<(Link) -> Unit> { {} }

@Composable
fun rememberAnnotated(rich: RichText): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val onLink = LocalLinkHandler.current
    return remember(rich, colors, onLink) {
        val linkStyles = TextLinkStyles(
            style = SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium),
            pressedStyle = SpanStyle(color = colors.onPrimaryContainer, background = colors.primaryContainer),
        )
        buildAnnotatedString {
            append(rich.text)
            for (span in rich.spans) {
                val style = when (span.style) {
                    SpanStyleKind.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
                    SpanStyleKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    SpanStyleKind.Superscript -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)
                    SpanStyleKind.Subscript -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)
                    SpanStyleKind.Small -> SpanStyle(fontSize = 0.86.em)
                    SpanStyleKind.Ipa -> SpanStyle(color = colors.tertiary, fontWeight = FontWeight.Medium)
                    SpanStyleKind.Self -> SpanStyle(fontWeight = FontWeight.Bold, color = colors.primary)
                    SpanStyleKind.Label -> SpanStyle(color = colors.tertiary, fontStyle = FontStyle.Italic)
                    SpanStyleKind.Muted -> SpanStyle(color = colors.onSurfaceVariant)
                    SpanStyleKind.Link -> null
                }
                if (style != null) addStyle(style, span.start, span.end)
                val link = span.link ?: continue
                addLink(
                    LinkAnnotation.Clickable(tag = link.toString(), styles = linkStyles) { onLink(link) },
                    span.start,
                    span.end,
                )
            }
        }
    }
}

@Composable
fun RichTextView(
    rich: RichText,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        rememberAnnotated(rich),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
