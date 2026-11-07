package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.IndentStyle
import io.github.draumaz.lexeme.data.Link
import io.github.draumaz.lexeme.data.ListStyle
import io.github.draumaz.lexeme.data.RichText
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.badgeShape
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.theme.Serif

/** Nesting depth of sense lists, so sub-senses get smaller, quieter badges. */
private val LocalSenseDepth = staticCompositionLocalOf { 0 }

/** Lists longer than this show a prefix and a "show all" button, so 2,000 compounds don't stall a frame. */
private const val LIST_PREVIEW = 40

@Composable
fun BlockList(blocks: List<Block>, modifier: Modifier = Modifier, spacing: Int = 10) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        for (block in blocks) BlockView(block)
    }
}

@Composable
fun BlockView(block: Block) {
    when (block) {
        is Block.Paragraph -> ParagraphView(block)
        is Block.ListBlock -> when (block.style) {
            ListStyle.Senses -> SenseList(block)
            ListStyle.Bullets -> BulletList(block)
            ListStyle.Chips -> ChipList(block)
            ListStyle.Quotations -> QuotationFold(block)
        }
        is Block.Indented -> IndentedView(block)
        is Block.Table -> TableView(block)
        is Block.Images -> ImageGallery(block.images)
        is Block.Audio -> AudioButton(block)
        is Block.Collapsible -> CollapsibleView(block)
        is Block.EtymologyTree -> EtymologyTreeView(block)
        // Lifted into the language's stroke-order card by the parser; nothing left to draw.
        is Block.StrokeOrder -> StrokeOrderStrip(block.images)
    }
}

@Composable
private fun ParagraphView(p: Block.Paragraph) {
    if (p.role == Block.Paragraph.Role.Headword) {
        RichTextView(
            p.text,
            // Short headword lines read as a title; long ones (German nouns list every form) step down.
            style = (if (p.text.text.length <= 60) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                .copy(fontFamily = Serif),
            modifier = Modifier.padding(bottom = 2.dp),
        )
    } else {
        RichTextView(p.text, style = LocalTextStyle.current, color = LocalContentColor.current)
    }
}

@Composable
private fun SenseList(list: Block.ListBlock) {
    val depth = LocalSenseDepth.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(if (depth == 0) 14.dp else 10.dp)) {
        list.items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                ShapeBadge(
                    shape = badgeShape(index + depth * 5),
                    color = if (depth == 0) colors.primaryContainer else colors.secondaryContainer,
                    size = if (depth == 0) 30.dp else 24.dp,
                ) {
                    Text(
                        if (depth == 0) "${index + 1}" else ('a' + index % 26).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (depth == 0) colors.onPrimaryContainer else colors.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                CompositionLocalProvider(LocalSenseDepth provides depth + 1) {
                    BlockList(item, Modifier.weight(1f).padding(top = 3.dp), spacing = 8)
                }
            }
        }
    }
}

@Composable
private fun BulletList(list: Block.ListBlock) {
    var showAll by remember(list) { mutableStateOf(false) }
    val visible = if (showAll) list.items else list.items.take(LIST_PREVIEW)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 9.dp, start = 2.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                BlockList(item, Modifier.weight(1f), spacing = 6)
            }
        }
        if (list.items.size > visible.size) ShowAllButton(list.items.size) { showAll = true }
    }
}

@Composable
private fun ShowAllButton(total: Int, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.testTag("show_all"),
    ) {
        Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Show all $total")
    }
}

/** Term lists (derived terms, compounds) as tappable pills. */
@Composable
private fun ChipList(list: Block.ListBlock) {
    var showAll by remember(list) { mutableStateOf(false) }
    val visible = if (showAll) list.items else list.items.take(LIST_PREVIEW)
    val onLink = LocalLinkHandler.current
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { item ->
                val p = item.single() as Block.Paragraph
                val target = p.text.spans.firstNotNullOfOrNull { it.link as? Link.Wiki }
                val source = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        target?.let(onLink)
                    },
                    enabled = target != null,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    interactionSource = source,
                    modifier = Modifier.pressScale(source, 0.9f),
                ) {
                    Text(
                        p.text.text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
        if (list.items.size > visible.size) ShowAllButton(list.items.size) { showAll = true }
    }
}

/** Quotations are long; they fold away behind a pill like on the site. */
@Composable
private fun QuotationFold(list: Block.ListBlock) {
    val count = list.items.size
    FoldPill(
        label = if (count == 1) "1 quotation" else "$count quotations",
        tag = "quotations",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            list.items.forEach { item ->
                QuoteBar { BlockList(item, spacing = 6) }
            }
        }
    }
}

@Composable
private fun FoldPill(label: String, tag: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
    val source = remember { MutableInteractionSource() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = {
                haptics.performHapticFeedback(if (open) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                open = !open
            },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            interactionSource = source,
            modifier = Modifier.pressScale(source).testTag(tag),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FormatQuote, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
            }
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) { content() }
    }
}

@Composable
private fun QuoteBar(content: @Composable () -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
        }
    }
}

@Composable
private fun IndentedView(block: Block.Indented) {
    when (block.style) {
        IndentStyle.Example -> Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                BlockList(block.blocks, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), spacing = 6)
            }
        }
        IndentStyle.Quotation -> QuoteBar { BlockList(block.blocks, spacing = 6) }
        IndentStyle.Plain -> CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            BlockList(block.blocks, Modifier.padding(start = 14.dp), spacing = 6)
        }
    }
}

@Composable
fun CollapsibleView(block: Block.Collapsible) {
    CollapsibleFrame(block.title, block.startExpanded) { BlockList(block.blocks) }
}

@Composable
fun CollapsibleFrame(title: RichText, startExpanded: Boolean, content: @Composable () -> Unit) {
    var open by remember(title) { mutableStateOf(startExpanded) }
    val haptics = LocalHapticFeedback.current
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
    val corner by androidx.compose.animation.core.animateDpAsState(
        if (open) 14.dp else 24.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "corner",
    )
    val source = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(
                onClick = {
                    haptics.performHapticFeedback(if (open) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    open = !open
                },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                interactionSource = source,
                modifier = Modifier.fillMaxWidth().pressScale(source, 0.97f).testTag("fold"),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RichTextView(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 3)
                    ShapeBadge(
                        shape = androidx.compose.material3.MaterialShapes.Cookie6Sided,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        size = 32.dp,
                        rotation = { rotation / 3f },
                    ) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = if (open) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
            }
            AnimatedVisibility(
                open,
                enter = expandVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}

/** Ancestors oldest first, joined by a line, ending at this page's word. */
@Composable
private fun EtymologyTreeView(block: Block.EtymologyTree) {
    val colors = MaterialTheme.colorScheme
    CollapsibleFrame(RichText.plain("Etymology tree"), startExpanded = true) {
        Column {
            block.nodes.forEachIndexed { index, node ->
                val last = index == block.nodes.lastIndex
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(8.dp)
                                .background(if (index == 0) colors.surfaceContainerHigh else colors.outlineVariant)
                        )
                        ShapeBadge(
                            shape = badgeShape(index * 3),
                            color = if (last) colors.primary else colors.tertiaryContainer,
                            size = if (last) 22.dp else 16.dp,
                        ) {}
                        Box(
                            Modifier
                                .width(3.dp)
                                .weight(1f)
                                .background(if (last) colors.surfaceContainerHigh else colors.outlineVariant)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.padding(vertical = 4.dp).weight(1f)) {
                        Text(node.language, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        RichTextView(
                            node.term,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif),
                        )
                        node.relation?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                        }
                    }
                }
            }
        }
    }
}
