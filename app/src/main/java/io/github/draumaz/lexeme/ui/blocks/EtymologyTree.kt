package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.EtymologyBranch
import io.github.draumaz.lexeme.data.EtymologyNode
import io.github.draumaz.lexeme.data.RichText
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import kotlinx.coroutines.delay

/**
 * The etymology tree laid out left to right like a branch: the oldest roots on the left
 * merge and grow along twigs into this page's word, which blooms at the tip. It scrolls
 * sideways instead of running down the page, and grows in step by step when first shown.
 */
@Composable
internal fun EtymologyTreeView(block: Block.EtymologyTree) {
    CollapsibleFrame(RichText.plain("Etymology tree"), startExpanded = true) {
        val scroll = rememberScrollState()
        Box(
            Modifier
                .fillMaxWidth()
                .edgeFade(scroll)
                .horizontalScroll(scroll)
                .padding(vertical = 2.dp)
                .testTag("etymology_tree")
        ) {
            BranchView(block.root, start = 0, trunk = true)
        }
    }
}

/** Fades whichever side still has more tree to scroll to, so cut-off cards read as "keep going". */
private fun Modifier.edgeFade(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 36.dp.toPx()
        if (scroll.value > 0) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scroll.value < scroll.maxValue) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private data class TreeColors(val bark: Color, val leaf: Color, val node: Color, val word: Color, val label: Color)

@Composable
private fun treeColors(): TreeColors {
    val c = MaterialTheme.colorScheme
    return TreeColors(
        bark = lerp(c.tertiary, c.outline, 0.35f),
        leaf = lerp(c.primary, c.tertiary, 0.5f),
        node = c.surfaceContainerHighest,
        word = c.primaryContainer,
        label = c.tertiary,
    )
}

/** A 0→1 growth value that starts [order] steps into the tree's growth. */
@Composable
private fun rememberGrowth(order: Int, bouncy: Boolean): Animatable<Float, *> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order.coerceAtMost(16) * 85L + if (bouncy) 70L else 0L)
        anim.animateTo(
            1f,
            if (bouncy) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
            else spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        )
    }
    return anim
}

/** [start] is this branch's place in the growth order: sources grow first, then the chain. */
@Composable
private fun BranchView(branch: EtymologyBranch, start: Int, trunk: Boolean) {
    val sourcesSize = branch.sources.sumOf { it.size }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (branch.sources.isNotEmpty()) {
            Merge(order = start + sourcesSize) {
                var offset = start
                branch.sources.forEach { source ->
                    BranchView(source, offset, trunk = false)
                    offset += source.size
                }
            }
        }
        branch.chain.forEachIndexed { i, node ->
            val order = start + sourcesSize + i
            if (i > 0 || branch.sources.isNotEmpty()) Twig(node.relation, order, leafUp = order % 2 == 0)
            NodeCard(node, order, word = trunk && i == branch.chain.lastIndex)
        }
    }
}

/** A leaf-shaped card: two round corners, two tight ones. */
private val LeafShape = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp)

@Composable
private fun NodeCard(node: EtymologyNode, order: Int, word: Boolean) {
    val colors = treeColors()
    val pop = rememberGrowth(order, bouncy = true)
    Surface(
        shape = LeafShape,
        color = if (word) colors.word else colors.node,
        modifier = Modifier
            .widthIn(max = 150.dp)
            .graphicsLayer {
                val p = pop.value
                scaleX = 0.5f + 0.5f * p
                scaleY = 0.5f + 0.5f * p
                alpha = p.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        Row(Modifier.padding(start = if (word) 6.dp else 9.dp, end = 9.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (word) {
                ShapeBadge(MaterialShapes.Flower, MaterialTheme.colorScheme.primary, size = 18.dp, rotation = { pop.value * 72f }) {}
                Spacer(Modifier.width(6.dp))
            }
            Column {
                if (node.language.isNotEmpty()) {
                    Text(node.language, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RichTextView(node.term, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp))
            }
        }
    }
}

/** The stretch of branch between two terms, with the relation ("loanword") written along it. */
@Composable
private fun Twig(relation: String?, order: Int, leafUp: Boolean) {
    val colors = treeColors()
    val grow = rememberGrowth(order, bouncy = false)
    val leaf = rememberGrowth(order, bouncy = true)
    Box(
        Modifier
            .widthIn(min = 28.dp)
            .height(32.dp)
            .drawBehind {
                val y = size.height / 2
                val path = Path().apply {
                    moveTo(0f, y)
                    // A slight sag, so it reads as a branch rather than a rule.
                    quadraticTo(size.width / 2, y + 3.dp.toPx(), size.width, y)
                }
                drawPartial(path, grow.value, colors.bark, 3.dp.toPx())
                // Leaves go on the side without a label.
                val up = if (relation != null) false else leafUp
                drawLeaf(Offset(size.width * 0.45f, y + 1.dp.toPx()), up, leaf.value, colors.leaf)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (relation != null) {
            Text(
                relation,
                style = MaterialTheme.typography.labelSmall,
                color = colors.label,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .offset(y = (-9).dp)
                    .graphicsLayer { alpha = grow.value },
            )
        }
    }
}

/**
 * Stacks merging limbs right-aligned and curves each one's end into a single joint on the
 * right, where the merged term's twig continues at the vertical centre.
 */
@Composable
private fun Merge(order: Int, content: @Composable () -> Unit) {
    val colors = treeColors()
    val grow = rememberGrowth(order, bouncy = false)
    val ends = remember { Ends() }
    Layout(
        content = content,
        modifier = Modifier.drawBehind {
            val jx = size.width
            val jy = size.height / 2
            val stroke = 3.dp.toPx()
            ends.ys.forEach { y ->
                val path = Path().apply {
                    moveTo(ends.x, y)
                    cubicTo(ends.x + (jx - ends.x) * 0.7f, y, ends.x + (jx - ends.x) * 0.3f, jy, jx, jy)
                }
                drawPartial(path, grow.value, colors.bark, stroke)
            }
        },
    ) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val joint = 24.dp.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        val width = placeables.maxOf { it.width }
        val height = placeables.sumOf { it.height } + gap * (placeables.size - 1)
        layout(width + joint, height) {
            var y = 0
            val ys = FloatArray(placeables.size)
            placeables.forEachIndexed { i, p ->
                p.place(width - p.width, y)
                ys[i] = y + p.height / 2f
                y += p.height + gap
            }
            ends.x = width.toFloat()
            ends.ys = ys
        }
    }
}

/** Where the merging limbs end; written during placement, read when drawing the curves. */
private class Ends {
    var x = 0f
    var ys = FloatArray(0)
}

private fun DrawScope.drawPartial(path: Path, progress: Float, color: Color, width: Float) {
    if (progress <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), part, true)
    drawPath(part, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** A slim leaf on a short stem, sprouting from [base] up or down; [scale] pops it in. */
private fun DrawScope.drawLeaf(base: Offset, up: Boolean, scale: Float, color: Color) {
    if (scale <= 0f) return
    val stem = 3.dp.toPx() * scale
    val len = 10.dp.toPx() * scale
    val half = 2.5.dp.toPx() * scale
    rotate(if (up) -48f else 48f, pivot = base) {
        val start = Offset(base.x + stem, base.y)
        drawLine(color, base, start, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        val tip = Offset(start.x + len, start.y)
        val leaf = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(start.x + len * 0.3f, start.y - half * 1.6f, start.x + len * 0.75f, start.y - half * 1.2f, tip.x, tip.y)
            cubicTo(start.x + len * 0.75f, start.y + half * 1.2f, start.x + len * 0.3f, start.y + half * 1.6f, start.x, start.y)
            close()
        }
        drawPath(leaf, color)
        drawLine(color.copy(alpha = 0.45f).compositeOverBlack(), start, Offset(tip.x - len * 0.2f, tip.y), strokeWidth = 1.dp.toPx() * scale)
    }
}

private fun Color.compositeOverBlack() = lerp(Color.Black, this, 0.6f).copy(alpha = 0.5f)
