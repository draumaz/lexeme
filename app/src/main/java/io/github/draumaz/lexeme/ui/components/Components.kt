package io.github.draumaz.lexeme.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import io.github.draumaz.lexeme.ui.theme.LexemeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Draws a [Morph] between two normalized polygons as a [Shape]. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/** The expressive shape set, cycled through for badges so neighbours never match. */
val BadgeShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Gem,
    MaterialShapes.SoftBurst, MaterialShapes.Cookie4Sided, MaterialShapes.Pentagon,
    MaterialShapes.Puffy, MaterialShapes.Cookie7Sided, MaterialShapes.Clover8Leaf,
)

fun badgeShape(seed: Int): RoundedPolygon = BadgeShapes[Math.floorMod(seed, BadgeShapes.size)]

/** Springy squish while pressed. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Pops content in from below with a bouncy spring the first time [key] is shown. [seen]
 * outlives the item so scrolling back up doesn't replay it; [order] staggers the first screenful.
 */
@Composable
fun Modifier.bouncyEnter(key: Any, seen: MutableSet<Any>, order: Int = 0): Modifier {
    val first = remember(key) { seen.add(key) }
    if (!first) return this
    val progress = remember(key) { Animatable(0f) }
    val spatial = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    LaunchedEffect(key) {
        delay(order.coerceIn(0, 8) * 45L)
        launch { progress.animateTo(1f, spatial) }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 56.dp.toPx()
        val s = 0.92f + 0.08f * p
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

/** A title whose characters bounce in one after another. */
@Composable
fun PoppingTitle(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    // One readable node for accessibility, not a letter per node.
    androidx.compose.foundation.layout.FlowRow(modifier.clearAndSetSemantics { contentDescription = text }) {
        val chars = remember(text) { text.codePoints().toArray().map { String(Character.toChars(it)) } }
        chars.forEachIndexed { index, ch ->
            androidx.compose.runtime.key(text, index) {
                val scale = remember { Animatable(0.2f) }
                LaunchedEffect(Unit) {
                    delay(index.coerceAtMost(24) * 28L)
                    scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                }
                Text(
                    ch,
                    style = style,
                    color = color,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = (scale.value * 1.4f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                )
            }
        }
    }
}

/** A container color plus its content color, from one of twelve hues around the wheel. */
data class HuePair(val container: Color, val content: Color)

private val Hues = listOf(
    0xFFFFB4AB to 0xFF690005, 0xFFFFB77C to 0xFF4A2800, 0xFFF0C048 to 0xFF3F2E00,
    0xFFAED460 to 0xFF1F3700, 0xFF7FDA96 to 0xFF003918, 0xFF82D5CB to 0xFF003733,
    0xFF5FD4FC to 0xFF003545, 0xFFA9C7FF to 0xFF003063, 0xFFC3C0FF to 0xFF241F70,
    0xFFDFB7FF to 0xFF4A1772, 0xFFFFAAEE to 0xFF5B0F55, 0xFFFFB1C8 to 0xFF650033,
)

/** A stable hue for a language name, softened toward the theme so it sits in with dynamic color. */
@Composable
fun hueFor(seed: String): HuePair {
    val (bg, fg) = Hues[Math.floorMod(seed.hashCode(), Hues.size)]
    val primary = MaterialTheme.colorScheme.primary
    val base = Color(bg)
    val container = if (LexemeTheme.dark) lerp(base, Color.Black, 0.08f) else lerp(base, Color.White, 0.45f)
    return HuePair(lerp(container, primary, 0.06f), Color(fg))
}

/** A filled expressive shape with centered content. */
@Composable
fun ShapeBadge(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    rotation: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val outline = remember(shape) { MorphShape(Morph(shape, shape), 0f) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation() }
                .background(color, outline)
        )
        content()
    }
}

/**
 * Segmented-list corners: generous on the ends of a group, tight between neighbours, so a
 * language's sections read as one stacked unit.
 */
fun segmentShape(first: Boolean, last: Boolean, outer: Dp = 28.dp, inner: Dp = 8.dp) = RoundedCornerShape(
    topStart = if (first) outer else inner,
    topEnd = if (first) outer else inner,
    bottomStart = if (last) outer else inner,
    bottomEnd = if (last) outer else inner,
)
