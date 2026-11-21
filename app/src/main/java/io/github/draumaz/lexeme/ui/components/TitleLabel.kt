package io.github.draumaz.lexeme.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.data.Namespace
import io.github.draumaz.lexeme.data.TitleParts

/** Each namespace's mark, beside its name in headers and search rows. */
val Namespace.icon: ImageVector
    get() = when (this) {
        Namespace.Category -> Icons.Rounded.Category
        Namespace.Appendix -> Icons.Rounded.Description
        Namespace.Thesaurus -> Icons.Rounded.Hub
        Namespace.Reconstruction -> Icons.Rounded.AccountTree
    }

/**
 * The small line naming what kind of page a title is: its namespace's icon, then
 * `Reconstruction · Proto-Germanic`. Draws nothing for plain entries.
 */
@Composable
fun NamespaceLabel(
    parts: TitleParts,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 18.dp,
) {
    val ns = parts.namespace ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(ns.icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(6.dp))
        Text(parts.label.orEmpty(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
