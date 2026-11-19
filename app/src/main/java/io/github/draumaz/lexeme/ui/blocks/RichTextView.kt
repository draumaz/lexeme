package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    val find = LocalFindHighlight.current
    val range = find?.ranges?.get(rich)
    // Only texts counted by find get highlighted, so what's lit always matches the count.
    val query = if (range != null) find.query else null
    val current = if (range != null && find.active in range) find.active - range.first else -1
    return remember(rich, colors, onLink, query, current) {
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
            if (query != null) {
                val match = SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
                val here = SpanStyle(background = colors.tertiary, color = colors.onTertiary)
                matchStarts(rich.text, query).forEachIndexed { k, start ->
                    addStyle(if (k == current) here else match, start, start + query.length)
                }
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
    val find = LocalFindHighlight.current
    val range = find?.ranges?.get(rich)
    val current = if (range != null && find.active in range) find.active - range.first else -1
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Holding the current match: once it's laid out and has stopped moving (a section or fold
    // may still be springing open), scroll it into view.
    if (current >= 0) {
        LaunchedEffect(find!!.token, current, find.query) {
            snapshotFlow { layout.value }.filterNotNull().first()
            // Rows above may still be springing in, and a scroll can bring more in, so settle,
            // measure, scroll, and check again until the match stays put in the clear.
            for (attempt in 0 until 4) {
                var lastY = Float.NaN
                for (frame in 0 until 40) {
                    withFrameNanos { }
                    val y = coords[0]?.takeIf { it.isAttached }?.positionInRoot()?.y ?: continue
                    if (y == lastY) break
                    lastY = y
                }
                val text = layout.value ?: return@LaunchedEffect
                val at = coords[0]?.takeIf { it.isAttached } ?: return@LaunchedEffect
                val start = matchStarts(rich.text, find.query).getOrNull(current) ?: return@LaunchedEffect
                val end = (start + find.query.length - 1).coerceAtMost(text.layoutInput.text.length - 1)
                val top = text.getLineTop(text.getLineForOffset(start))
                val bottom = text.getLineBottom(text.getLineForOffset(end))
                if (!find.reveal(at.localToRoot(Offset(0f, top)).y, at.localToRoot(Offset(0f, bottom)).y)) break
            }
        }
    }
    Text(
        rememberAnnotated(rich),
        modifier = if (range != null) modifier.onGloballyPositioned { coords[0] = it } else modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { layout.value = it },
    )
}
