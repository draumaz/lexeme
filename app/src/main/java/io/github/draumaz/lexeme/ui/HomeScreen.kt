package io.github.draumaz.lexeme.ui

import androidx.compose.material3.LocalContentColor
import io.github.draumaz.lexeme.ui.components.icon
import io.github.draumaz.lexeme.data.titleParts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.draumaz.lexeme.ui.components.morphBackground
import io.github.draumaz.lexeme.ui.components.glass
import io.github.draumaz.lexeme.ui.components.progressiveBlur
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.components.rememberSpin
import io.github.draumaz.lexeme.ui.theme.Wordmark
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

private val Samples = listOf("serendipity", "water", "Hund", "水", "книга", "amour", "saudade", "猫")

@Composable
fun HomeScreen(
    recent: List<String>,
    onOpen: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val seen = remember { HashSet<Any>() }
    val haptics = LocalHapticFeedback.current
    // No search bar up top here, so the content centres in the space above the search button.
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val split = LocalPaneSplit.current

    @Composable
    fun Hero() {
        HeroMark(Modifier.bouncyEnter("hero", seen, 0))
        Spacer(Modifier.height(16.dp))
        Text(
            "Lexeme",
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = Wordmark, fontWeight = FontWeight.Light),
            modifier = Modifier.bouncyEnter("title", seen, 1),
        )
    }

    @Composable
    fun WordsLabel() {
        if (recent.isNotEmpty()) {
            SectionLabel(Icons.Rounded.History, "Recent", Modifier.bouncyEnter("recentLabel", seen, 4)) {
                TextButton(onClick = onClearHistory, modifier = Modifier.testTag("clear_history")) { Text("Clear") }
            }
        } else {
            SectionLabel(Icons.Rounded.TipsAndUpdates, "Try", Modifier.bouncyEnter("tryLabel", seen, 4)) {}
        }
    }

    @Composable
    fun WordsChips() {
        if (recent.isNotEmpty()) WordChips(recent, seen, prefix = "recent", onOpen = onOpen)
        else WordChips(Samples, seen, prefix = "try", onOpen = onOpen)
    }

    @Composable
    fun Words() {
        WordsLabel()
        WordsChips()
    }

    /**
     * A pane's content, scrolling if it must but otherwise centred in the room it has. With
     * [anchored], the content is laid out by [AnchoredHome] instead of plain centring.
     */
    @Composable
    fun Centered(
        topPad: androidx.compose.ui.unit.Dp,
        bottomPad: androidx.compose.ui.unit.Dp,
        bottomBlur: Boolean = false,
        anchored: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val scrollState = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (anchored) {
                AnchoredHome(
                    room = maxHeight - topPad - bottomPad,
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .fillMaxWidth()
                        .padding(top = topPad, bottom = bottomPad)
                        .padding(horizontal = 24.dp),
                    content = content,
                )
            } else {
                Column(
                    Modifier
                        .verticalScroll(scrollState)
                        .heightIn(min = maxHeight)
                        .fillMaxWidth()
                        .padding(top = topPad, bottom = bottomPad)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { content() }
            }

            if (bottomBlur) {
                val blurFraction by animateFloatAsState(
                    targetValue = if (scrollState.canScrollForward) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "bottomBlur",
                )
                val surface = MaterialTheme.colorScheme.surface
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomPad)
                        .progressiveBlur(
                            blur = { 12.dp * blurFraction },
                            scrim = { surface.copy(alpha = 0.9f * blurFraction) },
                        )
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (split == null) {
            Centered(top + 24.dp, bottom + 124.dp, bottomBlur = true, anchored = true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Hero() }
                WordsLabel()
                WordsChips()
            }
        } else {
            // Two panes (a tablet, or the two screens of a dual-screen phone): the mark and
            // mark on one side (or the top), the words and search scallop on the other.
            // Side by side, the words clear the settings button above them; stacked, it's on the other screen.
            Panes(
                split,
                start = { Centered(top + 24.dp, split.startBottomInset(bottom) + 24.dp) { Hero() } },
                end = { Centered(split.endTopInset(top) + if (split.stacked) 24.dp else 72.dp, bottom + 124.dp, bottomBlur = true) { Words() } },
            )
        }
        SettingsButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = top + 12.dp, end = 20.dp),
        )
    }
}

/**
 * The phone home screen's layout: the hero (mark and wordmark), then the words'
 * label and chips. It's centred as if there were only one row of chips, so the hero holds its
 * place however many recent words there are; further rows run on down, off the screen and
 * under the bottom blur, for scrolling to. Expects exactly those three children, in that order.
 */
@Composable
private fun AnchoredHome(room: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val (hero, label, chips) = measurables.map { it.measure(loose) }
        val gap = HeroWordsGap.roundToPx()
        val roomPx = room.roundToPx().coerceAtLeast(0)
        val reference = hero.height + gap + label.height + minOf(chips.height, ChipRowHeight.roundToPx())
        val heroY = ((roomPx - reference) / 2).coerceAtLeast(0)
        val labelY = heroY + hero.height + gap
        val chipsY = labelY + label.height
        val width = constraints.maxWidth
        layout(width, maxOf(roomPx, chipsY + chips.height)) {
            hero.place((width - hero.width) / 2, heroY)
            label.place((width - label.width) / 2, labelY)
            chips.place((width - chips.width) / 2, chipsY)
        }
    }
}

/** The space between the wordmark and the words below it. */
private val HeroWordsGap = 36.dp

/** One row of word chips: a chip's 48dp touch target. */
private val ChipRowHeight = 48.dp

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, trailing: @Composable () -> Unit) {
    // As tall as the Clear button whether or not there is one, so Recent and Try measure the
    // same and the hero above doesn't shift when history is cleared.
    Row(modifier.padding(bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized)
        Spacer(Modifier.width(4.dp))
        trailing()
    }
}

@Composable
private fun WordChips(words: List<String>, seen: MutableSet<Any>, prefix: String, onOpen: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        words.forEachIndexed { index, word -> key(word) {
            val source = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpen(word)
                },
                shape = RoundedCornerShape(if (index % 3 == 0) 20.dp else 14.dp),
                color = if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (index % 2 == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                interactionSource = source,
                modifier = Modifier
                    .pressScale(source, 0.88f)
                    .bouncyEnter("$prefix-$word", seen, 5 + index)
                    .testTag("${prefix}_chip_$index"),
            ) {
                // Namespaced pages read as their name, led by their kind's icon; a
                // reconstruction keeps its language beside it, since "*watōr" alone is ambiguous.
                val parts = remember(word) { titleParts(word) }
                Row(
                    Modifier.padding(start = if (parts.namespace != null) 14.dp else 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    parts.namespace?.let { ns ->
                        Icon(ns.icon, contentDescription = ns.name, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(parts.name, style = MaterialTheme.typography.titleMedium)
                    parts.language?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.72f))
                    }
                }
            }
        } }
    }
}

/**
 * The app's mark: an expressive shape that keeps morphing through the shape set and slowly
 * turning, with the book icon riding on top. Tapping it gives a bouncy squish. Holding it down
 * charges it up: it swells, jiggles faster and faster and spins up, ticking as it goes, until it
 * pops with a ripple and springs back. It pops once per hold; lift and press again for another.
 */
@Composable
private fun HeroMark(modifier: Modifier = Modifier) {
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie12Sided, MaterialShapes.Flower, MaterialShapes.Clover4Leaf,
            MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided, MaterialShapes.Sunny,
        )
    }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val loop = rememberInfiniteTransition(label = "hero")
    val phase by loop.animateFloat(
        0f, shapes.size.toFloat(),
        infiniteRepeatable(tween(shapes.size * 2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    val squish = remember { Animatable(1f) }
    // The hold: how charged the mark is (0 to 1), the jiggle's clock, and the extra turn it winds up.
    val charge = remember { Animatable(0f) }
    val jiggle = remember { mutableFloatStateOf(0f) }
    val boost = remember { mutableFloatStateOf(0f) }
    val bursts = remember { mutableStateListOf<PopBurst>() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme

    fun pop() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            squish.snapTo(0.62f)
            squish.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
        }
        val burst = PopBurst()
        bursts += burst
        scope.launch {
            burst.progress.animateTo(1f, tween(380, easing = LinearEasing))
            bursts -= burst
        }
    }

    // Phase and spin are read only while drawing, so the endless morph never recomposes.
    Box(
        modifier
            .size(140.dp)
            .drawWithContent {
                // The pop's ripple goes under the mark, so the mark springs back over it.
                bursts.forEach { drawBurst(it, colors.primary) }
                drawContent()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var popped = false
                        val charging = scope.launch {
                            var last = withFrameNanos { it }
                            var held = 0f
                            var tick = 0f
                            while (!popped) {
                                val now = withFrameNanos { it }
                                val dt = (now - last) / 1_000_000f
                                last = now
                                held += dt
                                val c = (held / PopChargeMs).coerceIn(0f, 1f)
                                charge.snapTo(c)
                                jiggle.floatValue += dt * (0.012f + 0.05f * c * c)
                                boost.floatValue += dt * 0.9f * c * c
                                // Ticks that come quicker as it fills.
                                tick += dt
                                if (tick > 150f - 100f * c) {
                                    tick = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                                if (c >= 1f) {
                                    pop()
                                    popped = true
                                    charge.snapTo(0f)
                                }
                            }
                        }
                        val released = tryAwaitRelease()
                        charging.cancel()
                        scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 600f)) }
                        if (released && !popped) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            scope.launch {
                                squish.snapTo(0.78f)
                                squish.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f))
                            }
                        }
                    },
                )
            }
            .testTag("hero"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Swell with the charge, and jiggle (squash one way, stretch the other) harder as it fills.
                    val c = charge.value
                    val swell = squish.value * (1f + 0.16f * c)
                    val wobble = sin(jiggle.floatValue * 2f * PI.toFloat()) * 0.07f * c
                    scaleX = swell * (1f + wobble)
                    scaleY = swell * (1f - wobble)
                }
                .morphBackground(
                    colors.primaryContainer,
                    morph = { morphs[phase.toInt().coerceIn(0, shapes.lastIndex)] },
                    // Hold each shape, then morph over the last 40% of its slot.
                    progress = {
                        val index = phase.toInt().coerceIn(0, shapes.lastIndex)
                        FastOutSlowInEasing.transform(((phase - index - 0.6f) / 0.4f).coerceIn(0f, 1f))
                    },
                    rotation = { spin + boost.floatValue },
                )
        )
        Icon(
            Icons.Rounded.AutoStories, null,
            Modifier
                .size(68.dp)
                .graphicsLayer {
                    val c = charge.value
                    val s = squish.value * (1f + 0.1f * c)
                    scaleX = s; scaleY = s
                    rotationZ = sin(jiggle.floatValue * 2f * PI.toFloat() + 1f) * 6f * c
                },
            tint = colors.onPrimaryContainer,
        )
    }
}

/** How long a hold takes to charge the mark up to its pop. */
private const val PopChargeMs = 720f

/** One pop of the mark: its ripple runs out as [progress] goes 0 to 1. */
private class PopBurst {
    val progress = Animatable(0f)
}

/** Draws [burst]'s ripple: a ring out from the mark's edge, thinning and fading as it goes. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBurst(burst: PopBurst, color: androidx.compose.ui.graphics.Color) {
    val p = burst.progress.value
    if (p >= 1f) return
    val r = size.minDimension / 2f
    val eased = 1f - (1f - p).pow(3)
    drawCircle(
        color.copy(alpha = 0.55f * (1f - p)),
        radius = r * (0.85f + 0.55f * eased),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.12f * (1f - p)),
    )
}

/**
 * The landing page's search button, after Nickel's add button: a slowly turning scalloped
 * cookie that squishes when pressed and kicks round with a spring when tapped. It turns only
 * while [spinning] (on the landing page); on a page it holds still so it doesn't pull the eye
 * from the reading, though a tap still kicks it round.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFab(onClick: () -> Unit, modifier: Modifier = Modifier, spinning: Boolean = true) {
    val idleRotation by rememberSpin(14_000, running = spinning)
    val kick = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Box(
        modifier
            .size(SearchFabSize)
            .pressScale(interaction, 0.88f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                scope.launch { kick.animateTo(kick.value + 120f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                onClick()
            }
            .semantics { contentDescription = "Search" }
            .testTag("search_fab"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = idleRotation + kick.value }
                // Frosted glass, as in Nickel: mostly blur, with a wash of the (dynamic)
                // primaryContainer so the scallop's edge still reads over plain text. Before
                // Android 17 QPR2 it keeps the see-through tonal fill it always had.
                .glass(
                    shape = shape,
                    tint = colors.primaryContainer.copy(alpha = SearchFabGlassTint),
                    fallback = colors.primaryContainer.copy(alpha = 0.88f),
                )
        )
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(SearchFabSize * SearchFabIconScale))
    }
}

/**
 * The search scallop's size: Nickel's add button exactly (Expressive's medium FAB), which also
 * sits where Nickel's Scaffold puts its FAB, centred 16dp above the navigation bar.
 */
val SearchFabSize = 80.dp

/** The magnifier as a fraction of [SearchFabSize], as Nickel's plus is of its button. */
private const val SearchFabIconScale = 0.44f

/**
 * How much primaryContainer the search scallop's glass keeps: Nickel's add-button strength,
 * so the screen's main action holds its own over the page scrolling beneath it.
 */
private const val SearchFabGlassTint = 0.5f
