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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * Scripts whose letters render the same drawn one cluster at a time. Everything else (Arabic
 * joining, Indic conjuncts, right-to-left order) needs the whole word shaped as one run.
 */
private val SplittableScripts = setOf(
    Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED,
    Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.CYRILLIC,
    Character.UnicodeScript.ARMENIAN, Character.UnicodeScript.GEORGIAN, Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL,
    Character.UnicodeScript.BOPOMOFO, Character.UnicodeScript.ETHIOPIC, Character.UnicodeScript.CHEROKEE,
)

private fun isRtl(cp: Int): Boolean = when (Character.getDirectionality(cp)) {
    Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
    else -> false
}

/**
 * [text] as words of grapheme clusters (a trailing space stays on its word), or null when
 * splitting would change how it renders.
 */
private fun splitForPopping(text: String): List<List<String>>? {
    if (text.codePoints().anyMatch { isRtl(it) || Character.UnicodeScript.of(it) !in SplittableScripts }) return null
    val breaks = java.text.BreakIterator.getCharacterInstance().apply { setText(text) }
    val words = mutableListOf<MutableList<String>>(mutableListOf())
    var start = breaks.first()
    var end = breaks.next()
    while (end != java.text.BreakIterator.DONE) {
        val cluster = text.substring(start, end)
        words.last().add(cluster)
        if (cluster.isBlank()) words.add(mutableListOf())
        start = end
        end = breaks.next()
    }
    return words.filter { it.isNotEmpty() }
}

/**
 * A title whose letters bounce in one after another. Scripts that can't be drawn letter by
 * letter pop in as a whole word instead. With [animate] off it just shows the text, so
 * scrolling back to it (or returning to the page) doesn't replay the entrance.
 */
@Composable
fun PoppingTitle(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, animate: Boolean = true) {
    val words = remember(text) { splitForPopping(text) }
    if (!animate || words == null) {
        val scale = remember(text) { Animatable(if (animate) 0.6f else 1f) }
        LaunchedEffect(text) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
        Text(
            text,
            style = style,
            color = color,
            modifier = modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = (scale.value * 1.4f - 0.4f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 1f)
            },
        )
        return
    }
    // One readable node for accessibility, not a letter per node. Words wrap as units; a
    // word wider than the line still wraps between its letters rather than clipping.
    androidx.compose.foundation.layout.FlowRow(modifier.clearAndSetSemantics { contentDescription = text }) {
        var index = 0
        words.forEach { word ->
            androidx.compose.foundation.layout.FlowRow {
                word.forEach { cluster ->
                    val order = index++
                    androidx.compose.runtime.key(text, order) {
                        val scale = remember { Animatable(0.2f) }
                        LaunchedEffect(Unit) {
                            delay(order.coerceAtMost(24) * 28L)
                            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                        }
                        Text(
                            cluster,
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
    }
}

/**
 * The [bouncyEnter] stagger slot for an item first composed [sinceMillis] after the screen
 * appeared at [shownAt] (uptime): the first screenful staggers by [order], anything reached
 * later by scrolling pops in at once instead of sitting blank while a delay runs out.
 */
@Composable
fun rememberStagger(order: Int, shownAt: Long): Int =
    remember { if (android.os.SystemClock.uptimeMillis() - shownAt < STAGGER_WINDOW_MS) order else 0 }

private const val STAGGER_WINDOW_MS = 400L

/**
 * Fills [morph] at [progress], turned by [rotation] degrees, all read in the draw phase:
 * an endlessly morphing or spinning shape then only redraws, never recomposes.
 */
fun Modifier.morphBackground(
    color: Color,
    morph: () -> Morph,
    progress: () -> Float = { 0f },
    rotation: () -> Float = { 0f },
): Modifier = drawWithCache {
    val path = androidx.compose.ui.graphics.Path()
    val matrix = Matrix()
    onDrawBehind {
        path.rewind()
        morph().toPath(progress(), path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        rotate(rotation()) { drawPath(path, color) }
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

/**
 * Lets an inner scroller (the language list) mark a scroll as its own, so page-level
 * reactions like tucking the toolbar away ignore it. Inner post-scroll runs before outer.
 */
class ScrollClaim {
    var claimed = false
}

val LocalScrollClaim = androidx.compose.runtime.staticCompositionLocalOf { ScrollClaim() }

/**
 * True while the page is scrolling. Idle spins hold still meanwhile: each one would otherwise
 * redraw every frame, and the scroll needs that frame time more than a badge needs to turn.
 */
val LocalSpinPaused = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * A slow, endless turn in degrees for badges that idle by rotating, one full turn every
 * [periodMillis]; negative turns the other way. Unless it's [pausable] = false (a busy
 * indicator), it stops where it is while [LocalSpinPaused] is set and requests no frames at
 * all, then carries on from the same angle. It honours the system animation scale, and
 * doesn't turn at all with animations off.
 */
@Composable
fun rememberSpin(periodMillis: Int = 18_000, pausable: Boolean = true): State<Float> {
    val paused = LocalSpinPaused.current
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMillis, pausable, paused) {
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale <= 0f) return@LaunchedEffect
        val degreesPerNano = 360f / (periodMillis * scale * 1_000_000f)
        var last = -1L
        while (true) {
            if (pausable && paused.value) {
                snapshotFlow { paused.value }.first { !it }
                last = -1L
            }
            withFrameNanos { now ->
                if (last >= 0) angle.floatValue = (angle.floatValue + (now - last) * degreesPerNano) % 360f
                last = now
            }
        }
    }
    return angle
}
